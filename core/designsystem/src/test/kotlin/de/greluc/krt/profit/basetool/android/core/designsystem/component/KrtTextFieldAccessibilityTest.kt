/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.designsystem.component

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What a screen reader gets from [KrtTextField]: an accessible name, the placeholder in the tree,
 * and the error semantics.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [ROBOLECTRIC_SDK_LEVEL])
class KrtTextFieldAccessibilityTest {
    @get:Rule
    val compose = createComposeRule()

    /**
     * Renders one field.
     *
     * @param value the current text.
     * @param label the caption above the field, if any.
     * @param placeholder the hint shown while empty, if any.
     * @param isError whether the field fails validation.
     * @param errorText the message for that failure.
     */
    private fun field(
        value: String = "",
        label: String? = null,
        placeholder: String? = null,
        isError: Boolean = false,
        errorText: String? = null,
    ) {
        compose.setContent {
            KrtTheme {
                KrtTextField(
                    value = value,
                    onValueChange = {},
                    label = label,
                    placeholder = placeholder,
                    isError = isError,
                    errorText = errorText,
                )
            }
        }
    }

    @Test
    fun `the placeholder names the field when there is no label`() {
        field(placeholder = "Einsatz suchen")

        compose.onNodeWithContentDescription("Einsatz suchen").assertIsDisplayed()
    }

    @Test
    fun `the label wins over the placeholder as the name`() {
        field(label = "Betrag", placeholder = "0")

        compose.onNodeWithContentDescription("Betrag").assertIsDisplayed()
    }

    @Test
    fun `the name survives the member typing`() {
        field(value = "Konvoi", placeholder = "Einsatz suchen")

        compose.onNodeWithContentDescription("Einsatz suchen").assertIsDisplayed()
    }

    @Test
    fun `the placeholder is reachable in the tree, not merely painted`() {
        field(placeholder = "Einsatz suchen")

        compose.onNodeWithText("Einsatz suchen").assertIsDisplayed()
    }

    @Test
    fun `an error is attached to the field, not only rendered beneath it`() {
        field(value = "0", label = "Betrag", isError = true, errorText = "Betrag muss größer als 0 sein")

        compose.onNodeWithContentDescription("Betrag").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Error, "Betrag muss größer als 0 sein"),
        )
    }

    @Test
    fun `a field without an error carries no error semantics`() {
        field(label = "Betrag")

        compose.onNodeWithContentDescription("Betrag").assert(
            SemanticsMatcher.keyNotDefined(SemanticsProperties.Error),
        )
    }
}

/**
 * Robolectric SDK level for this class.
 *
 * Pinned rather than inherited from `targetSdk`: the app targets API 37, for which Robolectric has
 * no runtime yet. Nothing asserted here is API-level dependent — these are semantics-tree lookups.
 */
private const val ROBOLECTRIC_SDK_LEVEL = 34
