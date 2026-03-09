import org.jooq.meta.jaxb.MatcherRule
import org.jooq.meta.jaxb.MatcherTransformType

plugins {
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.jooq.codegen)
}

dependencies {
    jooqCodegen(libs.jooq.meta.extensions)

    implementation(project(":domain"))
    implementation(project(":application"))

    implementation(platform(libs.spring.boot.dependencies))
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.jooq)
    implementation(libs.jooq.kotlin)
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
    testImplementation(libs.h2)
}

jooq {
    configuration {
        generator {
            name = "org.jooq.codegen.KotlinGenerator"
            database {
                name = "org.jooq.meta.extensions.ddl.DDLDatabase"
                properties {
                    property {
                        key = "scripts"
                        value = "src/main/resources/db/migration/*.sql"
                    }
                }
            }
            strategy {
                name = "org.jooq.codegen.DefaultGeneratorStrategy"
                matchers {
                    tables {
                        table {
                            tableClass =
                                MatcherRule().apply {
                                    transform = MatcherTransformType.PASCAL
                                    expression = "$0_Table"
                                }
                        }
                    }
                    enums {
                        enum_ {
                            enumClass =
                                MatcherRule().apply {
                                    transform = MatcherTransformType.PASCAL
                                    expression = "$0_Enum"
                                }
                        }
                    }
                }
            }
        }
    }
}

sourceSets.main {
    kotlin {
        srcDir("build/generated-sources/jooq")
    }
}

tasks.compileKotlin {
    dependsOn(tasks.named("jooqCodegen"))
}

tasks.withType<org.springframework.boot.gradle.tasks.bundling.BootJar> {
    enabled = false
}
