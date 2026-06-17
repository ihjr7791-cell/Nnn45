package com.example

import android.Manifest
import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

// Utility to convert raw android HSV array to Jetpack Compose Color
fun hsvToColor(hsv: FloatArray): Color {
    val rgb = android.graphics.Color.HSVToColor(hsv)
    return Color(rgb)
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun LaserTagGameScreen(
    viewModel: LaserTagViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    // Modals & overlay controllers
    var showNetworkMenu by remember { mutableStateOf(false) }
    var showCalibrationMenu by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F1219)) // Deep tactical midnight black background
    ) {
        if (state.showLobby) {
            // Modern beautiful game lobby / launch interface designed for Landscape
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F1219))
            ) {
                // Left Pane: Cinematic illustration and Title branding
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1.2f)
                ) {
                    Image(
                        painter = painterResource(id = com.example.R.drawable.combat_hero_banner),
                        contentDescription = "Combat Arena Wallpaper",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
                    )
                    // Gradient overlay to fade left illustration to dark right controls seamlessly
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color(0xFF0F1219).copy(alpha = 0.6f),
                                        Color(0xFF0F1219)
                                    )
                                )
                            )
                    )

                    // Overlay Title Badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                            .background(Color(0xE61A1D26), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF00FFCC).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "BATTLEFIELD LOBBY",
                            color = Color(0xFF00FFCC),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // Right Pane: Action configurations and Deploy buttons (making full height available scrollably)
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Localized Header
                    Text(
                        text = "الساحة القتالية الرقمية",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "AR TACTICAL LASER TAG",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E6EE4),
                        letterSpacing = 1.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Tactical Specifications Info
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x1F00FFCC), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF00FFCC).copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FilterCenterFocus,
                                contentDescription = "Precision Reticle Info",
                                tint = Color(0xFF00FFCC),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "تم تصغير مربع تحديد الألوان بدقة عالية (4x4 بكسل)",
                                    fontSize = 10.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Super high precision 4x4 color reticle is now active.",
                                    fontSize = 9.sp,
                                    color = Color.LightGray
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Rules Guidance Card (miniaturized for landscape layout viewports)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0x66161C2C)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "⚔️ COMBAT INTEL",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            BulletIntelRow(
                                textAr = "صوّب مركز الشاشة نحو الخصم للإقفال التلقائي.",
                                textEn = "Aim center reticle directly on target armor zones to lock."
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            BulletIntelRow(
                                textAr = "اضغط على زر الإطلاق الضخم لضرب نقاط حياة الخصم.",
                                textEn = "Press the trigger button to fire and deal heavy damage."
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Main Action Launcher Button (Deploy!)
                    Button(
                        onClick = { viewModel.enterBattle() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE42E5B)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("enter_battle_arena"),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SportsEsports, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "انطلق إلى المعركة / DEPLOY",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Lobby setup quick access buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showNetworkMenu = true },
                            modifier = Modifier
                                .weight(1.3f)
                                .height(38.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                        ) {
                            Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ربط الشبكة / Link IP", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { showCalibrationMenu = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                        ) {
                            Icon(Icons.Default.Colorize, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("معايرة الدروع", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            if (cameraPermissionState.status.isGranted) {
                var currentZoom by remember { mutableStateOf(1f) }

                Box(modifier = Modifier.fillMaxSize()) {
                    // 1. Camera with smooth gesture-driven pinch zoom
                    key(showCalibrationMenu) {
                        CameraPreviewAndAnalyzer(
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize(),
                            onZoomChanged = { currentZoom = it }
                        )
                    }

                    // 2. Floating Laser Crosshair target sight
                    LaserTacticalCrosshair(
                        lockedZone = state.lockedZone,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Sniper Digital Zoom Scope Indicator Overlay
                    if (currentZoom > 1.05f) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 100.dp)
                                .background(Color(0xE6111724), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF00FFCC).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "ZOOM: " + String.format("%.1fx", currentZoom),
                                color = Color(0xFF00FFCC),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // 3. Compact Minimalist Top HUD (placed on Upper Corners in Landscape)
                    val isMatchActive = state.userHp > 0 && state.opponentHp > 0
                    CombatTopLandscapeHud(
                        userHp = state.userHp,
                        opponentHp = state.opponentHp,
                        networkState = state.networkState,
                        isMatchActive = isMatchActive,
                        onNetworkMenuOpen = { showNetworkMenu = true },
                        onCalibrationOpen = { showCalibrationMenu = true },
                        onLogsOpen = { showLogsDialog = true },
                        onExitToLobby = { viewModel.exitToLobby() },
                        onReset = { viewModel.resetMatch() }
                    )

                    // 4. Tactical Trigger on bottom-right (perfect right thumb reach)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 24.dp, bottom = 24.dp)
                    ) {
                        ShootTriggerButton(
                            lockedZone = state.lockedZone,
                            onFire = { viewModel.fireLaser(context) }
                        )
                    }
                }
            } else {
                // Permission request screen optimized for landscape UI
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .background(Color(0xE6161C2A), RoundedCornerShape(24.dp))
                            .border(1.dp, Color(0xFFE42E5B).copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                            .padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Camera Permission Needed",
                            tint = Color(0xFFE42E5B),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Camera Permission Required",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "AR Laser Tag detects calibrated armor colors using real-time pixel scanning.",
                            color = Color.LightGray,
                            textAlign = TextAlign.Center,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { cameraPermissionState.launchPermissionRequest() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE42E5B)),
                            modifier = Modifier
                                .testTag("request_permission_button")
                                .height(44.dp)
                        ) {
                            Text("Grant Camera Access", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 4. Game Over screen with interactive Reset popup
        if (state.userHp <= 0 || state.opponentHp <= 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xE60A0D14)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    val weWon = state.opponentHp <= 0
                    Icon(
                        imageVector = if (weWon) Icons.Default.EmojiEvents else Icons.Default.SentimentVeryDissatisfied,
                        contentDescription = "Combat Result",
                        tint = if (weWon) Color(0xFFFFD700) else Color(0xFFE42E5B),
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (weWon) "COMBAT VICTORY" else "YOU WERE ELIMINATED",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (weWon) Color(0xFF00FFCC) else Color(0xFFFF3B3B),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (weWon) "You successfully disabled the opponent's sensory armor tags." else "Sensory damage exceeded threshold (100 HP reached).",
                        color = Color.LightGray,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { viewModel.resetMatch() },
                        colors = ButtonDefaults.buttonColors(containerColor = if (weWon) Color(0xFF00FFCC) else Color(0xFFFF3B3B)),
                        modifier = Modifier
                            .testTag("victory_reset_button")
                            .height(50.dp)
                    ) {
                        Text(
                            text = "New Battle / Deploy Again",
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            }
        }

        // 5. Host / Join Network Setup Drawer
        if (showNetworkMenu) {
            NetworkMenuSheet(
                localIp = state.localIp,
                networkState = state.networkState,
                connectionType = state.connectionType,
                bluetoothDevices = state.bluetoothDevices,
                isScanningBluetooth = state.isScanningBluetooth,
                onSelectConnectionType = { viewModel.selectConnectionType(it) },
                onHostWifi = { viewModel.hostGame() },
                onJoinWifi = { hostIp -> viewModel.joinGame(hostIp) },
                onHostBluetooth = { viewModel.hostBluetoothGame(context) },
                onJoinBluetooth = { address -> viewModel.joinBluetoothGame(context, address) },
                onScanBluetooth = { viewModel.fetchPairedBluetoothDevices(context) },
                onDisconnect = { viewModel.disconnect() },
                onDismiss = { showNetworkMenu = false }
            )
        }

        // 6. Color Calibration Panel
        if (showCalibrationMenu) {
            CalibrationPanelSheet(
                currentTab = state.currentCalibrationTab,
                liveR = state.liveColor.r,
                liveG = state.liveColor.g,
                liveB = state.liveColor.b,
                liveH = state.liveColor.h,
                liveS = state.liveColor.s,
                liveV = state.liveColor.v,
                headColor = hsvToColor(state.headHsv),
                chestColor = hsvToColor(state.chestHsv),
                limbsColor = hsvToColor(state.limbsHsv),
                onSelectTab = { viewModel.selectCalibrationTab(it) },
                onLiveColorUpdated = { viewModel.updateLiveColor(it) },
                onCapture = { viewModel.captureCurrentColorForTab() },
                onDismiss = { showCalibrationMenu = false }
            )
        }

        // 7. Tactical Log Feed Overlay
        if (showLogsDialog) {
            TacticalLogsDialog(
                logs = state.logs,
                onDismiss = { showLogsDialog = false }
            )
        }
    }
}

@Composable
fun BulletIntelRow(textAr: String, textEn: String) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = "•", color = Color(0xFF00FFCC), fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
        Column {
            Text(text = textAr, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(text = textEn, color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Normal)
        }
    }
}

@Composable
fun CombatTopHud(
    userHp: Int,
    opponentHp: Int,
    networkState: NetworkState,
    statusMessage: String,
    isMatchActive: Boolean,
    onNetworkMenuOpen: () -> Unit,
    onCalibrationOpen: () -> Unit,
    onLogsOpen: () -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F1219),
                        Color(0xFF0F1219).copy(alpha = 0.9f),
                        Color.Transparent
                    )
                )
            )
            .padding(16.dp)
    ) {
        // First tier: Health Status meters side-by-side
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Self HP
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xCC131926), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF00FFCC).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = "YOUR ARMOR HP",
                    fontSize = 11.sp,
                    color = Color(0xFF00FFCC),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "My Armor",
                        tint = Color(0xFF00FFCC),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$userHp%",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { userHp / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (userHp > 40) Color(0xFF00FFCC) else Color(0xFFFF3366),
                    trackColor = Color(0xFF222B3E),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Opponent HP
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xCC131926), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFFF3B3B).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = "OPPONENT HP",
                    fontSize = 11.sp,
                    color = Color(0xFFFF3B3B),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Opponent Armor",
                        tint = Color(0xFFFF3B3B),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$opponentHp%",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { opponentHp / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFFFF3B3B),
                    trackColor = Color(0xFF222B3E),
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Second tier: Command actions (Network state, Calibration toggle, match timeline, reset)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Live net connectivity indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(Color(0x99111724), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFF3B66FF).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .clickable { onNetworkMenuOpen() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                val netColor = when (networkState) {
                    NetworkState.CONNECTED -> Color(0xFF00FF2B)
                    NetworkState.HOSTING, NetworkState.JOINING -> Color(0xFFFFC107)
                    else -> Color.Gray
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(netColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when (networkState) {
                        NetworkState.CONNECTED -> "CONNECTED"
                        NetworkState.HOSTING -> "HOSTING..."
                        NetworkState.JOINING -> "JOINING..."
                        else -> "OFFLINE MODE"
                    },
                    fontSize = 11.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Sync Info",
                    tint = Color.LightGray,
                    modifier = Modifier.size(12.dp)
                )
            }

            // Quick Menu Group buttons for tactical access
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Calibration Setup
                if (!isMatchActive) {
                    IconButton(
                        onClick = onCalibrationOpen,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0x99131A26), RoundedCornerShape(8.dp))
                            .border(1.dp, Color.Cyan.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Colorize,
                            contentDescription = "Configure Targets",
                            tint = Color.Cyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Match Logs Feed
                IconButton(
                    onClick = onLogsOpen,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0x99131A26), RoundedCornerShape(8.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = "Attack Logs",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Reset Game Match
                IconButton(
                    onClick = onReset,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0x99131A26), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = "Reset Battlefield",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Third tier: Status banner description
        Text(
            text = statusMessage,
            color = Color.LightGray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x66FFC107).copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                .padding(6.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun TargetTagIndicator(name: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(Color(0xFF1E2638), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = name,
            color = Color.LightGray,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun LaserTacticalCrosshair(
    lockedZone: HitZone,
    modifier: Modifier = Modifier
) {
    // Determine target color based on what is registered inside center box
    val activeGlowColor = when (lockedZone) {
        HitZone.HEAD -> Color(0xFFFF3333) // Blood Red Head lock-on
        HitZone.CHEST -> Color(0xFF00FF66) // Electric Green Torso lock-on
        HitZone.LIMBS -> Color(0xFF00BBFF) // Azure blue limbs lock-on
        else -> Color.White.copy(alpha = 0.5f) // White idle crosshair
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Fixed ROI scanning reference center overlay box (extremely precise center target frame)
        Canvas(
            modifier = Modifier.size(12.dp)
        ) {
            // Draw four precise corner brackets outlining the invisible threshold box area
            val s = size.width
            val len = 4f
            val strokeWidth = 2.5f

            // Top-left bracket
            drawLine(activeGlowColor, start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(len, 0f), strokeWidth)
            drawLine(activeGlowColor, start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(0f, len), strokeWidth)

            // Top-right bracket
            drawLine(activeGlowColor, start = androidx.compose.ui.geometry.Offset(s, 0f), end = androidx.compose.ui.geometry.Offset(s - len, 0f), strokeWidth)
            drawLine(activeGlowColor, start = androidx.compose.ui.geometry.Offset(s, 0f), end = androidx.compose.ui.geometry.Offset(s, len), strokeWidth)

            // Bottom-left bracket
            drawLine(activeGlowColor, start = androidx.compose.ui.geometry.Offset(0f, s), end = androidx.compose.ui.geometry.Offset(len, s), strokeWidth)
            drawLine(activeGlowColor, start = androidx.compose.ui.geometry.Offset(0f, s), end = androidx.compose.ui.geometry.Offset(0f, s - len), strokeWidth)

            // Bottom-right bracket
            drawLine(activeGlowColor, start = androidx.compose.ui.geometry.Offset(s, s), end = androidx.compose.ui.geometry.Offset(s - len, s), strokeWidth)
            drawLine(activeGlowColor, start = androidx.compose.ui.geometry.Offset(s, s), end = androidx.compose.ui.geometry.Offset(s, s - len), strokeWidth)
        }

        // Inner absolute center pointer crosshair (+)
        Canvas(
            modifier = Modifier.size(12.dp)
        ) {
            val half = size.width / 2
            val stroke = 3f

            // Horizontal cross line
            drawLine(
                color = activeGlowColor,
                start = androidx.compose.ui.geometry.Offset(0f, half),
                end = androidx.compose.ui.geometry.Offset(size.width, half),
                strokeWidth = stroke
            )
            // Vertical cross line
            drawLine(
                color = activeGlowColor,
                start = androidx.compose.ui.geometry.Offset(half, 0f),
                end = androidx.compose.ui.geometry.Offset(half, size.height),
                strokeWidth = stroke
            )
        }

        // Laser locks notification badge
        if (lockedZone != HitZone.NONE) {
            Column(
                modifier = Modifier
                    .offset(y = 28.dp)
                    .background(activeGlowColor.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "TARGET LOCK: ${lockedZone.name}",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.Black
                )
            }
        }
    }
}

@Composable
fun CombatTopLandscapeHud(
    userHp: Int,
    opponentHp: Int,
    networkState: NetworkState,
    isMatchActive: Boolean,
    onNetworkMenuOpen: () -> Unit,
    onCalibrationOpen: () -> Unit,
    onLogsOpen: () -> Unit,
    onExitToLobby: () -> Unit,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F1219).copy(alpha = 0.85f),
                        Color.Transparent
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        // Left Column (Your HP - Miniaturized & Minimalist)
        Column(
            modifier = Modifier
                .width(160.dp)
                .background(Color(0xB3111624), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF00FFCC).copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🛡️ YOUR HP",
                    fontSize = 10.sp,
                    color = Color(0xFF00FFCC),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$userHp%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { userHp / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = if (userHp > 40) Color(0xFF00FFCC) else Color(0xFFFF3366),
                trackColor = Color(0xFF222B3E),
            )
        }

        // Center Buttons Control Hub (Compact & Floating)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(Color(0xCC0F1219), RoundedCornerShape(12.dp))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            // Lobby / Connection Info Quick Access
            IconButton(
                onClick = onNetworkMenuOpen,
                modifier = Modifier.size(28.dp)
            ) {
                val netColor = when (networkState) {
                    NetworkState.CONNECTED -> Color(0xFF00FF2B)
                    NetworkState.HOSTING, NetworkState.JOINING -> Color(0xFFFFC107)
                    else -> Color.Gray
                }
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = "Link Wi-Fi",
                    tint = netColor,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Target calibration (Only show if match is NOT active - anti-cheat enforcement)
            if (!isMatchActive) {
                IconButton(
                    onClick = onCalibrationOpen,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Colorize,
                        contentDescription = "Calibrate",
                        tint = Color.Cyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Feed / Logs
            IconButton(
                onClick = onLogsOpen,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = "Logs",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Reset Game
            IconButton(
                onClick = onReset,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = "Reset Match",
                    tint = Color(0xFFFF5252),
                    modifier = Modifier.size(16.dp)
                )
            }

            // Vertical divider
            Box(
                modifier = Modifier
                    .height(16.dp)
                    .width(1.dp)
                    .background(Color.White.copy(alpha = 0.15f))
            )

            // Return to beautiful battlefield Lobby
            IconButton(
                onClick = onExitToLobby,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Logout,
                    contentDescription = "Lobby",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Right Column (Opponent HP - Miniaturized & Minimalist)
        Column(
            modifier = Modifier
                .width(160.dp)
                .background(Color(0xB3111624), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFFFF3B3B).copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "💥 OPPONENT",
                    fontSize = 10.sp,
                    color = Color(0xFFFF3B3B),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$opponentHp%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { opponentHp / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = Color(0xFFFF3B3B),
                trackColor = Color(0xFF222B3E),
            )
        }
    }
}

@Composable
fun ShootTriggerButton(
    lockedZone: HitZone,
    onFire: () -> Unit
) {
    val isLocked = lockedZone != HitZone.NONE
    val triggerColor = if (isLocked) Color(0xFFFF1744) else Color(0x66556075)
    val glowColor = if (isLocked) Color(0xFFFF1744) else Color.White.copy(alpha = 0.3f)

    Button(
        onClick = onFire,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = triggerColor
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 8.dp,
            pressedElevation = 2.dp
        ),
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier
            .testTag("shoot_trigger_button")
            .size(100.dp)
            .border(3.dp, glowColor, CircleShape)
            .padding(4.dp)
            .background(Color.Black.copy(alpha = 0.2f), CircleShape)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Double-concentric circular tactical military crosshair overlay graphics
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = glowColor.copy(alpha = 0.2f),
                    radius = size.minDimension / 2f
                )
                drawCircle(
                    color = glowColor.copy(alpha = 0.15f),
                    radius = size.minDimension / 3f,
                    style = Stroke(width = 1.5f)
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.GpsFixed,
                    contentDescription = "Attack Target Trigger",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = "FIRE",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

// Dialog to Host / Join games via Wifi or Bluetooth
@Composable
fun NetworkMenuSheet(
    localIp: String,
    networkState: NetworkState,
    connectionType: ConnectionType,
    bluetoothDevices: List<BtDevice>,
    isScanningBluetooth: Boolean,
    onSelectConnectionType: (ConnectionType) -> Unit,
    onHostWifi: () -> Unit,
    onJoinWifi: (String) -> Unit,
    onHostBluetooth: () -> Unit,
    onJoinBluetooth: (String) -> Unit,
    onScanBluetooth: () -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    var hostIpInput by remember { mutableStateOf("") }
    
    // Automatically trigger initial scan of bonded BT devices when BT tab is active
    val context = LocalContext.current
    LaunchedEffect(connectionType) {
        if (connectionType == ConnectionType.BLUETOOTH) {
            onScanBluetooth()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (connectionType == ConnectionType.WIFI) Icons.Default.Wifi else Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = Color(0xFF00FFCC),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ربط الهواتف / Link Battlefield Sockets",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        },
        containerColor = Color(0xFF161B29),
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Connection Mode Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F1219), RoundedCornerShape(8.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    // WIFI TAB
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (connectionType == ConnectionType.WIFI) Color(0xFF2E6EE4) else Color.Transparent)
                            .clickable { onSelectConnectionType(ConnectionType.WIFI) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Wi-Fi / Sockets",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // BLUETOOTH TAB
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (connectionType == ConnectionType.BLUETOOTH) Color(0xFF2E6EE4) else Color.Transparent)
                            .clickable { onSelectConnectionType(ConnectionType.BLUETOOTH) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Bluetooth",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Connection State Badge
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            when (networkState) {
                                NetworkState.CONNECTED -> Color(0x3300FFCC)
                                NetworkState.HOSTING, NetworkState.JOINING -> Color(0x33FFCC00)
                                NetworkState.ERROR -> Color(0x33FF3366)
                                else -> Color.White.copy(alpha = 0.1f)
                            },
                            RoundedCornerShape(6.dp)
                        )
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "STATUS: ${networkState.name}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = when (networkState) {
                            NetworkState.CONNECTED -> Color(0xFF00FFCC)
                            NetworkState.HOSTING, NetworkState.JOINING -> Color(0xFFFFCC00)
                            NetworkState.ERROR -> Color(0xFFFF3366)
                            else -> Color.LightGray
                        },
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (connectionType == ConnectionType.WIFI) {
                    // WI-FI METHOD LAYOUT
                    Text(
                        text = "Sync two phones over local Wi-Fi. Make sure BOTH are on the same local network.",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Host Server Box
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF222B3E), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("YOUR WIFI IP", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Text(localIp, fontSize = 14.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                        }
                        Button(
                            onClick = onHostWifi,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC)),
                            shape = RoundedCornerShape(6.dp),
                            enabled = networkState == NetworkState.DISCONNECTED || networkState == NetworkState.ERROR,
                            modifier = Modifier.testTag("host_battle_button")
                        ) {
                            Text("HOST GAME", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(14.dp))

                    // Connect client
                    Text("JOIN BATTLEFIELD", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = hostIpInput,
                        onValueChange = { hostIpInput = it },
                        placeholder = { Text("Enter Host's IP (e.g. 192.168.1.X)", fontSize = 12.sp, color = Color.Gray) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onAny = {
                                if (hostIpInput.trim().isNotEmpty()) {
                                    onJoinWifi(hostIpInput.trim())
                                }
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF2E6EE4),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedContainerColor = Color(0xFF1E2638),
                            unfocusedContainerColor = Color(0xFF121824)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        enabled = networkState == NetworkState.DISCONNECTED || networkState == NetworkState.ERROR,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("join_ip_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            if (hostIpInput.trim().isNotEmpty()) {
                                onJoinWifi(hostIpInput.trim())
                            }
                        },
                        enabled = (networkState == NetworkState.DISCONNECTED || networkState == NetworkState.ERROR) && hostIpInput.trim().isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("join_button")
                            .height(40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E6EE4))
                    ) {
                        Text("CONNECT TO IP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                } else {
                    // BLUETOOTH METHOD LAYOUT
                    Text(
                        text = "Play outdoors without setup routers! One player hosts, and the other selects their name from the paired list below.",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF222B3E), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("BLUETOOTH LINK", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Text("Create Server Socket", fontSize = 12.sp, color = Color.White)
                        }
                        Button(
                            onClick = onHostBluetooth,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC)),
                            shape = RoundedCornerShape(6.dp),
                            enabled = networkState == NetworkState.DISCONNECTED || networkState == NetworkState.ERROR,
                            modifier = Modifier.testTag("host_bluetooth_button")
                        ) {
                            Text("HOST (BT)", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("PAIRED DEVICES (" + bluetoothDevices.size + ")", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        TextButton(onClick = onScanBluetooth) {
                            Text(if (isScanningBluetooth) "RETRYING..." else "REFRESH", fontSize = 10.sp, color = Color(0xFF00FFCC))
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (bluetoothDevices.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No paired Bluetooth devices found.\nEnable Bluetooth & pair in Android System Settings first.",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            bluetoothDevices.forEach { device ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF1E2638), RoundedCornerShape(8.dp))
                                        .clickable(enabled = networkState == NetworkState.DISCONNECTED || networkState == NetworkState.ERROR) {
                                            onJoinBluetooth(device.address)
                                        }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(device.name, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                        Text(device.address, fontSize = 10.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Bluetooth,
                                        contentDescription = "Sync Pair",
                                        tint = Color(0xFF2E6EE4),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (networkState == NetworkState.CONNECTED || networkState == NetworkState.HOSTING || networkState == NetworkState.JOINING) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onDisconnect,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3366)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("قطع الاتصال / DISCONNECT TARGET", fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق / CLOSE", color = Color.LightGray, fontWeight = FontWeight.Bold)
            }
        }
    )
}

// Custom manual calibration dialog
@Composable
fun CalibrationPanelSheet(
    currentTab: HitZone,
    liveR: Int,
    liveG: Int,
    liveB: Int,
    liveH: Float,
    liveS: Float,
    liveV: Float,
    headColor: Color,
    chestColor: Color,
    limbsColor: Color,
    onSelectTab: (HitZone) -> Unit,
    onLiveColorUpdated: (RoiColorData) -> Unit,
    onCapture: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            try {
                if (cameraProviderFuture.isDone) {
                    val cameraProvider = cameraProviderFuture.get()
                    cameraProvider.unbindAll()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Colorize, contentDescription = null, tint = Color.Cyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text("🎯 Manual Color Calibration", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        containerColor = Color(0xFF161B29),
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Aims camera at opponent's armor. Verify color parameters below, then click CAPTURE.",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // 1. Mini Camera Preview Box (The Viewport with Center Crosshair)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.5.dp, Color(0xFF00FFCC).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx).apply {
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                            }
                            val executor = ContextCompat.getMainExecutor(ctx)
                            
                            cameraProviderFuture.addListener({
                                try {
                                    val cameraProvider = cameraProviderFuture.get()
                                    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                        val preview = Preview.Builder().build().also {
                                            it.setSurfaceProvider(previewView.surfaceProvider)
                                        }
                                        
                                        val imageAnalysis = ImageAnalysis.Builder()
                                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                            .build()
                                            .also {
                                                it.setAnalyzer(
                                                    executor,
                                                    ColorAnalyzer(
                                                        onTargetLocked = {},
                                                        onRoiColorUpdated = { color -> onLiveColorUpdated(color) },
                                                        targetHeadHsv = { null },
                                                        targetChestHsv = { null },
                                                        targetLimbsHsv = { null }
                                                    )
                                                )
                                            }
                                        
                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            imageAnalysis
                                        )
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }, executor)
                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    // Fixed crosshair (+) overlay in the center of the viewport
                    Box(
                        modifier = Modifier.size(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Horizontal crosshair line
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .height(2.dp)
                                .background(Color(0xFF00FFCC))
                        )
                        // Vertical crosshair line
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(20.dp)
                                .background(Color(0xFF00FFCC))
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))

                // Tab Selectors
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F1219), RoundedCornerShape(8.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    HitZone.values().filter { it != HitZone.NONE }.forEach { zone ->
                        val selected = currentTab == zone
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selected) Color(0xFF2A364F) else Color.Transparent)
                                .clickable { onSelectTab(zone) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = zone.name,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) Color.White else Color.Gray
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Calibration Area Detail
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1A2234), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "LIVE RETICLE COLOR",
                            fontSize = 10.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "RGB($liveR, $liveG, $liveB)",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "HSV(${liveH.toInt()}°, ${(liveS * 100).toInt()}%, ${(liveV * 100).toInt()}%)",
                            color = Color.Cyan,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Color(
                                    android.graphics.Color.HSVToColor(
                                        floatArrayOf(liveH, liveS, liveV)
                                    )
                                )
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Live", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Capture Color Button prominently placed right beneath the Live preview box!
                Button(
                    onClick = {
                        onCapture()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("capture_color_button")
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "CAPTURE COLOR",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Target calibrated values
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF121724), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("CURRENT REGISTERED TARGETS", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    
                    CalibrationRow(label = "HEAD TARGET (50 DMG)", savedColor = headColor)
                    CalibrationRow(label = "CHEST TARGET (25 DMG)", savedColor = chestColor)
                    CalibrationRow(label = "LIMBS TARGET (10 DMG)", savedColor = limbsColor)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("DONE", color = Color.Cyan)
            }
        }
    )
}

@Composable
fun CalibrationRow(label: String, savedColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 11.sp, color = Color.LightGray)
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(savedColor)
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
        )
    }
}

// Tactical Logs Feed Dialogue
@Composable
fun TacticalLogsDialog(
    logs: List<String>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF00FFCC))
                Spacer(modifier = Modifier.width(8.dp))
                Text("📡 Combat Feed Feed", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        containerColor = Color(0xFF161B29),
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(Color(0xFF0F1219), RoundedCornerShape(12.dp))
                    .padding(8.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    reverseLayout = false
                ) {
                    items(logs) { log ->
                        Text(
                            text = "> $log",
                            color = if (log.contains("🎯") || log.contains("💥")) Color(0xFFFF3366) else Color.LightGray,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("CLOSE", color = Color.LightGray)
            }
        }
    )
}

// Setup Camera Preview AND Real-time Color Analysis Bindings with Pinch To Zoom Support
@Composable
fun CameraPreviewAndAnalyzer(
    viewModel: LaserTagViewModel,
    modifier: Modifier = Modifier,
    onZoomChanged: (Float) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    
    // Hold reference to the bound camera instance for dynamic zoom adjustments
    val cameraRef = remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var scaleFactor by remember { mutableStateOf(1f) }

    // Cleanly unbind everything when this composable leaves the composition hierarchy
    DisposableEffect(lifecycleOwner) {
        onDispose {
            try {
                if (cameraProviderFuture.isDone) {
                    val cameraProvider = cameraProviderFuture.get()
                    cameraProvider.unbindAll()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoomAmount, _ ->
                    val cameraInstance = cameraRef.value
                    if (cameraInstance != null) {
                        val zoomState = cameraInstance.cameraInfo.zoomState.value
                        val currentZoom = zoomState?.zoomRatio ?: 1f
                        val minZoom = zoomState?.minZoomRatio ?: 1f
                        val maxZoom = zoomState?.maxZoomRatio ?: 6.0f
                        
                        val nextZoom = (currentZoom * zoomAmount).coerceIn(minZoom, maxZoom)
                        cameraInstance.cameraControl.setZoomRatio(nextZoom)
                        scaleFactor = nextZoom
                        onZoomChanged(nextZoom)
                    }
                }
            }
    ) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val executor = ContextCompat.getMainExecutor(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    // Ensure camera permission is actively granted before trying to bind the camera lifecycles
                    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        // 1. Live Preview layer
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        // 2. Real-time background pixel-level analyzer
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also {
                                it.setAnalyzer(
                                    executor,
                                    ColorAnalyzer(
                                        onTargetLocked = { zone -> viewModel.updateLockedZone(zone) },
                                        onRoiColorUpdated = { color -> viewModel.updateLiveColor(color) },
                                        targetHeadHsv = { viewModel.state.value.headHsv },
                                        targetChestHsv = { viewModel.state.value.chestHsv },
                                        targetLimbsHsv = { viewModel.state.value.limbsHsv }
                                    )
                                )
                            }

                        try {
                            // Unbind everything and bind new preview/analyzer bindings
                            cameraProvider.unbindAll()
                            val cam = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis
                            )
                            cameraRef.value = cam
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }, executor)
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
