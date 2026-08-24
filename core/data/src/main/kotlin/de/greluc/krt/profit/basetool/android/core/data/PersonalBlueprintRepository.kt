/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.contract.KrtJson
import de.greluc.krt.profit.basetool.android.core.contract.model.BlueprintCraftabilityDto
import de.greluc.krt.profit.basetool.android.core.contract.model.BlueprintProductDto
import de.greluc.krt.profit.basetool.android.core.contract.model.BlueprintRequirementIngredientDto
import de.greluc.krt.profit.basetool.android.core.contract.model.PageResponsePersonalBlueprintResponse
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
 * One product the member could add.
 *
 * @property productKey what a create sends
 * @property name the product
 * @property manufacturer who makes it, or `null`
 * @property owned whether the member already has this one — offering it again would be a create
 *   the server refuses
 */
data class BlueprintProduct(
    val productKey: String,
    val name: String,
    val manufacturer: String?,
    val owned: Boolean,
)

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
     * Reads the recipe of one owned blueprint — its ingredients and their required quality.
     *
     * @param id the owned-blueprint row, resolved server-side against the caller's own.
     * @return the recipe, or a failure the detail pane can show.
     */
    suspend fun recipe(id: String): ApiResult<BlueprintRecipe>
}

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
) : PersonalBlueprintSource {
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
    override suspend fun recipe(id: String): ApiResult<BlueprintRecipe> =
        when (
            val result =
                reader.get("$PATH/$id/recipe", PersonalBlueprintRecipeResponse.serializer())
        ) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.toModel())
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
private fun BlueprintProductDto.toModel(): BlueprintProduct? {
    val key = productKey?.takeIf { it.isNotBlank() } ?: return null
    return BlueprintProduct(
        productKey = key,
        name = name.orEmpty(),
        manufacturer = manufacturerName?.takeIf { it.isNotBlank() },
        owned = ownedByCurrentUser == true,
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
