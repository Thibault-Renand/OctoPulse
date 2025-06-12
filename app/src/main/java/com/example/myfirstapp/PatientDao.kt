// app/src/main/java/com/example/myfirstapp/PatientDao.kt
package com.example.myfirstapp

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientDao {
    @Query("SELECT * FROM patients")
    fun getAll(): Flow<List<Patient>>

    /** Retourne l’ID de la ligne insérée ou remplacée */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(patient: Patient): Long

    /** Retourne le nombre de lignes supprimées */
    @Delete
    fun delete(patient: Patient): Int
}
