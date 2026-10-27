plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "at.techbee.gplaypromocodeprovider.server.MainKt"
}

dependencies {
    implementation(projects.api)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.contentNegotiation)
    implementation(libs.ktor.server.statusPages)
    implementation(libs.ktor.server.sessions)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.serialization.kotlinxJson)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.sqlite.jdbc)
    implementation(libs.logback.classic)

    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.ktor.server.testHost)
    testImplementation(libs.ktor.client.contentNegotiation)
}
