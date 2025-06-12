// app/src/main/java/com/example/myfirstapp/PatientViewModel.kt
package com.example.myfirstapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PatientViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = PatientRepository(
        AppDatabase.getInstance(app).patientDao()
    )

    /** Expose la liste des patients en StateFlow pour l’UI */
    val patients: StateFlow<List<Patient>> =
        repo.patientsFlow
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** Sauvegarde en arrière-plan */
    fun save(patient: Patient) = viewModelScope.launch {
        repo.save(patient)
    }

    /** Supprime en arrière-plan */
    fun delete(patient: Patient) = viewModelScope.launch {
        repo.delete(patient)
    }
}
