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
 * Performs one authenticated request, parses its body and classifies its failures.
 *
 * - a transport failure is [ApiError.Network];
 * - a 2xx whose body will not parse is [ApiError.Server];
 * - everything else goes through [ApiErrorMapper], by problem `code` (ADR-0001).
 *
 * Interpreting the result is left to the caller.
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
     * Fetches a non-JSON body, such as a PDF or CSV report, reading it fully into memory.
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
     * Performs one GET whose answer may legitimately have no body, such as `204 No Content`; an empty
     * `200` body is treated the same way.
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
     * The parameters are encoded exactly once by `HttpUrl` and never logged (REQ-OBS-004).
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
     * Sends a body and parses the saved row that comes back, whose new `version` the next edit must
     * echo.
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
     * Sends a body and ignores the answer, e.g. an empty `202 Accepted` or a `201` the caller re-reads.
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
     * Replaces a row and ignores what comes back, for callers that re-read the row anyway.
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
     * Sends a `POST` that carries no body and ignores what comes back, for writes addressed entirely by
     * their path.
     *
     * @param path the API path, beginning with a slash
     * @return success, or the classified failure
     */
    suspend fun postAccepted(path: String): ApiResult<Unit> =
        withoutBody(path, Request.Builder().url("$baseUrl$path".toHttpUrl()).post(EMPTY_BODY))

    /**
     * Deletes a row, expecting `204 No Content`.
     *
     * @param path the API path, beginning with a slash
     * @return success, or the classified failure
     */
    suspend fun delete(path: String): ApiResult<Unit> =
        withoutBody(path, Request.Builder().url("$baseUrl$path".toHttpUrl()).delete())

    /**
     * Sends a `POST` with a JSON body whose answer may legitimately be empty, e.g. a book-out that
     * answers `204` once the stack is emptied.
     *
     * @param B the request type
     * @param path the API path, beginning with a slash
     * @param body the payload
     * @param bodySerializer the serializer for [B]
     * @return success on any 2xx, with or without a body, or the classified failure
     */
    suspend fun <B> postUnit(
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
     * Sends a `POST` that carries no body and parses what comes back, for writes addressed entirely by
     * their path.
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
     * @param T the response type
     * @param path the API path, beginning with a slash
     * @param partName the form field the server reads, `file` for every current caller
     * @param fileName the name sent with the part; it should say where the bytes came from
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
     * Sends a `PUT` that carries no body and parses what comes back, for writes addressed entirely by
     * their path.
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
     * Deletes a row and parses the answer, for a delete that returns the changed parent.
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
     * Builds and runs one body-carrying request under any verb, for verbs without a named method such
     * as `PATCH` or a `DELETE` with a body.
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
     * Executes a prepared request against the flavour's host and parses its body.
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
     * Compares by content, including the `ByteArray`.
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
