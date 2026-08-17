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
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import de.greluc.krt.profit.basetool.android.navigation.BasetoolApp

/**
 * The single activity of the app.
 *
 * Single-activity by design: the navigation graph owns every screen, which is what lets the back
 * rules of the design specification hold — per-destination back stacks, back from a root returning
 * to Übersicht, and back on Übersicht simply finishing the activity.
 *
 * Edge-to-edge is enabled before `super.onCreate` so the very first frame already draws behind the
 * system bars; at targetSdk 36 and above the platform enforces it anyway and there is no opt-out.
 */
class MainActivity : ComponentActivity() {
    /**
     * Enables edge-to-edge drawing and installs the Compose content.
     *
     * @param savedInstanceState the recreation state, restored by the navigation graph itself.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            KrtTheme {
                BasetoolApp()
            }
        }
    }
}
