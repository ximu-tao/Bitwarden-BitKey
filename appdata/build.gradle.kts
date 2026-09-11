import com.android.build.api.dsl.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

room {
    schemaDirectory("$projectDir/schemas")
}

configure<LibraryExtension> {
    namespace = "com.x8bit.bitwarden.data"
    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt())
    }

    defaultConfig {
        minSdk {
            version = release(libs.versions.minSdk.get().toInt())
        }
        // Keep the version name and keystore alias prefix in sync with the hosting app.
        buildConfigField(
            type = "String",
            name = "VERSION_NAME",
            value = "\"${libs.versions.appVersionName.get()}\"",
        )
        buildConfigField(
            type = "String",
            name = "APPLICATION_ID",
            value = "\"com.x8bit.bitwarden\"",
        )
        // Library modules do not generate BuildConfig.FLAVOR; keep it in sync with the app.
        buildConfigField(
            type = "String",
            name = "FLAVOR",
            value = "\"\"",
        )
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        debug {
            buildConfigField(type = "boolean", name = "HAS_DEBUG_MENU", value = "true")
            buildConfigField(type = "boolean", name = "HAS_LOGS_ENABLED", value = "true")
        }
        create("beta") {
            buildConfigField(type = "boolean", name = "HAS_DEBUG_MENU", value = "true")
            buildConfigField(type = "boolean", name = "HAS_LOGS_ENABLED", value = "false")
        }
        release {
            isMinifyEnabled = false
            buildConfigField(type = "boolean", name = "HAS_DEBUG_MENU", value = "false")
            buildConfigField(type = "boolean", name = "HAS_LOGS_ENABLED", value = "false")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility(libs.versions.jvmTarget.get())
        targetCompatibility(libs.versions.jvmTarget.get())
    }
    buildFeatures {
        buildConfig = true
    }
    testOptions {
        // Required for Robolectric
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
    testFixtures {
        enable = true
    }
    lint {
        disable += listOf(
            "MissingTranslation",
            "ExtraTranslation",
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(libs.versions.jvmTarget.get()))
    }
}

dependencies {
    implementation(project(":annotation"))
    implementation(project(":authenticatorbridge"))
    implementation(project(":core"))
    implementation(project(":data"))
    implementation(project(":network"))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.process)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.google.hilt.android)
    ksp(libs.google.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization)
    implementation(libs.bitwarden.sdk)
    implementation(platform(libs.square.okhttp.bom))
    implementation(libs.square.okhttp)
    implementation(libs.timber)

    // Pull in test fixtures from other modules
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":data")))
    testImplementation(testFixtures(project(":network")))

    testImplementation(libs.google.hilt.android.testing)
    testImplementation(platform(libs.junit.bom))
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.vintage)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk.mockk)
    testImplementation(libs.robolectric.robolectric)
    testImplementation(libs.square.turbine)

    // Shared test utilities consumed by this module's tests and the hosting app's tests.
    testFixturesImplementation(project(":annotation"))
    testFixturesImplementation(project(":authenticatorbridge"))
    testFixturesImplementation(project(":core"))
    testFixturesImplementation(project(":data"))
    testFixturesImplementation(project(":network"))
    testFixturesImplementation(libs.androidx.appcompat)
    testFixturesImplementation(libs.androidx.browser)
    testFixturesImplementation(libs.androidx.core.ktx)
    testFixturesImplementation(libs.androidx.lifecycle.process)
    testFixturesImplementation(libs.androidx.room.ktx)
    testFixturesImplementation(libs.androidx.room.runtime)
    testFixturesImplementation(libs.androidx.security.crypto)
    testFixturesImplementation(libs.androidx.work.runtime.ktx)
    testFixturesImplementation(libs.google.hilt.android)
    testFixturesImplementation(libs.kotlinx.coroutines.android)
    testFixturesImplementation(libs.kotlinx.serialization)
    testFixturesImplementation(libs.bitwarden.sdk)
    testFixturesImplementation(platform(libs.square.okhttp.bom))
    testFixturesImplementation(libs.square.okhttp)
    testFixturesImplementation(libs.timber)
    testFixturesImplementation(libs.google.hilt.android.testing)
    testFixturesImplementation(platform(libs.junit.bom))
    testFixturesImplementation(libs.junit.jupiter)
    testFixturesImplementation(libs.junit.vintage)
    testFixturesImplementation(libs.kotlinx.coroutines.test)
    testFixturesImplementation(libs.mockk.mockk)
    testFixturesImplementation(libs.robolectric.robolectric)
    testFixturesImplementation(libs.square.turbine)

    // Pull in test fixtures from other modules (same set as the test configuration)
    testFixturesImplementation(testFixtures(project(":core")))
    testFixturesImplementation(testFixtures(project(":data")))
    testFixturesImplementation(testFixtures(project(":network")))
}