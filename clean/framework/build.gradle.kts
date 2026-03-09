plugins {
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))
    implementation(project(":infrastructure"))
    implementation(project(":presentation"))

    implementation(platform(libs.spring.boot.dependencies))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.jackson.module.kotlin)
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test) {
        exclude(group = "org.junit.jupiter")
        exclude(group = "org.mockito")
    }
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.extensions.spring)
    testImplementation(libs.mockk)
    testImplementation(libs.h2)
}

springBoot {
    mainClass.set("com.wakita181009.clean.framework.ApplicationKt")
}
