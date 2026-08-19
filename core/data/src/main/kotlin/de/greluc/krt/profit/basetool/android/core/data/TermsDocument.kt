/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

/**
 * The Terms-of-Use wording in force, as the backend serves it.
 *
 * **The app carries no copy of this text.** It used to be unshippable any other way: the wording
 * lived in the web frontend's message bundle, and bundling it in the APK would have shown the member
 * the version this build was compiled with while the server recorded consent against whatever it has
 * in force. With distribution over GitHub Releases and Obtainium, adoption is slow and uneven, so
 * that drift would not have been a risk — it would have been the steady state, and a member reading
 * one wording while agreeing to another is not informed consent. The backend now serves it (main
 * repo ADR-0138), and this is the shape it arrives in.
 *
 * [version] travels with the text on purpose: it is the value an acceptance is recorded against, so
 * a client can display and accept in one exchange without the two referring to different wordings.
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
