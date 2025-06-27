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
    implementation("com.squareup.okhttp3:okhttp:${libs.versions.okhttp.get()}")
    implementation("org.jsoup:jsoup:1.13.1")
    implementation("androidx.annotation:annotation:1.2.0-rc01")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:${libs.versions.kotlin.get()}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${libs.versions.coroutines.get()}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${libs.versions.coroutines.get()}")
    implementation("com.michael-bull.kotlin-result:kotlin-result:${libs.versions.kotlinResult.get()}")

    // Kotest dependencies
    testImplementation("io.kotest:kotest-runner-junit5:${libs.versions.kotest.get()}")
    testImplementation("io.kotest:kotest-assertions-core:${libs.versions.kotest.get()}")
    testImplementation("io.kotest:kotest-extensions-junitxml:${libs.versions.kotest.get()}")
    testImplementation("io.kotest.extensions:kotest-extensions-mockserver:1.0.0")

    // (Required) Writing and executing Unit Tests on the JUnit Platform
    testImplementation("com.michael-bull.kotlin-result:kotlin-result:${libs.versions.kotlinResult.get()}")
    testImplementation("io.mockk:mockk:${libs.versions.mockk.get()}")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${libs.versions.coroutines.get()}")
    testImplementation("org.slf4j:slf4j-simple:${libs.versions.slf4j.get()}")
    testImplementation("org.slf4j:slf4j-api:${libs.versions.slf4j.get()}")
    testImplementation("io.mockk:mockk-agent-jvm:1.12.0")
    testImplementation("org.jetbrains.kotlin:kotlin-reflect:1.5.21")
    testImplementation("org.jdom:jdom2:2.0.6")
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
