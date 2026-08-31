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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.core.designsystem.R
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCardVariant
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCheckboxRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChipSelect
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChipTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCombobox
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtDepartmentTag
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEndOfList
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFanKitBand
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHeading
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHint
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHudBox
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtKeyValueRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtKpiCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtListRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadMore
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModal
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModalTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOfflineBanner
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOption
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOrgBadge
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOrgBadgeKind
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOutlineButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtPanelHeader
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtPresenceIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtQuietDangerButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRadioRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRecordCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSectionTitle
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSelectField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusBadge
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusPill
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStepperField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSuccessButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTable
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTableCell
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTableColumn
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
    var comboQuery by remember { mutableStateOf("quan") }
    var comboOpen by remember { mutableStateOf(false) }
    var selectValue by remember { mutableStateOf("Bereich Profit") }
    var selectOpen by remember { mutableStateOf(false) }
    var ltiInsured by remember { mutableStateOf(true) }
    var payoutToMember by remember { mutableStateOf(true) }

    val tableColumns =
        listOf(
            KrtTableColumn("Material", weight = 1.6f),
            KrtTableColumn("Ort", weight = 1f),
            KrtTableColumn("Qualität", weight = 0.8f, numeric = true),
            KrtTableColumn("Menge", weight = 0.9f, numeric = true),
        )
    val tableRows =
        listOf(
            listOf("Quantainium", "ARC-L1", "874", "642"),
            listOf("Laranite", "Everus Harbor", "655", "1.208"),
        )

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(KrtSpacing.s16),
        verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
    ) {
        KrtHeading("Komponenten")

        KrtSectionTitle("Buttons")
        Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8)) {
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
        Row(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8)) {
            KrtOrgBadge("Bereich Profit")
            KrtOrgBadge("TITAN", kind = KrtOrgBadgeKind.Foreign)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s4)) {
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

        KrtSectionTitle("Tabellen")
        KrtTable(columns = tableColumns, rowCount = tableRows.size) { row, column ->
            KrtTableCell(
                text = tableRows[row][column],
                column = tableColumns[column],
                emphasis = column == 0 || column == 3,
                unit = if (column == 3) "SCU" else null,
            )
        }
        KrtRecordCard(
            title = "Quantainium",
            value = "642",
            unit = "SCU",
            attributes =
                listOf(
                    "Ort" to "Lager Bereich Profit · ARC-L1",
                    "Qualität" to "874 / 1000",
                ),
        )

        KrtSectionTitle("Formular")
        KrtTextField(value = "", onValueChange = {}, label = "Schiffsname", placeholder = "z. B. Carrack")
        KrtTextField(
            value = "-200",
            onValueChange = {},
            label = "Menge (SCU)",
            isError = true,
            errorText = "Menge muss größer als 0 sein.",
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Teilmengen",
                style = MaterialTheme.typography.bodyMedium,
                color = KrtPalette.Gray1,
            )
            KrtHint("Teilmengen erlaubt: cSCU (0,01) und µSCU (0,001).")
        }
        KrtStepperField(value = "1.200", onValueChange = {}, onDecrement = {}, onIncrement = {}, label = "Menge")

        KrtSectionTitle("Auswahl")
        KrtCombobox(
            query = comboQuery,
            onQueryChange = { comboQuery = it },
            options =
                listOf(
                    KrtOption("quantainium", "Quantainium"),
                    KrtOption("quantum-fuel", "Quantum Fuel"),
                ).filter { it.label.contains(comboQuery, ignoreCase = true) },
            onSelect = { comboQuery = it.label },
            expanded = comboOpen,
            onExpandedChange = { comboOpen = it },
            label = "Material",
            notice = "2 von 118 Materialien",
        )
        KrtSelectField(
            value = selectValue,
            options = listOf(KrtOption("iri", "Bereich Profit"), KrtOption("sk", "SK VANGUARD")),
            onSelect = { selectValue = it.label },
            expanded = selectOpen,
            onExpandedChange = { selectOpen = it },
            label = "Org-Einheit",
        )
        KrtCheckboxRow(checked = ltiInsured, onCheckedChange = { ltiInsured = it }, label = "LTI versichert")
        KrtRadioRow(selected = payoutToMember, onSelect = { payoutToMember = true }, label = "Auszahlung")
        KrtRadioRow(selected = !payoutToMember, onSelect = { payoutToMember = false }, label = "Org-Kasse")
        KrtChipSelect(value = "Pilot", onClick = {})

        KrtSectionTitle("Overlays")
        Row(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8)) {
            KrtCtaButton("Modal", { modal = KrtModalTone.Standard })
            KrtQuietDangerButton("Danger", { modal = KrtModalTone.Danger })
        }
        KrtToast(title = "Gespeichert", message = "Schiff erfolgreich hinzugefügt.")

        KrtSectionTitle("System")
        KrtLoadingIndicator("Lade Einsätze…")
        KrtOfflineBanner(
            title = "Offline — keine Verbindung",
            reason = "Schreiben ist gesperrt, bis die Verbindung zurück ist.",
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
