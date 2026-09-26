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
import androidx.compose.foundation.layout.fillMaxHeight
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
import de.greluc.krt.profit.basetool.android.core.designsystem.component.krtUppercase
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
 * The consent gate: the document, the checkbox and the accept action.
 *
 * The text comes from `GET /api/v1/terms/document` with the version acceptance is recorded against;
 * the app bundles no copy. The CTA is enabled by the checkbox alone, with no scroll gate. Declining
 * signs out after a confirmation that says so.
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

        if (isWideWindow()) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopStart) {
                    TermsDocumentColumn(
                        document = document,
                        modifier = Modifier.widthIn(max = DOCUMENT_MAX_WIDTH).fillMaxHeight(),
                    )
                }
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
 * The scrolling document, shared by the phone's single column and the tablet's split.
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
                .padding(horizontal = KrtSpacing.s24, vertical = KrtSpacing.s16),
    ) {
        Text(
            text = document.intro,
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.Gray1,
        )
        document.sections.forEach { section ->
            Spacer(Modifier.height(KrtSpacing.s16))
            SectionBlock(section)
        }
        Spacer(Modifier.height(KrtSpacing.s16))
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
 * The version is the one the acceptance is recorded against.
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
                .padding(horizontal = KrtSpacing.s24, vertical = KrtSpacing.s16),
    ) {
        Text(
            text = stringResource(R.string.terms_eyebrow).krtUppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = KrtPalette.TextMuted,
        )
        Spacer(Modifier.height(KrtSpacing.s4))
        Text(
            text = document.title.krtUppercase(),
            style = MaterialTheme.typography.titleLarge,
            color = KrtPalette.Orange,
        )
        Spacer(Modifier.height(KrtSpacing.s4))
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
        Spacer(Modifier.height(KrtSpacing.s4))
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
            modifier = Modifier.padding(start = BULLET_INDENT, top = KrtSpacing.s4),
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
                .padding(horizontal = KrtSpacing.s24, vertical = KrtSpacing.s16),
    ) {
        KrtCheckboxRow(
            checked = checked,
            onCheckedChange = onCheckedChange,
            label = stringResource(R.string.terms_checkbox),
            enabled = !accepting,
        )
        errorRes?.let { message ->
            Spacer(Modifier.height(KrtSpacing.s8))
            Text(
                text = stringResource(message),
                style = MaterialTheme.typography.bodyMedium,
                color = KrtPalette.DangerText,
            )
        }
        Spacer(Modifier.height(KrtSpacing.s12))
        KrtCtaButton(
            text = stringResource(R.string.terms_accept),
            onClick = onAccept,
            enabled = checked && !accepting,
            iconRes = DesignR.drawable.ic_krt_clipboard_check,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(KrtSpacing.s4))
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
