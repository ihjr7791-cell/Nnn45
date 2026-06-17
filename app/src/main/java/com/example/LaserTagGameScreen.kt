package com.example

import android.Manifest
import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
        if (cameraPermissionState.status.isGranted) {
            // 1. Camera View covering the screen (HUD backdrop)
            CameraPreviewAndAnalyzer(
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Permission request screen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .background(Color(0xE6161C2A), RoundedCornerShape(24.dp))
                        .border(1.dp, Color(0xFFE42E5B).copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                        .padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = "Camera Permission Needed",
                        tint = Color(0xFFE42E5B),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Camera Permission Required",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "AR Laser Tag detects calibrated armor colors using direct, real-time pixel scanning on your screen center.",
                        color = Color.LightGray,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { cameraPermissionState.launchPermissionRequest() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE42E5B)),
                        modifier = Modifier
                            .testTag("request_permission_button")
                            .height(48.dp)
                    ) {
                        Text("Grant Camera Access", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 2. The Laser Crosshair centered precisely
        if (cameraPermissionState.status.isGranted) {
            LaserTacticalCrosshair(
                lockedZone = state.lockedZone,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 3. Status Action HUD / Quick Log
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp)
        ) {
            // Upper Tier: Health status and Match metadata
            CombatTopHud(
                userHp = state.userHp,
                opponentHp = state.opponentHp,
                networkState = state.networkState,
                statusMessage = state.statusMessage,
                onNetworkMenuOpen = { showNetworkMenu = true },
                onCalibrationOpen = { showCalibrationMenu = true },
                onLogsOpen = { showLogsDialog = true },
                onReset = { viewModel.resetMatch() }
            )

            Spacer(modifier = Modifier.weight(1f))

            // Lower Tier: Live color trace list, game terminal details, and shooting trigger button to match FPS design
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left hand widgets: Quick stats tracker
                Column(
                    modifier = Modifier
                        .weight(1.5f)
                        .background(Color(0xB3111622), RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFF2E6EE4).copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.0.dp)
                                .clip(CircleShape)
                                .background(
                                    Color(
                                        android.graphics.Color.HSVToColor(
                                            floatArrayOf(
                                                state.liveColor.h,
                                                state.liveColor.s,
                                                state.liveColor.v
                                            )
                                        )
                                    )
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CENTER PIXEL",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.LightGray,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Hue: ${state.liveColor.h.toInt()}° | Sat: ${(state.liveColor.s * 100).toInt()}% | Val: ${(state.liveColor.v * 100).toInt()}%",
                        color = Color.Cyan,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.1f))
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Calibrated Active Armor Ranges:",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.LightGray
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        TargetTagIndicator(name = "HEAD", color = hsvToColor(state.headHsv))
                        TargetTagIndicator(name = "CHEST", color = hsvToColor(state.chestHsv))
                        TargetTagIndicator(name = "LIMBS", color = hsvToColor(state.limbsHsv))
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Right hand Trigger: The giant right thumb SHOOT button which is tactile, reactive, and comfortable
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    ShootTriggerButton(
                        lockedZone = state.lockedZone,
                        onFire = { viewModel.fireLaser(context) }
                    )
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
                onHost = { viewModel.hostGame() },
                onJoin = { hostIp -> viewModel.joinGame(hostIp) },
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
fun CombatTopHud(
    userHp: Int,
    opponentHp: Int,
    networkState: NetworkState,
    statusMessage: String,
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
        // Fixed ROI scanning reference center overlay box (extremely faint guideline matching a radar aesthetic)
        Canvas(
            modifier = Modifier.size(48.dp)
        ) {
            // Draw four precise corner brackets outlining the invisible threshold box area
            val s = size.width
            val len = 10f
            val strokeWidth = 2f

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
            modifier = Modifier.size(16.dp)
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
                    .offset(y = 42.dp)
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
fun ShootTriggerButton(
    lockedZone: HitZone,
    onFire: () -> Unit
) {
    val isLocked = lockedZone != HitZone.NONE
    val triggerColor = if (isLocked) Color(0xFFFF2B55) else Color(0xFF556075)

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
        modifier = Modifier
            .testTag("shoot_trigger_button")
            .size(96.dp)
            .border(2.dp, Color.White.copy(alpha = 0.3f), CircleShape)
            .padding(2.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = "Trigger Shot",
                tint = Color.White,
                modifier = Modifier.size(34.dp)
            )
            Text(
                text = "FIRE",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 11.sp
            )
        }
    }
}

// Dialog to Host / Join games
@Composable
fun NetworkMenuSheet(
    localIp: String,
    networkState: NetworkState,
    onHost: () -> Unit,
    onJoin: (String) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    var hostIpInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "📶 Link Battlefield Sockets",
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        containerColor = Color(0xFF161B29),
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Sync two phones over local Wi-Fi. Make sure BOTH are on the same local network.",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Your IP info
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF222B3E), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("YOUR DEVICE IP", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Text(localIp, fontSize = 16.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                    }
                    Button(
                        onClick = onHost,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("host_battle_button")
                    ) {
                        Text("HOST", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(16.dp))

                // Connect to opponent IP
                Text("JOIN CO-OP BATTLE", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = hostIpInput,
                    onValueChange = { hostIpInput = it },
                    placeholder = { Text("Enter Host's IP Address (e.g. 192.168.1.X)", fontSize = 13.sp, color = Color.Gray) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onAny = {
                            if (hostIpInput.trim().isNotEmpty()) {
                                onJoin(hostIpInput.trim())
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("join_ip_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (hostIpInput.trim().isNotEmpty()) {
                            onJoin(hostIpInput.trim())
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("join_button")
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E6EE4))
                ) {
                    Text("CONNECT TO HOST", fontWeight = FontWeight.Bold, color = Color.White)
                }

                if (networkState == NetworkState.CONNECTED) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            onDisconnect()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3366))
                    ) {
                        Text("BREAK LINK / DISCONNECT", fontWeight = FontWeight.Bold)
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
    onCapture: () -> Unit,
    onDismiss: () -> Unit
) {
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
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Aims camera at opponent's armor. Verify color parameters below, then click CAPTURE.",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

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

                Spacer(modifier = Modifier.height(12.dp))

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

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        onCapture()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("capture_color_button")
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SAVE LIVE COLOR TO ${currentTab.name}",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
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

// Setup Camera Preview AND Real-time Color Analysis Bindings
@Composable
fun CameraPreviewAndAnalyzer(
    viewModel: LaserTagViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val executor = ContextCompat.getMainExecutor(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

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
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, executor)
            previewView
        },
        modifier = modifier
    )
}
