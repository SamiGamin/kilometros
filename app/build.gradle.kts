import java.util.Base64
import java.security.KeyStore

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "co.samidev.kilometrix"
    compileSdk = 37

    defaultConfig {
        applicationId = "co.SamiDev.kilometrix"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystoreFile = file("release.keystore")
            val keystoreBase64 = System.getenv("SIGNING_KEYSTORE_BASE64")
            if (!keystoreBase64.isNullOrEmpty()) {
                val cleanBase64 = keystoreBase64.replace("\r", "").replace("\n", "").trim()
                val bytes = Base64.getDecoder().decode(cleanBase64)
                keystoreFile.writeBytes(bytes)
            }
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                val sPass = System.getenv("KEYSTORE_PASSWORD")?.trim()?.ifEmpty { null } ?: "android"
                val kPass = System.getenv("KEY_PASSWORD")?.trim()?.ifEmpty { null } ?: sPass

                var resolvedAlias = System.getenv("KEY_ALIAS")?.trim()?.ifEmpty { null }
                try {
                    val ks = KeyStore.getInstance(KeyStore.getDefaultType())
                    keystoreFile.inputStream().use { stream ->
                        ks.load(stream, sPass.toCharArray())
                    }
                    if (resolvedAlias == null || !ks.containsAlias(resolvedAlias)) {
                        val aliases = ks.aliases()
                        if (aliases.hasMoreElements()) {
                            resolvedAlias = aliases.nextElement()
                        }
                    }
                } catch (e: Exception) {
                    // Fallback if inspection fails
                }

                storePassword = sPass
                keyAlias = resolvedAlias ?: "key0"
                keyPassword = kPass
            }
        }
    }

    buildTypes {
        release {
            val releaseSigning = signingConfigs.findByName("release")
            if (releaseSigning?.storeFile?.exists() == true) {
                signingConfig = releaseSigning
            }
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
    buildFeatures {
        compose = true
    }
}

// Renombrado 100% dinámico con la API moderna de Gradle
base {
    archivesName.set("kilometrix-v${android.defaultConfig.versionName}")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.ui.google.fonts)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material.icons.extended)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.googleid)

    // Dagger Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation("org.jetbrains.kotlin:kotlin-metadata-jvm:2.3.0")

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}