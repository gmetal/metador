import org.jetbrains.dokka.gradle.DokkaTask

plugins {
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.android.junit5)
    alias(libs.plugins.dokka)
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

    kotlinOptions {
        jvmTarget = "1.8"
    }

    namespace = "dev.gmetal.metador"
}

tasks.withType<DokkaTask> {
    moduleName.set("Metador")
    offlineMode.set(true)
    dokkaSourceSets.getByName("main"){
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

dependencies {
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.annotation)
    implementation(libs.kotlin.stdlib.jdk7)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlin.result)

    // Kotest dependencies
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.extensions.junitxml)
    testImplementation(libs.kotest.extensions.mockwebserver)
    testImplementation(libs.kotest.datatest)

    // (Required) Writing and executing Unit Tests on the JUnit Platform
    testImplementation(libs.kotlin.result)
    testImplementation(libs.mockk)
    testImplementation(libs.jetbrains.kotlinx.coroutines.test)
    testImplementation(libs.slf4j.simple)
    testImplementation(libs.slf4j.slf4j.api)
    testImplementation(libs.mockk.agent)
    testImplementation(libs.kotlin.reflect)
    testImplementation(libs.jdom2)
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
