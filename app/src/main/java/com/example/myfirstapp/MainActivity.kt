package com.example.myfirstapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myfirstapp.ui.theme.MyFirstAppTheme

class MainActivity : ComponentActivity() {
    // 1 = personnel non soignant (vue seule)
    // 2 = aide-soignante (incrémenter seulement + mixé)
    // 3 = infirmières (modifier tout sauf nom/prénom)
    private val priority: Int = 3
    private val vm: PatientViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyFirstAppTheme {
                Surface(Modifier.fillMaxSize()) {
                    GestionnaireRepasScreen(vm, priority)
                }
            }
        }
    }
}

@Composable
fun GestionnaireRepasScreen(vm: PatientViewModel, priority: Int) {
    val patients by vm.patients.collectAsState()
    var selectedId by remember { mutableStateOf<Long?>(null) }

    Row(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier
                .weight(1f)
                .padding(8.dp)
        ) {
            items(patients) { p ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                        .clickable { selectedId = p.id }
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Text("${p.nom} ${p.prenom}")
                        Text("Texture : ${p.texture}")
                        Text("Régime : ${p.regime}")
                    }
                }
            }
        }

        val patient = patients.find { it.id == selectedId }
        patient?.let {
            PatientDetailPanel(
                patient = it,
                priority = priority,
                onSave = { updated ->
                    vm.save(updated)
                    selectedId = updated.id
                }
            )
        }
    }
}

@Composable
fun PatientDetailPanel(
    patient: Patient,
    priority: Int,
    onSave: (Patient) -> Unit
) {
    var texture by remember { mutableStateOf(patient.texture) }
    var regime by remember { mutableStateOf(patient.regime) }
    var allergies by remember { mutableStateOf(patient.allergies.joinToString(", ")) }

    Column(
        Modifier
            .fillMaxHeight()
            .width(300.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Détails du patient", style = MaterialTheme.typography.headlineSmall)

        when (priority) {
            1 -> {
                // Lecture seule
                Text("Nom : ${patient.nom}")
                Text("Prénom : ${patient.prenom}")
                Text("Texture : ${patient.texture}")
                Text("Régime : ${patient.regime}")
                Text("Allergies : ${patient.allergies.joinToString(", ")}")
            }
            2 -> {
                // Aide-soignante : incrémentation seule vers plus mixé
                Text("Nom : ${patient.nom}")
                Text("Prénom : ${patient.prenom}")

                Text("Texture : $texture")
                Button(onClick = {
                    texture = when (texture) {
                        TextureRepas.NORMAL -> TextureRepas.HACHE
                        TextureRepas.HACHE  -> TextureRepas.MIXE
                        TextureRepas.MIXE   -> TextureRepas.MIXE
                    }
                    onSave(patient.copy(texture = texture, regime = patient.regime, allergies = patient.allergies))
                }) {
                    Text("+ mixé")
                }

                // Affichage en lecture seule du reste
                Text("Régime : ${patient.regime}")
                Text("Allergies : ${patient.allergies.joinToString(", ")}")
            }
            3 -> {
                // Infirmières : tout modifier sauf nom/prénom
                Text("Nom : ${patient.nom}")
                Text("Prénom : ${patient.prenom}")

                // Texture modifiable
                Text("Texture : $texture")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        texture = when (texture) {
                            TextureRepas.NORMAL -> TextureRepas.HACHE
                            TextureRepas.HACHE  -> TextureRepas.MIXE
                            TextureRepas.MIXE   -> TextureRepas.MIXE
                        }
                    }) { Text("+ mixé") }
                    Button(onClick = {
                        texture = when (texture) {
                            TextureRepas.MIXE   -> TextureRepas.HACHE
                            TextureRepas.HACHE  -> TextureRepas.NORMAL
                            TextureRepas.NORMAL -> TextureRepas.NORMAL
                        }
                    }) { Text("− mixé") }
                }

                // Régime modifiable via dropdown
                DropdownMenuBox(
                    label = "Régime",
                    options = RegimeType.values().toList(),
                    selected = regime,
                    onSelected = { regime = it }
                )

                // Allergies modifiables
                OutlinedTextField(
                    value = allergies,
                    onValueChange = { allergies = it },
                    label = { Text("Allergies (séparées par ,)") }
                )

                // Bouton Enregistrer
                Button(onClick = {
                    onSave(
                        patient.copy(
                            texture = texture,
                            regime = regime,
                            allergies = allergies.split(",").map(String::trim)
                        )
                    )
                }) {
                    Text("Enregistrer")
                }
            }
        }
    }
}

@Composable
fun <T> DropdownMenuBox(
    label: String,
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = selected.toString(),
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt.toString()) },
                    onClick = {
                        onSelected(opt)
                        expanded = false
                    }
                )
            }
        }
    }
}
