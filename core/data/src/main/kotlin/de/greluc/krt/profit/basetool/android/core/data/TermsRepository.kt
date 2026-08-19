/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiErrorMapper
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.await
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * The three questions the consent gate asks, as its caller needs them.
 *
 * A separate seam from the repository so the gate's logic — which is about *sequencing* three calls
 * and refusing to render without the middle one — can be exercised without a socket.
 */
interface TermsSource {
    /**
     * Reads whether the member has accepted the wording currently in force.
     *
     * @return the consent status, or a failure the caller can show
     */
    suspend fun status(): ApiResult<TermsStatus>

    /**
     * Reads the wording itself.
     *
     * @return the document, or a failure the caller can show
     */
    suspend fun document(): ApiResult<TermsDocument>

    /**
     * Records the member's consent to the version in force.
     *
     * @return the resulting status, or a failure the caller can show
     */
    suspend fun accept(): ApiResult<TermsStatus>
}

/**
 * Reads the Terms of Use and records consent to them.
 *
 * Separate from [AccountGateRepository] although both feed gates: that one answers a single enum
 * from a single endpoint, this one carries a document, its version and a write. Folding the two
 * together would put the app's only legal-text handling inside a class named after approvals.
 *
 * **Nothing is cached, and here that is not merely a default.** The document is the text a member is
 * about to agree to; a cached copy is a copy that can be older than the version consent is recorded
 * against, which is the exact failure this whole design (main repo ADR-0138) exists to remove.
 *
 * @property httpClient the API client, which supplies the bearer token through its interceptor
 * @property baseUrl the flavour's API origin
 * @property errorMapper turns a non-2xx response into a named [ApiError]
 * @property json tolerant reader; the server may add fields this build does not know
 */
class TermsRepository(
    private val httpClient: OkHttpClient,
    private val baseUrl: String,
    private val errorMapper: ApiErrorMapper = ApiErrorMapper(),
    private val json: Json = DEFAULT_JSON,
) : TermsSource {
    /**
     * Reads the consent status.
     *
     * A `TERMS_ACCEPTANCE_REQUIRED` refusal **is** the answer — it says "not accepted", which is
     * what was asked — so it becomes a successful `false` rather than an error. The version is lost
     * with it, because a problem body carries none; the caller has to cope with a `null` version
     * anyway, for a server that predates the field.
     *
     * @return the consent status, or a failure the caller can show
     */
    override suspend fun status(): ApiResult<TermsStatus> =
        when (val result = get(STATUS_PATH, TermsStatusDto.serializer())) {
            is ApiResult.Success -> {
                ApiResult.Success(result.value.toModel())
            }

            is ApiResult.Failure -> {
                if (result.error is ApiError.TermsAcceptanceRequired) {
                    ApiResult.Success(TermsStatus(accepted = false, version = null))
                } else {
                    result
                }
            }
        }

    /**
     * Reads the wording in force.
     *
     * @return the document, or a failure the caller can show
     */
    override suspend fun document(): ApiResult<TermsDocument> =
        when (val result = get(DOCUMENT_PATH, TermsDocumentDto.serializer())) {
            is ApiResult.Success -> ApiResult.Success(result.value.toModel())
            is ApiResult.Failure -> result
        }

    /**
     * Records consent.
     *
     * **Sends no request body**, and that is the server's contract rather than an omission (main
     * repo REQ-SEC-028): the version accepted is the one the server has in force, never a value the
     * client names. A client-supplied version would let a caller accept an older wording and pass
     * the gate without ever having seen the current one.
     *
     * @return the resulting status, or a failure the caller can show
     */
    override suspend fun accept(): ApiResult<TermsStatus> =
        when (
            val result =
                call(
                    ACCEPTANCE_PATH,
                    Request.Builder().url("$baseUrl$ACCEPTANCE_PATH".toHttpUrl()).post(EMPTY_BODY),
                    TermsStatusDto.serializer(),
                )
        ) {
            is ApiResult.Success -> ApiResult.Success(result.value.toModel())
            is ApiResult.Failure -> result
        }

    /**
     * Performs one authenticated GET.
     *
     * @param T the response type
     * @param path the API path, beginning with a slash
     * @param deserializer the serializer for [T]
     * @return the parsed value, or the classified failure
     */
    private suspend fun <T> get(
        path: String,
        deserializer: DeserializationStrategy<T>,
    ): ApiResult<T> = call(path, Request.Builder().url("$baseUrl$path".toHttpUrl()).get(), deserializer)

    /**
     * Executes one request and parses its body.
     *
     * @param T the response type
     * @param path the API path, for the diagnostic only
     * @param builder the prepared request
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
            KrtLog.w(LOG_TAG, io) { "terms request failed before a response arrived: $path" }
            ApiResult.Failure(ApiError.Network(io))
        } catch (malformed: SerializationException) {
            // A 200 whose body cannot be read is a broken server contract, not connectivity.
            KrtLog.w(LOG_TAG, malformed) { "terms response could not be parsed: $path" }
            ApiResult.Failure(ApiError.Server(status = HTTP_OK, problem = null))
        }

    private companion object {
        /** Log subsystem. The wording is public, but no member identity is ever written here. */
        const val LOG_TAG = "terms"

        /** Reachable while the consent gate itself is closed. */
        const val STATUS_PATH = "/api/v1/terms/status"

        /** Anonymous on the server (main repo ADR-0138); sent with a token here regardless. */
        const val DOCUMENT_PATH = "/api/v1/terms/document"

        /** Records consent; takes no request body. */
        const val ACCEPTANCE_PATH = "/api/v1/terms/acceptance"

        /** The status an unreadable body is reported under, since the response itself was fine. */
        const val HTTP_OK = 200

        /** The empty body the acceptance POST sends. */
        val EMPTY_BODY = ByteArray(0).toRequestBody()

        /** Ignores fields this build does not know, so a server addition is not a breaking change. */
        val DEFAULT_JSON = Json { ignoreUnknownKeys = true }
    }
}

/**
 * Wire shape of `GET /api/v1/terms/status` and of the acceptance response.
 *
 * @property accepted whether consent to [currentVersion] is on record
 * @property currentVersion the version in force
 */
@Serializable
private data class TermsStatusDto(
    @SerialName("accepted") val accepted: Boolean? = null,
    @SerialName("currentVersion") val currentVersion: String? = null,
) {
    /**
     * Maps to the model.
     *
     * @return the consent status
     */
    fun toModel(): TermsStatus = TermsStatus(accepted = accepted == true, version = currentVersion)
}

/**
 * Wire shape of `GET /api/v1/terms/document` (main repo ADR-0138).
 *
 * Every field is nullable although the server always sends them: a DTO that cannot represent a
 * missing field turns an unexpected body into a parse exception, and here that would be a crash on
 * the consent gate instead of an error the member can act on.
 *
 * @property version content digest of this wording
 * @property title the document heading
 * @property intro the lead paragraph
 * @property sections the numbered sections
 * @property lastUpdated the "Stand ..." line
 */
@Serializable
private data class TermsDocumentDto(
    val version: String? = null,
    val title: String? = null,
    val intro: String? = null,
    val sections: List<TermsSectionDto> = emptyList(),
    val lastUpdated: String? = null,
) {
    /**
     * Maps to the model.
     *
     * @return the document
     */
    fun toModel(): TermsDocument =
        TermsDocument(
            version = version.orEmpty(),
            title = title.orEmpty(),
            intro = intro.orEmpty(),
            sections = sections.map { it.toModel() },
            lastUpdated = lastUpdated.orEmpty(),
        )
}

/**
 * Wire shape of one section.
 *
 * @property heading the heading including its number
 * @property clauses the paragraphs
 */
@Serializable
private data class TermsSectionDto(
    val heading: String? = null,
    val clauses: List<TermsClauseDto> = emptyList(),
) {
    /**
     * Maps to the model.
     *
     * @return the section
     */
    fun toModel(): TermsSection = TermsSection(heading.orEmpty(), clauses.map { it.toModel() })
}

/**
 * Wire shape of one paragraph.
 *
 * @property text the paragraph
 * @property bullets the list items under it
 */
@Serializable
private data class TermsClauseDto(
    val text: String? = null,
    val bullets: List<String> = emptyList(),
) {
    /**
     * Maps to the model.
     *
     * @return the clause
     */
    fun toModel(): TermsClause = TermsClause(text.orEmpty(), bullets)
}
