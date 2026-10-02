/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * The committed app call list matches the calls `core:data` makes (REQ-APP-API-011).
 *
 * Every API call site is found in the sources and fingerprinted; the list must name every site
 * exactly once, every operation it names must exist in the vendored `openapi.json` with the verb
 * the site uses, every query parameter must be documented, a named response model must be the
 * documented one, and the fields must be exactly those the scan derives. A failure prints the line
 * the list should carry.
 */
class AppCallListTest {
    private val list = File("../contract/app-calls.txt")
    private val sources = File("src/main/kotlin")
    private val document: JsonObject =
        Json.parseToJsonElement(File("../contract/src/main/openapi/openapi.json").readText()).jsonObject
    private val files: List<SourceFile> by lazy { loadSources() }
    private val sites: List<Site> by lazy { files.flatMap { sitesIn(it) } }
    private val entries: List<Entry> by lazy { parseList() }

    /**
     * One source file with its comments removed and its declarations indexed.
     *
     * @property name the file name.
     * @property text the text without block comments, with `\n` line ends.
     * @property functions every `fun` declaration, in order.
     * @property declarations functions and upper-case constants with the text each spans.
     */
    private data class SourceFile(
        val name: String,
        val text: String,
        val functions: List<MatchResult>,
        val declarations: List<Declaration>,
    )

    /**
     * A function or constant and the text it spans.
     *
     * @property name its name.
     * @property start the offset its declaration starts at.
     * @property end the offset it ends at.
     */
    private data class Declaration(
        val name: String,
        val start: Int,
        val end: Int,
    )

    /**
     * One API call in the sources.
     *
     * @property token the identity the list names it by.
     * @property file the file it is in.
     * @property method the `ApiReader` or `SseStream` member it calls.
     * @property call the call expression, whitespace-normalised.
     * @property arguments its top-level arguments.
     */
    private data class Site(
        val token: String,
        val file: SourceFile,
        val method: String,
        val call: String,
        val arguments: List<String>,
    )

    /**
     * One line of the list.
     *
     * @property verb the HTTP verb.
     * @property path the documented path template.
     * @property query the query parameters sent.
     * @property fields the response fields that may be read.
     * @property sites the call sites that issue it.
     * @property raw the line as written.
     */
    private data class Entry(
        val verb: String,
        val path: String,
        val query: Set<String>,
        val fields: Set<String>,
        val sites: Set<String>,
        val raw: String,
    )

    @Test
    fun `every call site is listed and every listed site exists`() {
        val found = sites.map { it.token }.toSet()
        val listed = entries.flatMap { it.sites }.toSet()
        val missing = sites.filter { it.token !in listed }
        val stale = listed - found
        assertTrue(
            "call sites missing from ${list.path} (add a line or move the token):\n" +
                missing.joinToString("\n") { "${it.token}  ${it.call.take(CALL_PREVIEW)}" } +
                "\nlisted sites that no longer exist:\n" + stale.sorted().joinToString("\n"),
            missing.isEmpty() && stale.isEmpty(),
        )
    }

    @Test
    fun `every listed operation is documented with the verb its sites use`() {
        val bySite = sites.associateBy { it.token }
        val problems =
            entries.flatMap { entry ->
                val operation = operation(entry)
                val documented =
                    if (operation ==
                        null
                    ) {
                        listOf("${entry.verb} ${entry.path} is not in openapi.json")
                    } else {
                        emptyList()
                    }
                documented +
                    entry.sites.mapNotNull { token ->
                        val site = bySite[token] ?: return@mapNotNull null
                        val verb = verbOf(site)
                        if (verb != entry.verb) "$token sends $verb, listed under ${entry.verb} ${entry.path}" else null
                    }
            }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `every listed query parameter is documented`() {
        val problems =
            entries.flatMap { entry ->
                val documented = queryParameters(entry)
                (entry.query - documented).map {
                    "${entry.verb} ${entry.path} lists q=$it, which the document does not have"
                }
            }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `a named response model is the documented one`() {
        val bySite = sites.associateBy { it.token }
        val problems =
            entries.flatMap { entry ->
                val documented = responseSchemas(entry)
                entry.sites.mapNotNull { token ->
                    val site = bySite[token] ?: return@mapNotNull null
                    val model = responseModel(site) ?: return@mapNotNull null
                    if (model !in
                        documented
                    ) {
                        "$token decodes $model, ${entry.verb} ${entry.path} answers $documented"
                    } else {
                        null
                    }
                }
            }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `the listed fields are exactly the ones the app may read`() {
        val bySite = sites.associateBy { it.token }
        val words = identifiers()
        val problems =
            entries.mapNotNull { entry ->
                val reading = entry.sites.mapNotNull { bySite[it] }.any { reads(it) }
                val expected =
                    if (reading) {
                        responseFields(entry).filter {
                            it in words
                        }.toSortedSet()
                    } else {
                        sortedSetOf()
                    }
                if (expected == entry.fields.toSortedSet()) {
                    null
                } else {
                    "${entry.verb} ${entry.path} should list f=${expected.joinToString(",").ifEmpty { "-" }}"
                }
            }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `the list is sorted and names each operation once`() {
        val keys = entries.map { it.path to it.verb }
        assertEquals("one line per operation", keys.toSet().size, keys.size)
        val sorted = entries.sortedWith(compareBy<Entry>({ it.path }, { it.verb })).map { it.raw }
        assertEquals("lines sorted by path, then verb", sorted, entries.map { it.raw })
        entries.forEach { entry ->
            assertEquals("query sorted", entry.query.sorted(), entry.query.toList())
            assertEquals("fields sorted", entry.fields.sorted(), entry.fields.toList())
            assertEquals("sites sorted", entry.sites.sorted(), entry.sites.toList())
        }
    }

    @Test
    fun `the API is reached only through the shapes the scan reads`() {
        val problems = mutableListOf<String>()
        files.forEach { file ->
            MEMBER_ACCESS.findAll(file.text).forEach {
                if (it.groupValues[2] !in
                    METHODS
                ) {
                    problems += "${file.name}: ${it.value} is a call the scan does not read"
                }
            }
            TYPED_DECLARATION.findAll(file.text).forEach {
                val expected = if (it.groupValues[2] == "ApiReader") "reader" else "stream"
                if (it.groupValues[1] != expected) problems += "${file.name}: ${it.value} must be named $expected"
            }
            HIDDEN_RECEIVER.findAll(file.text).forEach { problems += "${file.name}: ${it.value} hides the receiver" }
        }
        outsideSources().forEach { file ->
            val text = file.readText()
            val transport =
                file.invariantSeparatorsPath.contains("core/network/") ||
                    file.invariantSeparatorsPath.contains("core/auth/")
            if (!transport &&
                OUTSIDE_CALL.containsMatchIn(text)
            ) {
                problems += "${file.path} calls the API outside core:data"
            }
            if (CONTRACT_MODEL.containsMatchIn(text)) problems += "${file.path} reads a wire model outside core:data"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    /**
     * Reads the list, failing on any line that is not `VERB PATH q=… f=… s=…`.
     *
     * @return the entries, in file order.
     */
    private fun parseList(): List<Entry> =
        list.readLines().filter { it.isNotBlank() }.map { raw ->
            val parts = raw.split(' ')
            assertTrue("malformed line: $raw", parts.size == LINE_TOKENS)
            val tokens = parts.iterator()
            val verb = tokens.next()
            val path = tokens.next()
            assertTrue("malformed line: $raw", verb in VERBS && path.startsWith("/api/"))
            Entry(
                verb = verb,
                path = path,
                query = values(tokens.next(), "q=", raw),
                fields = values(tokens.next(), "f=", raw),
                sites = values(tokens.next(), "s=", raw),
                raw = raw,
            )
        }

    /**
     * Splits one `key=a,b` token, where `-` stands for none.
     *
     * @param token the token.
     * @param key its expected key.
     * @param raw the line, for the failure message.
     * @return the values, in written order.
     */
    private fun values(
        token: String,
        key: String,
        raw: String,
    ): Set<String> {
        assertTrue("expected $key in: $raw", token.startsWith(key))
        val value = token.removePrefix(key)
        return if (value == "-") linkedSetOf() else value.split(',').toCollection(LinkedHashSet())
    }

    /**
     * Loads and indexes every `core:data` source file.
     *
     * @return the files, sorted by name.
     */
    private fun loadSources(): List<SourceFile> =
        sources.walkTopDown().filter { it.isFile && it.extension == "kt" }.sortedBy { it.name }.map { file ->
            val text = COMMENT.replace(file.readText().replace("\r\n", "\n"), "")
            val functions = FUN.findAll(text).toList()
            val declarations =
                functions.mapIndexed { i, match ->
                    Declaration(
                        match.groupValues[1],
                        match.range.first,
                        functions.getOrNull(i + 1)?.range?.first ?: text.length,
                    )
                } +
                    CONST.findAll(text).map { match ->
                        var j = text.indexOf('=', match.range.last + 1) + 1
                        while (j < text.length && text[j] in " \t\r\n") j++
                        val newline = text.indexOf('\n', j)
                        Declaration(match.groupValues[1], match.range.first, if (newline >= 0) newline else text.length)
                    }
            SourceFile(file.name, text, functions, declarations)
        }.toList()

    /**
     * Finds and fingerprints every call in [file].
     *
     * The fingerprint covers the enclosing function up to the call, every call of that function in
     * the same file, and every constant and `…Path` / `…Params` helper they reach, so a changed path,
     * parameter or verb anywhere on the way changes the token.
     *
     * @param file the file.
     * @return its sites, in source order.
     */
    private fun sitesIn(file: SourceFile): List<Site> =
        CALL.findAll(file.text).map { match ->
            val text = file.text
            val start = match.range.first
            val end = closingParenthesis(text, match.range.last)
            val function = file.functions.last { it.range.first < start }
            val name = function.groupValues[1]
            val snippet = text.substring(function.range.first, end + 1)
            val callers = callers(text, name)
            val seed = (listOf(snippet) + callers).joinToString("\n")
            val material =
                (
                    listOf(normalize(snippet)) +
                        callers.map {
                            normalize(it)
                        } + expansions(file, seed)
                ).joinToString("\n")
            val call = text.substring(start, end + 1)
            Site(
                token = "${file.name.removeSuffix(".kt")}.$name.${sha256(material).take(HASH_LENGTH)}",
                file = file,
                method = match.groupValues[2],
                call = normalize(call),
                arguments = arguments(call),
            )
        }.toList()

    /**
     * Finds the calls of the function [name] in [text], declarations excluded.
     *
     * @param text the file.
     * @param name the function.
     * @return each call expression, raw.
     */
    private fun callers(
        text: String,
        name: String,
    ): List<String> =
        Regex("(?<![.A-Za-z0-9_])" + Regex.escape(name) + "[ \\t\\r\\n]*\\(").findAll(text).mapNotNull { match ->
            val before = text.substring(maxOf(0, match.range.first - DECLARATION_WINDOW), match.range.first)
            if (DECLARATION_TAIL.containsMatchIn(before)) {
                null
            } else {
                text.substring(match.range.first, closingParenthesis(text, match.range.last) + 1)
            }
        }.toList()

    /**
     * Follows the constants and path or parameter helpers [seed] names, transitively.
     *
     * @param home the file the site is in, whose declarations win over another file's.
     * @param seed the text to start from.
     * @return the normalised text of every declaration reached, ordered by file and offset.
     */
    private fun expansions(
        home: SourceFile,
        seed: String,
    ): List<String> {
        val seen = mutableSetOf<Pair<String, Int>>()
        val found = mutableListOf<Triple<String, Int, String>>()
        val queue = ArrayDeque(listOf(seed))
        while (queue.isNotEmpty()) {
            val chunk = queue.removeLast()
            REFERENCE.findAll(chunk).map { it.groupValues[1] }.toSet().forEach { name ->
                var candidates = home.declarations.filter { it.name == name }.map { home to it }
                if (candidates.isEmpty() && (name[0].isUpperCase() || name.endsWith("Path"))) {
                    candidates = files.flatMap { f -> f.declarations.filter { it.name == name }.map { f to it } }
                }
                candidates.forEach { (file, declaration) ->
                    if (seen.add(file.name to declaration.start)) {
                        val body = file.text.substring(declaration.start, declaration.end)
                        found += Triple(file.name, declaration.start, body)
                        queue.addLast(body)
                    }
                }
            }
        }
        return found.sortedWith(compareBy({ it.first }, { it.second })).map { normalize(it.third) }
    }

    /**
     * The verb a site sends.
     *
     * @param site the site.
     * @return the upper-case verb, or `?` when the scan cannot tell.
     */
    private fun verbOf(site: Site): String =
        when (site.method) {
            "get", "getOptional", "getBytes", "events" -> {
                "GET"
            }

            "post", "postAccepted", "postUnit", "postFile" -> {
                "POST"
            }

            "put", "putAccepted" -> {
                "PUT"
            }

            "delete" -> {
                "DELETE"
            }

            "send" -> {
                literal(
                    site,
                    site.arguments.firstOrNull { it.startsWith("method") }?.substringAfter('=')
                        ?: site.arguments.getOrNull(1),
                )
            }

            "execute" -> {
                BUILDER_VERB.find(site.arguments.getOrNull(1).orEmpty())?.groupValues?.get(1)?.uppercase()
                    ?: "?"
            }

            else -> {
                "?"
            }
        }

    /**
     * Resolves a verb argument that is a string literal or a constant of the site's file.
     *
     * @param site the site.
     * @param argument the argument as written.
     * @return the verb, or `?`.
     */
    private fun literal(
        site: Site,
        argument: String?,
    ): String {
        val written = argument?.trim().orEmpty()
        return when {
            written.isEmpty() -> {
                "?"
            }

            written.startsWith('"') -> {
                written.trim('"')
            }

            else -> {
                Regex("val[ \\t]+" + Regex.escape(written) + "[ \\t]*(?::[ \\t]*String)?[ \\t]*=[ \\t]*\"([A-Z]+)\"")
                    .find(site.file.text)
                    ?.groupValues
                    ?.get(1) ?: "?"
            }
        }
    }

    /**
     * Whether a site decodes the response body.
     *
     * @param site the site.
     * @return `true` when it does.
     */
    private fun reads(site: Site): Boolean =
        when (site.method) {
            "get", "getOptional", "send", "postFile", "execute" -> {
                true
            }

            "post", "put" -> {
                site.arguments.any { it.startsWith("deserializer") } || site.arguments.size in READING_ARITIES
            }

            "delete" -> {
                site.arguments.size >= READING_DELETE_ARITY
            }

            else -> {
                false
            }
        }

    /**
     * The generated model a reading site decodes, when it names one.
     *
     * @param site the site.
     * @return the model's name, or `null` for a non-reading site or a built-in type.
     */
    private fun responseModel(site: Site): String? =
        SERIALIZER
            .findAll(site.call)
            .lastOrNull()
            ?.groupValues
            ?.get(1)
            ?.takeIf { reads(site) && it !in BUILT_INS }

    /**
     * The documented operation of [entry].
     *
     * @param entry the line.
     * @return the operation, or `null` when the document has none.
     */
    private fun operation(entry: Entry): JsonObject? =
        (document["paths"] as? JsonObject)?.get(entry.path)?.let {
            it as? JsonObject
        }?.get(entry.verb.lowercase()) as? JsonObject

    /**
     * The documented query parameters of [entry].
     *
     * @param entry the line.
     * @return their names.
     */
    private fun queryParameters(entry: Entry): Set<String> =
        (operation(entry)?.get("parameters") as? JsonArray).orEmpty()
            .map { it.jsonObject }
            .filter { it["in"]?.jsonPrimitive?.content == "query" }
            .mapNotNull { it["name"]?.jsonPrimitive?.content }
            .toSet()

    /**
     * The schema names the documented `2xx` answers of [entry] reference, directly or as array items.
     *
     * @param entry the line.
     * @return the names.
     */
    private fun responseSchemas(entry: Entry): Set<String> =
        successSchemas(entry).mapNotNull { schema ->
            reference(schema)
                ?: reference((schema as? JsonObject)?.get("items"))
        }.toSet()

    /**
     * The response fields of [entry] down to the depth the backend's contract test freezes.
     *
     * @param entry the line.
     * @return the property names, flat.
     */
    private fun responseFields(entry: Entry): Set<String> {
        val fields = mutableSetOf<String>()
        responseSchemas(entry).forEach { name ->
            properties(name)?.let {
                fields += it.keys
                nested(it, MAX_NESTING, fields)
            }
        }
        return fields
    }

    /**
     * Adds the properties of every schema [properties] references, down to [depth].
     *
     * @param properties the properties to descend from.
     * @param depth how many further levels to follow.
     * @param into the accumulator.
     */
    private fun nested(
        properties: JsonObject,
        depth: Int,
        into: MutableSet<String>,
    ) {
        if (depth <= 0) return
        properties.values.forEach { property ->
            val name = reference((property as? JsonObject)?.get("items")) ?: reference(property) ?: return@forEach
            properties(name)?.let {
                into += it.keys
                nested(it, depth - 1, into)
            }
        }
    }

    /**
     * The schemas of the documented `2xx` answers of [entry].
     *
     * @param entry the line.
     * @return the schema objects.
     */
    private fun successSchemas(entry: Entry): List<JsonElement> =
        (operation(entry)?.get("responses") as? JsonObject).orEmpty()
            .filterKeys { it.startsWith("2") }
            .values
            .flatMap { ((it as? JsonObject)?.get("content") as? JsonObject).orEmpty().values }
            .mapNotNull { (it as? JsonObject)?.get("schema") }

    /**
     * The schema name a `$ref` points at.
     *
     * @param element a schema, or `null`.
     * @return the name, or `null` when it is no reference.
     */
    private fun reference(element: JsonElement?): String? =
        (element as? JsonObject)?.get("\$ref")?.jsonPrimitive?.content?.substringAfterLast('/')

    /**
     * The properties of a named schema.
     *
     * @param name the schema.
     * @return its properties, or `null`.
     */
    private fun properties(name: String): JsonObject? =
        ((document["components"] as? JsonObject)?.get("schemas") as? JsonObject)?.get(name)?.let {
            it as? JsonObject
        }?.get("properties") as? JsonObject

    /**
     * Every identifier in `core:data`'s code, imports and package lines excluded.
     *
     * @return the identifiers.
     */
    private fun identifiers(): Set<String> =
        files.flatMap { file ->
            file.text.lines().filterNot { IMPORT_OR_PACKAGE.containsMatchIn(it) }.flatMap { line ->
                IDENTIFIER.findAll(line).map { it.value }
            }
        }.toSet()

    /**
     * Source files outside `core:data` whose API use the guard checks.
     *
     * @return the files.
     */
    private fun outsideSources(): List<File> =
        OUTSIDE_ROOTS.map { File(it) }.filter { it.isDirectory }.flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }

    /**
     * Splits a call's top-level arguments.
     *
     * @param call the call expression.
     * @return the arguments, trimmed.
     */
    private fun arguments(call: String): List<String> {
        val inner = call.substring(call.indexOf('(') + 1, call.length - 1)
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        var inString = false
        var i = 0
        while (i < inner.length) {
            val c = inner[i]
            when {
                inString && c == '\\' -> {
                    current.append(c).append(inner.getOrElse(i + 1) { ' ' })
                    i++
                }

                inString -> {
                    current.append(c)
                    if (c == '"') inString = false
                }

                c == '"' -> {
                    inString = true
                    current.append(c)
                }

                c in "([{" -> {
                    depth++
                    current.append(c)
                }

                c in ")]}" -> {
                    depth--
                    current.append(c)
                }

                c == ',' && depth == 0 -> {
                    out += current.toString().trim()
                    current.clear()
                }

                else -> {
                    current.append(c)
                }
            }
            i++
        }
        if (current.isNotBlank()) out += current.toString().trim()
        return out
    }

    private companion object {
        const val HASH_LENGTH = 8
        const val MAX_NESTING = 2
        const val LINE_TOKENS = 5
        const val CALL_PREVIEW = 160
        const val DECLARATION_WINDOW = 200

        /** A `post` or `put` decodes the answer with a path and model, or path, body, body model and model. */
        val READING_ARITIES = setOf(2, 4)

        /** A `delete` decodes the answer when it takes a model after its path. */
        const val READING_DELETE_ARITY = 2

        val VERBS = setOf("GET", "POST", "PUT", "PATCH", "DELETE")
        val METHODS =
            setOf(
                "get",
                "getOptional",
                "getBytes",
                "post",
                "put",
                "postAccepted",
                "putAccepted",
                "postUnit",
                "delete",
                "send",
                "postFile",
                "execute",
                "events",
            )
        val BUILT_INS = setOf("String", "Int", "Long", "Boolean", "Unit", "Double")
        val OUTSIDE_ROOTS =
            listOf(
                "../../app/src/main/kotlin",
                "../../app/src/dev/kotlin",
                "../../app/src/prod/kotlin",
                "../auth/src/main/kotlin",
                "../common/src/main/kotlin",
                "../designsystem/src/main/kotlin",
                "../network/src/main/kotlin",
            )

        val COMMENT = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
        val CALL =
            Regex(
                "\\b(reader|stream)[ \\t\\r\\n]*\\.(" + METHODS.joinToString("|") + ")[ \\t\\r\\n]*\\(",
            )
        val FUN =
            Regex(
                "(?m)^[ \\t]*(?:(?:private|internal|public|protected|override|suspend|inline|operator|open|final|" +
                    "abstract|tailrec)[ \\t]+)*fun[ \\t]+(?:<[^>\\n]*>[ \\t]*)?(?:[A-Za-z0-9_.]+\\.)?([A-Za-z0-9_]+)",
            )
        val CONST =
            Regex(
                "(?m)^[ \\t]*(?:(?:private|internal|public|protected|const)[ \\t]+)*val[ \\t]+([A-Z][A-Z0-9_]*)\\b",
            )
        val REFERENCE = Regex("\\b([A-Z][A-Z0-9_]*|[a-z][A-Za-z0-9_]*(?:Path|Params|Param)|params)\\b")
        val DECLARATION_TAIL = Regex("fun[ \\t]+(?:<[^>\\n]*>[ \\t]*)?$")
        val WHITESPACE = Regex("[ \\t\\r\\n]+")
        val SERIALIZER = Regex("([A-Za-z0-9_]+)\\.serializer\\(\\)")
        val BUILDER_VERB = Regex("\\.(get|post|put|delete|patch)\\(")
        val IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")
        val IMPORT_OR_PACKAGE = Regex("^\\s*(import|package)\\s")
        val MEMBER_ACCESS = Regex("\\b(reader|stream)[ \\t\\r\\n]*\\.[ \\t\\r\\n]*([A-Za-z_]+)")
        val TYPED_DECLARATION = Regex("\\b([A-Za-z_][A-Za-z0-9_]*)[ \\t]*:[ \\t]*(ApiReader|SseStream)\\b")
        val HIDDEN_RECEIVER =
            Regex(
                "fun[ \\t]+(?:<[^>\\n]*>[ \\t]*)?(?:ApiReader|SseStream)\\.|with\\((?:reader|stream)\\)|" +
                    "\\b(?:reader|stream)\\.(?:run|let|apply|also)\\b",
            )
        val OUTSIDE_CALL = Regex("\\bApiReader\\(|\\bSseStream\\(|\\.newCall\\(")
        val CONTRACT_MODEL =
            Regex("import de\\.greluc\\.krt\\.profit\\.basetool\\.android\\.core\\.contract\\.model\\.")

        /**
         * Collapses every whitespace run to one space.
         *
         * @param text the text.
         * @return the normalised text.
         */
        fun normalize(text: String): String = WHITESPACE.replace(text, " ").trim(' ')

        /**
         * Hex SHA-256 of [text]'s UTF-8 bytes.
         *
         * @param text the text.
         * @return the lower-case digest.
         */
        fun sha256(text: String): String =
            MessageDigest
                .getInstance("SHA-256")
                .digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

        /**
         * The offset of the parenthesis closing the one at [open].
         *
         * @param text the text.
         * @param open the offset of `(`.
         * @return the offset of its `)`.
         */
        fun closingParenthesis(
            text: String,
            open: Int,
        ): Int {
            var depth = 0
            for (i in open until text.length) {
                when (text[i]) {
                    '(' -> depth++
                    ')' -> if (--depth == 0) return i
                }
            }
            error("unbalanced parenthesis at $open")
        }
    }
}
