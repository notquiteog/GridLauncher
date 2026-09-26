import org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag

plugins {
    kotlin("kapt")
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
    alias(libs.plugins.com.android.application)
    alias(libs.plugins.org.jetbrains.kotlin.android)
    alias(libs.plugins.google.hilt)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "tgo1014.gridlauncher"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "io.github.notquiteog.gridlauncher"
        minSdk = 26
        targetSdk = 37
        versionCode = 20000 + (providers.environmentVariable("VERSION_CODE").orNull?.toInt() ?: 0)
        versionName = "2.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }
    signingConfigs {
        create("release") {
            storeFile = file(providers.environmentVariable("SIGNING_KEYSTORE").orNull ?: "dummyKey")
            storePassword = providers.environmentVariable("SIGNING_STORE_PASSWORD").orNull ?: "123456"
            keyAlias = providers.environmentVariable("SIGNING_KEY_ALIAS").orNull ?: "dummyKey"
            keyPassword = providers.environmentVariable("SIGNING_KEY_PASSWORD").orNull ?: "123456"
        }
    }
    buildTypes {
        debug {
            // isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures.compose = true
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin { jvmToolchain(17) }

kapt.correctErrorTypes = true
hilt.enableAggregatingTask = true

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.ui.util)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.material3)
    implementation(libs.bundles.hilt)
    kapt(libs.hilt.kapt)
    implementation(libs.coil.compose)
    implementation(libs.accompanist.systemuicontroller)
    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.lazytable)
    implementation(libs.haze)
    implementation(libs.haze.materials)
    implementation(libs.material.kolor)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.app.turbine)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)

}