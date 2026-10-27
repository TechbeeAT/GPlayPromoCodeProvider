import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

// API models (DTOs) shared by the server and the web app. Kept free of Compose so the
// server can depend on it without pulling in UI libraries.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm()
    js {
        browser()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
        }
    }
}
