// =========================================================================
// FICHIER 4 : src/main/kotlin/com/example/mealmanagementapp/backend/database/Entities.kt
// =========================================================================
package com.example.mealmanagementapp.backend.database

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID

// Ce fichier devrait maintenant compiler sans erreur grâce aux corrections dans Tables.kt
class ResidentEntity(id: EntityID<String>) : Entity<String>(id) {
    companion object : EntityClass<String, ResidentEntity>(Residents)
    var name by Residents.name
    var firstName by Residents.firstName
    var allergies by Residents.allergies
    var mealTexture by Residents.mealTexture
    var mealType by Residents.mealType
}

class StaffEntity(id: EntityID<String>) : Entity<String>(id) {
    companion object : EntityClass<String, StaffEntity>(StaffMembers)
    var name by StaffMembers.name
    var firstName by StaffMembers.firstName
    var role by StaffMembers.role
}
