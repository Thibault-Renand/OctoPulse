@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.mealmanagementapp

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.activity.viewModels
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

// --- 1. Data Models ---
data class Resident(
    val id: String = "",
    val name: String = "",
    val firstName: String = "",
    val allergies: List<String> = emptyList(),
    val mealTexture: String = "normal", // "normal", "haché", "mixé"
    val mealType: String = "aucun",     // "aucun", "vegetarien", "vegan", "hypocalorique", "hypercalorique"
    val linkedUserId: String? = null,
    val lastModified: Date = Date()
)

data class Staff(
    val id: String = "",
    val name: String = "",
    val firstName: String = "",
    val role: String = "Visiteur", // "Soignant", "Personnel", "Visiteur"
    val linkedUserId: String? = null,
    val lastModified: Date = Date()
)

data class MealRecord(
    val id: String = "",
    val personId: String = "",
    val personType: String = "resident", // "resident" or "staff"
    val name: String = "",
    val firstName: String = "",
    val mealConfirmed: Boolean = false,
    val date: String = "", // Format yyyy-MM-dd
    // Resident-specific fields
    val allergies: List<String> = emptyList(),
    val mealTexture: String = "normal",
    val mealType: String = "aucun",
    val timestamp: Date = Date()
)

// --- 2. FirestoreManager ---
class FirestoreManager(private val appId: String, private val context: Context) {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private val _residents = MutableStateFlow<List<Resident>>(emptyList())
    val residents: StateFlow<List<Resident>> = _residents.asStateFlow()

    private val _staff = MutableStateFlow<List<Staff>>(emptyList())
    val staff: StateFlow<List<Staff>> = _staff.asStateFlow()

    private val _todaysMealRecords = MutableStateFlow<List<MealRecord>>(emptyList())
    val todaysMealRecords: StateFlow<List<MealRecord>> = _todaysMealRecords.asStateFlow()

    init {
        initializeFirebase()
    }

    private fun initializeFirebase() {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        db = Firebase.firestore
        auth = Firebase.auth
    }

    suspend fun signIn(initialAuthToken: String?) {
        try {
            auth.signInAnonymously().await()
            println("Authentication successful. User ID: ${auth.currentUser?.uid}")
        } catch (e: Exception) {
            println("Authentication error: ${e.message}")
        }
    }

    fun startListeners() {
        db.collection("artifacts/${appId}/public/data/residents").addSnapshotListener { snapshot, e ->
            if (e != null) return@addSnapshotListener
            _residents.value = snapshot?.documents?.mapNotNull { it.toObject(Resident::class.java)?.copy(id = it.id) } ?: emptyList()
        }

        db.collection("artifacts/${appId}/public/data/staff").addSnapshotListener { snapshot, e ->
            if (e != null) return@addSnapshotListener
            _staff.value = snapshot?.documents?.mapNotNull { it.toObject(Staff::class.java)?.copy(id = it.id) } ?: emptyList()
        }

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        db.collection("artifacts/${appId}/public/data/meals").whereEqualTo("date", today).addSnapshotListener { snapshot, e ->
            if (e != null) return@addSnapshotListener
            _todaysMealRecords.value = snapshot?.documents?.mapNotNull { it.toObject(MealRecord::class.java)?.copy(id = it.id) } ?: emptyList()
        }
    }

    suspend fun populateInitialData() {
        val residentsCollection = db.collection("artifacts/${appId}/public/data/residents")
        val staffCollection = db.collection("artifacts/${appId}/public/data/staff")
        if (residentsCollection.get().await().isEmpty) {
            listOf(
                Resident(id = "resident-01", name = "Renand", firstName = "Thibault", allergies = listOf("lactose"), mealType = "aucun", linkedUserId = "user-thibault"),
                Resident(id = "resident-02", name = "Dupont", firstName = "Marie", allergies = listOf("gluten"), mealTexture = "haché", mealType = "vegetarien"),
                Resident(id = "resident-03", name = "Lefevre", firstName = "Pierre", mealTexture = "mixé", mealType = "hypocalorique"),
                Resident(id = "resident-04", name = "Martin", firstName = "Sophie", allergies = listOf("noix", "poisson"), mealType = "aucun"),
                Resident(id = "resident-05", name = "Bernard", firstName = "Luc", allergies = emptyList(), mealType = "vegan"),
                Resident(id = "resident-06", name = "Garcia", firstName = "Eva", allergies = listOf("gluten"), mealTexture = "normal", mealType = "vegetarien")
            ).forEach { residentsCollection.document(it.id).set(it).await() }
        }
        if (staffCollection.get().await().isEmpty) {
            listOf(
                Staff(id = "staff-01", name = "Durand", firstName = "Alain", role = "Soignant", linkedUserId = "user-alain"),
                Staff(id = "staff-02", name = "Petit", firstName = "Carole", role = "Personnel", linkedUserId = "user-carole"),
                Staff(id = "staff-03", name = "Moreau", firstName = "Julien", role = "Visiteur"),
            ).forEach { staffCollection.document(it.id).set(it).await() }
        }
    }

    private suspend fun confirmMeal(record: MealRecord): Boolean {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val collection = db.collection("artifacts/${appId}/public/data/meals")
        val query = collection.whereEqualTo("personId", record.personId).whereEqualTo("date", date)

        return try {
            val existing = query.get().await()
            if (existing.isEmpty) {
                collection.add(record).await()
            } else {
                collection.document(existing.documents.first().id).update("mealConfirmed", record.mealConfirmed, "timestamp", Date()).await()
            }
            Toast.makeText(context, "Confirmation enregistrée.", Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    suspend fun confirmResidentMeal(resident: Resident, eatOnSite: Boolean): Boolean {
        return confirmMeal(MealRecord(
            personId = resident.id,
            personType = "resident",
            name = resident.name,
            firstName = resident.firstName,
            mealConfirmed = eatOnSite,
            date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
            allergies = resident.allergies,
            mealTexture = resident.mealTexture,
            mealType = resident.mealType
        ))
    }

    suspend fun confirmStaffMeal(staff: Staff, eatOnSite: Boolean): Boolean {
        return confirmMeal(MealRecord(
            personId = staff.id,
            personType = "staff",
            name = staff.name,
            firstName = staff.firstName,
            mealConfirmed = eatOnSite,
            date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
        ))
    }

    suspend fun addOrUpdateResident(resident: Resident): Boolean {
        return try {
            db.collection("artifacts/${appId}/public/data/residents").document(resident.id).set(resident, SetOptions.merge()).await()
            true
        } catch (e: Exception) { false }
    }

    suspend fun deleteResident(residentId: String): Boolean {
        return try {
            db.collection("artifacts/${appId}/public/data/residents").document(residentId).delete().await()
            true
        } catch (e: Exception) { false }
    }

    suspend fun updateResidentTexture(residentId: String, newTexture: String): Boolean {
        return try {
            db.collection("artifacts/${appId}/public/data/residents").document(residentId).update("mealTexture", newTexture).await()
            true
        } catch (e: Exception) { false }
    }

    fun getMealSummary(): Map<String, Any> {
        val absentResidentIds = todaysMealRecords.value.filter { it.personType == "resident" && !it.mealConfirmed }.map { it.personId }.toSet()
        val presentResidents = _residents.value.filter { it.id !in absentResidentIds }
        val absentResidents = _residents.value.filter { it.id in absentResidentIds }

        val presentStaffCount = todaysMealRecords.value.count { it.personType == "staff" && it.mealConfirmed }

        val normalMeals = presentResidents
            .filter { it.mealType == "aucun" && it.allergies.isEmpty() }
            .groupBy { it.mealTexture }
            .mapValues { it.value.size }

        val specialMeals = presentResidents
            .filter { it.mealType != "aucun" || it.allergies.isNotEmpty() }
            .groupBy { it.mealType.uppercase() } // Group 1: Régime
            .mapValues { mealTypeEntry ->
                mealTypeEntry.value
                    .groupBy { it.allergies.sorted().joinToString(", ").ifEmpty { "Aucune" } } // Group 2: Allergies
                    .mapValues { allergyEntry ->
                        allergyEntry.value
                            .groupBy { it.mealTexture } // Group 3: Texture
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

// --- 3. ViewModel ---
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val firestoreManager = FirestoreManager("mealmanager-45be6", application.applicationContext)
    private val screenHistory = mutableStateListOf<String>()

    val priorityId = MutableStateFlow<Int?>(null)
    val currentScreen = MutableStateFlow("home")
    val residents: StateFlow<List<Resident>> = firestoreManager.residents
    val staff: StateFlow<List<Staff>> = firestoreManager.staff
    val showToast = MutableStateFlow<String?>(null)
    val showConfirmDialog = MutableStateFlow<String?>(null)
    private var onConfirmAction: () -> Unit = {}
    val loggedInStaff = MutableStateFlow<Staff?>(null)

    val residentPresence: StateFlow<Map<String, Boolean>> = firestoreManager.todaysMealRecords.map { records ->
        val presenceMap = mutableMapOf<String, Boolean>()
        records.filter { it.personType == "resident" }.forEach { record ->
            presenceMap[record.personId] = record.mealConfirmed
        }
        presenceMap
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        viewModelScope.launch {
            firestoreManager.signIn(null)
            firestoreManager.populateInitialData()
            firestoreManager.startListeners()
        }
    }

    fun setPriorityId(id: Int) {
        priorityId.value = id
        val destination = when (id) {
            0 -> "resident_confirmation"
            else -> "dashboard"
        }
        navigateTo(destination)
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

    fun dismissConfirmDialog() {
        showConfirmDialog.value = null
    }

    fun onConfirm() {
        onConfirmAction()
        dismissConfirmDialog()
    }

    fun confirmResidentMeal(resident: Resident, eatOnSite: Boolean) = viewModelScope.launch {
        firestoreManager.confirmResidentMeal(resident, eatOnSite)
    }

    fun confirmStaffMeal(staff: Staff, eatOnSite: Boolean) = viewModelScope.launch {
        firestoreManager.confirmStaffMeal(staff, eatOnSite)
        if(eatOnSite) navigateTo("meal_summary") // Go to recap after confirming
    }

    fun addOrUpdateResident(resident: Resident) = viewModelScope.launch {
        if (firestoreManager.addOrUpdateResident(resident)) showToast.value = "Résident sauvegardé."
    }

    fun deleteResident(residentId: String) = viewModelScope.launch {
        if(firestoreManager.deleteResident(residentId)) showToast.value = "Résident supprimé."
    }

    fun updateResidentTexture(residentId: String, currentTexture: String, increment: Boolean) = viewModelScope.launch {
        val levels = listOf("normal", "haché", "mixé")
        val currentIndex = levels.indexOf(currentTexture)
        val newIndex = if(increment) (currentIndex + 1).coerceAtMost(levels.lastIndex) else (currentIndex - 1).coerceAtLeast(0)
        if (currentIndex != newIndex) {
            if(firestoreManager.updateResidentTexture(residentId, levels[newIndex])) showToast.value = "Texture mise à jour."
        }
    }

    fun getMealSummary() = firestoreManager.getMealSummary()
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

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (currentScreen) {
            "home" -> AppEntryScreen(viewModel)
            "resident_confirmation" -> ResidentMealConfirmationScreen(viewModel)
            "dashboard" -> DashboardScreen(viewModel)
            "mymeal" -> MyMealStaffScreen(viewModel)
            "meal_manager" -> MealManagerScreen(viewModel)
            "meal_summary" -> MealSummaryScreen(viewModel)
        }
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
                                    IconButton(onClick = { viewModel.updateResidentTexture(resident.id, resident.mealTexture, true) }) { Icon(Icons.Default.Add, "Incrémenter texture") }
                                }
                                if (priority!! >= 3) {
                                    IconButton(onClick = { viewModel.updateResidentTexture(resident.id, resident.mealTexture, false) }) { Icon(Icons.Default.Remove, "Décrémenter texture") }
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
