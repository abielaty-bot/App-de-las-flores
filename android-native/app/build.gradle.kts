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
        versionCode = 2
        versionName = "0.2.0-alpha02"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".preview"
            resValue("string", "app_name", "Las Flores Beta")
        }
        release {
            isMinifyEnabled = false
            // La firma de publicación se configurará en una etapa independiente.
        }
    }
    buildFeatures { compose = true }
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
