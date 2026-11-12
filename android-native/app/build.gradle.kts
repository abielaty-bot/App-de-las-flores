plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

android {
    namespace = "pe.lasflores.controlinterno"
    compileSdk = 36

    defaultConfig {
        applicationId = "pe.lasflores.controlinterno"
        minSdk = 26
        targetSdk = 35
        versionCode = 40015
        versionName = "4.0015"
    }

    signingConfigs {
        create("beta") {
            val path = System.getenv("LF_KEYSTORE_PATH")
            if (!path.isNullOrBlank()) {
                storeFile = file(path)
                storePassword = System.getenv("LF_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("LF_KEY_ALIAS")
                keyPassword = System.getenv("LF_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".preview"
            resValue("string", "app_name", "Las Flores Beta")
        }
        release {
            // Canal de distribución separado de las APK de depuración ya instaladas.
            applicationIdSuffix = ".beta"
            resValue("string", "app_name", "Las Flores Actualizable")
            isMinifyEnabled = false
            if (!System.getenv("LF_KEYSTORE_PATH").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("beta")
            }
        }
    }
    buildFeatures { compose = true; resValues = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.coroutines.android)
    implementation(libs.room3.runtime)
    implementation(libs.sqlite.framework)
    ksp(libs.room3.compiler)
    testImplementation(libs.junit)
}
