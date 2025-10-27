// =========================================================================
// FICHIER 2 : src/main/kotlin/com/example/mealmanagementapp/backend/models/Models.kt
// =========================================================================
package com.example.mealmanagementapp.backend.models

import kotlinx.serialization.Serializable

@Serializable
data class Resident(
    val id: String,
    val name: String,
    val firstName: String,
    val allergies: List<String>,
    val mealTexture: String,
    val mealType: String
)

@Serializable
data class Staff(
    val id: String,
    val name: String,
    val firstName: String,
    val role: String
)
// AJOUT: Modèle pour la requête de création de personnel
@Serializable
data class NewStaffRequest(
    val name: String,
    val firstName: String
)
@Serializable
data class MealRecord(
    val id: Int = 0, // ID is now an Int from the DB
    val personId: String,
    val personType: String,
    val name: String,
    val firstName: String,
    val mealConfirmed: Boolean,
    val date: String,
    val allergies: List<String>,
    val mealTexture: String,
    val mealType: String
)