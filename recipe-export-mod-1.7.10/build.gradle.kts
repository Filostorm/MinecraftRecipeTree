import java.security.MessageDigest

plugins {
    java
    id("com.gtnewhorizons.retrofuturagradle") version "2.0.2"
}

apply(from = "gradle/exporter-provenance.gradle")

group = "com.recipetree"
version = "1.0.151"

val minecraftPin = "1.7.10"
val forgePin = "10.13.4.1614"
val neiPin = "2.8.44-GTNH"
val dreamCorePin = "2.7.268"
val gregTechPin = "5.09.51.482"
val packPin = "2.8.4"
val neiJarSha256 = "c3f0136f68a74c010593a51ecd3414c4eb8d861bebfe357a19e518a033aca92b"
val dreamCoreJarSha256 = "da36a9e1e6675d709969fc57aa0d443a183aa496f787713acc9e2582399235a1"
val gregTechJarSha256 = "4ab7ce174a8f6fb7a90d8d11d56056aab2de577c36c6084b37ce890d7b1d67bf"

val neiApiJar = providers.gradleProperty("neiApiJar").orNull?.let(::file)
    ?: throw GradleException(
        "[gtnh-nei-export] An explicit -PneiApiJar=/path/to/NotEnoughItems-$neiPin.jar is required. " +
            "This module intentionally has no unpinned API fallback."
    )
require(neiApiJar.isFile) {
    "[gtnh-nei-export] -PneiApiJar must identify a readable file; got $neiApiJar"
}

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

val actualNeiSha256 = sha256(neiApiJar)
require(actualNeiSha256 == neiJarSha256) {
    "[gtnh-nei-export] NEI API jar digest mismatch. Expected $neiJarSha256 for $neiPin, " +
        "got $actualNeiSha256 from $neiApiJar"
}
logger.lifecycle("[gtnh-nei-export] Using pinned NEI API jar: $neiApiJar ($actualNeiSha256)")

val dreamCoreApiJar = providers.gradleProperty("dreamCoreJar").orNull?.let(::file)
    ?: throw GradleException(
        "[gtnh-nei-export] An explicit -PdreamCoreJar=/path/to/GTNewHorizonsCoreMod-$dreamCorePin.jar is required. " +
            "The supervised shutdown audit intentionally has no unpinned API fallback."
    )
require(dreamCoreApiJar.isFile) {
    "[gtnh-nei-export] -PdreamCoreJar must identify a readable file; got $dreamCoreApiJar"
}
val actualDreamCoreSha256 = sha256(dreamCoreApiJar)
require(actualDreamCoreSha256 == dreamCoreJarSha256) {
    "[gtnh-nei-export] DreamCore API jar digest mismatch. Expected $dreamCoreJarSha256 for " +
        "$dreamCorePin, got $actualDreamCoreSha256 from $dreamCoreApiJar"
}
logger.lifecycle(
    "[gtnh-nei-export] Using pinned DreamCore API jar: $dreamCoreApiJar ($actualDreamCoreSha256)"
)

val gregTechApiJar = providers.gradleProperty("gregTechApiJar").orNull?.let(::file)
    ?: throw GradleException(
        "[gtnh-nei-export] An explicit -PgregTechApiJar=/path/to/gregtech-$gregTechPin.jar " +
            "is required. The outputless-recipe semantic preflight intentionally has no " +
            "unpinned API fallback."
    )
require(gregTechApiJar.isFile) {
    "[gtnh-nei-export] -PgregTechApiJar must identify a readable file; got $gregTechApiJar"
}
val actualGregTechSha256 = sha256(gregTechApiJar)
require(actualGregTechSha256 == gregTechJarSha256) {
    "[gtnh-nei-export] GregTech API jar digest mismatch. Expected $gregTechJarSha256 for " +
        "$gregTechPin, got $actualGregTechSha256 from $gregTechApiJar"
}
logger.lifecycle(
    "[gtnh-nei-export] Using pinned GregTech API jar: $gregTechApiJar ($actualGregTechSha256)"
)

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

minecraft {
    mcVersion.set(minecraftPin)
    username.set("GTNHRecipeExporter")
    extraRunJvmArguments.addAll(
        "-Dfile.encoding=UTF-8",
        "-Dgtnh.neiexport.auto=true"
    )
}

repositories {
    mavenCentral()
}

dependencies {
    val exactNei = rfg.deobf(files(neiApiJar))
    compileOnly(exactNei)
    runtimeOnly(exactNei)
    testCompileOnly(exactNei)
    compileOnly(files(dreamCoreApiJar))
    testCompileOnly(files(dreamCoreApiJar))
    compileOnly(files(gregTechApiJar))
    testImplementation(files(gregTechApiJar))
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
}

tasks.named<JavaCompile>("compileJava") {
    options.release.set(8)
}

val verifyNoThaumcraftResearchMutation by tasks.registering {
    group = "verification"
    description = "Fails if exporter sources call known Thaumcraft player-research mutation APIs."
    val exporterSources = fileTree("src/main/java") { include("**/*.java") }
    inputs.files(exporterSources)
    doLast {
        val mutationCalls = listOf(
            Regex("\\bcompleteResearch\\s*\\("),
            Regex("\\bprogressResearch\\s*\\("),
            Regex("\\bcreateResearchNote\\s*\\("),
            Regex("\\baddResearch\\s*\\("),
            Regex("\\bsetResearch\\s*\\(")
        )
        val violations = mutableListOf<String>()
        exporterSources.files.sortedBy { it.path }.forEach { source ->
            source.readLines(Charsets.UTF_8).forEachIndexed { index, line ->
                if (mutationCalls.any { it.containsMatchIn(line) }) {
                    violations += "${source.relativeTo(projectDir)}:${index + 1}: ${line.trim()}"
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "[gtnh-nei-export] Thaumcraft player-research mutation is forbidden:\n" +
                    violations.joinToString("\n")
            )
        }
        logger.lifecycle(
            "[gtnh-nei-export] Verified exporter sources contain no known Thaumcraft research mutation calls"
        )
    }
}

val verifySupervisedShutdownOwnership by tasks.registering {
    group = "verification"
    description = "Forbids client-tick code from bypassing Minecraft.run-owned final cleanup."
    val exporterSources = fileTree("src/main/java") { include("**/*.java") }
    inputs.files(exporterSources)
    doLast {
        val forbiddenCalls = listOf(
            Regex("\\bshutdownMinecraftApplet\\s*\\("),
            Regex("\\bloadWorld\\s*\\("),
            Regex("\\bdisableShowConfirmExitWindow\\s*\\("),
            Regex("\\bSystem\\s*\\.\\s*exit\\s*\\("),
            Regex("\\bRuntime\\s*\\.\\s*getRuntime\\s*\\(\\s*\\)\\s*\\.\\s*(?:exit|halt)\\s*\\(")
        )
        val violations = mutableListOf<String>()
        exporterSources.files.sortedBy { it.path }.forEach { source ->
            source.readLines(Charsets.UTF_8).forEachIndexed { index, line ->
                if (forbiddenCalls.any { it.containsMatchIn(line) }) {
                    violations += "${source.relativeTo(projectDir)}:${index + 1}: ${line.trim()}"
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "[gtnh-nei-export] Minecraft.run must exclusively own final client cleanup:\n" +
                    violations.joinToString("\n")
            )
        }
        val coordinator = file(
            "src/main/java/com/recipetree/neiexport1710/ExportCoordinator.java"
        ).readText(Charsets.UTF_8)
        val outerLoopRequests = Regex("\\bminecraft\\s*\\.\\s*shutdown\\s*\\(")
            .findAll(coordinator).count()
        val confirmationAudits = Regex(
            "\\bDreamCoreMod\\s*\\.\\s*showConfirmExitWindow\\b"
        ).findAll(coordinator).count()
        if (outerLoopRequests != 1 || confirmationAudits != 2) {
            throw GradleException(
                "[gtnh-nei-export] Supervised shutdown call topology drifted: expected one " +
                    "Minecraft.shutdown request and two DreamCore confirmation-field audits; " +
                    "received shutdown=$outerLoopRequests, dreamCoreAudits=$confirmationAudits"
            )
        }
        logger.lifecycle(
            "[gtnh-nei-export] Verified one retained-frame exit request, two DreamCore gate " +
                "audits, and exclusive final-cleanup ownership by Minecraft.run"
        )
    }
}

tasks.named("check") {
    dependsOn(verifyNoThaumcraftResearchMutation)
    dependsOn(verifySupervisedShutdownOwnership)
}

tasks.processResources {
    inputs.properties(
        "version" to project.version,
        "minecraftPin" to minecraftPin,
        "forgePin" to forgePin,
        "neiPin" to neiPin,
        "packPin" to packPin
    )
    filesMatching("mcmod.info") {
        expand(
            "version" to project.version,
            "minecraftPin" to minecraftPin,
            "forgePin" to forgePin,
            "neiPin" to neiPin,
            "packPin" to packPin
        )
    }
}

tasks.jar {
    archiveBaseName.set("recipe-tree-gtnh-nei-exporter")
    manifest {
        attributes(
            "Implementation-Title" to "Recipe Tree GTNH NEI Exporter",
            "Implementation-Version" to project.version,
            "Minecraft-Version" to minecraftPin,
            "Forge-Version" to forgePin,
            "NEI-Version" to neiPin,
            "DreamCore-Version" to dreamCorePin,
            "GregTech-Version" to gregTechPin,
            "GTNH-Version" to packPin,
            "NEI-API-SHA256" to neiJarSha256,
            "DreamCore-API-SHA256" to dreamCoreJarSha256,
            "GregTech-API-SHA256" to gregTechJarSha256
        )
    }
}

tasks.named("reobfJar") {
    doLast {
        val reobfuscatedJars = outputs.files.files
            .filter { it.isFile && it.extension == "jar" }
        require(reobfuscatedJars.size == 1) {
            "Expected reobfJar to declare exactly one regular JAR output, found " +
                reobfuscatedJars.joinToString(prefix = "[", postfix = "]") { it.absolutePath }
        }
        val embed = project.extra["embedMrtExporterBuildIdentity"] as groovy.lang.Closure<*>
        embed.call(reobfuscatedJars.single(), "forge-nei-gtnh-1.7.10", "1.7.10")
    }
}

tasks.register("releaseBuild") {
    group = "build"
    description = "Builds and provenance-seals the Java 8 reobfuscated exporter after exact API digest gates."
    dependsOn("test", "build")
}
