/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

/**
 * The Terms-of-Use wording in force, as the backend serves it (ADR-0138).
 *
 * The app carries no copy of the text; [version] travels with it so display and acceptance refer to
 * the same wording.
 *
 * @property version content digest of this wording
 * @property title the document's own heading
 * @property intro the lead paragraph, before the first numbered section
 * @property sections the numbered sections, in document order
 * @property lastUpdated the "Stand ..." line
 */
data class TermsDocument(
    val version: String,
    val title: String,
    val intro: String,
    val sections: List<TermsSection>,
    val lastUpdated: String,
)

/**
 * One numbered section of the document.
 *
 * @property heading the heading including its number — the numbering is part of the legal text and
 *   is cited as such, so it is rendered rather than derived from list position
 * @property clauses the section's paragraphs, in document order
 */
data class TermsSection(
    val heading: String,
    val clauses: List<TermsClause>,
)

/**
 * One paragraph and the bullets belonging to it.
 *
 * @property text the paragraph
 * @property bullets the list items under it; empty for a paragraph that has none
 */
data class TermsClause(
    val text: String,
    val bullets: List<String>,
)

/**
 * Whether the member has accepted the wording currently in force.
 *
 * @property accepted `true` once consent for [version] is on record
 * @property version the version in force, or `null` when the server did not say
 */
data class TermsStatus(
    val accepted: Boolean,
    val version: String?,
)
