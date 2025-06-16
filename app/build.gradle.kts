plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // Le plugin compose est activé via buildFeatures, cette ligne peut souvent être retirée
     alias(libs.plugins.kotlin.compose)

    // SUPPRESSION du plugin Google Services
    // id("com.google.gms.google-services")

    // AJOUT du plugin de sérialisation via l'alias
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.mealmanagementapp"
    compileSdk = 35 // Gardé comme vous l'avez défini

    defaultConfig {
        applicationId = "com.example.mealmanagementapp"
        minSdk = 24
        targetSdk = 35 // Gardé comme vous l'avez défini
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8 // Recommandé pour la compatibilité
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.3"
    }

    // CORRECTION: 'packagingOptions' est renommé en 'packaging'
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // SUPPRESSION des dépendances Firebase
    // implementation(platform("com.google.firebase:firebase-bom:32.8.1"))
    // implementation("com.google.firebase:firebase-firestore-ktx")
    // implementation("com.google.firebase:firebase-auth-ktx")
    // implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // AJOUT des dépendances Ktor Client
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android) // Utilise le moteur Android, pas CIO
    implementation(libs.ktor.client.contentnegotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Dépendances AndroidX & Compose existantes
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended:1.6.7")

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
