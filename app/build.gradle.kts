plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

import java.io.File
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// ---- Dynamic version: 0.1.$pack.$build.$commit ----
val commitSha: String = providers.exec {
    commandLine("git", "rev-parse", "--short=8", "HEAD")
}.standardOutput.asText.get().trim().ifBlank { "00000000" }

val commitDate: String = providers.exec {
    commandLine("git", "show", "-s", "--format=%cI", "HEAD")
}.standardOutput.asText.get().trim().ifBlank { "unknown" }

val commitCount: Int = providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
}.standardOutput.asText.get().trim().toIntOrNull() ?: 0

// pack: use GitHub Actions run number when building on CI; otherwise commit count as baseline
val packFromCi: String = providers.environmentVariable("GITHUB_RUN_NUMBER").orNull ?: ""
val pack = packFromCi.ifBlank { commitCount.toString() }

// build: tracked counter file (increments each build, committed manually)
val buildCounterFile = File(rootProject.projectDir, "build_count.txt")
val counterNow = runCatching { buildCounterFile.readText().trim().toInt() }
    .getOrDefault(commitCount)
val build = counterNow
buildCounterFile.writeText((counterNow + 1).toString())

// tokens: tracked counter file (updated by the AI, committed manually)
val tokensFile = File(rootProject.projectDir, "tokens.txt")
val tokenLines = runCatching { tokensFile.readLines() }.getOrDefault(emptyList())
fun tokenAt(index: Int): Long = tokenLines.getOrNull(index)?.trim()?.toLongOrNull() ?: 0L
val tokensInput = tokenAt(0)
val tokensOutput = tokenAt(1)
val tokensReasoning = tokenAt(2)
val tokensCacheRead = tokenAt(3)
val tokensCacheWrite = tokenAt(4)
val tokensMsgs = tokenAt(5)
val tokensTotal = tokensInput + tokensOutput + tokensReasoning + tokensCacheRead + tokensCacheWrite
val tokensModels = tokenLines.drop(6).joinToString("\n") { it.trim() }.trim('\n')

val appVersionName = "0.1.$pack.$build.$commitSha"

// ---- Release signing: password from local.properties (git-ignored) or CI env vars ----
val keystoreProps = Properties().apply {
    val lf = File(rootProject.projectDir, "local.properties")
    if (lf.exists()) lf.inputStream().use { load(it) }
}
fun secret(key: String, env: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(key)?.takeIf { it.isNotBlank() }
val releaseStoreFile = secret("keystore.file", "KEYSTORE_STORE_FILE")
val releaseStorePassword = secret("keystore.storePassword", "KEYSTORE_STORE_PASSWORD")
val releaseKeyPassword = secret("keystore.keyPassword", "KEYSTORE_KEY_PASSWORD") ?: releaseStorePassword
val releaseKeyAlias = secret("keystore.keyAlias", "KEYSTORE_KEY_ALIAS") ?: "geno"

android {
    namespace = "com.geno1024.ai.inspire"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.geno1024.ai.inspire"
        minSdk = 26
        targetSdk = 36
        versionCode = build
        versionName = appVersionName
        buildConfigField("String", "GIT_COMMIT", "\"$commitSha\"")
        buildConfigField("String", "BUILD_TIME", "\"$commitDate\"")
        buildConfigField("int", "PACK", "$pack")
        buildConfigField("int", "BUILD", "$build")
        buildConfigField("long", "TOKENS_TOTAL", "${tokensTotal}L")
        buildConfigField("long", "TOKENS_INPUT", "${tokensInput}L")
        buildConfigField("long", "TOKENS_OUTPUT", "${tokensOutput}L")
        buildConfigField("long", "TOKENS_REASONING", "${tokensReasoning}L")
        buildConfigField("long", "TOKENS_CACHE_READ", "${tokensCacheRead}L")
        buildConfigField("long", "TOKENS_CACHE_WRITE", "${tokensCacheWrite}L")
        buildConfigField("long", "TOKENS_MSGS", "${tokensMsgs}L")
        buildConfigField("String", "TOKENS_MODELS", "\"${tokensModels.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\"")
    }

    signingConfigs {
        create("release") {
            if (releaseStoreFile != null && releaseStorePassword != null) {
                storeFile = File(releaseStoreFile)
                storePassword = releaseStorePassword
                keyPassword = releaseKeyPassword
                keyAlias = releaseKeyAlias
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        baseline = file("lint-baseline.xml")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":markdown"))

    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core:1.7.8")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")

    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    implementation("androidx.datastore:datastore-preferences:1.2.1")

    testImplementation("junit:junit:4.13.2")
}
