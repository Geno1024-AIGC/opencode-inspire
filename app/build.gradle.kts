plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

import java.io.File

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

// build: commit count (baseline) + local per-build increment
val buildCounterFile = File(projectDir, "build/build_count.txt")
fun readCounter(): Int =
    runCatching { buildCounterFile.readText().trim().toInt() }.getOrDefault(0)
val extraBuilds = readCounter() + 1
buildCounterFile.parentFile?.mkdirs()
buildCounterFile.writeText(extraBuilds.toString())
val build = commitCount + extraBuilds

val appVersionName = "0.1.$pack.$build.$commitSha"

android {
    namespace = "com.example.opencodeclient"
    compileSdk = 36

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
            isMinifyEnabled = false
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

    kotlinOptions {
        jvmTarget = "17"
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

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("com.mikepenz:multiplatform-markdown-renderer-m3-android:0.24.0")
}
