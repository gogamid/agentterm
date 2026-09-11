plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "app.agentterm"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.agentterm"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-mvp"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        externalNativeBuild {
            cmake { cppFlags += ""; arguments += "-DANDROID_STL=none" }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val ks = System.getenv("AGENTTERM_KEYSTORE_B64")
            val pass = System.getenv("AGENTTERM_KEYSTORE_PASS")
            if (ks != null && pass != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    signingConfigs {
        create("release") {
            val ksB64 = System.getenv("AGENTTERM_KEYSTORE_B64") ?: return@create
            val tmp = File(System.getenv("RUNNER_TEMP") ?: "/tmp", "agentterm.jks")
            tmp.writeBytes(java.util.Base64.getDecoder().decode(ksB64))
            storeFile = tmp
            storePassword = System.getenv("AGENTTERM_KEYSTORE_PASS") ?: ""
            keyAlias = System.getenv("AGENTTERM_KEY_ALIAS") ?: "agentterm"
            keyPassword = System.getenv("AGENTTERM_KEYSTORE_PASS") ?: ""
        }
    }

    externalNativeBuild {
        cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging {
        jniLibs { useLegacyPackaging = true }
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.sshj)
    implementation(libs.bcprov)
}