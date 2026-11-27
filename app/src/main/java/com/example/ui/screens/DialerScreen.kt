package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.CallerCategory
import com.example.model.CallerLookupResult
import com.example.model.CallerTag
import com.example.model.DeviceContact
import com.example.model.NumberNote
import com.example.model.PhoneUtils
import com.example.model.UiState
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceElevated
import com.example.ui.theme.DialKeyColors
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCrimson
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoltLime
import com.example.viewmodel.PrismCallViewModel

private data class DialKeySpec(
    val digit: String,
    val letters: String,
    val color: Color
)

private val PrismKeys = listOf(
    DialKeySpec("1", "∞", DialKeyColors[0]),
    DialKeySpec("2", "ABC", DialKeyColors[1]),
    DialKeySpec("3", "DEF", DialKeyColors[2]),
    DialKeySpec("4", "GHI", DialKeyColors[3]),
    DialKeySpec("5", "JKL", DialKeyColors[4]),
    DialKeySpec("6", "MNO", DialKeyColors[5]),
    DialKeySpec("7", "PQRS", DialKeyColors[6]),
    DialKeySpec("8", "TUV", DialKeyColors[7]),
    DialKeySpec("9", "WXYZ", DialKeyColors[8]),
    DialKeySpec("*", "FX", DialKeyColors[9]),
    DialKeySpec("0", "+", DialKeyColors[10]),
    DialKeySpec("#", "NOTE", DialKeyColors[11])
)

@Composable
fun DialerScreen(
    viewModel: PrismCallViewModel,
    onNavigateTab: (String) -> Unit,
    onOpenQuickNote: (String, String?) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val dialedNumber by viewModel.dialedNumber.collectAsStateWithLifecycle()
    val matchingContacts by viewModel.matchingContacts.collectAsStateWithLifecycle()
    val notesState by viewModel.notesState.collectAsStateWithLifecycle()
    val tagsState by viewModel.callerTagsState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val geminiState by viewModel.geminiCommandState.collectAsStateWithLifecycle()
    val lookupState by viewModel.callerLookupState.collectAsStateWithLifecycle()
    val activeVoicePreset by viewModel.voicePreset.collectAsStateWithLifecycle()

    var geminiPrompt by remember { mutableStateOf("") }

    val notes = (notesState as? UiState.Success)?.data.orEmpty()
    val callerTags = (tagsState as? UiState.Success)?.data.orEmpty()
    val matchedTag: CallerTag? = remember(dialedNumber, callerTags) {
        val norm = PhoneUtils.normalizeNumber(dialedNumber)
        if (norm.isBlank()) null else callerTags.firstOrNull { it.normalizedNumber == norm }
    }
    val matchedNotes: List<NumberNote> = remember(dialedNumber, notes) {
        val norm = PhoneUtils.normalizeNumber(dialedNumber)
        if (norm.isBlank()) emptyList()
        else notes.filter { it.normalizedNumber.contains(norm) || it.phoneNumber.contains(dialedNumber) }
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
                .widthIn(max = 520.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Built-in Gemini Quick-Access Command Bar
            Surface(
                color = CyberSurface,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        Brush.horizontalGradient(listOf(NeonCyan.copy(0.55f), NeonMagenta.copy(0.55f))),
                        RoundedCornerShape(20.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = geminiPrompt,
                            onValueChange = { geminiPrompt = it },
                            placeholder = {
                                Text(
                                    stringResource(R.string.gemini_prompt_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = "Gemini", tint = NeonCyan)
                            },
                            trailingIcon = {
                                if (geminiState is UiState.Loading) {
                                    CircularProgressIndicator(
                                        color = NeonCyan,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else if (geminiPrompt.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            val q = geminiPrompt
                                            geminiPrompt = ""
                                            viewModel.runGeminiCommand(q, onNavigateTab, onOpenQuickNote)
                                        },
                                        modifier = Modifier.testTag("gemini_send_button")
                                    ) {
                                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Run Gemini Command", tint = VoltLime)
                                    }
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = CyberBorder,
                                focusedContainerColor = CyberSurfaceElevated,
                                unfocusedContainerColor = CyberSurfaceElevated
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gemini_command_input")
                        )
                    }

                    // Quick-access Gemini suggestion chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        val quickPrompts = listOf(
                            "Identify +18005550199" to NeonCyan,
                            "Open Baby Voice FX" to NeonMagenta,
                            "Find my gate note" to VoltLime
                        )
                        items(quickPrompts) { (promptText, tint) ->
                            AssistChip(
                                onClick = {
                                    viewModel.runGeminiCommand(promptText, onNavigateTab, onOpenQuickNote)
                                },
                                label = { Text(promptText, fontSize = 11.sp, color = TextPrimary) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = tint.copy(alpha = 0.12f)
                                ),
                                border = AssistChipDefaults.assistChipBorder(
                                    enabled = true,
                                    borderColor = tint.copy(alpha = 0.45f)
                                )
                            )
                        }
                    }

                    // Gemini command response banner
                    when (val gs = geminiState) {
                        is UiState.Success -> gs.data?.let { action ->
                            Surface(
                                color = CyberSurfaceElevated,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = action.assistantMessage,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = NeonCyan,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { viewModel.clearGeminiCommand() }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Dismiss", tint = TextMuted, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                        is UiState.Error -> {
                            Text(gs.message, style = MaterialTheme.typography.bodyMedium, color = NeonCrimson)
                        }
                        else -> Unit
                    }
                }
            }

            // 2. Live T9 Contacts & Notes Match Strip
            AnimatedVisibility(visible = matchingContacts.isNotEmpty() || matchedNotes.isNotEmpty() || matchedTag != null) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    matchedTag?.let { tag ->
                        item(key = "tag_${tag.id}") {
                            Surface(
                                color = NeonViolet.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .border(1.dp, NeonViolet, RoundedCornerShape(14.dp))
                                    .clickable { viewModel.setDialedNumber(tag.phoneNumber) }
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text(tag.displayName, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                                    Text("${tag.category} Tag • ${tag.phoneNumber}", style = MaterialTheme.typography.labelSmall, color = NeonViolet)
                                }
                            }
                        }
                    }
                    items(matchingContacts, key = { it.id }) { contact ->
                        Surface(
                            color = CyberSurfaceElevated,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .border(1.dp, NeonCyan.copy(0.45f), RoundedCornerShape(14.dp))
                                .clickable { viewModel.setDialedNumber(contact.phoneNumber) }
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(contact.name, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                                Text(contact.phoneNumber, style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                            }
                        }
                    }
                    items(matchedNotes, key = { it.id }) { note ->
                        Surface(
                            color = VoltLime.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .border(1.dp, VoltLime.copy(0.6f), RoundedCornerShape(14.dp))
                                .clickable { onOpenQuickNote(note.phoneNumber, note.body) }
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text("Note: ${note.title}", style = MaterialTheme.typography.labelLarge, color = VoltLime)
                                Text(note.body.take(36), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            }
                        }
                    }
                }
            }

            // 3. Unknown Caller Intelligence Result Card
            when (val ls = lookupState) {
                is UiState.Loading -> {
                    Surface(
                        color = CyberSurfaceElevated,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(color = NeonCyan, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            Text("Gemini identifying number pattern...", style = MaterialTheme.typography.bodyMedium, color = NeonCyan)
                        }
                    }
                }
                is UiState.Success -> ls.data?.let { lookup ->
                    CallerLookupBanner(
                        lookup = lookup,
                        onSaveTag = {
                            viewModel.saveCallerTag(
                                phoneNumber = lookup.phoneNumber,
                                displayName = lookup.suggestedLabel,
                                category = lookup.category,
                                blocked = lookup.category == CallerCategory.SPAM,
                                aiSummary = lookup.summary
                            )
                        },
                        onDismiss = { viewModel.clearCallerLookup() }
                    )
                }
                is UiState.Error -> {
                    Text(ls.message, style = MaterialTheme.typography.bodyMedium, color = NeonCrimson)
                }
            }

            // 4. Number Display & Quick Actions
            Surface(
                color = CyberSurface,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyberBorder, RoundedCornerShape(22.dp))
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = dialedNumber.ifBlank { "Enter Number" },
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = if (dialedNumber.length > 12) 22.sp else 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (dialedNumber.isBlank()) TextMuted else TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dialed_number_display")
                        )

                        if (dialedNumber.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.backspaceDigit() },
                                modifier = Modifier.testTag("dial_backspace_button")
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.Backspace,
                                    contentDescription = "Backspace",
                                    tint = NeonMagenta
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SmallActionPill(
                            icon = Icons.Rounded.PersonSearch,
                            label = "Identify",
                            tint = NeonCyan,
                            modifier = Modifier.weight(1f).testTag("dial_identify_button"),
                            onClick = { viewModel.lookupUnknownNumber(dialedNumber) }
                        )
                        SmallActionPill(
                            icon = Icons.Rounded.EditNote,
                            label = "Add Note",
                            tint = VoltLime,
                            modifier = Modifier.weight(1f).testTag("dial_add_note_button"),
                            onClick = {
                                val target = dialedNumber.ifBlank { "+1 555-0199" }
                                onOpenQuickNote(target, null)
                            }
                        )
                        SmallActionPill(
                            icon = Icons.Rounded.GraphicEq,
                            label = activeVoicePreset.displayName,
                            tint = NeonMagenta,
                            modifier = Modifier.weight(1f).testTag("dial_voice_fx_button"),
                            onClick = { onNavigateTab("studio") }
                        )
                    }
                }
            }

            // 5. 12-Key Prismatic Neon Keypad
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PrismKeys.chunked(3).forEach { rowKeys ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowKeys.forEach { key ->
                            PrismaticDialKey(
                                spec = key,
                                modifier = Modifier.weight(1f),
                                onTap = {
                                    if (settings.hapticDialKeys) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    when (key.digit) {
                                        "#" -> if (dialedNumber.isBlank()) {
                                            onOpenQuickNote("+1 555-0142", null)
                                        } else {
                                            viewModel.appendDigit("#")
                                        }
                                        else -> viewModel.appendDigit(key.digit)
                                    }
                                },
                                onLongPress = {
                                    if (settings.hapticDialKeys) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                    when (key.digit) {
                                        "0" -> viewModel.appendDigit("+")
                                        "1" -> if (settings.speedDialSlot1.isNotBlank()) viewModel.placeCall(settings.speedDialSlot1)
                                        "2" -> if (settings.speedDialSlot2.isNotBlank()) viewModel.placeCall(settings.speedDialSlot2)
                                        "3" -> if (settings.speedDialSlot3.isNotBlank()) viewModel.placeCall(settings.speedDialSlot3)
                                        "*" -> onNavigateTab("studio")
                                        "#" -> onOpenQuickNote(dialedNumber.ifBlank { "+1 555-0142" }, null)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // 6. Call & Incoming Call Preview Deck
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        val previewNum = dialedNumber.ifBlank { "+1 800-555-0199" }
                        viewModel.triggerSimulatedIncomingCall(
                            context = context,
                            phoneNumber = previewNum,
                            verificationBadge = "Verified Network ID"
                        )
                    },
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberSurfaceElevated,
                        contentColor = NeonAmber
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonAmber.copy(0.6f)),
                    modifier = Modifier
                        .height(58.dp)
                        .testTag("test_incoming_call_button")
                ) {
                    Icon(Icons.Rounded.NotificationsActive, contentDescription = "Preview Incoming Call", tint = NeonAmber)
                    Spacer(Modifier.width(6.dp))
                    Text("Test Ring", fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = { viewModel.placeCall() },
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonEmerald,
                        contentColor = ObsidianBg
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(58.dp)
                        .testTag("place_call_button")
                ) {
                    Icon(Icons.Rounded.Call, contentDescription = "Call", tint = ObsidianBg)
                    Spacer(Modifier.width(8.dp))
                    Text("Call Now", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PrismaticDialKey(
    spec: DialKeySpec,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 600f),
        label = "keyScale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .height(66.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        spec.color.copy(alpha = if (pressed) 0.34f else 0.16f),
                        CyberSurface
                    )
                )
            )
            .border(
                width = if (pressed) 2.dp else 1.2.dp,
                color = spec.color.copy(alpha = if (pressed) 0.95f else 0.55f),
                shape = RoundedCornerShape(20.dp)
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(color = spec.color),
                onClick = onTap,
                onLongClick = onLongPress
            )
            .testTag("dial_key_${spec.digit}"),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = spec.digit,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = spec.color
            )
            Text(
                text = spec.letters,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun SmallActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.12f))
            .border(1.dp, tint.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CallerLookupBanner(
    lookup: CallerLookupResult,
    onSaveTag: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        color = CyberSurfaceElevated,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NeonCyan.copy(0.6f), RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${lookup.suggestedLabel} • ${lookup.numberType}",
                    style = MaterialTheme.typography.titleMedium,
                    color = NeonCyan,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close lookup", tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }
            Text(
                text = "Region: ${lookup.regionHint} • Spam Risk: ${lookup.spamRisk}",
                style = MaterialTheme.typography.labelSmall,
                color = VoltLime
            )
            Text(lookup.summary, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = onSaveTag,
                    label = { Text("Save Caller Tag") }
                )
            }
        }
    }
}
