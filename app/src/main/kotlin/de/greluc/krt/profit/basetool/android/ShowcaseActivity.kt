/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.core.designsystem.R
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCardVariant
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChipTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtDepartmentTag
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEndOfList
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFanKitBand
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHeading
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHudBox
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtKeyValueRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtKpiCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtListRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadMore
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModal
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModalTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOfflineBanner
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOrgBadge
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOrgBadgeKind
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOutlineButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtPanelHeader
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtPresenceIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtQuietDangerButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSectionTitle
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusBadge
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusPill
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStepperField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSuccessButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtToast
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTotalTile
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtUpdateAvailablePill
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme

/**
 * Renders the KRT component library so it can be compared against the design references.
 *
 * This is a development surface, not a product screen: it exists to verify chapter 02 of the design
 * handoff (`docs/design/android/02 Components.dc.html`) on a real device at real densities and font
 * scales. The navigation shell and the actual screens replace it as they land.
 */
class ShowcaseActivity : ComponentActivity() {
    /**
     * Sets up the edge-to-edge window and hosts the component gallery.
     *
     * @param savedInstanceState the recreation state, unused here.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            KrtTheme {
                ComponentGallery()
            }
        }
    }
}

/** Vertical rhythm between gallery sections. */
private val SECTION_GAP = 24.dp

/**
 * The scrollable gallery of every component in the library.
 *
 * Grouped in the order of the design spec's component sheet so the two can be read side by side.
 */
@Composable
private fun ComponentGallery() {
    var modal by remember { mutableStateOf<KrtModalTone?>(null) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(KrtSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
    ) {
        KrtHeading("Komponenten")

        KrtSectionTitle("Buttons")
        Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm)) {
            KrtCtaButton("Anmelden", {}, iconRes = R.drawable.ic_krt_login)
            KrtSuccessButton("Check-In", {}, iconRes = R.drawable.ic_krt_check)
            KrtOutlineButton("Crew zuweisen", {}, iconRes = R.drawable.ic_krt_users)
            KrtGhostButton("Bearbeiten", {}, iconRes = R.drawable.ic_krt_edit)
            KrtQuietDangerButton("Löschen", {}, iconRes = R.drawable.ic_krt_trash)
            KrtGhostButton("Deaktiviert", {}, enabled = false)
        }

        KrtSectionTitle("Container")
        KrtHudBox {
            Text(
                text = "Nächster Einsatz",
                style = MaterialTheme.typography.titleMedium,
                color = KrtPalette.White,
            )
            Text(
                text = "Vertikaler Abbau — Lyria · heute 21:00",
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.Gray1,
            )
        }
        KrtCard { Text("Standardkarte", color = KrtPalette.Gray1) }
        KrtCard(variant = KrtCardVariant.Inset) { Text("Inset", color = KrtPalette.Gray1) }
        KrtCard(variant = KrtCardVariant.Accent) { Text("Summenkarte", color = KrtPalette.Gray1) }
        KrtPanelHeader(title = "Finanzen", expanded = false, onToggle = {}, count = 4)
        KrtCard {
            KrtKeyValueRow(label = "Treffpunkt", value = "ARC-L1 Wide Forest Station")
            KrtKeyValueRow(label = "Frequenz", value = "148.500")
        }

        KrtSectionTitle("Status")
        Row(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm)) {
            KrtOrgBadge("Bereich Profit")
            KrtOrgBadge("TITAN", kind = KrtOrgBadgeKind.Foreign)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
            KrtChip("Auftrag", tone = KrtChipTone.Primary)
            KrtChip("Ausgezahlt", tone = KrtChipTone.Success)
            KrtChip("Überbucht", tone = KrtChipTone.Danger)
        }
        KrtDepartmentTag("Profit", KrtTheme.colors.deptProfit)
        KrtStatusPill("Geplant", KrtStatusTone.Planned)
        KrtStatusBadge("Aktiv", KrtStatusTone.Active)
        KrtPresenceIndicator("Wird gerade bearbeitet von Rhea, Dorn", count = 2)
        KrtUpdateAvailablePill("Aktualisierung verfügbar", {})

        KrtSectionTitle("Listen")
        KrtListRow(
            title = "Vertikaler Abbau — Lyria",
            subtitle = "Heute · 21:00 · Geplant",
            leadingIcon = R.drawable.ic_krt_target,
            trailingValue = "in 2 Std.",
            trailingLabel = "12 angemeldet",
        )
        KrtListRow(
            title = "Ausgewählt",
            subtitle = "Auswahlmodus",
            leadingIcon = R.drawable.ic_krt_crate,
            selected = true,
            showChevron = false,
        )
        KrtLoadMore("Mehr laden — 40 von 143", {})
        KrtEndOfList("Ende der Liste")

        KrtSectionTitle("Formular")
        KrtTextField(value = "", onValueChange = {}, label = "Schiffsname", placeholder = "z. B. Carrack")
        KrtTextField(
            value = "-200",
            onValueChange = {},
            label = "Menge (SCU)",
            isError = true,
            errorText = "Menge muss größer als 0 sein.",
        )
        KrtStepperField(value = "1.200", onValueChange = {}, onDecrement = {}, onIncrement = {}, label = "Menge")

        KrtSectionTitle("Overlays")
        Row(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm)) {
            KrtCtaButton("Modal", { modal = KrtModalTone.Standard })
            KrtQuietDangerButton("Danger", { modal = KrtModalTone.Danger })
        }
        KrtToast(title = "Gespeichert", message = "Schiff erfolgreich hinzugefügt.")

        KrtSectionTitle("System")
        KrtLoadingIndicator("Lade Einsätze…")
        KrtOfflineBanner(
            title = "Offline — zeigt gespeicherten Stand",
            lastUpdated = "Zuletzt aktualisiert 17.08. 14:32",
            onRetry = {},
        )
        KrtEmptyState(
            iconRes = R.drawable.ic_krt_crate,
            title = "Keine Einträge",
            message = "Dein Lager ist leer. Buche Material ein, um es hier zu verwalten.",
            actionText = "Einbuchen",
            onAction = {},
        )
        KrtTotalTile(label = "Gesamt IRI", value = "1.245.300", unit = "aUEC")
        KrtKpiCard(
            title = "Einsatzkasse",
            value = "84.200",
            delta = "+12.400",
            sparkline = listOf(16f, 14f, 15f, 9f, 11f, 5f, 7f),
        )

        KrtFanKitBand()
    }

    modal?.let { tone ->
        val danger = tone == KrtModalTone.Danger
        KrtModal(
            title = if (danger) "Schiff wirklich löschen?" else "Check-In bestätigen",
            confirmText = if (danger) "Endgültig löschen" else "Check-In",
            onConfirm = { modal = null },
            onDismiss = { modal = null },
            tone = tone,
        ) {
            Text(
                text =
                    if (danger) {
                        "Die Carrack „Meridian\" wird dauerhaft aus deinem Hangar entfernt. " +
                            "Zugewiesene Crew-Slots in geplanten Einsätzen werden freigegeben."
                    } else {
                        "Du wirst als anwesend für „Vertikaler Abbau — Lyria\" markiert. " +
                            "Der Einsatzleiter sieht deinen Status sofort."
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = KrtPalette.Gray1,
            )
        }
    }
}
