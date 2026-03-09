plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover)
    alias(libs.plugins.jooq.codegen)
}

allprojects {
    group = "com.wakita181009.clean"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            freeCompilerArgs.addAll("-Xjsr305=strict")
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

kover {
    merge {
        projects(":domain", ":application", ":presentation")
    }
    reports {
        verify {
            rule {
                bound {
                    minValue = 80
                }
            }
        }
    }
}

val detektClasspath: Configuration by configurations.creating {
    dependencies {
        add(name, libs.detekt.cli)
    }
}
val detektTask =
    tasks.register<JavaExec>("detekt") {
        mainClass.set("dev.detekt.cli.Main")
        classpath = detektClasspath
        dependsOn(":detekt-rules:jar")

        val input = projectDir
        val config = "$projectDir/detekt.yml"
        val exclude = ".*/build/.*,.*/resources/.*"

        jvmArgs("--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow")
        args("--input", input, "--config", config, "--excludes", exclude)
        // argumentProviders is evaluated at execution time to resolve the plugin jar path
        argumentProviders.add {
            listOf(
                "--plugins",
                project(":detekt-rules")
                    .tasks
                    .getByName("jar")
                    .outputs.files.asPath,
            )
        }
    }

tasks.check {
    dependsOn(detektTask)
}
