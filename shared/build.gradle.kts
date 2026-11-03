import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    // Only for the IDE's Compose previews (rendered on the JVM) and fast tests. The app itself runs in
    // the browser (js, wasmJs); there is no desktop app.
    jvm()
    js {
        browser()
        binaries.executable()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }
    sourceSets {
        commonMain.dependencies {
            api(projects.api)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            // api: ApiClient's constructor takes an HttpClientEngine, so callers (webApp) need the type.
            api(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinxJson)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
        webMain.dependencies {
            implementation(libs.ktor.client.js)
            implementation(libs.kotlinx.browser)
        }
        jvmMain.dependencies {
            // Skia natives for the current OS, needed to render previews on the JVM.
            implementation(compose.desktop.currentOs)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.testJunit)
        }
    }
}

// The browser tests run west of UTC, where midnight UTC is still the day before, so time zone mix-ups
// (like the date picker's headline showing the previous day) fail there instead of passing unnoticed.
tasks.withType<org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest>().configureEach {
    environment("TZ", "America/New_York")
}
