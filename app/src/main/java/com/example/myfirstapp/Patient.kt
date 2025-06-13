package com.example.myfirstapp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "patients")
data class Patient(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val nom: String,
    val prenom: String,
    val texture: TextureRepas,
    val allergies: List<String>,
    val regime: RegimeType
)

enum class TextureRepas { NORMAL, HACHE, MIXE }
enum class RegimeType  { STANDARD, VEGETARIEN, HALAL }
