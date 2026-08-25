/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.network

/**
 * The major version of the backend API this build speaks.
 *
 * Shown in the version footers (design ch. 04 and 13: "v1.4.2 (Build 37) · API v1"), where it
 * answers a question the app version cannot: which contract the client was built against. When a
 * member reports that something is missing, the pair of numbers says whether they are on an old app
 * or on a new app against an old server.
 *
 * It is a constant rather than a value read from the server, because it describes **this build's**
 * expectation. Every repository path in `core:data` is hard-coded to `/api/v1`; if those move to
 * `/api/v2`, this moves with them in the same change or the footer starts lying.
 */
const val API_VERSION: String = "1"
