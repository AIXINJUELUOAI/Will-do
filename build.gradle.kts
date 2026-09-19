import java.io.File

// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools:r8:9.1.31")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    kotlin("plugin.serialization") version "2.4.0" apply false
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
            regex = Regex("import\\s+com\\.antgskds\\.calendarassistant\\.(ui\\.viewmodel|app\\.ui\\.state)"),
            description = "UI contracts must not depend on concrete ViewModels",
            pathRegex = Regex("app/src/main/.*/ui/contract/.*")
        ),
        ArchitectureGuardrailRule(
            id = "FLAVOR_CONCRETE_BUSINESS_IMPORT",
            regex = Regex("import\\s+com\\.antgskds\\.calendarassistant\\.(ui\\.viewmodel|app\\.ui\\.state|core\\.center|data\\.(repository|store))"),
            description = "Native UI hosts must consume contracts instead of concrete business implementations",
            pathRegex = Regex("app/src/native/.*")
        ),
        ArchitectureGuardrailRule(
            id = "RUNTIME_UI_STYLE",
            regex = Regex("\\bUiStyle\\b|updateUiStyle|settings\\.uiStyle|calendarassistant\\.miui"),
            description = "UI edition is selected at build time, not at runtime"
        )
    )

    val candidateFiles = linkedSetOf<File>()
    listOf("app/src/main", "app/src/native").forEach { sourceRoot ->
        fileTree(projectRoot.resolve(sourceRoot)) { include("**/*.kt") }.files.forEach(candidateFiles::add)
    }

    val hits = linkedMapOf<Pair<String, String>, MutableSet<Int>>()

    candidateFiles.sortedBy { it.absolutePath }.forEach { target ->
        val lines = target.readLines()
        val relativePath = target.relativeTo(projectRoot).path.replace(File.separatorChar, '/')

        rules.filter { rule -> rule.pathRegex.matches(relativePath) }.forEach { rule ->
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

fun extractEnumMembers(sourceFile: File, enumName: String): Set<String> {
    if (!sourceFile.exists()) {
        throw GradleException("Architecture guardrail source not found: ${sourceFile.path}")
    }

    val source = sourceFile.readText()
    val declaration = Regex(
        "\\benum\\s+class\\s+${Regex.escape(enumName)}\\b[^\\{]*\\{"
    ).find(source) ?: throw GradleException(
        "Architecture guardrail could not find enum $enumName in ${sourceFile.path}"
    )
    val openingBrace = declaration.range.last
    var depth = 0
    var closingBrace = -1
    for (index in openingBrace until source.length) {
        when (source[index]) {
            '{' -> depth += 1
            '}' -> {
                depth -= 1
                if (depth == 0) {
                    closingBrace = index
                    break
                }
            }
        }
    }
    if (closingBrace < 0) {
        throw GradleException("Architecture guardrail found an unterminated enum $enumName in ${sourceFile.path}")
    }

    val simpleEntry = Regex("^([A-Za-z_][A-Za-z0-9_]*)\\s*,?\\s*$")
    val members = source.substring(openingBrace + 1, closingBrace)
        .lineSequence()
        .map { it.substringBefore("//").trim() }
        .mapNotNull { simpleEntry.matchEntire(it)?.groupValues?.get(1) }
        .toCollection(linkedSetOf())
    if (members.isEmpty()) {
        throw GradleException("Architecture guardrail found no enum members for $enumName in ${sourceFile.path}")
    }
    return members
}

fun extractRegisteredEnumMembers(
    catalogFile: File,
    entryConstructor: String,
    enumName: String
): Set<String> {
    if (!catalogFile.exists()) {
        throw GradleException("Architecture guardrail catalog not found: ${catalogFile.path}")
    }

    val entryRegex = Regex(
        "\\b${Regex.escape(entryConstructor)}\\s*\\(\\s*" +
            "${Regex.escape(enumName)}\\.([A-Za-z_][A-Za-z0-9_]*)\\b"
    )
    return entryRegex.findAll(catalogFile.readText())
        .map { it.groupValues[1] }
        .toCollection(linkedSetOf())
}

tasks.register("checkArchitectureGuardrails") {
    group = "verification"
    description = "Checks architecture boundaries, Center allowlist, and catalog registration"

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
        val currentCenters = listOf("app/src/main", "app/src/native")
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

        val settingsDestinations = extractEnumMembers(
            rootDir.resolve(
                "app/src/main/java/com/antgskds/calendarassistant/app/ui/navigation/SettingsNavigationContract.kt"
            ),
            "SettingsDestination"
        )
        val registeredPages = extractRegisteredEnumMembers(
            rootDir.resolve(
                "app/src/main/java/com/antgskds/calendarassistant/shared/management/catalog/PageCatalog.kt"
            ),
            "PageEntry",
            "SettingsDestination"
        )
        val pagesNotRegistered = settingsDestinations - registeredPages

        val notificationKinds = extractEnumMembers(
            rootDir.resolve(
                "app/src/main/java/com/antgskds/calendarassistant/feature/notification/model/NotificationModels.kt"
            ),
            "NotificationKind"
        )
        val registeredKinds = extractRegisteredEnumMembers(
            rootDir.resolve(
                "app/src/main/java/com/antgskds/calendarassistant/shared/management/catalog/NotificationKindCatalog.kt"
            ),
            "KindEntry",
            "NotificationKind"
        )
        val kindsNotRegistered = notificationKinds - registeredKinds

        if (
            unexpected.isEmpty() &&
            newCenters.isEmpty() &&
            pagesNotRegistered.isEmpty() &&
            kindsNotRegistered.isEmpty()
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
        pagesNotRegistered.sorted().forEach {
            println("- [PAGE_NOT_REGISTERED] SettingsDestination.$it")
        }
        kindsNotRegistered.sorted().forEach {
            println("- [KIND_NOT_REGISTERED] NotificationKind.$it")
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
