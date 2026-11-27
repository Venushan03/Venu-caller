package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Elderly
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Face3
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Man
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Woman
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.VoicePreset
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceElevated
import com.example.ui.theme.DialKeyColors
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCrimson
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoltLime
import com.example.viewmodel.PrismCallViewModel
import kotlin.math.sin

@Composable
fun VoiceStudioScreen(
    viewModel: PrismCallViewModel
) {
    val context = LocalContext.current
    val activePreset by viewModel.voicePreset.collectAsStateWithLifecycle()
    val isMonitoring by viewModel.isMonitoring.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val isPlayingClip by viewModel.isPlayingClip.collectAsStateWithLifecycle()
    val hasRecordedClip by viewModel.hasRecordedClip.collectAsStateWithLifecycle()
    val amplitude by viewModel.amplitudeLevel.collectAsStateWithLifecycle()
    val voiceError by viewModel.voiceError.collectAsStateWithLifecycle()
    val hasMicPerm by viewModel.hasMicPerm.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var speed1 by remember(settings.speedDialSlot1) { mutableStateOf(settings.speedDialSlot1) }
    var speed2 by remember(settings.speedDialSlot2) { mutableStateOf(settings.speedDialSlot2) }
    var speed3 by remember(settings.speedDialSlot3) { mutableStateOf(settings.speedDialSlot3) }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshPermissionsAndData()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 580.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Real-Time Neon Audio Waveform & Studio Card
            Surface(
                color = CyberSurface,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        Brush.horizontalGradient(listOf(NeonMagenta.copy(0.6f), NeonCyan.copy(0.6f))),
                        RoundedCornerShape(22.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Voice FX Studio", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                            Text(
                                "Active: ${activePreset.displayName} (${activePreset.pitchFactor}x pitch)",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonMagenta
                            )
                        }
                        Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(30.dp))
                    }

                    NeonWaveformCanvas(
                        amplitude = amplitude,
                        isActive = isMonitoring || isRecording || isPlayingClip,
                        accent = if (isRecording) NeonCrimson else NeonCyan
                    )

                    if (!hasMicPerm) {
                        Button(
                            onClick = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonMagenta, contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth().testTag("grant_mic_button")
                        ) {
                            Icon(Icons.Rounded.Mic, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Enable Microphone for Voice Studio")
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.toggleLiveMonitor() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isMonitoring) NeonCrimson else NeonCyan,
                                    contentColor = ObsidianBg
                                ),
                                modifier = Modifier.weight(1f).testTag("voice_live_monitor_button")
                            ) {
                                Icon(if (isMonitoring) Icons.Rounded.Stop else Icons.Rounded.Hearing, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (isMonitoring) "Stop Live" else "Live Preview")
                            }

                            Button(
                                onClick = { viewModel.toggleRecordFxClip() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isRecording) NeonCrimson else NeonMagenta,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.weight(1f).testTag("voice_record_clip_button")
                            ) {
                                Icon(if (isRecording) Icons.Rounded.Stop else Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (isRecording) "Stop Rec" else "Record FX")
                            }
                        }

                        if (hasRecordedClip) {
                            OutlinedButton(
                                onClick = { viewModel.togglePlayClip() },
                                modifier = Modifier.fillMaxWidth().testTag("voice_play_clip_button")
                            ) {
                                Icon(
                                    if (isPlayingClip) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = VoltLime
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(if (isPlayingClip) "Stop Playback" else "Play Transformed Clip")
                            }
                        }
                    }

                    voiceError?.let { err ->
                        Text(err, style = MaterialTheme.typography.bodyMedium, color = NeonCrimson)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CyberSurfaceElevated)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Info, contentDescription = null, tint = NeonAmber, modifier = Modifier.size(18.dp))
                        Text(
                            text = stringResource(R.string.voice_disclaimer),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }

            // 2. Six Voice Presets Grid (Original, Male, Female, Old, Child, Baby)
            Text("Select Voice Character", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            val presets = VoicePreset.entries
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                presets.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { preset ->
                            VoicePresetCard(
                                preset = preset,
                                selected = activePreset == preset,
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.setVoicePreset(preset) }
                            )
                        }
                    }
                }
            }

            // 3. Incoming Call Simulator Launcher (Quick Personas)
            Surface(
                color = CyberSurface,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyberBorder, RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = NeonAmber)
                        Text("Incoming Caller ID & Swipe Preview", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                viewModel.triggerSimulatedIncomingCall(
                                    context = context,
                                    phoneNumber = "+91 98400 77889",
                                    presetName = null,
                                    verificationBadge = "Verified Network ID"
                                )
                            },
                            modifier = Modifier.weight(1f).testTag("sim_unknown_call_btn")
                        ) {
                            Text("Unknown Caller")
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.triggerSimulatedIncomingCall(
                                    context = context,
                                    phoneNumber = "+1 800-555-0192",
                                    presetName = null,
                                    verificationBadge = "Unverified / Risk"
                                )
                            },
                            modifier = Modifier.weight(1f).testTag("sim_spam_call_btn")
                        ) {
                            Text("Spam Alert Ring")
                        }
                    }
                }
            }

            // 4. Future Advance Options & Speed Dial Slots
            Surface(
                color = CyberSurface,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyberBorder, RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Tune, contentDescription = null, tint = VoltLime)
                        Text("Future Advance Controls", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    }

                    SettingToggleRow(
                        title = "Flashlight Strobe on Incoming Call",
                        subtitle = "Pulse camera LED when ringing",
                        checked = settings.flashStrobeOnIncoming,
                        onCheckedChange = { viewModel.setFlashStrobe(it) }
                    )
                    SettingToggleRow(
                        title = "Haptic Key Feedback",
                        subtitle = "Tactile vibration on prismatic keys",
                        checked = settings.hapticDialKeys,
                        onCheckedChange = { viewModel.setHapticDialKeys(it) }
                    )
                    SettingToggleRow(
                        title = "DTMF Audio Tones",
                        subtitle = "Play classic keypad tones while dialing",
                        checked = settings.dtmfTonesEnabled,
                        onCheckedChange = { viewModel.setDtmfTones(it) }
                    )
                    SettingToggleRow(
                        title = "Auto-Screen Unverified Numbers",
                        subtitle = "Flag failed STIR/SHAKEN callers",
                        checked = settings.autoScreenFailedStirShaken,
                        onCheckedChange = { viewModel.setAutoScreenSpam(it) }
                    )

                    Text("Speed Dial Long-Press Slots (Keys 1, 2, 3)", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = speed1,
                            onValueChange = { speed1 = it },
                            label = { Text("Key 1") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = { viewModel.setSpeedDial(1, speed1) }) { Text("Save") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = speed2,
                            onValueChange = { speed2 = it },
                            label = { Text("Key 2") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = { viewModel.setSpeedDial(2, speed2) }) { Text("Save") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = speed3,
                            onValueChange = { speed3 = it },
                            label = { Text("Key 3") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = { viewModel.setSpeedDial(3, speed3) }) { Text("Save") }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun VoicePresetCard(
    preset: VoicePreset,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val (icon, color) = when (preset) {
        VoicePreset.ORIGINAL -> Icons.Rounded.Face to NeonCyan
        VoicePreset.MALE -> Icons.Rounded.Man to DialKeyColors[7]
        VoicePreset.FEMALE -> Icons.Rounded.Woman to NeonMagenta
        VoicePreset.OLD -> Icons.Rounded.Elderly to NeonAmber
        VoicePreset.CHILD -> Icons.Rounded.Face3 to VoltLime
        VoicePreset.BABY -> Icons.Rounded.ChildCare to NeonViolet
    }
    Surface(
        color = if (selected) color.copy(alpha = 0.2f) else CyberSurface,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .border(if (selected) 2.dp else 1.dp, if (selected) color else CyberBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("voice_preset_${preset.name.lowercase()}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = preset.displayName, tint = color, modifier = Modifier.size(26.dp))
            Column {
                Text(preset.displayName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(preset.subtitle, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun NeonWaveformCanvas(
    amplitude: Float,
    isActive: Boolean,
    accent: Color
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianBg)
            .padding(horizontal = 8.dp)
    ) {
        val bars = 28
        val barWidth = (size.width / bars) * 0.58f
        val step = size.width / bars
        val baseH = size.height * 0.16f
        for (i in 0 until bars) {
            val wave = if (isActive) {
                val factor = 0.35f + 0.65f * kotlin.math.abs(sin(i * 0.55f + amplitude * 9f))
                (baseH + amplitude * size.height * 0.72f * factor).coerceIn(baseH, size.height * 0.9f)
            } else {
                baseH
            }
            val top = (size.height - wave) / 2f
            drawRoundRect(
                color = if (isActive) accent else CyberBorder,
                topLeft = Offset(i * step + (step - barWidth) / 2f, top),
                size = Size(barWidth, wave),
                cornerRadius = CornerRadius(6f, 6f)
            )
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
