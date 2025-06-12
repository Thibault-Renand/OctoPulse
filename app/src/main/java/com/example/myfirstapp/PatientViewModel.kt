package com.example.myfirstapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PatientViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.getInstance(app).patientDao()
    private val repo = PatientRepository(dao)

    /** Flux de tous les patients en base, démarré _tout de suite_ */
    val patients: StateFlow<List<Patient>> =
        repo.patientsFlow
            .stateIn(viewModelScope,
                SharingStarted.Eagerly,
                emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            if (repo.patientsFlow.first().isEmpty()) {
                listOf(
                    Patient(
                        nom = "Dupont",
                        prenom = "Alice",
                        texture = TextureRepas.NORMAL,
                        allergies = listOf("Lait"),
                        regime = RegimeType.VEGETARIEN
                    ),
                    Patient(
                        nom = "Martin",
                        prenom = "Bob",
                        texture = TextureRepas.HACHE,
                        allergies = emptyList(),
                        regime = RegimeType.HALAL
                    ),
                    Patient(
                        nom = "Durand",
                        prenom = "Claire",
                        texture = TextureRepas.MIXE,
                        allergies = listOf("Gluten", "Fruits à coque"),
                        regime = RegimeType.STANDARD
                    ),
                    Patient(
                        nom = "Bernard",
                        prenom = "David",
                        texture = TextureRepas.NORMAL,
                        allergies = listOf("Poisson"),
                        regime = RegimeType.VEGETARIEN
                    ),
                    Patient(
                        nom = "Petit",
                        prenom = "Emma",
                        texture = TextureRepas.HACHE,
                        allergies = emptyList(),
                        regime = RegimeType.STANDARD
                    )
                ).forEach { repo.save(it) }
            }
        }
    }

    /** Sauvegarde un patient (insert/replace) */
    fun save(patient: Patient) = viewModelScope.launch(Dispatchers.IO) {
        repo.save(patient)
    }

    /** Supprime un patient */
    fun delete(patient: Patient) = viewModelScope.launch(Dispatchers.IO) {
        repo.delete(patient)
    }
}
