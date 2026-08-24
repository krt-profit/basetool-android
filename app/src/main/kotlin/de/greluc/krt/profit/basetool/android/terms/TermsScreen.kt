/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.terms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.TermsClause
import de.greluc.krt.profit.basetool.android.core.data.TermsDocument
import de.greluc.krt.profit.basetool.android.core.data.TermsSection
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCheckboxRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHairlineRule
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModal
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModalTone
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import de.greluc.krt.profit.basetool.android.ui.isWideWindow
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Bullet marker; the design uses a disc, and Compose has no list primitive. */
private const val BULLET = "•  "

/** Indent of a bullet under its paragraph. */
private val BULLET_INDENT = 16.dp

/**
 * The consent gate — the document, the checkbox and the one action that gets past it.
 *
 * **The text is the server's, never this build's.** It arrives from `GET /api/v1/terms/document`
 * together with the version an acceptance is recorded against (main repo ADR-0138). A copy compiled
 * into the APK would show the wording this build shipped with while the server records consent
 * against whatever it currently has in force — and with distribution over GitHub Releases, that
 * drift is the steady state rather than a risk. A member reading one wording and agreeing to another
 * is not informed consent, so the app carries no copy at all.
 *
 * **The CTA is disabled until the box is ticked, and there is no scroll-to-bottom gate** (design
 * ch. 04). Forcing a scroll measures that a finger moved, not that anything was read, and it
 * punishes the member who genuinely wants to read on a large screen where the text already fits.
 *
 * **Declining is a logout, and it says so before it happens.** The confirmation names the
 * consequence rather than asking an abstract "are you sure" — refusing the terms means leaving the
 * tool, which is not obvious from a button labelled "Ablehnen".
 *
 * @param document the wording in force
 * @param accepting whether an acceptance is currently in flight
 * @param errorRes a message from a failed acceptance, or `null`
 * @param onAccept records consent
 * @param onDecline signs out
 * @param modifier layout modifier from the caller
 */
@Composable
fun TermsScreen(
    document: TermsDocument,
    accepting: Boolean,
    errorRes: Int?,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // rememberSaveable, not remember: a rotation must not silently untick a box the member ticked,
    // which would turn a disabled CTA into a mystery.
    var checked by rememberSaveable { mutableStateOf(false) }
    var confirmingDecline by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Header(document)

        // Design ch. 04 gives the Terms — and only the Terms — a split on the tablet: the
        // document on the left at a readable measure, the action rail on the right. Login and
        // the pending/app-lock screens keep their single 480 dp column, so this is not the
        // general wide layout of the auth family but this one screen's rule.
        //
        // Splitting matters here more than elsewhere: this is the one screen a member must read
        // before acting, and a full-tablet-width line of legal prose is the hardest thing to read
        // the app could put in front of them. The rail also keeps the CTA visible while they
        // scroll, instead of hiding the thing they are scrolling towards.
        if (isWideWindow()) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                TermsDocumentColumn(
                    document = document,
                    modifier = Modifier.weight(1f).widthIn(max = DOCUMENT_MAX_WIDTH),
                )
                ActionBar(
                    checked = checked,
                    onCheckedChange = { checked = it },
                    accepting = accepting,
                    errorRes = errorRes,
                    onAccept = onAccept,
                    onDecline = { confirmingDecline = true },
                    modifier = Modifier.width(ACTION_RAIL_WIDTH),
                )
            }
        } else {
            TermsDocumentColumn(document = document, modifier = Modifier.weight(1f))
            ActionBar(
                checked = checked,
                onCheckedChange = { checked = it },
                accepting = accepting,
                errorRes = errorRes,
                onAccept = onAccept,
                onDecline = { confirmingDecline = true },
            )
        }
    }

    if (confirmingDecline) {
        KrtModal(
            title = stringResource(R.string.terms_decline_title),
            confirmText = stringResource(R.string.terms_decline_confirm),
            onConfirm = onDecline,
            onDismiss = { confirmingDecline = false },
            tone = KrtModalTone.Danger,
            cancelText = stringResource(R.string.terms_decline_cancel),
        ) {
            Text(
                text = stringResource(R.string.terms_decline_body),
                style = MaterialTheme.typography.bodyMedium,
                color = KrtPalette.Gray1,
            )
        }
    }
}

/**
 * The scrolling document itself.
 *
 * Extracted so the phone's single column and the tablet's split can share it — the text is the
 * same text, and a second copy would be a second place for the prose to drift.
 *
 * @param document what to render.
 * @param modifier layout modifier; the caller decides the width and the weight.
 */
@Composable
private fun TermsDocumentColumn(
    document: TermsDocument,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = KrtSpacing.xl, vertical = KrtSpacing.lg),
    ) {
        Text(
            text = document.intro,
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.Gray1,
        )
        document.sections.forEach { section ->
            Spacer(Modifier.height(KrtSpacing.lg))
            SectionBlock(section)
        }
        Spacer(Modifier.height(KrtSpacing.lg))
        Text(
            text = document.lastUpdated,
            style = MaterialTheme.typography.labelSmall,
            color = KrtPalette.TextMuted,
        )
    }
}

/**
 * Widest the document column gets on a tablet.
 *
 * Design ch. 04 states the measure as "\u2264 80 ch"; at the body style's 16 sp Lato that is about
 * this many dp. Expressed in dp because a Compose width constraint cannot take characters, and
 * kept as one number so the reason survives next to it.
 */
private val DOCUMENT_MAX_WIDTH = 720.dp

/** Width of the action rail beside the document. */
private val ACTION_RAIL_WIDTH = 360.dp

/**
 * The fixed header: eyebrow, title, version and date.
 *
 * The version is shown because it is what the acceptance is recorded against — a member who is
 * re-prompted after a change can see that the document is a different one, which is otherwise
 * invisible.
 *
 * @param document the wording in force
 */
@Composable
private fun Header(document: TermsDocument) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(KrtPalette.Gray4)
                .padding(horizontal = KrtSpacing.xl, vertical = KrtSpacing.lg),
    ) {
        Text(
            text = stringResource(R.string.terms_eyebrow),
            style = MaterialTheme.typography.labelMedium,
            color = KrtPalette.TextMuted,
        )
        Spacer(Modifier.height(KrtSpacing.xs))
        Text(
            text = document.title,
            style = MaterialTheme.typography.titleLarge,
            color = KrtPalette.Orange,
        )
        Spacer(Modifier.height(KrtSpacing.xs))
        Text(
            text = stringResource(R.string.terms_version, document.version),
            style = MaterialTheme.typography.labelSmall,
            color = KrtPalette.TextMuted,
        )
    }
    KrtHairlineRule()
}

/**
 * One numbered section with its paragraphs and bullets.
 *
 * @param section the section to render
 */
@Composable
private fun SectionBlock(section: TermsSection) {
    Text(
        text = section.heading,
        style = MaterialTheme.typography.labelLarge,
        color = KrtPalette.Gray1,
    )
    section.clauses.forEach { clause ->
        Spacer(Modifier.height(KrtSpacing.xs))
        ClauseBlock(clause)
    }
}

/**
 * One paragraph and the bullets belonging to it.
 *
 * @param clause the paragraph to render
 */
@Composable
private fun ClauseBlock(clause: TermsClause) {
    Text(
        text = clause.text,
        style = MaterialTheme.typography.bodyMedium,
        color = KrtPalette.TextMuted,
    )
    clause.bullets.forEach { bullet ->
        Text(
            text = BULLET + bullet,
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.TextMuted,
            modifier = Modifier.padding(start = BULLET_INDENT, top = KrtSpacing.xs),
        )
    }
}

/**
 * The sticky bar that carries the one decision on this screen.
 *
 * @param checked whether the member ticked the box
 * @param onCheckedChange toggles it
 * @param accepting whether an acceptance is in flight
 * @param errorRes a message from a failed acceptance, or `null`
 * @param onAccept records consent
 * @param onDecline asks for confirmation before signing out
 */
@Composable
private fun ActionBar(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accepting: Boolean,
    errorRes: Int?,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KrtHairlineRule()
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(KrtPalette.Gray4)
                .padding(horizontal = KrtSpacing.xl, vertical = KrtSpacing.lg),
    ) {
        KrtCheckboxRow(
            checked = checked,
            onCheckedChange = onCheckedChange,
            label = stringResource(R.string.terms_checkbox),
            enabled = !accepting,
        )
        errorRes?.let { message ->
            Spacer(Modifier.height(KrtSpacing.sm))
            Text(
                text = stringResource(message),
                style = MaterialTheme.typography.bodyMedium,
                color = KrtPalette.DangerText,
            )
        }
        Spacer(Modifier.height(KrtSpacing.md))
        KrtCtaButton(
            text = stringResource(R.string.terms_accept),
            onClick = onAccept,
            // Both conditions, not just the box: a second tap while the first acceptance is still
            // in flight would post consent twice. The server is idempotent about it, but the member
            // would be looking at a button that appears to do nothing.
            enabled = checked && !accepting,
            iconRes = DesignR.drawable.ic_krt_clipboard_check,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(KrtSpacing.xs))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            KrtGhostButton(
                text = stringResource(R.string.terms_decline),
                onClick = onDecline,
                enabled = !accepting,
            )
        }
    }
}

/** A short stand-in document for the previews. */
private fun previewDocument(): TermsDocument =
    TermsDocument(
        version = "07d8b5ff678b80a2",
        title = "Nutzungsbedingungen",
        intro = "Diese Nutzungsbedingungen regeln die Nutzung des Profit Basetool.",
        sections =
            listOf(
                TermsSection(
                    heading = "1. Geltungsbereich und Vertragspartner",
                    clauses =
                        listOf(
                            TermsClause(
                                text = "Sie gelten zwischen dem Betreiber und allen Nutzern der Plattform.",
                                bullets = emptyList(),
                            ),
                        ),
                ),
                TermsSection(
                    heading = "4. Pflichten der Nutzer",
                    clauses =
                        listOf(
                            TermsClause(
                                text = "Der Nutzer verpflichtet sich insbesondere zu Folgendem:",
                                bullets =
                                    listOf(
                                        "Wahrheitsgemäße und aktuelle Angaben zum Profil zu machen.",
                                        "Nur freigegebene Client-Software an den Schnittstellen zu verwenden.",
                                    ),
                            ),
                        ),
                ),
            ),
        lastUpdated = "Stand dieser Nutzungsbedingungen: 05.08.2026",
    )

/**
 * Preview of the gate before the box is ticked.
 */
@Preview(name = "Terms — unchecked", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun TermsScreenPreview() {
    KrtTheme {
        TermsScreen(
            document = previewDocument(),
            accepting = false,
            errorRes = null,
            onAccept = {},
            onDecline = {},
        )
    }
}

/**
 * Preview after a failed acceptance.
 */
@Preview(name = "Terms — failed", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun TermsScreenErrorPreview() {
    KrtTheme {
        TermsScreen(
            document = previewDocument(),
            accepting = false,
            errorRes = R.string.terms_error,
            onAccept = {},
            onDecline = {},
        )
    }
}
