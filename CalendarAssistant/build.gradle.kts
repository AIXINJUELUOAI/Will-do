import java.io.File

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    kotlin("plugin.serialization") version "2.3.21" apply false
}

data class ArchitectureGuardrailRule(
    val id: String,
    val regex: Regex,
    val description: String
)

val architectureGuardrailsBaselineFile = file("gradle/architecture-guardrails-baseline.txt")

fun collectArchitectureGuardrailHits(projectRoot: File): Map<Pair<String, String>, List<Int>> {
    val rules = listOf(
        ArchitectureGuardrailRule(
            id = "REPO_GET_INSTANCE",
            regex = Regex("\\bAppRepository\\.getInstance\\s*\\("),
            description = "Do not call AppRepository.getInstance in caller layers"
        ),
        ArchitectureGuardrailRule(
            id = "APP_REPOSITORY_PROPERTY",
            regex = Regex("\\(\\s*applicationContext\\s+as\\s+App\\s*\\)\\.repository"),
            description = "Do not access (applicationContext as App).repository in caller layers"
        ),
        ArchitectureGuardrailRule(
            id = "DB_GET_INSTANCE",
            regex = Regex("\\bAppDatabase\\.getInstance\\s*\\("),
            description = "Do not call AppDatabase.getInstance in caller layers"
        )
    )

    val guardedRoots = listOf(
        projectRoot.resolve("app/src/main/java/com/antgskds/calendarassistant/ui"),
        projectRoot.resolve("app/src/main/java/com/antgskds/calendarassistant/service")
    )

    val workerFiles = fileTree(projectRoot.resolve("app/src/main/java/com/antgskds/calendarassistant")) {
        include("**/*Worker.kt")
    }.files

    val candidateFiles = linkedSetOf<File>()

    guardedRoots.filter { it.exists() }.forEach { root ->
        fileTree(root) {
            include("**/*.kt")
        }.files.forEach(candidateFiles::add)
    }
    workerFiles.filter { it.isFile }.forEach(candidateFiles::add)

    val hits = linkedMapOf<Pair<String, String>, MutableSet<Int>>()

    candidateFiles.sortedBy { it.absolutePath }.forEach { target ->
        val lines = target.readLines()
        val relativePath = target.relativeTo(projectRoot).path.replace(File.separatorChar, '/')

        rules.forEach { rule ->
            lines.forEachIndexed { index, line ->
                if (rule.regex.containsMatchIn(line)) {
                    val key = rule.id to relativePath
                    hits.getOrPut(key) { linkedSetOf() }.add(index + 1)
                }
            }
        }
    }

    return hits.mapValues { it.value.toList().sorted() }
}

fun loadArchitectureGuardrailBaseline(file: File): Set<Pair<String, String>> {
    if (!file.exists()) return emptySet()

    val parsed = linkedSetOf<Pair<String, String>>()
    file.readLines().forEachIndexed { index, rawLine ->
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed

        val parts = line.split('|')
        if (parts.size != 2) {
            throw GradleException(
                "Invalid baseline line ${index + 1} in ${file.path}: '$rawLine'. Expected format: RULE_ID|path"
            )
        }

        parsed.add(parts[0].trim() to parts[1].trim().replace('\\', '/'))
    }
    return parsed
}

tasks.register("checkArchitectureGuardrails") {
    group = "verification"
    description = "Checks forbidden repository/database access in caller layers"

    doLast {
        val hits = collectArchitectureGuardrailHits(rootDir)
        val baseline = loadArchitectureGuardrailBaseline(architectureGuardrailsBaselineFile)

        val unexpected = hits
            .filterKeys { key -> key !in baseline }
            .toSortedMap(compareBy<Pair<String, String>> { it.second }.thenBy { it.first })

        if (unexpected.isEmpty()) {
            println("Architecture guardrails passed (no unexpected direct access).")
            return@doLast
        }

        println("Architecture guardrails failed. Unexpected direct access found:")
        unexpected.forEach { (key, lines) ->
            val (ruleId, path) = key
            val lineDesc = lines.joinToString(",")
            println("- [$ruleId] $path:$lineDesc")
        }

        throw GradleException(
            "Unexpected architecture guardrail violations detected. " +
                "Fix direct access or baseline approved legacy hits in gradle/architecture-guardrails-baseline.txt"
        )
    }
}

tasks.register("updateArchitectureGuardrailsBaseline") {
    group = "verification"
    description = "Rebuilds guardrail baseline from current caller-layer hits"

    doLast {
        val hits = collectArchitectureGuardrailHits(rootDir)
        val lines = mutableListOf<String>()
        lines += "# Architecture guardrail baseline"
        lines += "# Format: RULE_ID|relative/path/to/file.kt"
        lines += "# Keep this file as small as possible. Remove entries once migrated."
        lines += ""

        hits.keys
            .toSortedSet(compareBy<Pair<String, String>> { it.second }.thenBy { it.first })
            .forEach { (ruleId, path) ->
                lines += "$ruleId|$path"
            }

        architectureGuardrailsBaselineFile.parentFile?.mkdirs()
        architectureGuardrailsBaselineFile.writeText(lines.joinToString(System.lineSeparator()))
        println("Wrote baseline to ${architectureGuardrailsBaselineFile.path}")
    }
}
