import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.PI
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
        versionCode = 40032
        versionName = "4.0032"
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

val pulseRes = layout.buildDirectory.dir("generated/pulse/res")
val generatePulseSounds = tasks.register("generatePulseSounds") {
    outputs.dir(pulseRes)
    doLast {
        val raw = pulseRes.get().dir("raw").asFile.apply { mkdirs() }
        val tones = mapOf(
            "signature" to listOf(659.25,880.0),
            "attention" to listOf(523.25,698.46,783.99),
            "urgent" to listOf(440.0,587.33,440.0,587.33),
            "critical" to listOf(349.23,523.25,349.23,523.25,698.46),
            "resolved" to listOf(523.25,659.25,783.99))
        tones.forEach { (name, notes) ->
            val rate = 22050
            val step = if(name=="resolved") 0.11 else 0.16
            val duration = if(name=="critical") 0.20 else 0.27
            val count = ((step*(notes.size-1)+duration)*rate).toInt()
            val buf = ByteBuffer.allocate(44+count*2).order(ByteOrder.LITTLE_ENDIAN)
            buf.put("RIFF".toByteArray()); buf.putInt(36+count*2); buf.put("WAVEfmt ".toByteArray())
            buf.putInt(16); buf.putShort(1); buf.putShort(1); buf.putInt(rate); buf.putInt(rate*2)
            buf.putShort(2); buf.putShort(16); buf.put("data".toByteArray()); buf.putInt(count*2)
            for(i in 0 until count) {
                val t=i.toDouble()/rate
                var sample=0.0
                notes.forEachIndexed { j, hz ->
                    val x=t-j*step
                    if(x>=0 && x<duration) {
                        val envelope=minOf(x/0.025,1.0)*exp(-5*x/duration)
                        sample+=sin(2*PI*hz*x)*envelope*0.20
                    }
                }
                buf.putShort((sample.coerceIn(-1.0,1.0)*32767).toInt().toShort())
            }
            raw.resolve("pulse_"+name+".wav").writeBytes(buf.array())
        }
    }
}
android.sourceSets.getByName("main").res.srcDir(pulseRes.get().asFile)
tasks.named("preBuild").configure { dependsOn(generatePulseSounds) }
