// Pure Kotlin module — no Spring dependencies allowed

dependencies {
    api(libs.arrow.core)
    implementation(libs.slf4j.api)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.assertions.arrow)
    testImplementation(libs.kotest.property)
}
