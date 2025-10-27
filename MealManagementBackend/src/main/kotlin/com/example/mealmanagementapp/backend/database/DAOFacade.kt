// =========================================================================
// FICHIER 5 : src/main/kotlin/com/example/mealmanagementapp/backend/database/DAOFacade.kt
// =========================================================================
package com.example.mealmanagementapp.backend.database

import com.example.mealmanagementapp.backend.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.text.SimpleDateFormat
import java.util.*

interface DAOFacade {
    suspend fun getAllResidents(): List<Resident>
    suspend fun addResident(name: String, firstName: String, allergies: List<String>, mealTexture: String, mealType: String, id: String? = null): Resident
    suspend fun updateResident(id: String, resident: Resident): Boolean
    suspend fun deleteResident(id: String): Boolean

    suspend fun getAllStaff(): List<Staff>
    suspend fun addStaff(name: String, firstName: String, role: String, id: String? = null): Staff

    suspend fun getTodaysMealRecords(): List<MealRecord>
    suspend fun confirmMeal(mealRecord: MealRecord): Boolean
}

val dao: DAOFacade = DAOFacadeImpl()

class DAOFacadeImpl : DAOFacade {
    private fun resultRowToResident(row: ResultRow) = Resident(
        id = row[Residents.id].value,
        name = row[Residents.name],
        firstName = row[Residents.firstName],
        allergies = row[Residents.allergies].split(",").filter { it.isNotBlank() },
        mealTexture = row[Residents.mealTexture],
        mealType = row[Residents.mealType]
    )

    private fun resultRowToStaff(row: ResultRow) = Staff(
        id = row[StaffMembers.id].value,
        name = row[StaffMembers.name],
        firstName = row[StaffMembers.firstName],
        role = row[StaffMembers.role]
    )

    private fun resultRowToMealRecord(row: ResultRow) = MealRecord(
        id = row[MealRecords.id].value,
        personId = row[MealRecords.personId],
        personType = row[MealRecords.personType],
        name = row[MealRecords.name],
        firstName = row[MealRecords.firstName],
        mealConfirmed = row[MealRecords.mealConfirmed],
        date = row[MealRecords.date],
        allergies = row[MealRecords.allergies].split(",").filter { it.isNotBlank() },
        mealTexture = row[MealRecords.mealTexture],
        mealType = row[MealRecords.mealType]
    )

    override suspend fun getAllResidents(): List<Resident> = DatabaseFactory.dbQuery {
        Residents.selectAll().map(::resultRowToResident)
    }

    override suspend fun addResident(name: String, firstName: String, allergies: List<String>, mealTexture: String, mealType: String, id: String?): Resident = DatabaseFactory.dbQuery {
        val newId = id ?: "resident-${UUID.randomUUID()}"
        Residents.insert {
            it[Residents.id] = newId
            it[Residents.name] = name
            it[Residents.firstName] = firstName
            it[Residents.allergies] = allergies.joinToString(",")
            it[Residents.mealTexture] = mealTexture
            it[Residents.mealType] = mealType
        }
        // Return the full resident object after insertion
        Resident(newId, name, firstName, allergies, mealTexture, mealType)
    }

    override suspend fun updateResident(id: String, resident: Resident): Boolean = DatabaseFactory.dbQuery {
        Residents.update({ Residents.id eq id }) {
            it[name] = resident.name
            it[firstName] = resident.firstName
            it[allergies] = resident.allergies.joinToString(",")
            it[mealTexture] = resident.mealTexture
            it[mealType] = resident.mealType
        } > 0
    }

    override suspend fun deleteResident(id: String): Boolean = DatabaseFactory.dbQuery {
        Residents.deleteWhere { Residents.id eq id } > 0
    }

    override suspend fun getAllStaff(): List<Staff> = DatabaseFactory.dbQuery {
        StaffMembers.selectAll().map(::resultRowToStaff)
    }

    override suspend fun addStaff(name: String, firstName: String, role: String, id: String?): Staff = DatabaseFactory.dbQuery {
        val newId = id ?: "staff-${UUID.randomUUID()}"
        StaffMembers.insert {
            it[StaffMembers.id] = newId
            it[StaffMembers.name] = name
            it[StaffMembers.firstName] = firstName
            it[StaffMembers.role] = role
        }
        Staff(newId, name, firstName, role)
    }

    override suspend fun getTodaysMealRecords(): List<MealRecord> = DatabaseFactory.dbQuery {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        MealRecords.select { MealRecords.date eq today }.map(::resultRowToMealRecord)
    }

    override suspend fun confirmMeal(mealRecord: MealRecord): Boolean = DatabaseFactory.dbQuery {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        // CORRECTION: la syntaxe de la requête était correcte, mais l'erreur venait d'ailleurs.
        // On s'assure que l'import de `eq` est bien `org.jetbrains.exposed.sql.SqlExpressionBuilder.eq`
        val existingRecord = MealRecords.select { (MealRecords.personId eq mealRecord.personId) and (MealRecords.date eq today) }.singleOrNull()

        if (existingRecord != null) {
            MealRecords.update({ MealRecords.id eq existingRecord[MealRecords.id] }) {
                it[mealConfirmed] = mealRecord.mealConfirmed
            }
        } else {
            MealRecords.insert {
                it[personId] = mealRecord.personId
                it[personType] = mealRecord.personType
                it[name] = mealRecord.name
                it[firstName] = mealRecord.firstName
                it[mealConfirmed] = mealRecord.mealConfirmed
                it[date] = today
                it[allergies] = mealRecord.allergies.joinToString(",")
                it[mealTexture] = mealRecord.mealTexture
                it[mealType] = mealRecord.mealType
            }
        }
        true
    }
}
