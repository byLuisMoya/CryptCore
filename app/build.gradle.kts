import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing: local keystore.properties (git-ignored) or CI environment variables.
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(key: String, env: String): String? =
    keystoreProps.getProperty(key) ?: System.getenv(env)

android {
    namespace = "io.github.byluismoya.cryptcore"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.byluismoya.cryptcore"
        minSdk = 28
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"

        buildConfigField(
            "String",
            "RELEASES_URL",
            "\"https://github.com/byLuisMoya/CryptCore/releases/latest\"",
        )
        // Only for taking store/README screenshots: ./gradlew assembleDebug -Pcryptcore.allowScreenshots=true
        val allowScreenshots = (project.findProperty("cryptcore.allowScreenshots") as String?) == "true"
        buildConfigField("boolean", "ALLOW_SCREENSHOTS", allowScreenshots.toString())
    }

    signingConfigs {
        create("release") {
            signingValue("storeFile", "CRYPTCORE_KEYSTORE")?.let { path ->
                storeFile = rootProject.file(path)
                storePassword = signingValue("storePassword", "CRYPTCORE_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "CRYPTCORE_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "CRYPTCORE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val release = signingConfigs.getByName("release")
            if (release.storeFile != null) signingConfig = release
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // No Google-encrypted dependency blob in the APK: keeps builds transparent for GitHub/F-Droid users.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.biometric)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.bouncycastle.prov)

    testImplementation(libs.junit)
}
