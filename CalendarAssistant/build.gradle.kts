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
    val description: String,
    val pathRegex: Regex = Regex(".*")
)

val architectureGuardrailsBaselineFile = file("gradle/architecture-guardrails-baseline.txt")
val centerFilesBaselineFile = file("gradle/center-files-baseline.txt")

fun collectArchitectureGuardrailHits(projectRoot: File): Map<Pair<String, String>, List<Int>> {
    val rules = listOf(
        ArchitectureGuardrailRule(
            id = "REPO_GET_INSTANCE",
            regex = Regex("\\bAppRepository\\.getInstance\\s*\\("),
            description = "Do not call AppRepository.getInstance in caller layers",
            pathRegex = Regex("app/src/main/.*/(ui|service)/.*|app/src/main/.*Worker\\.kt")
        ),
        ArchitectureGuardrailRule(
            id = "APP_REPOSITORY_PROPERTY",
            regex = Regex("\\(\\s*applicationContext\\s+as\\s+App\\s*\\)\\.repository"),
            description = "Do not access (applicationContext as App).repository in caller layers",
            pathRegex = Regex("app/src/main/.*/(ui|service)/.*|app/src/main/.*Worker\\.kt")
        ),
        ArchitectureGuardrailRule(
            id = "DB_GET_INSTANCE",
            regex = Regex("\\bAppDatabase\\.getInstance\\s*\\("),
            description = "Do not call AppDatabase.getInstance in caller layers",
            pathRegex = Regex("app/src/main/.*/(ui|service)/.*|app/src/main/.*Worker\\.kt")
        ),
        ArchitectureGuardrailRule(
            id = "CONTRACT_VIEWMODEL_IMPORT",
            regex = Regex("import\\s+com\\.antgskds\\.calendarassistant\\.ui\\.viewmodel"),
            description = "UI contracts must not depend on concrete ViewModels",
            pathRegex = Regex("app/src/main/.*/ui/contract/.*")
        ),
        ArchitectureGuardrailRule(
            id = "FLAVOR_CONCRETE_BUSINESS_IMPORT",
            regex = Regex("import\\s+com\\.antgskds\\.calendarassistant\\.(ui\\.viewmodel|core\\.center|data\\.(repository|store))"),
            description = "Flavor UI must consume contracts instead of concrete business implementations",
            pathRegex = Regex("app/src/(native|hyperos)/.*")
        ),
        ArchitectureGuardrailRule(
            id = "RUNTIME_UI_STYLE",
            regex = Regex("\\bUiStyle\\b|updateUiStyle|settings\\.uiStyle|calendarassistant\\.miui"),
            description = "UI edition is selected at build time, not at runtime"
        )
    )

    val candidateFiles = linkedSetOf<File>()
    listOf("app/src/main", "app/src/native", "app/src/hyperos").forEach { sourceRoot ->
        fileTree(projectRoot.resolve(sourceRoot)) { include("**/*.kt") }.files.forEach(candidateFiles::add)
    }

    val hits = linkedMapOf<Pair<String, String>, MutableSet<Int>>()

    candidateFiles.sortedBy { it.absolutePath }.forEach { target ->
        val lines = target.readLines()
        val relativePath = target.relativeTo(projectRoot).path.replace(File.separatorChar, '/')

        rules.filter { it.pathRegex.matches(relativePath) }.forEach { rule ->
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
    description = "Checks architecture boundaries, Center allowlist, and flavor host symmetry"

    doLast {
        val hits = collectArchitectureGuardrailHits(rootDir)
        val baseline = loadArchitectureGuardrailBaseline(architectureGuardrailsBaselineFile)

        val unexpected = hits
            .filterKeys { key -> key !in baseline }
            .toSortedMap(compareBy<Pair<String, String>> { it.second }.thenBy { it.first })

        val allowedCenters = centerFilesBaselineFile.takeIf(File::exists)
            ?.readLines()
            ?.map(String::trim)
            ?.filter { it.isNotEmpty() && !it.startsWith("#") }
            ?.map { it.replace('\\', '/') }
            ?.toSet()
            .orEmpty()
        val currentCenters = listOf("app/src/main", "app/src/native", "app/src/hyperos")
            .flatMap { sourceRoot ->
                fileTree(rootDir.resolve(sourceRoot)) {
                    include("**/*Center.kt")
                }.files
            }
            .map { centerFile ->
                centerFile.relativeTo(rootDir).path.replace(File.separatorChar, '/')
            }
            .toSet()
        val newCenters = currentCenters - allowedCenters

        fun collectFlavorHosts(flavor: String): Set<String> {
            val hostRoot = rootDir.resolve(
                "app/src/$flavor/java/com/antgskds/calendarassistant/ui/flavor"
            )
            return fileTree(hostRoot) {
                include("**/*.kt")
            }.files.map { hostFile ->
                hostFile.relativeTo(hostRoot).path.replace(File.separatorChar, '/')
            }.toSet()
        }

        val nativeHosts = collectFlavorHosts("native")
        val hyperosHosts = collectFlavorHosts("hyperos")
        val missingInHyperos = nativeHosts - hyperosHosts
        val missingInNative = hyperosHosts - nativeHosts

        if (
            unexpected.isEmpty() &&
            newCenters.isEmpty() &&
            missingInHyperos.isEmpty() &&
            missingInNative.isEmpty()
        ) {
            println("Architecture guardrails passed.")
            return@doLast
        }

        println("Architecture guardrails failed:")
        unexpected.forEach { (key, lines) ->
            val (ruleId, path) = key
            val lineDesc = lines.joinToString(",")
            println("- [$ruleId] $path:$lineDesc")
        }
        newCenters.sorted().forEach { println("- [NEW_CENTER_FILE] $it") }
        missingInHyperos.sorted().forEach {
            println("- [FLAVOR_HOST_MISSING_IN_HYPEROS] $it")
        }
        missingInNative.sorted().forEach {
            println("- [FLAVOR_HOST_MISSING_IN_NATIVE] $it")
        }

        throw GradleException(
            "Unexpected architecture guardrail violations detected. " +
                "Fix the reported violations or update the appropriate approved legacy baseline."
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
