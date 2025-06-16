// =========================================================================
// FICHIER 6 : src/main/kotlin/com/example/mealmanagementapp/backend/database/DatabaseFactory.kt
// =========================================================================
package com.example.mealmanagementapp.backend.database

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    fun init() {
        val driverClassName = "org.h2.Driver"
        val jdbcURL = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"
        val database = Database.connect(jdbcURL, driverClassName)
        transaction(database) {
            SchemaUtils.create(Residents, StaffMembers, MealRecords)

            // CORRECTION: On insère directement les données dans la transaction synchrone.
            if (Residents.selectAll().empty()) {
                listOf(
                    Pair("resident-01", mapOf("name" to "Renand", "firstName" to "Thibault", "allergies" to "lactose", "mealTexture" to "normal", "mealType" to "aucun")),
                    Pair("resident-02", mapOf("name" to "Dupont", "firstName" to "Marie", "allergies" to "gluten", "mealTexture" to "haché", "mealType" to "vegetarien")),
                    Pair("resident-03", mapOf("name" to "Lefevre", "firstName" to "Pierre", "allergies" to "", "mealTexture" to "mixé", "mealType" to "hypocalorique")),
                    Pair("resident-04", mapOf("name" to "Martin", "firstName" to "Sophie", "allergies" to "noix,poisson", "mealTexture" to "normal", "mealType" to "aucun")),
                    Pair("resident-05", mapOf("name" to "Bernard", "firstName" to "Luc", "allergies" to "", "mealTexture" to "normal", "mealType" to "vegan")),
                    Pair("resident-06", mapOf("name" to "Garcia", "firstName" to "Eva", "allergies" to "gluten", "mealTexture" to "normal", "mealType" to "vegetarien"))
                ).forEach { (resId, data) ->
                    Residents.insert {
                        it[id] = resId
                        it[name] = data["name"]!!
                        it[firstName] = data["firstName"]!!
                        it[allergies] = data["allergies"]!!
                        it[mealTexture] = data["mealTexture"]!!
                        it[mealType] = data["mealType"]!!
                    }
                }
            }

            if(StaffMembers.selectAll().empty()){
                listOf(
                    Pair("staff-01", mapOf("name" to "Durand", "firstName" to "Alain", "role" to "Soignant")),
                    Pair("staff-02", mapOf("name" to "Petit", "firstName" to "Carole", "role" to "Personnel")),
                    Pair("staff-03", mapOf("name" to "Moreau", "firstName" to "Julien", "role" to "Visiteur"))
                ).forEach { (staffId, data) ->
                    StaffMembers.insert {
                        it[id] = staffId
                        it[name] = data["name"]!!
                        it[firstName] = data["firstName"]!!
                        it[role] = data["role"]!!
                    }
                }
            }
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}