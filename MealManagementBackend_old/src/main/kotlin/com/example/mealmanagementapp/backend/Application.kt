// FICHIER : src/main/kotlin/com/example/mealmanagementapp/backend/Application.kt

package com.example.mealmanagementapp.backend

import com.example.mealmanagementapp.backend.database.DatabaseFactory
import com.example.mealmanagementapp.backend.database.dao
import com.example.mealmanagementapp.backend.models.MealRecord
import com.example.mealmanagementapp.backend.models.Resident
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface

const val DISCOVERY_PORT = 9999
const val DISCOVERY_KEYWORD = "poulpe"

fun main() {
    // Lancement du serveur web Ktor
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    DatabaseFactory.init()
    install(ContentNegotiation) {
        json()
    }
    configureRouting()

    // Lancement du service de découverte dans une coroutine en arrière-plan
    launch(Dispatchers.IO) {
        startDiscoveryService()
    }
}

/**
 * Service qui écoute les "pings" UDP sur le réseau et répond avec l'IP du serveur.
 */
fun startDiscoveryService() {
    DatagramSocket(DISCOVERY_PORT).use { socket ->
        println("Discovery service started on port $DISCOVERY_PORT")
        while (true) {
            try {
                val buffer = ByteArray(1024)
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)

                val message = String(packet.data, 0, packet.length)
                if (message == DISCOVERY_KEYWORD) {
                    println("Discovery request received from ${packet.address}:${packet.port}")
                    val serverIp = getLocalIPAddress()
                    if (serverIp != null) {
                        val responseData = serverIp.toByteArray()
                        val responsePacket = DatagramPacket(responseData, responseData.size, packet.address, packet.port)
                        socket.send(responsePacket)
                        println("Responded with IP: $serverIp")
                    }
                }
            } catch (e: Exception) {
                println("Discovery service error: ${e.message}")
            }
        }
    }
}

/**
 * Trouve la première adresse IP non-localhost de la machine.
 */
fun getLocalIPAddress(): String? {
    NetworkInterface.getNetworkInterfaces().toList().map { networkInterface ->
        networkInterface.inetAddresses.toList().map { inetAddress ->
            if (!inetAddress.isLoopbackAddress && inetAddress is java.net.Inet4Address) {
                return inetAddress.hostAddress
            }
        }
    }
    return null
}


fun Application.configureRouting() {
    routing {
        route("/residents") {
            get { call.respond(dao.getAllResidents()) }
            post {
                val resident = call.receive<Resident>()
                val newResident = dao.addResident(resident.name, resident.firstName, resident.allergies, resident.mealTexture, resident.mealType)
                call.respond(newResident)
            }
            put("/{id}") {
                val id = call.parameters["id"]!!
                val resident = call.receive<Resident>()
                dao.updateResident(id, resident)
                call.respond(mapOf("status" to "OK"))
            }
            delete("/{id}") {
                val id = call.parameters["id"]!!
                dao.deleteResident(id)
                call.respond(mapOf("status" to "OK"))
            }
        }

        route("/staff") {
            get { call.respond(dao.getAllStaff()) }
        }

        route("/meals") {
            get("/today") { call.respond(dao.getTodaysMealRecords()) }
            post {
                val mealRecord = call.receive<MealRecord>()
                dao.confirmMeal(mealRecord)
                call.respond(mapOf("status" to "OK"))
            }
        }
    }
}
