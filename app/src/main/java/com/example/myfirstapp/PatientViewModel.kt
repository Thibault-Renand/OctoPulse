package com.example.myfirstapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers                   // ← Import ajouté
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PatientViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = PatientRepository(
        AppDatabase.getInstance(app).patientDao()
    )

    /** Flux de tous les patients en base */
    val patients: StateFlow<List<Patient>> =
        repo.patientsFlow
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {   // ← Passe sur IO
            val existing = repo.patientsFlow.first()
            if (existing.isEmpty()) {
                repo.save(
                    Patient(
                        nom = "Dupont",
                        prenom = "Alice",
                        texture = TextureRepas.NORMAL,
                        allergies = listOf("Lait"),
                        regime = RegimeType.VEGETARIEN
                    )
                )
                repo.save(
                    Patient(
                        nom = "Martin",
                        prenom = "Bob",
                        texture = TextureRepas.HACHE,
                        allergies = emptyList(),
                        regime = RegimeType.HALAL
                    )
                )
            }
        }
    }

    /** Sauvegarde en arrière-plan */
    fun save(patient: Patient) = viewModelScope.launch(Dispatchers.IO) {
        repo.save(patient)
    }

    /** Supprime en arrière-plan */
    fun delete(patient: Patient) = viewModelScope.launch(Dispatchers.IO) {
        repo.delete(patient)
    }
}
