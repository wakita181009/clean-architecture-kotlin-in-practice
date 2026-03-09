plugins {
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":application"))
    // domain types accessible transitively via application's api dependency

    implementation(platform(libs.spring.boot.dependencies))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.jackson.module.kotlin)
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    testImplementation(libs.spring.boot.starter.test) {
        exclude(group = "org.junit.jupiter")
        exclude(group = "org.mockito")
    }
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.assertions.arrow)
    testImplementation(libs.kotest.extensions.spring)
    testImplementation(libs.mockk)
}

tasks.withType<org.springframework.boot.gradle.tasks.bundling.BootJar> {
    enabled = false
}
