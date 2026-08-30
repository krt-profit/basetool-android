/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.network

import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * One authenticated request, its body parsed, its failures classified — the part every repository
 * repeats.
 *
 * It exists because the third copy was about to be written. The first two were fine: a repository
 * that owns its own request building is easy to read. What is not fine is that the **failure
 * semantics** were copied with them, and those are not obvious:
 *
 * - a transport failure is [ApiError.Network], because the member can act on it;
 * - a 2xx whose body will not parse is [ApiError.Server], **not** `Network` — the connection
 *   plainly worked, and telling somebody to check their connection is advice that cannot help;
 * - everything else goes through [ApiErrorMapper], which classifies by the backend's stable problem
 *   `code` rather than by HTTP status (ADR-0001).
 *
 * Three hand-copies of that would agree today and drift the first time one of them is "improved".
 *
 * What deliberately stays with the caller is *meaning*: which failures to fold into a success,
 * which absent field is an error, how to page-walk. This type answers "what came back", never "what
 * it means".
 *
 * @property httpClient the shared API client, which supplies the mandatory headers
 * @property baseUrl the flavour's API origin, e.g. `https://api.profit-base.online`
 * @property json the reader configured for this backend's wire format
 * @property logTag the subsystem name for diagnostics; no token, name or email is ever logged
 * @property errorMapper turns a non-2xx response into a named [ApiError]
 */
class ApiReader(
    private val httpClient: OkHttpClient,
    private val baseUrl: String,
    private val json: Json,
    private val logTag: String,
    private val errorMapper: ApiErrorMapper = ApiErrorMapper(),
) {
    /**
     * Performs one GET and parses its body.
     *
     * @param T the response type
     * @param path the API path, beginning with a slash
     * @param deserializer the serializer for [T]
     * @return the parsed value, or the classified failure
     */
    suspend fun <T> get(
        path: String,
        deserializer: DeserializationStrategy<T>,
    ): ApiResult<T> = call(path, Request.Builder().url("$baseUrl$path".toHttpUrl()).get(), deserializer)

    /**
     * Fetches a body that is not JSON.
     *
     * Reports have no schema to decode: the server answers a PDF or a CSV with a
     * `Content-Disposition` naming the file. Everything else the reader does — the bearer token,
     * the mandatory headers, the failure classification — applies unchanged.
     *
     * The whole body is read into memory. That is right for these two reports, which are a page of
     * bookings and a quarter of them; it would not be for something unbounded, and a streaming
     * variant should be its own function rather than a flag on this one.
     *
     * @param path where to fetch from.
     * @param params query parameters, in order.
     * @param headers extra headers this call needs beyond the client's own.
     * @return the bytes and the server's own filename, or the classified failure.
     */
    suspend fun getBytes(
        path: String,
        params: List<Pair<String, String>> = emptyList(),
        headers: List<Pair<String, String>> = emptyList(),
    ): ApiResult<DownloadedFile> =
        try {
            val url =
                "$baseUrl$path".toHttpUrl().newBuilder()
                    .apply { params.forEach { (name, value) -> addQueryParameter(name, value) } }
                    .build()
            val request =
                Request.Builder().url(url).get()
                    .apply { headers.forEach { (name, value) -> addHeader(name, value) } }
                    .build()
            httpClient.newCall(request).await().use { response ->
                if (!response.isSuccessful) {
                    ApiResult.Failure(errorMapper.map(response))
                } else {
                    ApiResult.Success(
                        DownloadedFile(
                            bytes = response.body.bytes(),
                            fileName = response.header("Content-Disposition").fileName(),
                            mediaType = response.body.contentType()?.toString(),
                        ),
                    )
                }
            }
        } catch (io: IOException) {
            KrtLog.w(logTag, io) { "download failed before a response arrived: $path" }
            ApiResult.Failure(ApiError.Network(io))
        }

    /**
     * Performs one GET whose answer may legitimately have **no body**.
     *
     * `GET /api/v1/announcement` is the case this exists for: it answers `204 No Content` when
     * there is nothing to announce, and that is a result, not a failure. Read through [get] the
     * empty body would fail to parse and surface as a broken server contract — an error banner
     * where the correct rendering is no banner at all.
     *
     * An empty body on a `200` is treated the same way. A server that answers "nothing" with a
     * zero-length body rather than a status is being sloppy, not broken, and the distinction is
     * invisible to the member either way.
     *
     * @param T the response type
     * @param path the API path, beginning with a slash
     * @param deserializer the serializer for [T]
     * @return the parsed value, `null` when the answer carried no body, or the classified failure
     */
    suspend fun <T> getOptional(
        path: String,
        deserializer: DeserializationStrategy<T>,
    ): ApiResult<T?> =
        try {
            httpClient.newCall(Request.Builder().url("$baseUrl$path".toHttpUrl()).get().build())
                .await()
                .use { response ->
                    when {
                        !response.isSuccessful -> {
                            ApiResult.Failure(errorMapper.map(response))
                        }

                        else -> {
                            val body = response.body.string()
                            if (body.isBlank()) {
                                ApiResult.Success(null)
                            } else {
                                ApiResult.Success(json.decodeFromString(deserializer, body))
                            }
                        }
                    }
                }
        } catch (io: IOException) {
            KrtLog.w(logTag, io) { "request failed before a response arrived: $path" }
            ApiResult.Failure(ApiError.Network(io))
        } catch (malformed: SerializationException) {
            KrtLog.w(logTag, malformed) { "response could not be parsed: $path" }
            ApiResult.Failure(ApiError.Server(status = HTTP_OK, problem = null))
        }

    /**
     * Performs one GET with query parameters and parses its body.
     *
     * The parameters are handed to `HttpUrl` as **raw** values and encoded exactly once, by it.
     * Building the query by string concatenation instead is how a member's search term containing
     * `&`, `=` or `+` either truncates the request or arrives double-encoded and matches nothing —
     * a failure that looks like "the server found nothing" rather than like a bug.
     *
     * The values never reach the diagnostic: [call] logs the bare path, and a search term is member
     * input (REQ-OBS-004 in the main repo).
     *
     * @param T the response type
     * @param path the API path, beginning with a slash
     * @param query the parameters, unencoded; a name may repeat for a list-valued parameter
     * @param deserializer the serializer for [T]
     * @return the parsed value, or the classified failure
     */
    suspend fun <T> get(
        path: String,
        query: List<Pair<String, String>>,
        deserializer: DeserializationStrategy<T>,
    ): ApiResult<T> {
        val url =
            "$baseUrl$path".toHttpUrl().newBuilder()
                .apply { query.forEach { (name, value) -> addQueryParameter(name, value) } }
                .build()
        return call(path, Request.Builder().url(url).get(), deserializer)
    }

    /**
     * Sends a body and parses what comes back.
     *
     * The server answers a write with the saved row, and that answer is not a courtesy: it carries
     * the **new `version`**, which the next edit has to echo. A client that ignored the response
     * and kept its old version would 409 on its own second save.
     *
     * @param B the request type
     * @param T the response type
     * @param path the API path, beginning with a slash
     * @param body the payload
     * @param bodySerializer the serializer for [B]
     * @param deserializer the serializer for [T]
     * @return the saved value, or the classified failure
     */
    suspend fun <B, T> post(
        path: String,
        body: B,
        bodySerializer: SerializationStrategy<B>,
        deserializer: DeserializationStrategy<T>,
    ): ApiResult<T> = send(path, "POST", body, bodySerializer, deserializer)

    /**
     * Replaces a row and parses what comes back.
     *
     * @param B the request type
     * @param T the response type
     * @param path the API path, beginning with a slash
     * @param body the payload, including the `version` read from the server
     * @param bodySerializer the serializer for [B]
     * @param deserializer the serializer for [T]
     * @return the saved value, or the classified failure — [ApiError.OptimisticLock] when somebody
     *   else saved first
     */
    suspend fun <B, T> put(
        path: String,
        body: B,
        bodySerializer: SerializationStrategy<B>,
        deserializer: DeserializationStrategy<T>,
    ): ApiResult<T> = send(path, "PUT", body, bodySerializer, deserializer)

    /**
     * Sends a body and expects no answer.
     *
     * For an endpoint whose answer the caller does not need — `202 Accepted` with an empty body,
     * which is what the live-sync signal gets, or a `201 Created` whose returned row the caller
     * re-reads anyway. Distinct from [post] for the reason [delete] is: handing an empty body to
     * the parser would turn every such success into a reported server error.
     *
     * @param B the request type
     * @param path the API path, beginning with a slash
     * @param body the payload
     * @param bodySerializer the serializer for [B]
     * @return success, or the classified failure
     */
    suspend fun <B> postAccepted(
        path: String,
        body: B,
        bodySerializer: SerializationStrategy<B>,
    ): ApiResult<Unit> =
        withoutBody(
            path,
            Request.Builder()
                .url("$baseUrl$path".toHttpUrl())
                .post(json.encodeToString(bodySerializer, body).toRequestBody(JSON_MEDIA_TYPE)),
        )

    /**
     * Replaces a row and ignores what comes back.
     *
     * The [put] sibling parses the answer, which is right when the caller needs the new version.
     * It is wrong when the caller re-reads the row anyway: a field the app never touches drifting
     * on the server would then fail a write that in fact succeeded.
     *
     * @param B the request type
     * @param path the API path, beginning with a slash
     * @param body the payload
     * @param bodySerializer the serializer for [B]
     * @return success, or the classified failure
     */
    suspend fun <B> putAccepted(
        path: String,
        body: B,
        bodySerializer: SerializationStrategy<B>,
    ): ApiResult<Unit> =
        withoutBody(
            path,
            Request.Builder()
                .url("$baseUrl$path".toHttpUrl())
                .put(json.encodeToString(bodySerializer, body).toRequestBody(JSON_MEDIA_TYPE)),
        )

    /**
     * Deletes a row.
     *
     * Separate from the three above because the answer is `204 No Content`: there is no body to
     * parse, and running it through the parser would turn every successful delete into a reported
     * server error.
     *
     * @param path the API path, beginning with a slash
     * @return success, or the classified failure
     */
    suspend fun delete(path: String): ApiResult<Unit> =
        withoutBody(path, Request.Builder().url("$baseUrl$path".toHttpUrl()).delete())

    /**
     * Sends a `POST` that carries no body and parses what comes back.
     *
     * Some writes are entirely addressed by their path — "put this member on this order" names
     * both in the URL and has nothing left to say in a payload. Sending `{}` instead would work
     * and would be a lie about the shape of the request.
     *
     * @param T the response type
     * @param path the API path, beginning with a slash
     * @param deserializer the serializer for [T]
     * @return the parsed answer, or the classified failure
     */
    suspend fun <T> post(
        path: String,
        deserializer: DeserializationStrategy<T>,
    ): ApiResult<T> =
        call(
            path,
            Request.Builder().url("$baseUrl$path".toHttpUrl()).post(EMPTY_BODY),
            deserializer,
        )

    /**
     * Uploads one file as `multipart/form-data` and parses what comes back.
     *
     * The hangar's Fleetview endpoint takes a file part rather than a JSON body, and it takes the
     * same part whether the member picked a file or pasted the export into a box — the paste is
     * turned into bytes here rather than becoming a second endpoint.
     *
     * @param T the response type
     * @param path the API path, beginning with a slash
     * @param partName the form field the server reads, `file` for every current caller
     * @param fileName the name sent with the part; servers log it, so it should say where the
     *   bytes came from rather than be invented
     * @param bytes the file's content
     * @param mediaType the part's content type
     * @param deserializer the serializer for [T]
     * @return the parsed answer, or the classified failure
     */
    @Suppress("LongParameterList")
    suspend fun <T> postFile(
        path: String,
        partName: String,
        fileName: String,
        bytes: ByteArray,
        mediaType: String,
        deserializer: DeserializationStrategy<T>,
    ): ApiResult<T> {
        val body =
            MultipartBody
                .Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(partName, fileName, bytes.toRequestBody(mediaType.toMediaType()))
                .build()
        return call(path, Request.Builder().url("$baseUrl$path".toHttpUrl()).post(body), deserializer)
    }

    /**
     * Sends a `PUT` that carries no body and parses what comes back.
     *
     * The sibling of the body-less `POST` above, for a write whose whole instruction is its path —
     * an account's all-members switch is `.../all-members/true`, and there is nothing left to put
     * in a payload.
     *
     * @param T the response type
     * @param path the API path, beginning with a slash
     * @param deserializer the serializer for [T]
     * @return the parsed answer, or the classified failure
     */
    suspend fun <T> put(
        path: String,
        deserializer: DeserializationStrategy<T>,
    ): ApiResult<T> =
        call(
            path,
            Request.Builder().url("$baseUrl$path".toHttpUrl()).put(EMPTY_BODY),
            deserializer,
        )

    /**
     * Deletes a row and parses the answer.
     *
     * The `Unit` variant above is for the `204` case. This one is for a delete that answers with
     * the parent it just changed — the assignee edge does, and the screen redraws the whole order
     * from it rather than guessing at the new version.
     *
     * @param T the response type
     * @param path the API path, beginning with a slash
     * @param query the parameters, unencoded; empty for a delete addressed entirely by its path
     * @param deserializer the serializer for [T]
     * @return the parsed answer, or the classified failure
     */
    suspend fun <T> delete(
        path: String,
        query: List<Pair<String, String>>,
        deserializer: DeserializationStrategy<T>,
    ): ApiResult<T> {
        val url =
            "$baseUrl$path".toHttpUrl().newBuilder()
                .apply { query.forEach { (name, value) -> addQueryParameter(name, value) } }
                .build()
        return call(path, Request.Builder().url(url).delete(), deserializer)
    }

    /**
     * Deletes a row and parses the answer, with nothing in the query.
     *
     * @param T the response type
     * @param path the API path, beginning with a slash
     * @param deserializer the serializer for [T]
     * @return the parsed answer, or the classified failure
     */
    suspend fun <T> delete(
        path: String,
        deserializer: DeserializationStrategy<T>,
    ): ApiResult<T> = delete(path, emptyList(), deserializer)

    /**
     * Builds and runs one body-carrying request under any verb.
     *
     * The escape hatch behind [post] and [put], public because this API uses two verbs they do not
     * cover: `PATCH`, and a `DELETE` that carries a body — the inventory's allocation endpoint
     * names its target in the payload rather than in the path, so removing one is a DELETE with
     * `{field, targetId, version}`. Reach for the named methods first; this is for the verbs that
     * have no named method rather than a second way to POST.
     *
     * @param B the request type
     * @param T the response type
     * @param path the API path
     * @param method the verb
     * @param body the payload
     * @param bodySerializer the serializer for [B]
     * @param deserializer the serializer for [T]
     * @return the parsed answer, or the classified failure
     */
    suspend fun <B, T> send(
        path: String,
        method: String,
        body: B,
        bodySerializer: SerializationStrategy<B>,
        deserializer: DeserializationStrategy<T>,
    ): ApiResult<T> =
        call(
            path,
            Request.Builder()
                .url("$baseUrl$path".toHttpUrl())
                .method(method, json.encodeToString(bodySerializer, body).toRequestBody(JSON_MEDIA_TYPE)),
            deserializer,
        )

    /**
     * Runs a request whose success carries no body.
     *
     * @param path the API path, used only in the diagnostic
     * @param builder the prepared request
     * @return success, or the classified failure
     */
    private suspend fun withoutBody(
        path: String,
        builder: Request.Builder,
    ): ApiResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                httpClient.newCall(builder.build()).await().use { response ->
                    if (response.isSuccessful) {
                        ApiResult.Success(Unit)
                    } else {
                        ApiResult.Failure(errorMapper.map(response))
                    }
                }
            } catch (io: IOException) {
                KrtLog.w(logTag, io) { "request failed before a response arrived: $path" }
                ApiResult.Failure(ApiError.Network(io))
            }
        }

    /**
     * Executes a prepared request and parses its body.
     *
     * The builder arrives without a URL so the caller cannot accidentally address a different host
     * than the flavour's; the path is applied here.
     *
     * @param T the response type
     * @param path the API path, beginning with a slash
     * @param builder the prepared request, without its URL
     * @param deserializer the serializer for [T]
     * @return the parsed value, or the classified failure
     */
    suspend fun <T> execute(
        path: String,
        builder: Request.Builder,
        deserializer: DeserializationStrategy<T>,
    ): ApiResult<T> = call(path, builder.url("$baseUrl$path".toHttpUrl()), deserializer)

    /**
     * Runs the call and classifies its outcome.
     *
     * @param T the response type
     * @param path the API path, used only in the diagnostic
     * @param builder the request, URL already applied
     * @param deserializer the serializer for [T]
     * @return the parsed value, or the classified failure
     */
    private suspend fun <T> call(
        path: String,
        builder: Request.Builder,
        deserializer: DeserializationStrategy<T>,
    ): ApiResult<T> =
        withContext(Dispatchers.IO) {
            try {
                httpClient.newCall(builder.build()).await().use { response ->
                    if (response.isSuccessful) {
                        ApiResult.Success(
                            json.decodeFromString(deserializer, response.body.string()),
                        )
                    } else {
                        ApiResult.Failure(errorMapper.map(response))
                    }
                }
            } catch (io: IOException) {
                KrtLog.w(logTag, io) { "request failed before a response arrived: $path" }
                ApiResult.Failure(ApiError.Network(io))
            } catch (malformed: SerializationException) {
                // A 200 whose body cannot be read is a broken server contract, not a connectivity
                // problem. Reporting it as Network would tell the member to check their
                // connection, which is advice that cannot possibly help.
                KrtLog.w(logTag, malformed) { "response could not be parsed: $path" }
                ApiResult.Failure(ApiError.Server(status = HTTP_OK, problem = null))
            }
        }

    private companion object {
        /** The status an unreadable body is reported under, since the response itself was fine. */
        const val HTTP_OK = 200

        /** What every write on this API sends and receives. */
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /** What a body-less write sends: nothing, with the length header OkHttp requires. */
        val EMPTY_BODY = ByteArray(0).toRequestBody(null, 0, 0)
    }
}

/**
 * A file the server sent.
 *
 * @property bytes its content.
 * @property fileName what the server called it, or `null` when it named none.
 * @property mediaType its content type, or `null`.
 */
data class DownloadedFile(
    val bytes: ByteArray,
    val fileName: String?,
    val mediaType: String?,
) {
    /**
     * Compares by content.
     *
     * `ByteArray` compares by identity, which would make two equal downloads unequal and is exactly
     * the trap a data class hides.
     *
     * @param other what to compare with.
     * @return whether the two carry the same file.
     */
    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is DownloadedFile &&
                    bytes.contentEquals(other.bytes) &&
                    fileName == other.fileName &&
                    mediaType == other.mediaType
            )

    /**
     * Hashes by content, to match [equals].
     *
     * @return the hash.
     */
    override fun hashCode(): Int =
        bytes.contentHashCode() * HASH_PRIME + (fileName?.hashCode() ?: 0) * HASH_PRIME +
            (mediaType?.hashCode() ?: 0)

    private companion object {
        /** An odd multiplier, as the platform's own data classes use. */
        const val HASH_PRIME = 31
    }
}

/**
 * The file name out of a `Content-Disposition` header.
 *
 * The server names the file — „kontoauszug-<id>.pdf" — and inventing one on the device would make
 * two systems disagree about what the same download is called.
 *
 * @return the name, or `null` when the header carries none.
 */
private fun String?.fileName(): String? =
    this?.substringAfter("filename=", "")
        ?.trim('"', ' ')
        ?.takeIf { it.isNotEmpty() }
