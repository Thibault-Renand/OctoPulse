package com.example.myfirstapp

import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.myfirstapp.ui.theme.MyFirstAppTheme

class MainActivity : ComponentActivity() {
    private val priority: Int = 1
    private val vm: PatientViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyFirstAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (priority == 0) {
                        EatHereScreen()
                    } else {
                        GestionnaireRepasScreen(vm)
                    }
                }
            }
        }
    }
}

@Composable
fun EatHereScreen() {
    var showDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Souhaitez-vous manger sur place ?",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = { showDialog = true },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Oui")
            }
            OutlinedButton(
                onClick = {
                    Toast.makeText(context, "Vous avez choisi Non.", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Non")
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Confirmation") },
            text = { Text("Vous avez choisi de manger sur place.") },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun GestionnaireRepasScreen(vm: PatientViewModel) {
    val patients by vm.patients.collectAsState()
    var selected by remember { mutableStateOf<Patient?>(null) }

    Row(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(8.dp)
        ) {
            items(patients) { p ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                        .clickable { selected = p }
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Text("${p.nom} ${p.prenom}")
                        Text("Régime : ${p.regime}")
                    }
                }
            }
        }

        selected?.let { patient ->
            PatientDetailPanel(
                patient = patient,
                onSave = {
                    vm.save(it)
                    selected = it
                },
                onDelete = {
                    vm.delete(it)
                    selected = null
                }
            )
        }
    }
}

@Composable
fun PatientDetailPanel(
    patient: Patient,
    onSave: (Patient) -> Unit,
    onDelete: (Patient) -> Unit
) {
    var nom by remember { mutableStateOf(patient.nom) }
    var prenom by remember { mutableStateOf(patient.prenom) }
    var texture by remember { mutableStateOf(patient.texture) }
    var regime by remember { mutableStateOf(patient.regime) }
    var allergies by remember { mutableStateOf(patient.allergies.joinToString(",")) }

    Column(
        Modifier
            .fillMaxHeight()
            .width(300.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Édition du patient", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = nom,
            onValueChange = { nom = it },
            label = { Text("Nom") }
        )
        OutlinedTextField(
            value = prenom,
            onValueChange = { prenom = it },
            label = { Text("Prénom") }
        )

        DropdownMenuBox(
            label = "Texture",
            options = TextureRepas.values().toList(),
            selected = texture,
            onSelected = { texture = it }
        )

        DropdownMenuBox(
            label = "Régime",
            options = RegimeType.values().toList(),
            selected = regime,
            onSelected = { regime = it }
        )

        OutlinedTextField(
            value = allergies,
            onValueChange = { allergies = it },
            label = { Text("Allergies (séparées par ,)") }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                onSave(
                    patient.copy(
                        nom = nom,
                        prenom = prenom,
                        texture = texture,
                        regime = regime,
                        allergies = allergies.split(",").map(String::trim)
                    )
                )
            }) {
                Text("Enregistrer")
            }
            OutlinedButton(onClick = { onDelete(patient) }) {
                Text("Supprimer")
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

@Preview(showBackground = true)
@Composable
fun EatHerePreview() {
    MyFirstAppTheme {
        EatHereScreen()
    }
}
