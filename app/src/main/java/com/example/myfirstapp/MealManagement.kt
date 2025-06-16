@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.myfirstapp

import android.app.Application
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.activity.viewModels
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.call.body
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.UUID

// --- Constantes pour la Découverte ---
const val DISCOVERY_PORT = 9999
const val DISCOVERY_KEYWORD = "poulpe"
const val EMULATOR_FALLBACK_IP = "10.0.2.2"

// --- 1. Data Models ---
@Serializable
data class Resident(
    val id: String = "",
    val name: String = "",
    val firstName: String = "",
    val allergies: List<String> = emptyList(),
    val mealTexture: String = "normal",
    val mealType: String = "aucun"
)

@Serializable
data class Staff(
    val id: String = "",
    val name: String = "",
    val firstName: String = "",
    val role: String = "Visiteur"
)

@Serializable
data class MealRecord(
    val id: Int = 0,
    val personId: String,
    val personType: String,
    val name: String,
    val firstName: String,
    val mealConfirmed: Boolean,
    val date: String,
    val allergies: List<String> = emptyList(),
    val mealTexture: String = "normal",
    val mealType: String = "aucun"
)


// --- 2. Data Repository ---
class DataRepository {
    private val client = HttpClient(Android) {
        install(ContentNegotiation) { json() }
    }
    private var baseUrl: String? = null

    fun setBaseUrl(ip: String) {
        baseUrl = "http://$ip:8080"
    }

    /**
     * Tente de découvrir le serveur sur le réseau via UDP.
     */
    suspend fun discoverServer(): String? {
        return withContext(Dispatchers.IO) {
            try {
                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    socket.soTimeout = 3000 // Attend 3 secondes max

                    val sendData = DISCOVERY_KEYWORD.toByteArray()
                    // Adresse de broadcast pour le réseau local
                    val broadcastAddress = InetAddress.getByName("255.255.255.255")
                    val sendPacket = DatagramPacket(sendData, sendData.size, broadcastAddress, DISCOVERY_PORT)
                    socket.send(sendPacket)

                    println("Discovery request sent.")

                    val receiveBuffer = ByteArray(1024)
                    val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
                    socket.receive(receivePacket)

                    val serverIp = String(receivePacket.data, 0, receivePacket.length)
                    println("Server found via discovery: $serverIp")
                    serverIp
                }
            } catch (e: Exception) {
                println("Discovery failed: ${e.message}")
                null
            }
        }
    }

    private suspend inline fun <T> safeApiCall(call: () -> T): T? {
        if (baseUrl == null) {
            println("Cannot make API call: server IP is not set.")
            return null
        }
        return try {
            call()
        } catch (e: Exception) {
            println("API call failed: ${e.message}")
            null
        }
    }

    suspend fun getResidents(): List<Resident> = safeApiCall { client.get("$baseUrl/residents").body() } ?: emptyList()
    suspend fun getStaff(): List<Staff> = safeApiCall { client.get("$baseUrl/staff").body() } ?: emptyList()
    suspend fun getTodaysMealRecords(): List<MealRecord> = safeApiCall { client.get("$baseUrl/meals/today").body() } ?: emptyList()

    suspend fun confirmMeal(record: MealRecord) = safeApiCall {
        client.post("$baseUrl/meals") {
            contentType(ContentType.Application.Json)
            setBody(record)
        }
    }

    suspend fun addOrUpdateResident(resident: Resident) = safeApiCall {
        if (resident.id.startsWith("resident-")) {
            client.put("$baseUrl/residents/${resident.id}") {
                contentType(ContentType.Application.Json)
                setBody(resident)
            }
        } else {
            client.post("$baseUrl/residents") {
                contentType(ContentType.Application.Json)
                setBody(resident)
            }
        }
    }

    suspend fun deleteResident(id: String) = safeApiCall { client.delete("$baseUrl/residents/$id") }
    suspend fun updateResident(id: String, resident: Resident) = safeApiCall {
        client.put("$baseUrl/residents/$id") {
            contentType(ContentType.Application.Json)
            setBody(resident)
        }
    }
}

// --- 3. ViewModel ---
sealed class ServerState {
    object Searching : ServerState()
    data class Connected(val ip: String, val method: String) : ServerState()
    object Error : ServerState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DataRepository()
    private val screenHistory = mutableStateListOf<String>()

    val serverState = MutableStateFlow<ServerState>(ServerState.Searching)
    val priorityId = MutableStateFlow<Int?>(null)
    val currentScreen = MutableStateFlow("home")
    val residents = MutableStateFlow<List<Resident>>(emptyList())
    val staff = MutableStateFlow<List<Staff>>(emptyList())
    val todaysMealRecords = MutableStateFlow<List<MealRecord>>(emptyList())
    val showToast = MutableStateFlow<String?>(null)
    val showConfirmDialog = MutableStateFlow<String?>(null)
    private var onConfirmAction: () -> Unit = {}
    val loggedInStaff = MutableStateFlow<Staff?>(null)

    val residentPresence: StateFlow<Map<String, Boolean>> = todaysMealRecords.map { records ->
        records.filter { it.personType == "resident" }.associate { it.personId to it.mealConfirmed }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())


    init {
        initializeConnection()
    }

    fun initializeConnection() {
        serverState.value = ServerState.Searching
        viewModelScope.launch {
            // Tentative 1: Découverte automatique
            var foundIp = repository.discoverServer()
            var connectionMethod = "Découverte automatique"

            // Tentative 2: Secours pour l'émulateur
            if (foundIp == null) {
                println("Discovery failed. Trying fallback for emulator...")
                connectionMethod = "Secours (IP fixe)"
                repository.setBaseUrl(EMULATOR_FALLBACK_IP)
                // On teste si la connexion de secours fonctionne en récupérant une donnée
                if (repository.getStaff().isNotEmpty()) {
                    foundIp = EMULATOR_FALLBACK_IP
                } /*else {
                    repository.setBaseUrl(null) // Reset si ça ne marche pas non plus
                }*/
            } else {
                repository.setBaseUrl(foundIp)
            }

            // Mise à jour de l'UI
            if (foundIp != null) {
                serverState.value = ServerState.Connected(foundIp, connectionMethod)
                fetchAllData()
            } else {
                serverState.value = ServerState.Error
            }
        }
    }

    private fun fetchAllData() {
        viewModelScope.launch {
            residents.value = repository.getResidents()
            staff.value = repository.getStaff()
            todaysMealRecords.value = repository.getTodaysMealRecords()
        }
    }

    fun setPriorityId(id: Int) {
        priorityId.value = id
        navigateTo(if (id == 0) "resident_confirmation" else "dashboard")
    }

    fun navigateTo(screen: String) {
        if (currentScreen.value != screen) {
            screenHistory.add(currentScreen.value)
            currentScreen.value = screen
        }
    }

    fun navigateBack() {
        if (screenHistory.isNotEmpty()) {
            currentScreen.value = screenHistory.removeAt(screenHistory.lastIndex)
        } else {
            navigateTo("home")
        }
    }

    fun showConfirmDialog(title: String, onConfirm: () -> Unit) {
        onConfirmAction = onConfirm
        showConfirmDialog.value = title
    }

    fun dismissConfirmDialog() { showConfirmDialog.value = null }
    fun onConfirm() {
        onConfirmAction()
        dismissConfirmDialog()
    }

    fun confirmResidentMeal(resident: Resident, eatOnSite: Boolean) = viewModelScope.launch {
        repository.confirmMeal(MealRecord(
            personId = resident.id, personType = "resident", name = resident.name, firstName = resident.firstName,
            mealConfirmed = eatOnSite, date = "", allergies = resident.allergies,
            mealTexture = resident.mealTexture, mealType = resident.mealType
        ))
        fetchAllData()
    }

    fun confirmStaffMeal(staff: Staff, eatOnSite: Boolean) = viewModelScope.launch {
        repository.confirmMeal(MealRecord(
            personId = staff.id, personType = "staff", name = staff.name, firstName = staff.firstName,
            mealConfirmed = eatOnSite, date = ""
        ))
        fetchAllData()
        if(eatOnSite) navigateTo("meal_summary")
    }

    fun addOrUpdateResident(resident: Resident) = viewModelScope.launch {
        repository.addOrUpdateResident(resident)
        showToast.value = "Résident sauvegardé."
        fetchAllData()
    }

    fun deleteResident(residentId: String) = viewModelScope.launch {
        repository.deleteResident(residentId)
        showToast.value = "Résident supprimé."
        fetchAllData()
    }

    fun updateResidentTexture(resident: Resident, increment: Boolean) = viewModelScope.launch {
        val levels = listOf("normal", "haché", "mixé")
        val currentIndex = levels.indexOf(resident.mealTexture)
        val newIndex = if(increment) (currentIndex + 1).coerceAtMost(levels.lastIndex) else (currentIndex - 1).coerceAtLeast(0)
        if (currentIndex != newIndex) {
            val updatedResident = resident.copy(mealTexture = levels[newIndex])
            repository.updateResident(resident.id, updatedResident)
            showToast.value = "Texture mise à jour."
            fetchAllData()
        }
    }

    fun getMealSummary(): Map<String, Any> {
        val absentResidentIds = todaysMealRecords.value.filter { it.personType == "resident" && !it.mealConfirmed }.map { it.personId }.toSet()
        val presentResidents = residents.value.filter { it.id !in absentResidentIds }
        val absentResidents = residents.value.filter { it.id in absentResidentIds }

        val presentStaffCount = todaysMealRecords.value.count { it.personType == "staff" && it.mealConfirmed }

        val normalMeals = presentResidents
            .filter { it.mealType == "aucun" && it.allergies.isEmpty() }
            .groupBy { it.mealTexture }
            .mapValues { it.value.size }

        val specialMeals = presentResidents
            .filter { it.mealType != "aucun" || it.allergies.isNotEmpty() }
            .groupBy { it.mealType.uppercase() }
            .mapValues { mealTypeEntry ->
                mealTypeEntry.value
                    .groupBy { it.allergies.sorted().joinToString(", ").ifEmpty { "Aucune" } }
                    .mapValues { allergyEntry ->
                        allergyEntry.value
                            .groupBy { it.mealTexture }
                            .mapValues { it.value.size }
                    }
            }

        return mapOf(
            "absentResidents" to absentResidents,
            "presentStaffCount" to presentStaffCount,
            "normalMeals" to normalMeals,
            "specialMeals" to specialMeals
        )
    }
}

// --- 4. MainActivity ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel: MainViewModel by viewModels()
        setContent { MaterialTheme { AppContent(viewModel) } }
    }
}

// --- 5. UI Composables ---
@Composable
fun AppContent(viewModel: MainViewModel) {
    val serverState by viewModel.serverState.collectAsState()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val state = serverState) {
            is ServerState.Searching -> {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Recherche du serveur sur le réseau...")
                    }
                }
            }
            is ServerState.Connected -> {
                // Toast.makeText(LocalContext.current, "Connecté via ${state.method}", Toast.LENGTH_SHORT).show()
                MainAppNavigation(viewModel)
            }
            is ServerState.Error -> {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Warning, contentDescription = "Error", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Serveur introuvable", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error)
                        Text(
                            "Veuillez vérifier que le serveur est bien lancé sur le même réseau Wi-Fi.",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Button(onClick = { viewModel.initializeConnection() }, modifier = Modifier.padding(top = 24.dp)) {
                            Text("Réessayer")
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun MainAppNavigation(viewModel: MainViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val context = LocalContext.current

    viewModel.showToast.collectAsState().value?.let {
        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        viewModel.showToast.value = null
    }

    viewModel.showConfirmDialog.collectAsState().value?.let { title ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissConfirmDialog() },
            title = { Text(title) },
            text = { Text("Cette action est irréversible.")},
            confirmButton = { Button(onClick = { viewModel.onConfirm() }) { Text("Confirmer") } },
            dismissButton = { Button(onClick = { viewModel.dismissConfirmDialog() }) { Text("Annuler") } }
        )
    }

    when (currentScreen) {
        "home" -> AppEntryScreen(viewModel)
        "resident_confirmation" -> ResidentMealConfirmationScreen(viewModel)
        "dashboard" -> DashboardScreen(viewModel)
        "mymeal" -> MyMealStaffScreen(viewModel)
        "meal_manager" -> MealManagerScreen(viewModel)
        "meal_summary" -> MealSummaryScreen(viewModel)
        else -> AppEntryScreen(viewModel)
    }
}


@Composable fun TopBar(title: String, onBackClick: () -> Unit) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.Filled.ArrowBack, "Retour") } }
    )
}

@Composable fun AppEntryScreen(viewModel: MainViewModel) {
    var priorityIdInput by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Text("Gestion des Repas", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = priorityIdInput,
            onValueChange = { priorityIdInput = it.filter { c -> c.isDigit() } },
            label = { Text("ID de Priorité (0, 1, 2, 3)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = { priorityIdInput.toIntOrNull()?.let { viewModel.setPriorityId(it) } }) {
            Text("Accéder")
        }
    }
}

@Composable fun ResidentMealConfirmationScreen(viewModel: MainViewModel) {
    val resident = viewModel.residents.collectAsState().value.find { it.id == "resident-01" } ?: Resident(firstName = "Thibault", name = "Renand")
    Scaffold(topBar = { TopBar("Ma Confirmation", onBackClick = { viewModel.navigateTo("home") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), Arrangement.Center, Alignment.CenterHorizontally) {
            Text("Bonjour, ${resident.firstName} ${resident.name}", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(32.dp))
            Text("Serez-vous présent pour le prochain repas ?", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                Button(onClick = { viewModel.confirmResidentMeal(resident, true) }, Modifier.weight(1f).height(50.dp)) { Text("Oui") }
                Spacer(Modifier.width(16.dp))
                Button(onClick = { viewModel.confirmResidentMeal(resident, false) }, Modifier.weight(1f).height(50.dp)) { Text("Non") }
            }
        }
    }
}

@Composable fun DashboardScreen(viewModel: MainViewModel) {
    Scaffold(topBar = { TopBar("Tableau de Bord", onBackClick = { viewModel.navigateTo("home") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), Arrangement.Center, Alignment.CenterHorizontally) {
            Button(onClick = { viewModel.navigateTo("mymeal") }, modifier = Modifier.fillMaxWidth().height(60.dp)) { Text("MyMeal (Personnel)", fontSize = 18.sp) }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { viewModel.navigateTo("meal_manager") }, modifier = Modifier.fillMaxWidth().height(60.dp)) { Text("MealManager (Résidents)", fontSize = 18.sp) }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { viewModel.navigateTo("meal_summary") }, modifier = Modifier.fillMaxWidth().height(60.dp)) { Text("Récapitulatif des Repas", fontSize = 18.sp) }
        }
    }
}

@Composable fun MyMealStaffScreen(viewModel: MainViewModel) {
    val staffList by viewModel.staff.collectAsState()
    val selectedStaff by viewModel.loggedInStaff.collectAsState()

    Scaffold(topBar = { TopBar("MyMeal (Personnel)", onBackClick = { viewModel.navigateBack() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Qui êtes-vous ?", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(staffList) { staff ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { viewModel.loggedInStaff.value = staff },
                        colors = CardDefaults.cardColors(containerColor = if (selectedStaff == staff) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text("${staff.firstName} ${staff.name} (${staff.role})", modifier = Modifier.padding(16.dp))
                    }
                }
            }
            selectedStaff?.let { staff ->
                Spacer(Modifier.height(16.dp))
                Text("Confirmer présence pour ${staff.firstName} :", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    Button(onClick = { viewModel.confirmStaffMeal(staff, true) }, Modifier.weight(1f).height(50.dp)) { Text("Oui") }
                    Spacer(Modifier.width(16.dp))
                    Button(onClick = { viewModel.confirmStaffMeal(staff, false) }, Modifier.weight(1f).height(50.dp)) { Text("Non") }
                }
            }
        }
    }
}

@Composable fun MealManagerScreen(viewModel: MainViewModel) {
    val residents by viewModel.residents.collectAsState()
    val priority by viewModel.priorityId.collectAsState()
    val presenceMap by viewModel.residentPresence.collectAsState()
    var showForm by remember { mutableStateOf(false) }
    var editingResident by remember { mutableStateOf<Resident?>(null) }

    var name by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var allergies by remember { mutableStateOf("") }
    var mealType by remember { mutableStateOf("aucun") }
    var mealTypeExpanded by remember { mutableStateOf(false) }
    val mealTypesOptions = listOf("aucun", "vegetarien", "vegan", "hypocalorique", "hypercalorique")

    fun openForm(resident: Resident? = null) {
        editingResident = resident
        name = resident?.name ?: ""
        firstName = resident?.firstName ?: ""
        allergies = resident?.allergies?.joinToString(", ") ?: ""
        mealType = resident?.mealType ?: "aucun"
        showForm = true
    }

    fun resetForm() {
        showForm = false
        editingResident = null
    }

    Scaffold(topBar = { TopBar("MealManager (Résidents)", { viewModel.navigateBack() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (priority == 3 && !showForm) {
                Button(onClick = { openForm() }) { Text("Ajouter Résident") }
                Spacer(Modifier.height(16.dp))
            }

            if (showForm) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if(editingResident != null) "Modifier Résident" else "Nouveau Résident", style = MaterialTheme.typography.titleLarge)
                        OutlinedTextField(value = firstName, onValueChange = {firstName = it}, label = { Text("Prénom") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = name, onValueChange = {name = it}, label = { Text("Nom") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = allergies, onValueChange = {allergies = it}, label = { Text("Allergies (virgules)") }, modifier = Modifier.fillMaxWidth())

                        ExposedDropdownMenuBox(
                            expanded = mealTypeExpanded,
                            onExpandedChange = { mealTypeExpanded = !mealTypeExpanded }
                        ) {
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                readOnly = true,
                                value = mealType,
                                onValueChange = {},
                                label = { Text("Régime") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = mealTypeExpanded) }
                            )
                            ExposedDropdownMenu(
                                expanded = mealTypeExpanded,
                                onDismissRequest = { mealTypeExpanded = false }
                            ) {
                                mealTypesOptions.forEach { selectionOption ->
                                    DropdownMenuItem(
                                        text = { Text(selectionOption) },
                                        onClick = {
                                            mealType = selectionOption
                                            mealTypeExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Row {
                            Button(onClick = {
                                val resident = editingResident?.copy(
                                    name = name, firstName = firstName, allergies = allergies.split(",").map{it.trim()}.filter{it.isNotEmpty()}, mealType = mealType
                                ) ?: Resident(id = "resident-${UUID.randomUUID()}", name = name, firstName = firstName, allergies = allergies.split(",").map{it.trim()}.filter{it.isNotEmpty()}, mealType = mealType)
                                viewModel.addOrUpdateResident(resident)
                                resetForm()
                            }) { Text("Sauvegarder") }
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { resetForm() }) { Text("Annuler") }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            LazyColumn {
                items(residents, key = { it.id }) { resident ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${resident.firstName} ${resident.name}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                if (priority!! >= 1) {
                                    Switch(
                                        checked = presenceMap[resident.id] ?: true,
                                        onCheckedChange = { isPresent -> viewModel.confirmResidentMeal(resident, isPresent) }
                                    )
                                }
                            }
                            Text("Allergies: ${resident.allergies.joinToString(", ").ifEmpty { "Aucune" }}")
                            Text("Régime: ${resident.mealType.uppercase()}")
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Texture: ${resident.mealTexture}", modifier = Modifier.weight(1f))
                                if (priority!! >= 2) {
                                    IconButton(onClick = { viewModel.updateResidentTexture(resident, true) }) { Icon(Icons.Default.Add, "Incrémenter texture") }
                                }
                                if (priority!! >= 3) {
                                    IconButton(onClick = { viewModel.updateResidentTexture(resident, false) }) { Icon(Icons.Default.Remove, "Décrémenter texture") }
                                }
                            }
                            if (priority!! >= 2) {
                                Row(Modifier.fillMaxWidth(), Arrangement.End) {
                                    IconButton(onClick = { openForm(resident) }) { Icon(Icons.Default.Edit, "Modifier") }
                                    if(priority == 3) {
                                        IconButton(onClick = { viewModel.showConfirmDialog("Supprimer ${resident.firstName}?") { viewModel.deleteResident(resident.id) }}) { Icon(Icons.Default.Delete, "Supprimer") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable fun MealSummaryScreen(viewModel: MainViewModel) {
    val summary = viewModel.getMealSummary()
    val absentResidents = summary["absentResidents"] as List<Resident>
    val presentStaffCount = summary["presentStaffCount"] as Int
    val normalMeals = summary["normalMeals"] as Map<String, Int>
    @Suppress("UNCHECKED_CAST")
    val specialMeals = summary["specialMeals"] as Map<String, Map<String, Map<String, Int>>>

    Scaffold(topBar = { TopBar("Récapitulatif des Repas", { viewModel.navigateBack() }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            item {
                Text("Résidents Absents Aujourd'hui", style = MaterialTheme.typography.titleLarge)
                if (absentResidents.isEmpty()) {
                    Text("Aucun", modifier = Modifier.padding(8.dp))
                } else {
                    absentResidents.forEach { Text("• ${it.firstName} ${it.name}", modifier = Modifier.padding(start = 8.dp)) }
                }
                Divider(Modifier.padding(vertical = 16.dp))
            }

            item {
                Text("Repas du Personnel", style = MaterialTheme.typography.titleLarge)
                Text("$presentStaffCount repas confirmés", modifier = Modifier.padding(8.dp))
                Divider(Modifier.padding(vertical = 16.dp))
            }

            item {
                Text("Repas Résidents (Normaux)", style = MaterialTheme.typography.titleLarge)
                if (normalMeals.isEmpty()) Text("Aucun", modifier = Modifier.padding(8.dp))
                else normalMeals.forEach { (texture, count) -> Text("• $count en texture ${texture}", modifier = Modifier.padding(start = 8.dp)) }
                Divider(Modifier.padding(vertical = 16.dp))
            }

            item {
                Text("Repas Résidents (Spécifiques)", style = MaterialTheme.typography.titleLarge)
                if (specialMeals.isEmpty()) {
                    Text("Aucun", modifier = Modifier.padding(8.dp))
                } else {
                    specialMeals.forEach { (type, allergyMap) ->
                        Text(type, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, start = 8.dp), fontSize = 18.sp)
                        allergyMap.forEach{ (allergy, textureMap) ->
                            Text("  Allergie(s): $allergy", modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
                            textureMap.forEach{ (texture, count) ->
                                Text("    • $count en texture ${texture}", modifier = Modifier.padding(start = 24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Preview ---
@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MaterialTheme {
        val mockApplication = Application()
        val mockViewModel = MainViewModel(mockApplication)
        AppContent(viewModel = mockViewModel)
    }
}
