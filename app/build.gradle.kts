plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

import java.io.File
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// ---- Dynamic version: 0.1.$pack.$build.$commit ----
val commitSha: String = providers.exec {
    commandLine("git", "rev-parse", "--short=8", "HEAD")
}.standardOutput.asText.get().trim().ifBlank { "00000000" }

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

val appVersionName = "0.1.$pack.$build.$commitSha"

android {
    namespace = "com.example.opencodeclient"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.opencodeclient"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = appVersionName
        buildConfigField("String", "GIT_COMMIT", "\"$commitSha\"")
        buildConfigField("int", "PACK", "$pack")
        buildConfigField("int", "BUILD", "$build")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
}
