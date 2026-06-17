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

enum class ConnectionType {
    WIFI, BLUETOOTH
}

data class BtDevice(
    val name: String,
    val address: String
)

data class LaserTagState(
    val userHp: Int = 100,
    val opponentHp: Int = 100,
    val lockedZone: HitZone = HitZone.NONE,
    val liveColor: RoiColorData = RoiColorData(0, 0, 0, 0f, 0f, 0f),
    val networkState: NetworkState = NetworkState.DISCONNECTED,
    val connectionType: ConnectionType = ConnectionType.WIFI,
    val isHost: Boolean = false,
    val localIp: String = "Unknown",
    val statusMessage: String = "Ready for combat. Calibrate colors first!",
    val logs: List<String> = listOf("Welcome to AR Laser Tag!"),
    
    // Calibrated HSVs (H, S, V)
    val headHsv: FloatArray = floatArrayOf(0f, 0.85f, 0.85f), // Red default
    val chestHsv: FloatArray = floatArrayOf(120f, 0.85f, 0.85f), // Green default
    val limbsHsv: FloatArray = floatArrayOf(240f, 0.85f, 0.85f), // Blue default
    
    // UI selections
    val currentCalibrationTab: HitZone = HitZone.HEAD,
    val showLobby: Boolean = true,
    
    // Bluetooth discovery list
    val bluetoothDevices: List<BtDevice> = emptyList(),
    val isScanningBluetooth: Boolean = false
)

class LaserTagViewModel : ViewModel() {

    private val _state = MutableStateFlow(LaserTagState())
    val state = _state.asStateFlow()

    // Sockets & Network Thread variables
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var printWriter: PrintWriter? = null
    private var readerThreadJob: kotlinx.coroutines.Job? = null

    // Bluetooth Sockets
    private var bluetoothServerSocket: android.bluetooth.BluetoothServerSocket? = null
    private var bluetoothSocket: android.bluetooth.BluetoothSocket? = null
    private var activeOutputStream: java.io.OutputStream? = null

    private val MY_UUID = java.util.UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @Volatile
    private var isPlayingAudio = false

    init {
        fetchLocalIp()
    }

    fun selectConnectionType(type: ConnectionType) {
        _state.update { it.copy(connectionType = type) }
    }

    fun playLaserSound() {
        if (isPlayingAudio) return
        viewModelScope.launch(Dispatchers.Default) {
            isPlayingAudio = true
            try {
                val sampleRate = 44100
                val durationS = 0.18f 
                val numSamples = (durationS * sampleRate).toInt()
                val sample = DoubleArray(numSamples)
                val generatedSnd = ShortArray(numSamples)

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    // Blaster sound sweep: 1400Hz down to 250Hz
                    val frequency = 1400.0 - (1150.0 * (t / durationS))
                    sample[i] = Math.sin(2.0 * Math.PI * frequency * t)
                    
                    // Secondary metallic futuristic harmonic
                    val harmonic = 0.3 * Math.sin(2.0 * Math.PI * (frequency * 1.5) * t)
                    
                    // Smooth fade out over last 20% to avoid audio pops
                    val fade = if (i > numSamples * 0.8) {
                        ((numSamples - i).toDouble() / (numSamples * 0.2))
                    } else {
                        1.0
                    }
                    
                    val blendedWave = (sample[i] + harmonic) / 1.3
                    generatedSnd[i] = (blendedWave * 32767.0 * fade).toInt().toShort()
                }

                val audioTrack = android.media.AudioTrack(
                    android.media.AudioManager.STREAM_MUSIC,
                    sampleRate,
                    android.media.AudioFormat.CHANNEL_OUT_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT,
                    numSamples * 2,
                    android.media.AudioTrack.MODE_STATIC
                )
                audioTrack.write(generatedSnd, 0, numSamples)
                audioTrack.play()
                
                kotlinx.coroutines.delay((durationS * 1000).toLong() + 30)
                audioTrack.release()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isPlayingAudio = false
            }
        }
    }

    fun fetchPairedBluetoothDevices(context: Context) {
        _state.update { it.copy(isScanningBluetooth = true) }
        try {
            val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            if (adapter == null) {
                _state.update {
                    it.copy(
                        isScanningBluetooth = false,
                        statusMessage = "Bluetooth not supported on this device.",
                        logs = it.logs.toMutableList().apply { add(0, "⚠️ Bluetooth adapter is null.") }
                    )
                }
                return
            }
            if (!adapter.isEnabled) {
                _state.update {
                    it.copy(
                        isScanningBluetooth = false,
                        statusMessage = "Please enable Bluetooth.",
                        logs = it.logs.toMutableList().apply { add(0, "⚠️ Bluetooth is disabled.") }
                    )
                }
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.BLUETOOTH_CONNECT
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    _state.update {
                        it.copy(
                            isScanningBluetooth = false,
                            statusMessage = "Requires Bluetooth Connect permission."
                        )
                    }
                    return
                }
            }

            val pairedDevices = adapter.bondedDevices
            val list = pairedDevices.map { BtDevice(it.name ?: "Unknown Device", it.address) }
            _state.update {
                it.copy(
                    bluetoothDevices = list,
                    isScanningBluetooth = false,
                    logs = it.logs.toMutableList().apply { add(0, "Found ${list.size} paired devices.") }
                )
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    isScanningBluetooth = false,
                    statusMessage = "Error: ${e.localizedMessage}"
                )
            }
        }
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

    fun enterBattle() {
        _state.update { it.copy(showLobby = false) }
    }

    fun exitToLobby() {
        _state.update { it.copy(showLobby = true) }
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
                connectionType = ConnectionType.WIFI,
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
                    activeOutputStream = socket.getOutputStream()
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
                    
                    startListening(socket.getInputStream())
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

    // Join Battle over Local Wifi TCP
    fun joinGame(targetIp: String) {
        disconnect()
        _state.update {
            it.copy(
                connectionType = ConnectionType.WIFI,
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
                activeOutputStream = socket.getOutputStream()
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

                startListening(socket.getInputStream())
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

    // Host Battle via Bluetooth
    fun hostBluetoothGame(context: Context) {
        disconnect()
        _state.update {
            it.copy(
                connectionType = ConnectionType.BLUETOOTH,
                networkState = NetworkState.HOSTING,
                isHost = true,
                statusMessage = "Hosting via Bluetooth. Awaiting opponent...",
                logs = it.logs.toMutableList().apply { add(0, "Started Bluetooth hosting.") }
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                if (adapter == null) {
                    throw Exception("Bluetooth is not supported or active on this device.")
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.BLUETOOTH_CONNECT
                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        throw Exception("Missing Bluetooth Connect permission.")
                    }
                }

                bluetoothServerSocket = adapter.listenUsingRfcommWithServiceRecord("LaserTag", MY_UUID)
                val socket = bluetoothServerSocket?.accept()
                if (socket != null) {
                    bluetoothSocket = socket
                    activeOutputStream = socket.outputStream
                    
                    _state.update {
                        it.copy(
                            networkState = NetworkState.CONNECTED,
                            statusMessage = "Opponent connected via Bluetooth!",
                            logs = it.logs.toMutableList().apply { add(0, "Bluetooth connection established with opponent!") }
                        )
                    }

                    // Sync initial health state
                    sendNetworkMessage("SYNC:HP:${_state.value.userHp}")
                    
                    startListening(socket.inputStream)
                }
            } catch (e: Exception) {
                if (_state.value.networkState == NetworkState.HOSTING) {
                    _state.update {
                        it.copy(
                            networkState = NetworkState.ERROR,
                            statusMessage = "Bluetooth Host failed: ${e.localizedMessage}"
                        )
                    }
                }
            }
        }
    }

    // Join Battle via Bluetooth
    fun joinBluetoothGame(context: Context, address: String) {
        disconnect()
        _state.update {
            it.copy(
                connectionType = ConnectionType.BLUETOOTH,
                networkState = NetworkState.JOINING,
                isHost = false,
                statusMessage = "Connecting to Bluetooth Device $address...",
                logs = it.logs.toMutableList().apply { add(0, "Connecting to BT device address $address") }
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                if (adapter == null) {
                    throw Exception("Bluetooth is not supported on this device.")
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.BLUETOOTH_CONNECT
                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        throw Exception("Missing Bluetooth Connect permission.")
                    }
                }

                val remoteDevice = adapter.getRemoteDevice(address)
                val socket = remoteDevice.createRfcommSocketToServiceRecord(MY_UUID)
                bluetoothSocket = socket
                
                try {
                    adapter.cancelDiscovery()
                } catch (e: Exception) {}

                socket.connect()
                activeOutputStream = socket.outputStream

                _state.update {
                    it.copy(
                        networkState = NetworkState.CONNECTED,
                        statusMessage = "Connected to Bluetooth host!",
                        logs = it.logs.toMutableList().apply { add(0, "Successfully connected to Bluetooth host $address") }
                    )
                }

                // Sync initial health state
                sendNetworkMessage("SYNC:HP:${_state.value.userHp}")

                startListening(socket.inputStream)
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        networkState = NetworkState.ERROR,
                        statusMessage = "Bluetooth pairing failed: ${e.localizedMessage}",
                        logs = it.logs.toMutableList().apply { add(0, "BT Connection error: ${e.localizedMessage}") }
                    )
                }
            }
        }
    }

    private fun startListening(inputStream: java.io.InputStream) {
        readerThreadJob?.cancel()
        readerThreadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val reader = BufferedReader(InputStreamReader(inputStream))
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
                // Support both new JSON payload format and legacy hit format
                msg.contains("take_damage") -> {
                    val match = Regex("""["']amount["']\s*:\s*(\d+)""").find(msg)
                    val amount = match?.groupValues?.get(1)?.toIntOrNull() ?: 25
                    applyDamageAndVibrate(amount)
                }
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
                            logs = it.logs.toMutableList().apply { add(0, "Opponent HP synchronized to $oppHp%") }
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
                // Write exactly once using the active channel to avoid duplicate hits on the opponent's screen
                if (_state.value.connectionType == ConnectionType.BLUETOOTH || printWriter == null) {
                    activeOutputStream?.let { out ->
                        val data = (msg + "\n").toByteArray(Charsets.UTF_8)
                        out.write(data)
                        out.flush()
                    }
                } else {
                    printWriter?.let { writer ->
                        writer.println(msg)
                        writer.flush()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Fire laser!
    fun fireLaser(context: Context) {
        // ALWAYS play high-quality laser gunshot synthesized sound and haptic vibration feedback instantly!
        playLaserSound()
        triggerVibrate(context)

        val currZone = _state.value.lockedZone
        val isOpponentConnected = _state.value.networkState == NetworkState.CONNECTED

        if (currZone == HitZone.NONE) {
            // A missed shot! Include sound/vibe feedback
            _state.update {
                it.copy(
                    logs = it.logs.toMutableList().apply { add(0, "💨 Shot fired! No target locked.") }
                )
            }
            return
        }

        val damage = when (currZone) {
            HitZone.HEAD -> 50
            HitZone.CHEST -> 25
            HitZone.LIMBS -> 10
            else -> 0
        }

        // REDUCE HP immediately on both clients for a completely lag-free, zero perceived latency multiplayer experience!
        _state.update {
            val updatedLogs = it.logs.toMutableList()
            updatedLogs.add(0, "🎯 HIT REGISTERED! You shot opponent's ${currZone.name} to deal -$damage DMG!")
            
            val oppNewHp = maxOf(0, it.opponentHp - damage)
            it.copy(
                opponentHp = oppNewHp,
                logs = updatedLogs
            )
        }

        // Transmit hit packages to opponent instantly via the JSON payload format
        if (isOpponentConnected) {
            val payload = """{"event": "take_damage", "amount": $damage}"""
            sendNetworkMessage(payload)
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
                    vibrator.vibrate(VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(180)
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
        try {
            bluetoothServerSocket?.close()
            bluetoothSocket?.close()
            activeOutputStream?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        printWriter = null
        clientSocket = null
        serverSocket = null
        bluetoothServerSocket = null
        bluetoothSocket = null
        activeOutputStream = null
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
