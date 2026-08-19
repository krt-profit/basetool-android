/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCheckboxRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtListRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSectionTitle
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.navigation.KrtDestination
import de.greluc.krt.profit.basetool.android.navigation.MORE_DESTINATIONS

/**
 * The "Mehr" overflow list.
 *
 * A phone bottom bar holds five destinations; everything else lives here as a plain list of dense
 * rows. The list is identical on tablets even though the rail already exposes three of these
 * entries, so a user who learned where something lives keeps finding it in the same place.
 *
 * Sign-out lives here rather than on a settings screen that does not exist yet. It is a separate
 * section because it is the one row that ends something instead of navigating somewhere, and a
 * member should not reach it by mis-tapping the list above it.
 *
 * @param onOpen invoked with the chosen destination.
 * @param onLogout ends the session; the caller opens the realm's end-session URL.
 * @param modifier layout modifier.
 * @param appLockEnabled whether the member has switched the app lock on.
 * @param appLockAvailable whether the device can prompt at all; the row is disabled when it cannot.
 * @param onAppLockChange invoked with the new setting.
 */
@Composable
fun MoreScreen(
    onOpen: (KrtDestination) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    appLockEnabled: Boolean = false,
    appLockAvailable: Boolean = true,
    onAppLockChange: (Boolean) -> Unit = {},
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = KrtSpacing.md),
    ) {
        KrtSectionTitle(
            text = stringResource(R.string.more_section_secondary),
            modifier = Modifier.padding(horizontal = KrtSpacing.lg, vertical = KrtSpacing.sm),
        )
        MORE_DESTINATIONS.forEach { destination ->
            KrtListRow(
                title = destination.title,
                leadingIcon = destination.iconRes,
                onClick = { onOpen(destination) },
            )
        }

        KrtSectionTitle(
            text = stringResource(R.string.more_section_session),
            modifier = Modifier.padding(horizontal = KrtSpacing.lg, vertical = KrtSpacing.sm),
        )
        // Interim home. The app lock belongs in Einstellungen (design ch. 13), which is not built
        // yet — and a security feature that ships with no way to switch it on is dead code, so it
        // sits here until that chapter moves it. Disabled rather than hidden when the device has no
        // screen lock at all: hiding it would read as a missing feature, and the hint names the one
        // thing the member can actually do about it.
        KrtCheckboxRow(
            checked = appLockEnabled,
            onCheckedChange = onAppLockChange,
            label =
                stringResource(
                    if (appLockAvailable) R.string.lock_setting else R.string.lock_setting_unavailable,
                ),
            enabled = appLockAvailable,
            modifier = Modifier.padding(horizontal = KrtSpacing.lg, vertical = KrtSpacing.sm),
        )
        Text(
            text = stringResource(R.string.lock_setting_hint),
            style = MaterialTheme.typography.labelSmall,
            color = KrtPalette.TextMuted,
            modifier = Modifier.padding(horizontal = KrtSpacing.lg),
        )
        Spacer(Modifier.height(KrtSpacing.sm))
        KrtListRow(
            title = stringResource(R.string.logout),
            onClick = onLogout,
        )
    }
}
