package com.example

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

enum class NetworkState {
    DISCONNECTED, HOSTING, JOINING, CONNECTED, ERROR
}

data class LaserTagState(
    val userHp: Int = 100,
    val opponentHp: Int = 100,
    val lockedZone: HitZone = HitZone.NONE,
    val liveColor: RoiColorData = RoiColorData(0, 0, 0, 0f, 0f, 0f),
    val networkState: NetworkState = NetworkState.DISCONNECTED,
    val isHost: Boolean = false,
    val localIp: String = "Unknown",
    val statusMessage: String = "Ready for combat. Calibrate colors first!",
    val logs: List<String> = listOf("Welcome to AR Laser Tag!"),
    
    // Calibrated HSVs (H, S, V)
    val headHsv: FloatArray = floatArrayOf(0f, 0.85f, 0.85f), // Red default
    val chestHsv: FloatArray = floatArrayOf(120f, 0.85f, 0.85f), // Green default
    val limbsHsv: FloatArray = floatArrayOf(240f, 0.85f, 0.85f), // Blue default
    
    // UI selections
    val currentCalibrationTab: HitZone = HitZone.HEAD
)

class LaserTagViewModel : ViewModel() {

    private val _state = MutableStateFlow(LaserTagState())
    val state = _state.asStateFlow()

    // Sockets & Network Thread variables
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var printWriter: PrintWriter? = null
    private var readerThreadJob: kotlinx.coroutines.Job? = null

    init {
        fetchLocalIp()
    }

    private fun fetchLocalIp() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                for (networkInterface in interfaces) {
                    val addresses = networkInterface.inetAddresses
                    for (address in addresses) {
                        if (!address.isLoopbackAddress && address is Inet4Address) {
                            val ip = address.hostAddress ?: "Unknown"
                            if (ip.startsWith("192.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                                _state.update { it.copy(localIp = ip) }
                                return@launch
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            _state.update { it.copy(localIp = "127.0.0.1") }
        }
    }

    fun selectCalibrationTab(zone: HitZone) {
        if (zone != HitZone.NONE) {
            _state.update { it.copy(currentCalibrationTab = zone) }
        }
    }

    fun captureCurrentColorForTab() {
        val tab = _state.value.currentCalibrationTab
        val live = _state.value.liveColor
        val hsv = floatArrayOf(live.h, live.s, live.v)
        
        _state.update {
            val updatedLogs = it.logs.toMutableList()
            updatedLogs.add(0, "Calibrated ${tab.name}: H=${live.h.toInt()}°, S=${(live.s * 100).toInt()}%, V=${(live.v * 100).toInt()}%")
            
            when (tab) {
                HitZone.HEAD -> it.copy(headHsv = hsv, logs = updatedLogs)
                HitZone.CHEST -> it.copy(chestHsv = hsv, logs = updatedLogs)
                HitZone.LIMBS -> it.copy(limbsHsv = hsv, logs = updatedLogs)
                else -> it
            }
        }
    }

    fun updateLiveColor(color: RoiColorData) {
        _state.update { it.copy(liveColor = color) }
    }

    fun updateLockedZone(zone: HitZone) {
        if (_state.value.lockedZone != zone) {
            _state.update { it.copy(lockedZone = zone) }
        }
    }

    // Host Battle over Local Wifi TCP
    fun hostGame() {
        disconnect()
        _state.update {
            it.copy(
                networkState = NetworkState.HOSTING,
                isHost = true,
                statusMessage = "Hosting on port 8888. Awaiting opponent...",
                logs = it.logs.toMutableList().apply { add(0, "Started Hosting. Tell opponent your IP: ${it.localIp}") }
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(8888)
                val socket = serverSocket?.accept()
                if (socket != null) {
                    clientSocket = socket
                    printWriter = PrintWriter(socket.getOutputStream(), true)
                    
                    _state.update {
                        it.copy(
                            networkState = NetworkState.CONNECTED,
                            statusMessage = "Opponent connected!",
                            logs = it.logs.toMutableList().apply { add(0, "Client connected from ${socket.inetAddress.hostAddress}") }
                        )
                    }
                    
                    // Sync initial health state
                    sendNetworkMessage("SYNC:HP:${_state.value.userHp}")
                    
                    startListening(socket)
                }
            } catch (e: Exception) {
                if (_state.value.networkState == NetworkState.HOSTING) {
                    _state.update {
                        it.copy(
                            networkState = NetworkState.ERROR,
                            statusMessage = "Host failed: ${e.localizedMessage}"
                        )
                    }
                }
            }
        }
    }

    // Join Battle
    fun joinGame(targetIp: String) {
        disconnect()
        _state.update {
            it.copy(
                networkState = NetworkState.JOINING,
                isHost = false,
                statusMessage = "Connecting to $targetIp:8888...",
                logs = it.logs.toMutableList().apply { add(0, "Attempting to join host $targetIp") }
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val socket = Socket(targetIp, 8888)
                clientSocket = socket
                printWriter = PrintWriter(socket.getOutputStream(), true)

                _state.update {
                    it.copy(
                        networkState = NetworkState.CONNECTED,
                        statusMessage = "Connected to host!",
                        logs = it.logs.toMutableList().apply { add(0, "Successfully joined host at $targetIp") }
                    )
                }

                // Sync initial health state
                sendNetworkMessage("SYNC:HP:${_state.value.userHp}")

                startListening(socket)
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        networkState = NetworkState.ERROR,
                        statusMessage = "Connection failed: ${e.localizedMessage}",
                        logs = it.logs.toMutableList().apply { add(0, "Error joining: ${e.localizedMessage}") }
                    )
                }
            }
        }
    }

    private fun startListening(socket: Socket) {
        readerThreadJob?.cancel()
        readerThreadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    processIncomingMessage(line!!)
                }
            } catch (e: Exception) {
                // Connection lost
                handleDisconnect("Lost connection to opponent.")
            }
        }
    }

    private fun processIncomingMessage(msg: String) {
        viewModelScope.launch(Dispatchers.Main) {
            when {
                msg.startsWith("HIT:HEAD") -> {
                    // We were hit in the head!
                    applyDamageAndVibrate(50)
                }
                msg.startsWith("HIT:CHEST") -> {
                    // We were hit in the torso!
                    applyDamageAndVibrate(25)
                }
                msg.startsWith("HIT:LIMBS") -> {
                    // We were hit in the arms/legs!
                    applyDamageAndVibrate(10)
                }
                msg.startsWith("SYNC:HP:") -> {
                    val oppHp = msg.substringAfter("SYNC:HP:").toIntOrNull() ?: 100
                    _state.update {
                        it.copy(
                            opponentHp = oppHp,
                            logs = it.logs.toMutableList().apply { add(0, "Opponent HP updated to $oppHp%") }
                        )
                    }
                }
                msg == "RESET" -> {
                    _state.update {
                        it.copy(
                            userHp = 100,
                            opponentHp = 100,
                            statusMessage = "Match restarted!",
                            logs = it.logs.toMutableList().apply { add(0, "Opponent requested a Match Reset!") }
                        )
                    }
                }
            }
        }
    }

    private fun applyDamageAndVibrate(dmg: Int) {
        val newHp = maxOf(0, _state.value.userHp - dmg)
        _state.update {
            it.copy(
                userHp = newHp,
                statusMessage = "Ouch! Hit received: -$dmg HP!",
                logs = it.logs.toMutableList().apply { add(0, "💥 You were hit for -$dmg DMG! Current HP: $newHp%") }
            )
        }
        // Send our updated HP to client so we coordinate HP bars
        sendNetworkMessage("SYNC:HP:$newHp")
    }

    private fun sendNetworkMessage(msg: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                printWriter?.println(msg)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Fire laser!
    fun fireLaser(context: Context) {
        val currZone = _state.value.lockedZone
        val isOpponentConnected = _state.value.networkState == NetworkState.CONNECTED

        if (currZone == HitZone.NONE) {
            // A missed shot!
            _state.update {
                it.copy(
                    logs = it.logs.toMutableList().apply { add(0, "💨 Missed shot! Crosshair is idle.") }
                )
            }
            return
        }

        // We successfully locked on and hit! Provide instant vibration haptic confirmation
        triggerVibrate(context)

        val damage = when (currZone) {
            HitZone.HEAD -> 50
            HitZone.CHEST -> 25
            HitZone.LIMBS -> 10
            else -> 0
        }

        _state.update {
            val updatedLogs = it.logs.toMutableList()
            updatedLogs.add(0, "🎯 HIT SUCCESSFULLY! You shot opponent's ${currZone.name} to deal -$damage DMG!")
            
            // If offline, simulate opponent health reduction for a smooth interactive mockup play experience!
            val oppNewHp = if (!isOpponentConnected) {
                maxOf(0, it.opponentHp - damage)
            } else {
                it.opponentHp
            }

            it.copy(
                opponentHp = oppNewHp,
                logs = updatedLogs
            )
        }

        // If online, transmit hit message to client!
        if (isOpponentConnected) {
            sendNetworkMessage("HIT:$currZone")
        }
    }

    private fun triggerVibrate(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(220, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(220)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Reset battle
    fun resetMatch() {
        _state.update {
            it.copy(
                userHp = 100,
                opponentHp = 100,
                statusMessage = "Match restarted!",
                logs = it.logs.toMutableList().apply { add(0, "🔄 Match Reset! Scores cleared.") }
            )
        }
        sendNetworkMessage("RESET")
    }

    fun disconnect() {
        handleDisconnect("Disconnected.")
    }

    private fun handleDisconnect(reason: String) {
        readerThreadJob?.cancel()
        readerThreadJob = null
        try {
            printWriter?.close()
            clientSocket?.close()
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        printWriter = null
        clientSocket = null
        serverSocket = null

        _state.update {
            it.copy(
                networkState = NetworkState.DISCONNECTED,
                statusMessage = reason,
                logs = it.logs.toMutableList().apply { add(0, "🔌 Connection Closed: $reason") }
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}
