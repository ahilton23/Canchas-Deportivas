import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Sin credenciales, debug utiliza únicamente los emuladores de Firebase.
val firebaseConfig = rootProject.file("firebase.properties")
val firebaseValues = Properties().apply {
    if (firebaseConfig.exists()) firebaseConfig.inputStream().use { load(it) }
}
fun firebaseValue(key: String, fallback: String) = firebaseValues.getProperty(key, fallback)
fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.example.canchasdeportivas"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.canchasdeportivas"
        minSdk = 24
        targetSdk = 37
        versionCode = 2
        versionName = "3.0-spark"
        buildConfigField("String", "FIREBASE_PROJECT", quoted(firebaseValue("projectId", "demo-canchas-spark")))
        buildConfigField("String", "FIREBASE_API_KEY", quoted(firebaseValue("apiKey", "AIzaSyAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")))
        buildConfigField("String", "FIREBASE_APP_ID", quoted(firebaseValue("appId", "1:1234567890:android:1234567890abcdee")))
        buildConfigField("String", "EMULATOR_HOST", quoted(firebaseValue("emulatorHost", "10.0.2.2")))
        buildConfigField("boolean", "FIREBASE_EMULATORS", firebaseValue("useEmulators", "true"))

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0")
    // Compatible con Kotlin 2.2 del proyecto; BoM 35 requiere metadatos Kotlin 2.4.
    implementation(platform("com.google.firebase:firebase-bom:34.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation("androidx.test:rules:1.7.0")
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        check(firebaseConfig.exists() && firebaseValue("useEmulators", "true") == "false" &&
            !firebaseValue("projectId", "demo-canchas-spark").startsWith("demo-")) {
            "Configura firebase.properties con tu proyecto real antes de generar release."
        }
    }
}
