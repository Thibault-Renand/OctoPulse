// =========================================================================
// FICHIER 3 : src/main/kotlin/com/example/mealmanagementapp/backend/database/Tables.kt
// =========================================================================
package com.example.mealmanagementapp.backend.database

import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable

// CORRECTION: Use IdTable<String> for tables with a String primary key to work with the DAO pattern.
object Residents : IdTable<String>("residents") {
    override val id = varchar("id", 128).entityId()
    val name = varchar("name", 128)
    val firstName = varchar("first_name", 128)
    val allergies = varchar("allergies", 1024)
    val mealTexture = varchar("meal_texture", 64)
    val mealType = varchar("meal_type", 64)
    override val primaryKey = PrimaryKey(id)
}

object StaffMembers : IdTable<String>("staff") {
    override val id = varchar("id", 128).entityId()
    val name = varchar("name", 128)
    val firstName = varchar("first_name", 128)
    val role = varchar("role", 64)
    override val primaryKey = PrimaryKey(id)
}

// CORRECTION: Use IntIdTable for tables with an auto-incrementing Integer primary key.
object MealRecords : IntIdTable("meal_records") {
    val personId = varchar("person_id", 128)
    val personType = varchar("person_type", 16)
    val name = varchar("name", 128)
    val firstName = varchar("first_name", 128)
    val mealConfirmed = bool("meal_confirmed")
    val date = varchar("date", 10)
    val allergies = varchar("allergies", 1024)
    val mealTexture = varchar("meal_texture", 64)
    val mealType = varchar("meal_type", 64)
}
