/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.contract.KrtJson
import de.greluc.krt.profit.basetool.android.core.contract.model.TermsClauseDto
import de.greluc.krt.profit.basetool.android.core.contract.model.TermsDocumentDto
import de.greluc.krt.profit.basetool.android.core.contract.model.TermsSectionDto
import de.greluc.krt.profit.basetool.android.core.contract.model.TermsStatusDto
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiErrorMapper
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
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
 * @property reader performs the calls and classifies their failures
 */
class TermsRepository(
    private val reader: ApiReader,
) : TermsSource {
    /**
     * Convenience constructor for the object graph.
     *
     * @param httpClient the API client, which supplies the bearer token through its interceptor
     * @param baseUrl the flavour's API origin
     */
    constructor(httpClient: OkHttpClient, baseUrl: String) : this(
        ApiReader(httpClient = httpClient, baseUrl = baseUrl, json = KrtJson, logTag = LOG_TAG),
    )

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
        when (val result = reader.get(STATUS_PATH, TermsStatusDto.serializer())) {
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
        when (val result = reader.get(DOCUMENT_PATH, TermsDocumentDto.serializer())) {
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
                reader.execute(
                    ACCEPTANCE_PATH,
                    Request.Builder().post(EMPTY_BODY),
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
    }
}

/**
 * Maps the consent status onto the model.
 *
 * `accepted == true` rather than `accepted != false`: the field is nullable in the generated
 * model, and a status the server did not state is not consent.
 *
 * @return the consent status
 */
private fun TermsStatusDto.toModel(): TermsStatus =
    TermsStatus(accepted = accepted == true, version = currentVersion)

/**
 * Maps the document onto the model.
 *
 * Every field is nullable in the generated model because the contract marks nothing required, so
 * the mapping decides what absent means: empty text rather than a parse failure. A DTO that cannot
 * represent a missing field would turn an unexpected body into a crash on the consent gate instead
 * of an error the member can act on.
 *
 * @return the document
 */
private fun TermsDocumentDto.toModel(): TermsDocument =
    TermsDocument(
        version = version.orEmpty(),
        title = title.orEmpty(),
        intro = intro.orEmpty(),
        sections = sections.orEmpty().map { it.toModel() },
        lastUpdated = lastUpdated.orEmpty(),
    )

/**
 * Maps one section onto the model.
 *
 * @return the section
 */
private fun TermsSectionDto.toModel(): TermsSection =
    TermsSection(heading.orEmpty(), clauses.orEmpty().map { it.toModel() })

/**
 * Maps one paragraph onto the model.
 *
 * @return the clause
 */
private fun TermsClauseDto.toModel(): TermsClause = TermsClause(text.orEmpty(), bullets.orEmpty())
