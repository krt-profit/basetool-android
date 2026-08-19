/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pins the adaptive launcher icon and, above all, keeps its two artwork layers from drifting apart.
 *
 * `ic_launcher_foreground.xml` (brand orange) and `ic_launcher_monochrome.xml` (flat white, tinted
 * by the system on Android 13+) are the *same* mark drawn twice. Nothing in the build couples them:
 * editing one and forgetting the other produces a themed icon that is subtly the wrong shape or
 * sits off-centre, and it is only ever seen by users who turned themed icons on — which is exactly
 * the population least likely to report it. Comparing the geometry here turns that into a red
 * build.
 *
 * Reading the XML as text is deliberate, like [BackupExclusionTest]: the fact under test is a
 * string-level one (does layer A carry the same path data and the same group transform as layer B),
 * and inflating the drawables would only add a Robolectric dependency without adding certainty.
 */
class LauncherIconTest {
    private val manifest = File("src/main/AndroidManifest.xml")
    private val adaptiveIcon = File("src/main/res/mipmap/ic_launcher.xml")
    private val foreground = File("src/main/res/drawable/ic_launcher_foreground.xml")
    private val monochrome = File("src/main/res/drawable/ic_launcher_monochrome.xml")

    /**
     * Without this attribute the app ships the green Android robot. AGP's `MissingApplicationIcon`
     * lint used to be disabled here while the icon was outstanding; it is enabled again, but lint
     * runs in a separate task that a `test`-only invocation never reaches.
     */
    @Test
    fun `the manifest declares the launcher icon`() {
        assertTrue(
            "AndroidManifest.xml must set android:icon=\"@mipmap/ic_launcher\"",
            read(manifest).contains("android:icon=\"@mipmap/ic_launcher\""),
        )
    }

    /**
     * All three layers are load-bearing: dropping `monochrome` silently downgrades the icon on
     * themed-icon launchers, and dropping `background` makes the mark float on whatever the
     * launcher happens to paint behind it.
     */
    @Test
    fun `the launcher icon is adaptive with background, foreground and monochrome layers`() {
        val xml = read(adaptiveIcon)

        assertTrue("ic_launcher.xml must be an <adaptive-icon>", xml.contains("<adaptive-icon"))
        assertTrue(
            "background layer must reference @color/ic_launcher_background",
            xml.contains("<background android:drawable=\"@color/ic_launcher_background\""),
        )
        assertTrue(
            "foreground layer must reference @drawable/ic_launcher_foreground",
            xml.contains("<foreground android:drawable=\"@drawable/ic_launcher_foreground\""),
        )
        assertTrue(
            "monochrome layer must reference @drawable/ic_launcher_monochrome",
            xml.contains("<monochrome android:drawable=\"@drawable/ic_launcher_monochrome\""),
        )
    }

    /** The two artwork layers must draw the same shapes, in the same order. */
    @Test
    fun `foreground and monochrome carry identical path geometry`() {
        assertEquals(
            "ic_launcher_monochrome.xml drifted from ic_launcher_foreground.xml — the themed icon " +
                "must draw the same mark",
            pathData(read(foreground)),
            pathData(read(monochrome)),
        )
    }

    /**
     * The scale keeps the mark's furthest point inside the 36 dp radius that every launcher mask is
     * guaranteed to show, and the translation centres the source viewBox's *asymmetric* content
     * box. A layer that kept the paths but lost the transform would render the mark at seven times
     * its intended size, anchored to the top-left corner.
     */
    @Test
    fun `foreground and monochrome share the same group transform`() {
        assertEquals(
            "the two layers must place the artwork identically",
            groupTransform(read(foreground)),
            groupTransform(read(monochrome)),
        )
    }

    /**
     * Extracts every `android:pathData` value, in document order.
     *
     * @param xml the vector-drawable source
     * @return the path data strings, order-sensitive
     */
    private fun pathData(xml: String): List<String> =
        PATH_DATA.findAll(xml).map { it.groupValues[1] }.toList()

    /**
     * Extracts the `<group>` placement attributes.
     *
     * @param xml the vector-drawable source
     * @return translateX/Y and scaleX/Y as `name=value` pairs, in document order
     */
    private fun groupTransform(xml: String): List<String> =
        GROUP_ATTR.findAll(xml).map { "${it.groupValues[1]}=${it.groupValues[2]}" }.toList()

    /**
     * Reads a resource file, failing loudly when it is missing rather than returning an empty
     * string that would make every `contains` assertion below fail with a useless message.
     *
     * @param file the file to read, relative to the module directory
     * @return the file contents
     */
    private fun read(file: File): String {
        assertTrue("${file.path} must exist", file.exists())
        return file.readText()
    }

    private companion object {
        val PATH_DATA = Regex("""android:pathData="([^"]+)"""")
        val GROUP_ATTR = Regex("""android:(translateX|translateY|scaleX|scaleY)="([^"]+)"""")
    }
}
