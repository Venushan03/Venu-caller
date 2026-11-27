package com.example.telecom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.FirestoreNotesRepository
import com.example.firebase.configureAppCheck
import com.example.model.CallerCategory
import com.example.model.PhoneUtils
import com.example.model.VoicePreset
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceElevated
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCrimson
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PrismCallTheme
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoltLime
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class InCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent {
            PrismCallTheme {
                val callState by CallCoordinator.callState.collectAsStateWithLifecycle()
                val quickReplies by CallCoordinator.quickReplies.collectAsStateWithLifecycle()

                LaunchedEffect(callState) {
                    if (callState == null) {
                        finish()
                    }
                }

                callState?.let { state ->
                    InCallScreen(
                        state = state,
                        quickReplies = quickReplies,
                        onAnswer = { CallCoordinator.answerCall(this) },
                        onRejectOrEnd = { CallCoordinator.rejectOrEndCall(this) },
                        onToggleMute = { CallCoordinator.toggleMute() },
                        onToggleSpeaker = { CallCoordinator.toggleSpeaker() },
                        onToggleHold = { CallCoordinator.toggleHold() },
                        onDtmf = { CallCoordinator.sendDtmf(it) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InCallScreen(
    state: ActiveCallUiState,
    quickReplies: List<String>,
    onAnswer: () -> Unit,
    onRejectOrEnd: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleHold: () -> Unit,
    onDtmf: (Char) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var showNoteSheet by remember { mutableStateOf(false) }
    var showReplySheet by remember { mutableStateOf(false) }
    var showKeypad by remember { mutableStateOf(false) }
    var selectedVoiceFx by remember { mutableStateOf(VoicePreset.ORIGINAL) }
    var noteTitle by remember { mutableStateOf("Call Note — ${state.displayName}") }
    var noteBody by remember { mutableStateOf("") }
    var saveBanner by remember { mutableStateOf<String?>(null) }

    val notesRepo = remember(context) {
        if (Firebase.auth.currentUser != null) FirestoreNotesRepository(context) else null
    }
    val localRepo = remember(context) { com.example.data.local.LocalNotesRepository(context) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A0F24),
                        ObsidianBg,
                        Color(0xFF14081F)
                    )
                )
            )
            .safeDrawingPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
                .align(Alignment.Center)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Status & Verification Pill
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(CyberSurfaceElevated.copy(alpha = 0.85f))
                        .border(1.dp, NeonCyan.copy(alpha = 0.45f), RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = "Caller verification status",
                        tint = if (state.verificationBadge.contains("Risk")) NeonCrimson else NeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = state.verificationBadge + if (state.isSimulated) " • Preview" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = when (state.phase) {
                        CallPhase.RINGING -> "INCOMING PRISM CALL"
                        CallPhase.DIALING -> "CONNECTING..."
                        CallPhase.ACTIVE -> PhoneUtils.formatDuration(state.durationSeconds)
                        CallPhase.HOLDING -> "ON HOLD • ${PhoneUtils.formatDuration(state.durationSeconds)}"
                        CallPhase.DISCONNECTED -> "CALL ENDED"
                    },
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (state.phase) {
                        CallPhase.RINGING -> VoltLime
                        CallPhase.ACTIVE -> NeonEmerald
                        CallPhase.DISCONNECTED -> NeonCrimson
                        else -> NeonAmber
                    },
                    letterSpacing = 1.6.sp
                )
            }

            // Center Neon Caller ID Halo & Identity Card
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                NeonCallerHalo(
                    initial = state.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    isRinging = state.phase == CallPhase.RINGING || state.phase == CallPhase.ACTIVE
                )

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = state.displayName,
                    style = MaterialTheme.typography.displayMedium,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = state.phoneNumber,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 18.sp,
                    color = NeonCyan,
                    textAlign = TextAlign.Center
                )

                if (state.cachedCategory != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.cachedCategory.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = ObsidianBg,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(VoltLime)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Unknown Number Auto-Intelligence Card
                AnimatedVisibility(visible = state.isLookingUp || state.aiLookup != null) {
                    Surface(
                        color = CyberSurface.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                Brush.linearGradient(listOf(NeonCyan.copy(0.6f), NeonMagenta.copy(0.6f))),
                                RoundedCornerShape(20.dp)
                            )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = "Gemini Caller Intelligence",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (state.isLookingUp) "Gemini identifying unknown caller..."
                                    else "${state.aiLookup?.numberType} • ${state.aiLookup?.regionHint} • Risk: ${state.aiLookup?.spamRisk}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = NeonCyan
                                )
                            }
                            state.aiLookup?.let { info ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = info.summary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                saveBanner?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.labelLarge,
                        color = VoltLime
                    )
                }
            }

            // Bottom Action Area: Stylish Swipe Pill when Ringing, Command Deck when Active
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (state.phase == CallPhase.RINGING) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        OutlinedButton(
                            onClick = { showReplySheet = true },
                            modifier = Modifier.testTag("incall_quick_reply_button"),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonMagenta.copy(0.6f))
                        ) {
                            Icon(Icons.Rounded.Sms, contentDescription = null, tint = NeonMagenta, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Quick Reply", color = TextPrimary)
                        }
                        OutlinedButton(
                            onClick = { showNoteSheet = true },
                            modifier = Modifier.testTag("incall_ringing_note_button"),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(0.6f))
                        ) {
                            Icon(Icons.Rounded.EditNote, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Quick Note", color = TextPrimary)
                        }
                    }

                    SwipeCallAttendantPill(
                        onAnswer = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAnswer()
                        },
                        onDecline = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onRejectOrEnd()
                        }
                    )
                } else {
                    // Voice FX Quick Bar during Active Call
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                    ) {
                        VoicePreset.entries.take(6).forEach { preset ->
                            val selected = selectedVoiceFx == preset
                            FilterChip(
                                selected = selected,
                                onClick = { selectedVoiceFx = preset },
                                label = { Text(preset.displayName, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonMagenta.copy(alpha = 0.25f),
                                    selectedLabelColor = NeonMagenta
                                )
                            )
                        }
                    }

                    if (showKeypad) {
                        InCallMiniKeypad(onDigit = onDtmf)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        InCallToggleAction(
                            icon = Icons.Rounded.MicOff,
                            label = "Mute",
                            active = state.isMuted,
                            accent = NeonAmber,
                            onClick = onToggleMute
                        )
                        InCallToggleAction(
                            icon = Icons.AutoMirrored.Rounded.VolumeUp,
                            label = "Speaker",
                            active = state.isSpeaker,
                            accent = NeonCyan,
                            onClick = onToggleSpeaker
                        )
                        InCallToggleAction(
                            icon = Icons.Rounded.Pause,
                            label = "Hold",
                            active = state.phase == CallPhase.HOLDING,
                            accent = NeonViolet,
                            onClick = onToggleHold
                        )
                        InCallToggleAction(
                            icon = Icons.Rounded.Dialpad,
                            label = "Keypad",
                            active = showKeypad,
                            accent = VoltLime,
                            onClick = { showKeypad = !showKeypad }
                        )
                        InCallToggleAction(
                            icon = Icons.Rounded.EditNote,
                            label = "Note",
                            active = showNoteSheet,
                            accent = NeonMagenta,
                            onClick = { showNoteSheet = true }
                        )
                    }

                    Button(
                        onClick = onRejectOrEnd,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCrimson),
                        shape = CircleShape,
                        modifier = Modifier
                            .size(width = 180.dp, height = 58.dp)
                            .testTag("incall_end_button")
                    ) {
                        Icon(Icons.Rounded.CallEnd, contentDescription = "End Call", tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("End Call", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showNoteSheet) {
        ModalBottomSheet(
            onDismissRequest = { showNoteSheet = false },
            containerColor = CyberSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Pin Call Note", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Text("Number: ${state.phoneNumber}", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                OutlinedTextField(
                    value = noteTitle,
                    onValueChange = { noteTitle = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = noteBody,
                    onValueChange = { noteBody = it },
                    label = { Text("Details, gate code, callback reminder...") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        scope.launch {
                            val titleText = noteTitle.ifBlank { "Call Note" }
                            val tagList = listOf(state.cachedCategory ?: "Call")
                            val res = if (notesRepo != null) {
                                notesRepo.saveNumberNote(
                                    phoneNumber = state.phoneNumber,
                                    contactName = state.displayName,
                                    title = titleText,
                                    body = noteBody,
                                    tags = tagList,
                                    pinned = true
                                )
                            } else {
                                localRepo.saveNumberNote(
                                    phoneNumber = state.phoneNumber,
                                    contactName = state.displayName,
                                    title = titleText,
                                    body = noteBody,
                                    tags = tagList,
                                    pinned = true
                                )
                            }
                            saveBanner = if (res.isSuccess) {
                                if (notesRepo != null) "Saved to Cloud Notepad" else "Saved to Local Notepad"
                            } else {
                                "Save failed: ${res.exceptionOrNull()?.localizedMessage}"
                            }
                            showNoteSheet = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Note")
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showReplySheet) {
        ModalBottomSheet(
            onDismissRequest = { showReplySheet = false },
            containerColor = CyberSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Gemini Quick Replies", style = MaterialTheme.typography.titleLarge, color = NeonCyan)
                quickReplies.forEach { reply ->
                    Surface(
                        color = CyberSurfaceElevated,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, CyberBorder, RoundedCornerShape(14.dp))
                            .clickable {
                                saveBanner = "Selected reply: \"$reply\""
                                showReplySheet = false
                                onRejectOrEnd()
                            }
                    ) {
                        Text(
                            text = reply,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun NeonCallerHalo(initial: String, isRinging: Boolean) {
    val transition = rememberInfiniteTransition(label = "halo")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(168.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension * 0.31f
            if (isRinging) {
                val r1 = baseRadius + pulse * (size.minDimension * 0.18f)
                val r2 = baseRadius + ((pulse + 0.5f) % 1f) * (size.minDimension * 0.18f)
                drawCircle(
                    color = NeonCyan.copy(alpha = (1f - pulse) * 0.55f),
                    radius = r1,
                    center = center,
                    style = Stroke(width = 3.dp.toPx())
                )
                drawCircle(
                    color = NeonMagenta.copy(alpha = (1f - ((pulse + 0.5f) % 1f)) * 0.55f),
                    radius = r2,
                    center = center,
                    style = Stroke(width = 3.dp.toPx())
                )
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(NeonCyan.copy(alpha = 0.35f), NeonViolet.copy(alpha = 0.2f))
                ),
                radius = baseRadius,
                center = center
            )
            drawCircle(
                brush = Brush.linearGradient(listOf(NeonCyan, NeonMagenta, VoltLime)),
                radius = baseRadius,
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )
        }
        Text(
            text = initial,
            style = MaterialTheme.typography.displayLarge,
            color = TextPrimary
        )
    }
}

@Composable
fun SwipeCallAttendantPill(
    onAnswer: () -> Unit,
    onDecline: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(74.dp)
            .clip(RoundedCornerShape(40.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        NeonCrimson.copy(alpha = 0.22f),
                        CyberSurfaceElevated,
                        NeonEmerald.copy(alpha = 0.22f)
                    )
                )
            )
            .border(1.5.dp, CyberBorder, RoundedCornerShape(40.dp))
            .testTag("swipe_call_attendant_pill"),
        contentAlignment = Alignment.Center
    ) {
        val density = LocalDensity.current
        val thumbSizePx = with(density) { 62.dp.toPx() }
        val maxTravelPx = ((constraints.maxWidth - thumbSizePx) / 2f - with(density) { 8.dp.toPx() }).coerceAtLeast(40f)
        var rawOffset by remember { mutableFloatStateOf(0f) }
        val animatedOffset by animateFloatAsState(
            targetValue = rawOffset,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
            label = "pillOffset"
        )

        // Left Decline & Right Answer indicators
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, tint = NeonCrimson, modifier = Modifier.size(16.dp))
                Text("Decline", color = NeonCrimson, style = MaterialTheme.typography.labelLarge)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Answer", color = NeonEmerald, style = MaterialTheme.typography.labelLarge)
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(16.dp))
            }
        }

        // Center Draggable Neon Orb
        val orbTint = when {
            rawOffset > maxTravelPx * 0.35f -> NeonEmerald
            rawOffset < -maxTravelPx * 0.35f -> NeonCrimson
            else -> NeonCyan
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .size(62.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(orbTint, NeonViolet)))
                .border(2.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        rawOffset = (rawOffset + delta).coerceIn(-maxTravelPx, maxTravelPx)
                    },
                    onDragStopped = {
                        when {
                            rawOffset >= maxTravelPx * 0.7f -> {
                                rawOffset = maxTravelPx
                                onAnswer()
                            }
                            rawOffset <= -maxTravelPx * 0.7f -> {
                                rawOffset = -maxTravelPx
                                onDecline()
                            }
                            else -> rawOffset = 0f
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (rawOffset < -maxTravelPx * 0.3f) Icons.Rounded.CallEnd else Icons.Rounded.Call,
                contentDescription = "Swipe right to answer or left to decline",
                tint = ObsidianBg,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun InCallToggleAction(
    icon: ImageVector,
    label: String,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(if (active) accent.copy(alpha = 0.25f) else CyberSurfaceElevated)
                .border(1.dp, if (active) accent else CyberBorder, CircleShape)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) accent else TextPrimary
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (active) accent else TextSecondary)
    }
}

@Composable
private fun InCallMiniKeypad(onDigit: (Char) -> Unit) {
    val rows = listOf(listOf('1', '2', '3'), listOf('4', '5', '6'), listOf('7', '8', '9'), listOf('*', '0', '#'))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { ch ->
                    Surface(
                        color = CyberSurfaceElevated,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(54.dp)
                            .border(1.dp, CyberBorder, CircleShape)
                            .clickable { onDigit(ch) }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(ch.toString(), fontFamily = JetBrainsMonoFamily, fontSize = 18.sp, color = NeonCyan)
                        }
                    }
                }
            }
        }
    }
}
