/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.contract.KrtJson
import de.greluc.krt.profit.basetool.android.core.contract.model.BlueprintCraftabilityDto
import de.greluc.krt.profit.basetool.android.core.contract.model.BlueprintImportApplyRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.BlueprintImportEntryDto
import de.greluc.krt.profit.basetool.android.core.contract.model.BlueprintImportPreviewDto
import de.greluc.krt.profit.basetool.android.core.contract.model.BlueprintImportResolutionDto
import de.greluc.krt.profit.basetool.android.core.contract.model.BlueprintImportResultDto
import de.greluc.krt.profit.basetool.android.core.contract.model.BlueprintOverviewOwnerDto
import de.greluc.krt.profit.basetool.android.core.contract.model.BlueprintProductDto
import de.greluc.krt.profit.basetool.android.core.contract.model.BlueprintRequirementIngredientDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponseBlueprintOverviewEntryDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponsePersonalBlueprintResponse
import de.greluc.krt.profit.basetool.android.core.contract.model.PersonalBlueprintBatchCreateRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.PersonalBlueprintBatchResult
import de.greluc.krt.profit.basetool.android.core.contract.model.PersonalBlueprintBulkDeleteResult
import de.greluc.krt.profit.basetool.android.core.contract.model.PersonalBlueprintCreateRequest
import de.greluc.krt.profit.basetool.android.core.contract.model.PersonalBlueprintRecipeResponse
import de.greluc.krt.profit.basetool.android.core.contract.model.PersonalBlueprintResponse
import de.greluc.krt.profit.basetool.android.core.contract.model.PersonalBlueprintUpdateRequest
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient

/**
 * One blueprint the member owns.
 *
 * @property id the server id
 * @property productKey the catalogue key the entry was created from
 * @property productName the product, as the catalogue names it
 * @property note the member's own note, or `null`
 * @property acquiredAt when they got it, as the server wrote it, or `null`
 * @property removable whether the server will let this entry go — an entry it holds on to must not
 *   be offered a delete that then answers 409
 * @property version the optimistic lock, echoed on the next save
 */
data class OwnedBlueprint(
    val id: String,
    val productKey: String?,
    val productName: String,
    val note: String?,
    val acquiredAt: String?,
    val removable: Boolean,
    val version: Long?,
)

/** One page of owned blueprints.
 *
 * @property items the rows on this page
 * @property page the zero-based page index
 * @property totalElements how many exist in total
 * @property totalPages how many pages exist
 */
data class OwnedBlueprintPage(
    val items: List<OwnedBlueprint>,
    val page: Int,
    val totalElements: Long,
    val totalPages: Int,
) {
    /** Whether another page exists after this one. */
    val hasMore: Boolean get() = page + 1 < totalPages
}

/**
 * What one material contributes to a blueprint's craftability.
 *
 * @property name the material
 * @property requiredScu how much one build needs
 * @property availableScu how much the member can reach
 * @property missingScu the shortfall, or `null` when the server did not state one
 * @property missingScuWithRefinery the shortfall once refining is allowed for — its own field,
 *   because refining changes which materials fall short and by how much
 */
data class CraftabilityMaterial(
    val name: String,
    val requiredScu: Double?,
    val availableScu: Double?,
    val missingScu: Double?,
    val missingScuWithRefinery: Double?,
)

/**
 * Whether a blueprint can be built, and what stops it.
 *
 * @property blueprintId which owned entry this belongs to
 * @property recipeResolved whether the server could resolve a recipe at all; when it could not,
 *   nothing below it means anything
 * @property craftable how many can be built from what is reachable now
 * @property craftableWithRefinery the same count once refining is allowed for
 * @property limitingMaterial the material that runs out first, or `null`
 * @property limitingMaterialWithRefinery the same, with refining
 * @property materials the breakdown, for the member who wants to know why
 */
data class Craftability(
    val blueprintId: String,
    val recipeResolved: Boolean,
    val craftable: Int,
    val craftableWithRefinery: Int,
    val limitingMaterial: String?,
    val limitingMaterialWithRefinery: String?,
    val materials: List<CraftabilityMaterial>,
) {
    /**
     * How many materials fall short.
     *
     * @param withRefinery whether refining counts towards what is reachable.
     * @return the count the chip states.
     */
    fun missingCount(withRefinery: Boolean): Int =
        materials.count { material ->
            val missing = if (withRefinery) material.missingScuWithRefinery else material.missingScu
            (missing ?: 0.0) > 0
        }
}

/**
 * One overview page as the screen holds it.
 *
 * A row without a product key is dropped: the key is how its owners are asked for, so a row
 * without one is a card that can never fill in its own second line.
 *
 * @receiver what the server sent.
 * @param page which page was asked for; the response does not always echo it.
 * @return the page.
 */
private fun PageResponseBlueprintOverviewEntryDto.toModel(page: Int): BlueprintOverviewPage =
    BlueprintOverviewPage(
        entries =
            content.orEmpty().mapNotNull { row ->
                row.productKey?.takeIf { it.isNotBlank() }?.let {
                    BlueprintOverviewEntry(
                        productKey = it,
                        productName = row.productName.orEmpty(),
                        ownerCount = row.ownerCount ?: 0L,
                    )
                }
            },
        page = this.page ?: page,
        totalPages = totalPages ?: 1,
        totalElements = totalElements ?: 0L,
    )

/**
 * One product the member could add.
 *
 * @property productKey what a create sends
 * @property name the product
 * @property manufacturer who makes it, or `null`
 * @property owned whether the member already has this one — offering it again would be a create
 *   the server refuses
 * @property variantCount how many blueprint variants produce it. Informational: no write carries a
 *   variant, and the Materialbörse's item half shows it so a product with several can be named
 *   precisely in the remark.
 */
data class BlueprintProduct(
    val productKey: String,
    val name: String,
    val manufacturer: String?,
    val owned: Boolean,
    val variantCount: Int = 0,
)

/**
 * One row of the org-wide blueprint overview.
 *
 * @property productKey the catalogue key, which is also how its owners are asked for.
 * @property productName what it is called.
 * @property ownerCount how many members in the caller's oversight scope hold it. `0` is a real
 *   answer — „nicht erfasst" — and not a missing one.
 */
data class BlueprintOverviewEntry(
    val productKey: String,
    val productName: String,
    val ownerCount: Long,
) {
    /** Whether nobody in scope holds it. */
    val unrecorded: Boolean get() = ownerCount <= 0
}

/**
 * One page of that overview.
 *
 * @property entries the rows.
 * @property page the zero-based page index.
 * @property totalPages how many pages exist.
 * @property totalElements how many blueprints the search matches.
 */
data class BlueprintOverviewPage(
    val entries: List<BlueprintOverviewEntry>,
    val page: Int,
    val totalPages: Int,
    val totalElements: Long,
) {
    /** Whether another page exists after this one. */
    val hasMore: Boolean get() = page + 1 < totalPages
}

/**
 * One member who holds a blueprint.
 *
 * @property name their display name, as the server rendered it.
 * @property orgUnitMember whether they belong to the org unit the caller is looking at. `false`
 *   means the row is visible through the **global blueprint sharing** rather than through the
 *   unit, which the screen says in so many words — otherwise a stranger's name in a unit list
 *   reads as a bug.
 */
data class BlueprintOwner(
    val name: String,
    val orgUnitMember: Boolean,
)

/**
 * What a batch add did.
 *
 * The server answers with three counts rather than with rows, and the sheet reports them verbatim:
 * design ch. 17 artboard 5 draws „2 übernommen · 1 bereits vorhanden", and inventing a total from
 * the number sent would hide exactly the case the line exists for.
 *
 * @property added how many were taken over.
 * @property alreadyOwned how many the member already had.
 * @property unresolved how many keys the catalogue could not resolve — normally zero, because the
 *   keys come from its own search, and worth showing when it is not.
 */
data class BlueprintBatchResult(
    val added: Int,
    val alreadyOwned: Int,
    val unresolved: Int,
) {
    /** Whether anything at all was taken over. */
    val anyAdded: Boolean get() = added > 0
}

/**
 * Reads and applies a blueprint export file.
 *
 * Its own seam rather than two more methods on [PersonalBlueprintSource]: the import is a pair of
 * calls with a state of their own between them, and only one of the two writes.
 */
interface BlueprintImportSource {
    /**
     * Reads an export file and answers what it found — **without writing anything**.
     *
     * The first of two steps. A one-step import would be a mass write with no preview, which is
     * exactly what design ch. 18 §2 refuses.
     *
     * @param fileName the name the member picked it under; servers log it, so it says where the
     *   bytes came from rather than being invented.
     * @param bytes the file's content, read on the device and sent once.
     * @return what the file contains, or the classified failure. A file the server cannot parse
     *   comes back as an ordinary failure, not as an empty preview.
     */
    suspend fun importPreview(
        fileName: String,
        bytes: ByteArray,
    ): ApiResult<BlueprintImportPreview>

    /**
     * Writes the lines the preview resolved. The only step of the two that writes.
     *
     * @param entries the resolved lines to take over; each carries its own product key.
     * @return what was written, or the classified failure.
     */
    suspend fun importApply(entries: List<BlueprintImportEntry>): ApiResult<BlueprintImportResult>
}

/**
 * The member's own blueprints, as a seam.
 */
interface PersonalBlueprintSource {
    /**
     * Reads one page.
     *
     * @param query a name fragment, or blank for everything.
     * @param page the zero-based page index.
     * @param pageSize how many rows to ask for.
     * @return the page, or the classified failure.
     */
    suspend fun page(
        query: String = "",
        page: Int = 0,
        pageSize: Int = PersonalBlueprintRepository.DEFAULT_PAGE_SIZE,
    ): ApiResult<OwnedBlueprintPage>

    /**
     * Reads the craftability of everything the member owns.
     *
     * One call for the whole list, which is how the server offers it: asking per row would be one
     * request per card on a screen that scrolls.
     *
     * @return the entries, keyed by blueprint id.
     */
    suspend fun craftability(): ApiResult<Map<String, Craftability>>

    /**
     * Adds a blueprint.
     *
     * @param productKey the catalogue key.
     * @param note an optional note.
     * @return the saved row.
     */
    suspend fun add(
        productKey: String,
        note: String?,
    ): ApiResult<OwnedBlueprint>

    /**
     * Changes an entry's note.
     *
     * @param id the row.
     * @param version the version the row was read at.
     * @param note the new note, or `null` to clear it.
     * @return the saved row, or the classified failure.
     */
    suspend fun updateNote(
        id: String,
        version: Long,
        note: String?,
    ): ApiResult<OwnedBlueprint>

    /**
     * Removes an entry.
     *
     * @param id the row.
     * @return success, or the classified failure.
     */
    suspend fun remove(id: String): ApiResult<Unit>

    /**
     * Searches the catalogue for something to add.
     *
     * @param query what the member typed.
     * @return the matches, capped by the server.
     */
    suspend fun products(query: String): ApiResult<List<BlueprintProduct>>

    /**
     * Takes over several products at once.
     *
     * `POST /personal-blueprints/batch`, which carries **only** the keys — no note, no acquisition
     * date. A single add keeps [add], which does carry both.
     *
     * @param productKeys which products; the server skips the ones already owned.
     * @return what it did, or the classified failure.
     */
    suspend fun addAll(productKeys: List<String>): ApiResult<BlueprintBatchResult>

    /**
     * Reads one page of the org-wide blueprint overview.
     *
     * Officer and above, in the caller's oversight scope — the server decides, and the app asks
     * `GET /me/capabilities` (`canSeeBlueprintOverview`) before offering the screen at all.
     *
     * @param query a blueprint-name fragment, or blank for everything.
     * @param page the zero-based page index.
     * @param pageSize how many rows to ask for.
     * @return the page, or the classified failure.
     */
    suspend fun overview(
        query: String = "",
        page: Int = 0,
        pageSize: Int = PersonalBlueprintRepository.DEFAULT_PAGE_SIZE,
    ): ApiResult<BlueprintOverviewPage>

    /**
     * Reads who holds one blueprint.
     *
     * A **separate** call per row, as the web does it: the overview page carries counts only, and
     * one row's failure must not take the list with it (design ch. 17 artboard 6 draws all three
     * states — loading, empty, failed — per row).
     *
     * @param productKey which blueprint.
     * @return its owners, or the classified failure.
     */
    suspend fun owners(productKey: String): ApiResult<List<BlueprintOwner>>

    /**
     * Reads the recipe of one owned blueprint — its ingredients and their required quality.
     *
     * @param id the owned-blueprint row, resolved server-side against the caller's own.
     * @return the recipe, or a failure the detail pane can show.
     */
    suspend fun recipe(id: String): ApiResult<BlueprintRecipe>

    /**
     * Deletes **every** blueprint the member owns, in one call.
     *
     * The endpoint takes neither ids nor a body: it is all or nothing. That is why the screen
     * reaches it through „Alles wählen" rather than through a menu entry — deleting 41 rows is for
     * somebody who has seen the 41 rows (design ch. 18 §3).
     *
     * @return how many were deleted, or the classified failure.
     */
    suspend fun removeAll(): ApiResult<Int>
}

/**
 * How one line of an import file resolved against the product catalogue.
 *
 * Five states on the wire, three buckets on screen. `MATCHED` and `MATCHED_BY_ALIAS` are ready to
 * write; `ALREADY_OWNED` is not a result; `UNMATCHED` is skipped. `SUGGESTED` is the awkward one:
 * the server found fuzzy candidates but resolved nothing, so those rows need a human pick that this
 * app has no picker for — see [BlueprintImportPreview.unresolved].
 */
enum class BlueprintImportStatus {
    /** Resolved outright. */
    MATCHED,

    /** Resolved through an alias somebody taught the server earlier. */
    MATCHED_BY_ALIAS,

    /** Candidates were found, nothing was resolved; a pick is needed. */
    SUGGESTED,

    /** Nothing was found. */
    UNMATCHED,

    /** Resolved, and the member already owns it. */
    ALREADY_OWNED,

    /** A state this build does not know; treated as unresolvable rather than guessed at. */
    UNKNOWN,
    ;

    companion object {
        /**
         * Maps the wire value.
         *
         * @param raw as the server wrote it.
         * @return the status, or [UNKNOWN].
         */
        fun from(raw: String?): BlueprintImportStatus =
            entries.firstOrNull { it.name == raw?.trim()?.uppercase() } ?: UNKNOWN
    }
}

/**
 * One line of the file, and what became of it.
 *
 * @property externalName the name exactly as it stood in the export — what is shown when nothing
 *   resolved, because it is the only thing the member can recognise the line by.
 * @property status how it resolved.
 * @property productKey the resolved product, or `null`. `SUGGESTED` and `UNMATCHED` carry none.
 * @property productName what the resolved product is called, or `null`.
 * @property acquiredAt when the export says it was acquired, or `null`.
 */
data class BlueprintImportEntry(
    val externalName: String,
    val status: BlueprintImportStatus,
    val productKey: String?,
    val productName: String?,
    val acquiredAt: String?,
)

/**
 * What the preview found, before anything is written.
 *
 * @property entries every line of the file, in the order it stood there.
 */
data class BlueprintImportPreview(
    val entries: List<BlueprintImportEntry>,
) {
    /** The lines that will be written — resolved, and not already owned. */
    val importable: List<BlueprintImportEntry>
        get() = entries.filter { it.productKey != null && it.status != BlueprintImportStatus.ALREADY_OWNED }

    /** The lines the member already has. Not a result, so they are a number and never a list. */
    val alreadyOwned: Int get() = entries.count { it.status == BlueprintImportStatus.ALREADY_OWNED }

    /**
     * The lines nothing can be done with here.
     *
     * `UNMATCHED` because the server found nothing, and `SUGGESTED` because it found candidates but
     * resolved none — and picking between them is a control this app does not have. Both are
     * **skipped, not refused**: the import runs anyway, which is why they are counted and named
     * rather than turned into an error.
     */
    val unresolved: List<BlueprintImportEntry>
        get() =
            entries.filter {
                it.productKey == null && it.status != BlueprintImportStatus.ALREADY_OWNED
            }
}

/**
 * What the apply wrote.
 *
 * @property added rows created.
 * @property skipped rows the server passed over.
 * @property alreadyOwned rows that were already there.
 */
data class BlueprintImportResult(
    val added: Int,
    val skipped: Int,
    val alreadyOwned: Int,
)

/**
 * Reads and writes the member's own blueprints.
 *
 * Me-scoped like [PersonalInventoryRepository]: no path here names a user, and the `/overview`
 * family that does — who else owns what — is deliberately not touched.
 *
 * @property reader performs the calls and classifies their failures
 */
class PersonalBlueprintRepository(
    private val reader: ApiReader,
) : PersonalBlueprintSource,
    BlueprintImportSource {
    /**
     * Convenience constructor for the object graph.
     *
     * @param httpClient the API client, which supplies the bearer token and the mandatory headers
     * @param baseUrl the flavour's API origin
     */
    constructor(httpClient: OkHttpClient, baseUrl: String) : this(
        ApiReader(httpClient = httpClient, baseUrl = baseUrl, json = KrtJson, logTag = LOG_TAG),
    )

    override suspend fun page(
        query: String,
        page: Int,
        pageSize: Int,
    ): ApiResult<OwnedBlueprintPage> {
        val params =
            buildList {
                query.trim().takeIf { it.isNotEmpty() }?.let { add(QUERY_PARAM to it) }
                add(PAGE_PARAM to page.toString())
                add(SIZE_PARAM to pageSize.toString())
            }
        return when (
            val result = reader.get(PATH, params, PageResponsePersonalBlueprintResponse.serializer())
        ) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.toModel(page))
        }
    }

    override suspend fun craftability(): ApiResult<Map<String, Craftability>> =
        when (
            val result =
                reader.get(
                    CRAFTABILITY_PATH,
                    listOf(REFINERY_PARAM to true.toString()),
                    ListSerializer(BlueprintCraftabilityDto.serializer()),
                )
        ) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                ApiResult.Success(
                    result.value.mapNotNull { it.toModel() }.associateBy { it.blueprintId },
                )
            }
        }

    override suspend fun add(
        productKey: String,
        note: String?,
    ): ApiResult<OwnedBlueprint> =
        when (
            val result =
                reader.post(
                    PATH,
                    PersonalBlueprintCreateRequest(productKey = productKey, note = note),
                    PersonalBlueprintCreateRequest.serializer(),
                    PersonalBlueprintResponse.serializer(),
                )
        ) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.toModel())
        }

    override suspend fun updateNote(
        id: String,
        version: Long,
        note: String?,
    ): ApiResult<OwnedBlueprint> =
        when (
            val result =
                reader.put(
                    "$PATH/$id",
                    PersonalBlueprintUpdateRequest(version = version, note = note),
                    PersonalBlueprintUpdateRequest.serializer(),
                    PersonalBlueprintResponse.serializer(),
                )
        ) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.toModel())
        }

    override suspend fun remove(id: String): ApiResult<Unit> = reader.delete("$PATH/$id")

    /**
     * Reads one blueprint's recipe.
     *
     * @param id the owned-blueprint row.
     * @return the recipe, or the classified failure.
     */

    override suspend fun importPreview(
        fileName: String,
        bytes: ByteArray,
    ): ApiResult<BlueprintImportPreview> =
        when (
            val result =
                reader.postFile(
                    path = IMPORT_PREVIEW_PATH,
                    partName = FILE_PART,
                    fileName = fileName,
                    bytes = bytes,
                    mediaType = JSON_MEDIA_TYPE,
                    deserializer = BlueprintImportPreviewDto.serializer(),
                )
        ) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.toModel())
        }

    override suspend fun importApply(
        entries: List<BlueprintImportEntry>,
    ): ApiResult<BlueprintImportResult> {
        val request =
            BlueprintImportApplyRequest(
                resolutions =
                    entries.mapNotNull { entry ->
                        entry.productKey?.let {
                            BlueprintImportResolutionDto(
                                externalName = entry.externalName,
                                productKey = it,
                                acquiredAt = entry.acquiredAt,
                            )
                        }
                    },
            )
        return when (
            val result =
                reader.post(
                    IMPORT_APPLY_PATH,
                    request,
                    BlueprintImportApplyRequest.serializer(),
                    BlueprintImportResultDto.serializer(),
                )
        ) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.toModel())
        }
    }

    override suspend fun removeAll(): ApiResult<Int> =
        when (
            val result = reader.delete(PATH, PersonalBlueprintBulkDeleteResult.serializer())
        ) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.deleted ?: 0)
        }

    override suspend fun recipe(id: String): ApiResult<BlueprintRecipe> =
        when (
            val result =
                reader.get("$PATH/$id/recipe", PersonalBlueprintRecipeResponse.serializer())
        ) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.toModel())
        }

    override suspend fun addAll(productKeys: List<String>): ApiResult<BlueprintBatchResult> =
        when (
            val result =
                reader.post(
                    path = BATCH_PATH,
                    body = PersonalBlueprintBatchCreateRequest(productKeys = productKeys),
                    bodySerializer = PersonalBlueprintBatchCreateRequest.serializer(),
                    deserializer = PersonalBlueprintBatchResult.serializer(),
                )
        ) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                ApiResult.Success(
                    BlueprintBatchResult(
                        added = result.value.added ?: 0,
                        alreadyOwned = result.value.skippedAlreadyOwned ?: 0,
                        unresolved = result.value.skippedUnresolved ?: 0,
                    ),
                )
            }
        }

    override suspend fun overview(
        query: String,
        page: Int,
        pageSize: Int,
    ): ApiResult<BlueprintOverviewPage> {
        val params =
            listOfNotNull(
                query.trim().takeIf { it.isNotEmpty() }?.let { SEARCH_PARAM to it },
                PAGE_PARAM to page.toString(),
                SIZE_PARAM to pageSize.toString(),
            )
        return when (
            val result =
                reader.get(
                    OVERVIEW_PATH,
                    params,
                    PageResponseBlueprintOverviewEntryDto.serializer(),
                )
        ) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.toModel(page))
        }
    }

    override suspend fun owners(productKey: String): ApiResult<List<BlueprintOwner>> =
        when (
            val result =
                reader.get(
                    OWNERS_PATH,
                    listOf(PRODUCT_KEY_PARAM to productKey),
                    ListSerializer(BlueprintOverviewOwnerDto.serializer()),
                )
        ) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                ApiResult.Success(
                    result.value.mapNotNull { row ->
                        row.ownerName?.takeIf { it.isNotBlank() }?.let {
                            BlueprintOwner(name = it, orgUnitMember = row.orgUnitMember == true)
                        }
                    },
                )
            }
        }

    override suspend fun products(query: String): ApiResult<List<BlueprintProduct>> {
        val params = listOf(QUERY_PARAM to query.trim(), LIMIT_PARAM to PRODUCT_LIMIT.toString())
        return when (
            val result =
                reader.get(PRODUCTS_PATH, params, ListSerializer(BlueprintProductDto.serializer()))
        ) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                ApiResult.Success(result.value.mapNotNull { it.toModel() })
            }
        }
    }

    companion object {
        /** Rows per page. */
        const val DEFAULT_PAGE_SIZE: Int = 30

        /** How many products the picker asks for; the screen says so when the answer is full. */
        const val PRODUCT_LIMIT: Int = 25

        private const val LOG_TAG = "personal-blueprints"
        private const val PATH = "/api/v1/personal-blueprints"
        private const val IMPORT_PREVIEW_PATH = "/api/v1/personal-blueprints/import/preview"
        private const val IMPORT_APPLY_PATH = "/api/v1/personal-blueprints/import/apply"
        private const val FILE_PART = "file"
        private const val JSON_MEDIA_TYPE = "application/json"
        private const val BATCH_PATH = "/api/v1/personal-blueprints/batch"
        private const val OVERVIEW_PATH = "/api/v1/personal-blueprints/overview"
        private const val OWNERS_PATH = "/api/v1/personal-blueprints/overview/owners"
        private const val SEARCH_PARAM = "search"
        private const val PRODUCT_KEY_PARAM = "productKey"
        private const val CRAFTABILITY_PATH = "/api/v1/personal-blueprints/craftability"
        private const val PRODUCTS_PATH = "/api/v1/blueprints/products/search"
        private const val QUERY_PARAM = "q"
        private const val PAGE_PARAM = "page"
        private const val SIZE_PARAM = "size"
        private const val LIMIT_PARAM = "limit"
        private const val REFINERY_PARAM = "includeRefinery"
    }
}

/**
 * Maps a page of rows.
 *
 * A row without an id is dropped for the same reason as everywhere else: it cannot be edited or
 * removed, so offering it produces a tap that does nothing.
 *
 * @param page the requested index.
 * @return the page.
 */
private fun PageResponsePersonalBlueprintResponse.toModel(page: Int): OwnedBlueprintPage =
    OwnedBlueprintPage(
        items = content.orEmpty().filter { !it.id.isNullOrBlank() }.map { it.toModel() },
        page = this.page ?: page,
        totalElements = totalElements ?: 0L,
        totalPages = totalPages ?: 0,
    )

/**
 * Maps one owned blueprint.
 *
 * `removable` defaults to **false** when the server did not say. Guessing the other way would offer
 * a delete the server then refuses, which reads as a broken button.
 *
 * @return the model.
 */
private fun PersonalBlueprintResponse.toModel(): OwnedBlueprint =
    OwnedBlueprint(
        id = id.orEmpty(),
        productKey = productKey?.takeIf { it.isNotBlank() },
        productName = productName.orEmpty(),
        note = note?.takeIf { it.isNotBlank() },
        acquiredAt = acquiredAt?.takeIf { it.isNotBlank() },
        removable = removable == true,
        version = version,
    )

/**
 * Maps one craftability entry.
 *
 * @return the model, or `null` when the entry names no blueprint and therefore cannot be shown
 *   against one.
 */
private fun BlueprintCraftabilityDto.toModel(): Craftability? {
    val id = blueprintId?.takeIf { it.isNotBlank() } ?: return null
    return Craftability(
        blueprintId = id,
        recipeResolved = recipeResolved == true,
        craftable = craftable ?: 0,
        craftableWithRefinery = craftableWithRefinery ?: 0,
        limitingMaterial = limitingMaterialName?.takeIf { it.isNotBlank() },
        limitingMaterialWithRefinery = limitingMaterialNameWithRefinery?.takeIf { it.isNotBlank() },
        materials =
            materials.orEmpty().map { material ->
                CraftabilityMaterial(
                    name = material.materialName.orEmpty(),
                    requiredScu = material.requiredScu,
                    availableScu = material.availableScu,
                    missingScu = material.missingScu,
                    missingScuWithRefinery = material.missingScuWithRefinery,
                )
            },
    )
}

/**
 * Maps one catalogue product.
 *
 * @return the model, or `null` without a key — a row that cannot be added is not worth offering.
 */
internal fun BlueprintProductDto.toModel(): BlueprintProduct? {
    val key = productKey?.takeIf { it.isNotBlank() } ?: return null
    return BlueprintProduct(
        productKey = key,
        name = name.orEmpty(),
        manufacturer = manufacturerName?.takeIf { it.isNotBlank() },
        owned = ownedByCurrentUser == true,
        variantCount = variantCount ?: 0,
    )
}

/**
 * What one owned blueprint is made of.
 *
 * @property productName what the blueprint produces.
 * @property variantCount how many variants the recipe covers; `1` for a plain one.
 * @property ingredients every ingredient, flattened out of the server's requirement groups.
 */
data class BlueprintRecipe(
    val productName: String,
    val variantCount: Int,
    val ingredients: List<BlueprintIngredient>,
)

/**
 * One ingredient of a recipe.
 *
 * **Both quantities are carried, not one plus a conversion.** They are the same amount in two
 * scales, and converting between them in the client is exactly the mistake that produced the
 * refinery's hundred-fold stock bug. A column labelled SCU renders [quantityScu]; a column
 * labelled units renders [quantityUnits].
 *
 * @property name the material.
 * @property kind what sort of ingredient it is, as the server names it.
 * @property quantityScu the amount in SCU, when the server states one.
 * @property quantityUnits the amount in whole units, when the server states one.
 * @property minQuality the lowest quality grade that still satisfies the requirement, or `null`
 *   when the ingredient has no quality dimension.
 * @property groupName the requirement group it came from, kept so a grouped recipe can still be
 *   read as one list without losing which alternative an ingredient belongs to.
 */
data class BlueprintIngredient(
    val name: String,
    val kind: String?,
    val quantityScu: Double?,
    val quantityUnits: Int?,
    val minQuality: Int?,
    val groupName: String?,
)

/**
 * Maps the wire recipe onto the model, flattening the requirement groups.
 *
 * The server sends ingredients twice over: once at the top level and once inside each requirement
 * group. Both are taken, keyed by name and group so an ingredient that appears in two groups stays
 * two rows — collapsing them would hide that the recipe offers a choice.
 *
 * @return the model.
 */
private fun PersonalBlueprintRecipeResponse.toModel(): BlueprintRecipe {
    val flat =
        ingredients.orEmpty().map { it.toModel(groupName = null) } +
            requirementGroups.orEmpty().flatMap { group ->
                group.ingredients.orEmpty().map { it.toModel(groupName = group.name) }
            }
    return BlueprintRecipe(
        productName = productName.orEmpty(),
        variantCount = variantCount ?: 1,
        ingredients = flat.filter { it.name.isNotBlank() },
    )
}

/**
 * Maps one wire ingredient.
 *
 * @param groupName the requirement group it sits in, or `null` at the top level.
 * @return the model.
 */
private fun BlueprintRequirementIngredientDto.toModel(groupName: String?): BlueprintIngredient =
    BlueprintIngredient(
        name = name.orEmpty(),
        kind = kind,
        quantityScu = quantityScu,
        quantityUnits = quantityUnits,
        minQuality = minQuality,
        groupName = groupName,
    )

/**
 * The preview, as the app reads it.
 *
 * @return the preview.
 */
private fun BlueprintImportPreviewDto.toModel(): BlueprintImportPreview =
    BlueprintImportPreview(entries = propertyEntries.orEmpty().map { it.toModel() })

/**
 * One line of it.
 *
 * @return the entry. A line with no external name is kept rather than dropped: it is still one of
 *   the file's rows, and the counts have to add up to what the member can see in their export.
 */
private fun BlueprintImportEntryDto.toModel(): BlueprintImportEntry =
    BlueprintImportEntry(
        externalName = externalName.orEmpty(),
        status = BlueprintImportStatus.from(status?.value),
        productKey = productKey?.takeIf { it.isNotBlank() },
        productName = productName?.takeIf { it.isNotBlank() },
        acquiredAt = suggestedAcquiredAt,
    )

/**
 * What the apply reported.
 *
 * @return the result.
 */
private fun BlueprintImportResultDto.toModel(): BlueprintImportResult =
    BlueprintImportResult(
        added = added ?: 0,
        skipped = skipped ?: 0,
        alreadyOwned = alreadyOwned ?: 0,
    )
