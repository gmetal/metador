import org.jetbrains.dokka.gradle.DokkaTask

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.android.junit5)
    alias(libs.plugins.dokka)}

kotlin {
    androidTarget()

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.cio)
                implementation(libs.kotlinx.io)
                implementation(project.dependencies.platform(libs.kotlin.crypto.hash))
                implementation(libs.kotlin.crypto.hash.sha2)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotest.assertions.core)
                implementation(libs.kotest.extensions.junitxml)
                implementation(libs.kotest.extensions.mockwebserver)
                implementation(libs.kotest.datatest)
            }
        }
        androidMain {
            dependencies {
                implementation(libs.okhttp)
                implementation(libs.jsoup)
                implementation(libs.annotation)
                implementation(libs.kotlinx.coroutines.android)
            }
        }
        androidUnitTest {
            dependencies {
                implementation(libs.kotest.runner.junit5)
                implementation(libs.mockk)
                implementation(libs.jetbrains.kotlinx.coroutines.test)
                implementation(libs.slf4j.simple)
                implementation(libs.slf4j.slf4j.api)
                implementation(libs.mockk.agent)
                implementation(libs.kotlin.reflect)
                implementation(libs.jdom2)
            }
        }
    }
}

android {
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testOptions.targetSdk = libs.versions.targetSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    namespace = "dev.gmetal.metador"
}

tasks.withType<DokkaTask> {
    moduleName.set("Metador")
    offlineMode.set(true)
    dokkaSourceSets.getByName("commonMain"){
        displayName.set("main")
        includes.from("description.md")
        jdkVersion.set(8)
        suppress.set(false)
        platform.set(org.jetbrains.dokka.Platform.jvm)
        noStdlibLink.set(false)
        noJdkLink.set(false)
        noAndroidSdkLink.set(false)
    }
}

val archiveSources by tasks.registering(Jar::class) {
    archiveFileName.set("metador-lib-sources.jar")
    from(android.sourceSets.findByName("main")!!.kotlin.directories)
    exclude("dev/gmetal/metador/response")
}
val javadocJar by tasks.registering(Jar::class) {
    dependsOn(tasks.dokkaHtml)
    archiveClassifier.set("javadoc")
    from(tasks.dokkaJavadoc.get().outputDirectory)
}
