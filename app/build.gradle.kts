plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.koala.docs"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.koala.docs"
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        versionName = "1.0.10"
    }

    signingConfigs {
        create("release") {
            storeFile = file("koaladocs.jks")
            storePassword = "koaladocs"
            keyAlias = "koaladocs"
            keyPassword = "koaladocs"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
        // Miroir Google de Maven Central, plus fiable pour le moteur de test Robolectric
        unitTests.all { it.systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2") }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging { resources { excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*") } }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Lecture, fusion, découpe, rotation, extraction de texte des PDF
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    // Scanner de documents Google (détection des bords, recadrage, filtres) : module déjà sur le téléphone
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
}
