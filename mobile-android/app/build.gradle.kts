import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// La URL de la API se configura en local.properties (no versionado):
//   pettech.apiBaseUrl=http://192.168.1.50:8000/api/v1/
// Si no existe, se usa 10.0.2.2 (el equipo anfitrión visto desde el emulador).
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val apiBaseUrlDebug: String = localProps.getProperty("pettech.apiBaseUrl") ?: "http://10.0.2.2:8000/api/v1/"
val apiBaseUrlRelease: String = localProps.getProperty("pettech.apiBaseUrl.release") ?: "https://api.pettech.invalid/api/v1/"

// Firma de publicación (Google Play). El keystore y sus claves viven SOLO en local.properties (no versionado):
//   pettech.upload.storeFile=keystore/pettech-upload.jks
//   pettech.upload.storePassword=...   pettech.upload.keyAlias=pettech-upload   pettech.upload.keyPassword=...
val uploadStoreFile: String? = localProps.getProperty("pettech.upload.storeFile")

android {
    namespace = "cl.pettech.app"
    // Google Play exige targetSdk 36 (Android 16) para apps nuevas desde el 31-08-2026.
    compileSdk = 36

    defaultConfig {
        applicationId = "cl.pettech.app"
        minSdk = 28 // Android 9 (RNF-06)
        targetSdk = 36
        versionCode = 4
        versionName = "0.4.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (uploadStoreFile != null) {
            create("upload") {
                storeFile = rootProject.file(uploadStoreFile)
                storePassword = localProps.getProperty("pettech.upload.storePassword")
                keyAlias = localProps.getProperty("pettech.upload.keyAlias")
                keyPassword = localProps.getProperty("pettech.upload.keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrlDebug\"")
            buildConfigField("boolean", "HTTP_LOGGING", "true")
        }
        release {
            // Endurecimiento (R8/ofuscación) se aborda en la fase de consolidación.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrlRelease\"")
            if (uploadStoreFile != null) signingConfig = signingConfigs.getByName("upload")
            buildConfigField("boolean", "HTTP_LOGGING", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.play.services.location)
    // Mapa OpenStreetMap (sin clave ni facturación; atribución obligatoria, DEC-030)
    implementation(libs.osmdroid.android)
    // Recordatorios diarios de vacunas (HU-06)
    implementation(libs.androidx.work.runtime)
    // OCR del carnet en papel, en el dispositivo (DEC-032)
    implementation(libs.mlkit.text.recognition)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

// Protección: no generar un paquete de publicación que apunte a una URL inexistente o sin firma.
gradle.taskGraph.whenReady {
    val publishing = allTasks.any { task ->
        task.project == project && (task.name == "bundleRelease" || task.name == "assembleRelease")
    }
    if (publishing) {
        require(!apiBaseUrlRelease.contains(".invalid") && apiBaseUrlRelease.startsWith("https://")) {
            "Falta pettech.apiBaseUrl.release (https://...) en local.properties. Ver docs/PUBLICACION_PLAY_STORE.md"
        }
        require(uploadStoreFile != null) {
            "Falta la firma de publicación (pettech.upload.*) en local.properties. Ver docs/PUBLICACION_PLAY_STORE.md"
        }
    }
}
