// app/src/main/java/com/example/myfirstapp/PatientRepository.kt
package com.example.myfirstapp

import kotlinx.coroutines.flow.Flow

class PatientRepository(private val dao: PatientDao) {
    /** Flux de tous les patients */
    val patientsFlow: Flow<List<Patient>> = dao.getAll()

    /** Sauvegarde un patient et retourne son newRowId */
    fun save(patient: Patient): Long = dao.insert(patient)

    /** Supprime un patient et retourne le nombre de lignes affectées */
    fun delete(patient: Patient): Int = dao.delete(patient)
}
