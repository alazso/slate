import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.paperweight.userdev)
    alias(libs.plugins.run.paper)
    alias(libs.plugins.shadow)
    alias(libs.plugins.plugin.yml.paper)
}

group = "so.alaz"
version = "0.6.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://jitpack.io")
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
        vendor.set(JvmVendorSpec.ADOPTIUM)
    }
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget("25"))
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

dependencies {
    paperweight.paperDevBundle(libs.versions.paper.get())

    implementation(libs.kotlin.coroutines.core)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.hikari)
    implementation(libs.sqlite)
    implementation(libs.postgres)
    implementation(libs.bstats)
    implementation(libs.jedis)
    compileOnly(libs.placeholderapi)
    compileOnly(libs.vault.api) {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    compileOnly(libs.ledger.api)
    testCompileOnly(libs.vault.api) {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    testCompileOnly(libs.ledger.api)
    testRuntimeOnly(libs.vault.api) {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    testRuntimeOnly(libs.ledger.api)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockk)
    testImplementation(libs.mockbukkit)
    testImplementation(libs.kotlin.coroutines.test)
}

paperweight {
    reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION
}

paper {
    name = "Slate"
    main = "so.alaz.slate.SlatePlugin"
    bootstrapper = "so.alaz.slate.SlateBootstrap"
    apiVersion = "26.1"
    authors = listOf("alazso")
    website = "https://github.com/alazso/slate"
    description = "Modern crate plugin for Paper 26.1+"
    loader = null
    foliaSupported = true
    generateLibrariesJson = false
    serverDependencies {
        register("Ledger") {
            load = net.minecrell.pluginyml.paper.PaperPluginDescription.RelativeLoadOrder.BEFORE
            required = false
        }
        register("Vault") {
            load = net.minecrell.pluginyml.paper.PaperPluginDescription.RelativeLoadOrder.BEFORE
            required = false
        }
        register("PlaceholderAPI") {
            load = net.minecrell.pluginyml.paper.PaperPluginDescription.RelativeLoadOrder.BEFORE
            required = false
        }
    }
}

tasks {
    withType<Test>().configureEach {
        useJUnitPlatform()
    }

    runServer {
        minecraftVersion("26.1.2")
    }

    register("runPurpurServer") {
        group = "run paper"
        description = "Run a local Purpur 26.1 server"
        dependsOn("shadowJar")
        doLast {
            val runDir = layout.projectDirectory.dir("run-purpur").asFile
            val pluginsDir = runDir.resolve("plugins")
            val jar = runDir.resolve("purpur-26.1.jar")
            runDir.mkdirs()
            pluginsDir.mkdirs()

            val shadedJar = project.tasks.named<ShadowJar>("shadowJar").get().archiveFile.get().asFile
            shadedJar.copyTo(pluginsDir.resolve(shadedJar.name), overwrite = true)

            if (!jar.exists()) {
                URI.create("https://api.purpurmc.org/v2/purpur/26.1/latest/download").toURL().openStream().use { input ->
                    jar.outputStream().use { output -> input.copyTo(output) }
                }
            }

            providers.exec {
                workingDir(runDir)
                commandLine("java", "-jar", jar.absolutePath, "nogui")
            }.result.get()
        }
    }

    withType<ShadowJar>().configureEach {
        archiveClassifier.set("")
        mergeServiceFiles()
        relocate("kotlin", "so.alaz.slate.libs.kotlin")
        relocate("kotlinx", "so.alaz.slate.libs.kotlinx")
        relocate("org.jetbrains.exposed", "so.alaz.slate.libs.exposed")
        relocate("com.zaxxer.hikari", "so.alaz.slate.libs.hikari")
        relocate("org.postgresql", "so.alaz.slate.libs.postgresql")
        relocate("org.bstats", "so.alaz.slate.libs.bstats")
        relocate("redis.clients", "so.alaz.slate.libs.redis")
    }

    named("jar") {
        enabled = false
    }

    build {
        dependsOn(shadowJar)
    }
    assemble {
        dependsOn(shadowJar)
    }
}
