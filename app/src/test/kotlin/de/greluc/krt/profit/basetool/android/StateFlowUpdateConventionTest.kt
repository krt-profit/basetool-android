/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * A state change is written as `state.update { it.copy(…) }`, never as
 * `state.value = state.value.copy(…)` (project `CLAUDE.md`, Kotlin conventions).
 *
 * **Why it is a rule and not a style.** The second form reads the value, builds a copy and writes it
 * back as three separate steps. On the main thread, where nearly every write in this app runs, the
 * three cannot interleave — so this is not the fix for a lost update anybody observed. It is correct
 * on one thread only: the first write from anywhere else (a collector moved to
 * `Dispatchers.Default`, a platform callback) could start from a value another write has already
 * replaced and silently drop that write's field. `MutableStateFlow.update` is a compare-and-set
 * loop that re-runs the lambda on the value that actually won, so it is correct on every thread,
 * and it names the flow once instead of twice. There were 563 of the read-copy-assign form across
 * 32 ViewModels until 2026-09-22 (improvement audit, SIB-MOD-01).
 *
 * The lambda may run more than once, which is the one discipline the rule asks for in return: it
 * builds a value and does nothing else. A request, a log line or a counter goes before the
 * `update`, and its result goes in — see `PersonalBlueprintsViewModel.Import.onFile`.
 *
 * Matched as text, like [ProcessStoreOwnershipTest]: "does a source file write a state flow the
 * racy way" is a syntactic fact, and detekt has no rule that says it. A multi-line assignment is
 * caught as well, because the pattern allows any whitespace around the `=`.
 */
class StateFlowUpdateConventionTest {
    @Test
    fun `no source writes a state flow by reading, copying and assigning it`() {
        val offenders =
            sequenceOf(File("src/main/kotlin"), File("src/dev/kotlin"), File("src/prod/kotlin"), File("../core"))
                .filter { it.isDirectory }
                .flatMap { it.walkTopDown() }
                .filter { it.isFile && it.extension == "kt" && NOT_PRODUCTION.none { dir -> it.path.contains(dir) } }
                .flatMap { file ->
                    val text = file.readText()
                    RACY_WRITE.findAll(text).map { "${file.name}:${text.lineNumberOf(it.range.first)}" }
                }.toList()

        assertEquals(
            "these read-copy-assign a state flow; write `x.update { it.copy(…) }`, which stays " +
                "correct off the main thread, and keep side effects out of the lambda",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `the pattern finds the racy form, on one line and across two`() {
        // The guard is only worth something if it matches what it forbids. Without this, a typo in
        // the expression would leave the test above green forever.
        assertEquals(1, RACY_WRITE.findAll("mutableState.value = mutableState.value.copy(a = 1)").count())
        assertEquals(1, RACY_WRITE.findAll("_state.value =\n    _state.value.copy(\n  a = 1)").count())
        assertEquals(0, RACY_WRITE.findAll("mutableState.update { it.copy(a = 1) }").count())
        assertEquals(0, RACY_WRITE.findAll("mutableState.value = current.copy(a = 1)").count())
    }

    /**
     * The 1-based line an offset falls on, for a failure message that points somewhere.
     *
     * @param offset a character offset into this text.
     * @return its line number.
     */
    private fun String.lineNumberOf(offset: Int): Int = substring(0, offset).count { it == '\n' } + 1

    private companion object {
        /** `x.value = x.value.copy(` with the same `x` on both sides, across any whitespace. */
        val RACY_WRITE = Regex("""\b([A-Za-z_][A-Za-z0-9_.]*)\.value\s*=\s*\1\.value\.copy\(""")

        /**
         * Directories whose sources ship in no APK: tests may build a fake state however they like,
         * and generated code is not written by anyone who could follow the rule.
         */
        val NOT_PRODUCTION =
            listOf("test", "androidTest", "build").map { "${File.separator}$it${File.separator}" }
    }
}
