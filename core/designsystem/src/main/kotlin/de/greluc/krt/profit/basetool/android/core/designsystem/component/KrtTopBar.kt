/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.core.designsystem.R
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPreviewSurface
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing

/** Height of the top app bar. */
private val TOP_BAR_HEIGHT = 64.dp

/**
 * The app's top bar.
 *
 * Carries three things and nothing else: where the user is (the orange uppercase title), which org
 * unit the screen is scoped to (the badge, which opens the switcher), and whether anything is
 * waiting (the bell with its count). The 2 dp orange under-rule is the same device the tables use —
 * it separates chrome from content without a shadow.
 *
 * The back arrow appears **only on pushed detail screens** and behaves exactly like system back.
 * There is deliberately no hamburger: the web app's drawer is replaced by the bottom bar and the
 * tablet rail.
 *
 * @param title screen title.
 * @param subject whether the title names a THING rather than a section. A section is shouted in
 *   orange caps ("EINSÄTZE"); a thing keeps its own spelling in white, because "EINSATZKASSE" is
 *   not what the account is called. Independent of [subtitle] — an account with no status line is
 *   still an account.
 * @param subtitle drawn under the title, small. The chapters put a subject's status directly under
 *   its name.
 * @param modifier layout modifier.
 * @param onBack when non-null a back arrow is shown and this is invoked; pass `null` on roots.
 * @param orgBadge optional org-context chip, typically a [KrtOrgBadge].
 * @param notificationCount unread notifications; `null` hides the bell, `0` shows it without badge.
 * @param onNotificationsClick invoked when the bell is tapped.
 */
@Composable
fun KrtTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subject: Boolean = false,
    subtitle: (@Composable () -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    orgBadge: @Composable (() -> Unit)? = null,
    notificationCount: Int? = null,
    onNotificationsClick: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(TOP_BAR_HEIGHT)
                    .padding(start = if (onBack == null) KrtSpacing.lg else KrtSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                KrtIconButton(
                    iconRes = R.drawable.ic_krt_arrow_left,
                    label = "Zurück",
                    onClick = onBack,
                    style = KrtButtonStyles.chrome,
                )
            }
            Column(modifier = Modifier.weight(1f).padding(end = KrtSpacing.sm)) {
                Text(
                    text = if (subject) title else title.krtUppercase(),
                    style =
                        if (subject) {
                            MaterialTheme.typography.titleMedium
                        } else {
                            MaterialTheme.typography.headlineSmall
                        },
                    color = if (subject) KrtPalette.White else MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.invoke()
            }
            orgBadge?.invoke()
            if (notificationCount != null && onNotificationsClick != null) {
                Box(
                    modifier = Modifier.padding(end = KrtSpacing.xs),
                    contentAlignment = Alignment.TopEnd,
                ) {
                    KrtIconButton(
                        iconRes = R.drawable.ic_krt_bell,
                        label = "Benachrichtigungen",
                        onClick = onNotificationsClick,
                        style = KrtButtonStyles.chrome,
                    )
                    if (notificationCount > 0) {
                        KrtCountBadge(
                            count = notificationCount,
                            modifier = Modifier.padding(top = KrtSpacing.xs, end = KrtSpacing.xs),
                        )
                    }
                }
            }
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(KrtSpacing.headingRule)
                    .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Preview(name = "Top bar", showBackground = true, backgroundColor = 0xFF000000, widthDp = 412)
@Composable
private fun TopBarPreview() {
    KrtPreviewSurface {
        Column {
            KrtTopBar(
                title = "Übersicht",
                orgBadge = { KrtOrgBadge("Profit") },
                notificationCount = 3,
                onNotificationsClick = {},
            )
            KrtTopBar(
                title = "Vertikaler Abbau — Lyria",
                onBack = {},
                notificationCount = 0,
                onNotificationsClick = {},
            )
        }
    }
}
