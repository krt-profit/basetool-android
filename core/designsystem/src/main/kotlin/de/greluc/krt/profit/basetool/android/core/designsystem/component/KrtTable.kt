/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KRT_TABULAR_FIGURES
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPreviewSurface
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing

/** Alpha of the zebra stripe on every second table row — barely there by design. */
private const val ZEBRA_ALPHA = 0.02f

/** Horizontal padding inside a table cell. */
private val CELL_PADDING_H = 14.dp

/** Vertical padding inside a table cell. */
private val CELL_PADDING_V = 8.dp

/** Minimum height of a table row. */
private val TABLE_ROW_HEIGHT = 48.dp

/**
 * One column of a [KrtTable].
 *
 * @property title column heading; uppercased for display.
 * @property weight share of the available width this column takes.
 * @property numeric whether the column holds numbers — right-aligned and tabular.
 */
@Immutable
data class KrtTableColumn(
    val title: String,
    val weight: Float = 1f,
    val numeric: Boolean = false,
)

/**
 * The dense data table — the **tablet** representation of a record list.
 *
 * On phones a table is the wrong shape: the design system collapses the same record into
 * [KrtRecordCard] instead, because horizontal page scrolling is forbidden. Pick by window size
 * class, never by squeezing columns.
 *
 * The header carries the system's signature: input-fill background, uppercase micro-labels and a
 * 2 dp orange under-rule. Rows alternate with an almost invisible zebra so the eye can track a line
 * across wide columns without the stripes becoming decoration.
 *
 * @param columns the column definitions; their weights must match the cells supplied per row.
 * @param rowCount number of rows to render.
 * @param modifier layout modifier.
 * @param onRowClick optional per-row tap handler; when set the whole row becomes the target.
 * @param cell renders one cell: given the row and column index, emit the cell content.
 */
@Composable
fun KrtTable(
    columns: List<KrtTableColumn>,
    rowCount: Int,
    modifier: Modifier = Modifier,
    onRowClick: ((Int) -> Unit)? = null,
    cell: @Composable RowScope.(row: Int, column: Int) -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .border(KrtSpacing.hairline, KrtPalette.Gray3),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(KrtPalette.SurfaceInput),
        ) {
            columns.forEach { column ->
                Text(
                    text = column.title.krtUppercase(),
                    modifier =
                        Modifier
                            .weight(column.weight)
                            .padding(horizontal = CELL_PADDING_H, vertical = 9.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = KrtPalette.Gray1,
                    textAlign = if (column.numeric) TextAlign.End else TextAlign.Start,
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(KrtSpacing.headingRule)
                    .background(MaterialTheme.colorScheme.primary),
        )
        repeat(rowCount) { rowIndex ->
            val zebra =
                if (rowIndex % 2 == 1) KrtPalette.White.copy(alpha = ZEBRA_ALPHA) else Color.Transparent
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(zebra)
                        .then(
                            if (onRowClick != null) {
                                Modifier.clickable(role = Role.Button) { onRowClick(rowIndex) }
                            } else {
                                Modifier
                            },
                        )
                        .defaultMinSize(minHeight = TABLE_ROW_HEIGHT),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                columns.forEachIndexed { columnIndex, _ -> cell(rowIndex, columnIndex) }
            }
            if (rowIndex < rowCount - 1) {
                KrtHairlineRule(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * A text cell for [KrtTable].
 *
 * @param text the cell content.
 * @param column the column this cell belongs to; drives alignment and figure style.
 * @param modifier layout modifier — pass `Modifier.weight(column.weight)` from the row scope.
 * @param emphasis whether this is the row's identifying value (bright white bold) rather than a
 *   secondary attribute.
 * @param unit optional unit rendered after the value in a quieter tone.
 */
@Composable
fun KrtTableCell(
    text: String,
    column: KrtTableColumn,
    modifier: Modifier = Modifier,
    emphasis: Boolean = false,
    unit: String? = null,
) {
    Row(
        modifier = modifier.padding(horizontal = CELL_PADDING_H, vertical = CELL_PADDING_V),
        horizontalArrangement =
            if (column.numeric) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = text,
            style =
                if (emphasis) {
                    MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = KRT_TABULAR_FIGURES)
                } else {
                    MaterialTheme.typography.bodyMedium.copy(
                        fontFeatureSettings = if (column.numeric) KRT_TABULAR_FIGURES else null,
                    )
                },
            color = if (emphasis) KrtPalette.White else KrtPalette.Gray1,
        )
        if (unit != null) {
            Text(
                text = unit,
                modifier = Modifier.padding(start = KrtSpacing.xs),
                style = MaterialTheme.typography.labelMedium,
                color = KrtPalette.Gray2,
            )
        }
    }
}

/**
 * The **phone** representation of a table record: a card with a headline, its key figure and the
 * remaining attributes as label/value pairs.
 *
 * Carries exactly the same data as one [KrtTable] row. Collapsing rather than scrolling is a
 * binding rule — the page must never scroll horizontally.
 *
 * @param title the record's identifying name.
 * @param value the record's key figure, already formatted.
 * @param modifier layout modifier.
 * @param unit optional unit for [value].
 * @param onClick optional tap handler opening the record.
 * @param attributes the remaining label/value pairs, in the column order of the table.
 */
@Composable
fun KrtRecordCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    onClick: (() -> Unit)? = null,
    attributes: List<Pair<String, String>> = emptyList(),
) {
    KrtCard(modifier = modifier, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = KrtPalette.White,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                KrtDataValue(text = value)
                if (unit != null) {
                    Text(
                        text = unit,
                        modifier = Modifier.padding(start = KrtSpacing.xs),
                        style = MaterialTheme.typography.labelMedium,
                        color = KrtPalette.Gray2,
                    )
                }
            }
        }
        attributes.forEach { (label, attributeValue) ->
            KrtKeyValueRow(
                label = label,
                value = attributeValue,
                modifier = Modifier.padding(top = KrtSpacing.xs),
                valueColor = KrtPalette.Gray1,
            )
        }
    }
}

@Preview(name = "Table and record card", showBackground = true, backgroundColor = 0xFF000000, widthDp = 720)
@Composable
private fun TablePreview() {
    val columns =
        listOf(
            KrtTableColumn("Material", weight = 1.6f),
            KrtTableColumn("Ort", weight = 1f),
            KrtTableColumn("Qualität", weight = 0.7f, numeric = true),
            KrtTableColumn("Menge", weight = 0.8f, numeric = true),
        )
    val rows =
        listOf(
            listOf("Quantainium", "ARC-L1", "874", "642"),
            listOf("Laranite", "Everus Harbor", "655", "1.208"),
            listOf("Agricium (Pressed)", "Lorville", "901", "96"),
        )
    KrtPreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.lg)) {
            KrtTable(columns = columns, rowCount = rows.size) { row, column ->
                KrtTableCell(
                    text = rows[row][column],
                    column = columns[column],
                    modifier = Modifier.weight(columns[column].weight),
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
        }
    }
}
