// build.gradle.kts (Niveau du projet)

plugins {
    // Les plugins de l'application et de Kotlin, avec `apply false`
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply true // Si vous utilisez le plugin compose au niveau du projet

    // AJOUTEZ CETTE LIGNE pour déclarer le plugin Google Services avec sa version
    id("com.google.gms.google-services") version "4.4.1" apply false // Utilisez la version la plus récente si disponible, ex: 4.4.1
}

// Ce bloc est généralement présent pour définir les dépôts de plugins
// Ne modifiez pas si votre fichier est déjà configuré comme ceci ou utilise `dependencyResolutionManagement`
/*
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.google.gms:google-services:4.4.1") // Dépendance du plugin pour l'ancien DSL Groovy, mais utile pour reference
    }
}
*/

// Ce bloc gère la résolution des dépendances au niveau du projet
// Il est souvent présent dans les nouveaux projets Android Studio.
// Si vous avez un bloc `dependencyResolutionManagement` ou `repositories`,
// assurez-vous que `google()` et `mavenCentral()` sont présents.
/*
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
*/