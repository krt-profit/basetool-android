/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModal
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.network.ApiError

/**
 * What a refused save looks like: design chapter 14's conflict dialog.
 *
 * A 409 means the save did **not** happen and the record on the server has moved on. Until this
 * existed, every write surface said so with a `KrtFieldError` under the form — a line that is easy
 * to miss under a scrolled sheet, and a member who misses it believes they saved.
 *
 * **Two of the artboard's sentences are not used, and neither is a translation question.**
 *
 * The chapter writes *„Der Datensatz wurde zwischenzeitlich von Rhea geändert"*. The 409 carries no
 * identity — the app does not know who changed the record, and naming somebody would be inventing
 * them. It also writes *„Deine Eingaben bleiben in der Zwischenablage erhalten"*, which would only
 * become true if the app wrote the member's input to the **system** clipboard, where every other
 * app on the device can read it. For an app whose whole posture is that nothing leaves it without a
 * decision, that is not a copy fix. The wording here says what actually happens: the input stays in
 * the form.
 *
 * **The primary action reloads; it does not retry.** The chapter labels it „NEU LADEN UND ERNEUT
 * VERSUCHEN", and a button that re-sent the same values against the newer version would overwrite
 * whatever the other person changed without either of them seeing it — which is the exact outcome
 * optimistic locking exists to prevent. So it reloads, the member sees the current state, and they
 * decide. Recorded as a deviation in `REQ-APP-UI-008` rather than taken quietly.
 *
 * @param onReload the primary action: discard this attempt and read the record again.
 * @param onDismiss the secondary action: keep the form and its input, and close the dialog.
 */
@Composable
fun ConflictModal(
    onReload: () -> Unit,
    onDismiss: () -> Unit,
) {
    KrtModal(
        title = stringResource(R.string.conflict_title),
        confirmText = stringResource(R.string.conflict_reload),
        cancelText = stringResource(R.string.conflict_cancel),
        onConfirm = onReload,
        onDismiss = onDismiss,
    ) {
        Text(
            text = stringResource(R.string.conflict_body),
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.Gray1,
        )
    }
}

/**
 * Shows [ConflictModal] for a refused save, once per refusal.
 *
 * The one line every write surface adds, so the eleven of them cannot drift on when the dialog
 * appears or on what dismissing it means.
 *
 * **Dismissal is tracked by identity, not by value.** `ApiError.OptimisticLock` is a data class, so
 * two separate refusals can compare equal — a `remember(error)` key would treat the second one as
 * the first, and a member who dismissed the dialog once would never see it again in that session.
 * Each refusal produces a new instance, so `!==` is the question that actually distinguishes them.
 *
 * @param error the write's last outcome, or `null`.
 * @param onReload the primary action: read the record again.
 */
@Composable
fun ConflictOn(
    error: ApiError?,
    onReload: () -> Unit,
) {
    var seen by remember { mutableStateOf<ApiError?>(null) }
    if (shouldRaiseConflict(error = error, seen = seen)) {
        ConflictModal(onReload = onReload, onDismiss = { seen = error })
    }
}

/**
 * Whether a refusal still needs to be shown.
 *
 * Its own function because it is the part worth a test and the part that is easy to get wrong, and
 * because testing it through the dialog means testing Robolectric's handling of two windows instead
 * of the rule.
 *
 * @param error the write's last outcome.
 * @param seen the refusal the member has already dismissed, if any.
 * @return `true` for a conflict the member has not dismissed yet.
 */
internal fun shouldRaiseConflict(
    error: ApiError?,
    seen: ApiError?,
): Boolean = error is ApiError.OptimisticLock && seen !== error
