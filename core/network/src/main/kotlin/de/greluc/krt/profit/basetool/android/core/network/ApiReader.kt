/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.network

import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
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
        try {
            httpClient.newCall(builder.build()).await().use { response ->
                if (response.isSuccessful) {
                    ApiResult.Success(json.decodeFromString(deserializer, response.body.string()))
                } else {
                    ApiResult.Failure(errorMapper.map(response))
                }
            }
        } catch (io: IOException) {
            KrtLog.w(logTag, io) { "request failed before a response arrived: $path" }
            ApiResult.Failure(ApiError.Network(io))
        } catch (malformed: SerializationException) {
            // A 200 whose body cannot be read is a broken server contract, not a connectivity
            // problem. Reporting it as Network would tell the member to check their connection,
            // which is advice that cannot possibly help.
            KrtLog.w(logTag, malformed) { "response could not be parsed: $path" }
            ApiResult.Failure(ApiError.Server(status = HTTP_OK, problem = null))
        }

    private companion object {
        /** The status an unreadable body is reported under, since the response itself was fine. */
        const val HTTP_OK = 200
    }
}
