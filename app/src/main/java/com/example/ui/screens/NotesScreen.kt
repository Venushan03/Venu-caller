package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.NumberNote
import com.example.model.UiState
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceElevated
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCrimson
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoltLime
import com.example.viewmodel.PrismCallViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    viewModel: PrismCallViewModel,
    prefillNoteRequest: Pair<String, String?>?,
    onConsumePrefill: () -> Unit
) {
    val notesState by viewModel.notesState.collectAsStateWithLifecycle()
    val polishState by viewModel.notePolishState.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var activeFilterTag by remember { mutableStateOf("All") }
    var showEditor by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<NumberNote?>(null) }

    var draftPhone by remember { mutableStateOf("") }
    var draftName by remember { mutableStateOf("") }
    var draftTitle by remember { mutableStateOf("") }
    var draftBody by remember { mutableStateOf("") }
    var draftTag by remember { mutableStateOf("Call") }
    var draftPinned by remember { mutableStateOf(true) }

    LaunchedEffect(prefillNoteRequest) {
        if (prefillNoteRequest != null) {
            editingNote = null
            draftPhone = prefillNoteRequest.first
            draftName = ""
            draftTitle = "Number Note"
            draftBody = prefillNoteRequest.second ?: ""
            draftTag = "Callback"
            draftPinned = true
            showEditor = true
            onConsumePrefill()
        }
    }

    val allNotes = (notesState as? UiState.Success)?.data.orEmpty()
    val filteredNotes = remember(allNotes, searchQuery, activeFilterTag) {
        allNotes.filter { note ->
            val matchesSearch = searchQuery.isBlank() ||
                note.title.contains(searchQuery, ignoreCase = true) ||
                note.body.contains(searchQuery, ignoreCase = true) ||
                note.phoneNumber.contains(searchQuery, ignoreCase = true) ||
                (note.contactName?.contains(searchQuery, ignoreCase = true) == true)
            val matchesTag = activeFilterTag == "All" ||
                (activeFilterTag == "Pinned" && note.pinned) ||
                note.tags.any { it.equals(activeFilterTag, ignoreCase = true) }
            matchesSearch && matchesTag
        }
    }

    Scaffold(
        containerColor = ObsidianBg,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingNote = null
                    draftPhone = ""
                    draftName = ""
                    draftTitle = ""
                    draftBody = ""
                    draftTag = "Call"
                    draftPinned = false
                    showEditor = true
                },
                containerColor = VoltLime,
                contentColor = ObsidianBg,
                modifier = Modifier.testTag("add_note_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "New Number Note")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search number notes, gate codes, callbacks...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "Search notes", tint = VoltLime) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("notes_search_input")
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val filters = listOf("All", "Pinned", "Call", "Callback", "Delivery", "VIP", "Work")
                    items(filters) { chip ->
                        FilterChip(
                            selected = activeFilterTag == chip,
                            onClick = { activeFilterTag = chip },
                            label = { Text(chip) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VoltLime.copy(alpha = 0.2f),
                                selectedLabelColor = VoltLime
                            )
                        )
                    }
                }

                when (val ns = notesState) {
                    is UiState.Loading -> {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = VoltLime)
                        }
                    }
                    is UiState.Error -> {
                        Surface(
                            color = NeonCrimson.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(ns.message, color = NeonCrimson, modifier = Modifier.padding(14.dp))
                        }
                    }
                    is UiState.Success -> {
                        if (filteredNotes.isEmpty()) {
                            Surface(
                                color = CyberSurface,
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, CyberBorder, RoundedCornerShape(20.dp))
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Rounded.EditNote, contentDescription = null, tint = VoltLime, modifier = Modifier.size(40.dp))
                                    Text("No Number Notes Yet", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                    Text(
                                        "Tap + to pin gate codes, delivery info, or callback reminders to any phone number.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 80.dp)
                            ) {
                                items(filteredNotes, key = { it.id }, contentType = { "number_note" }) { note ->
                                    NumberNoteCard(
                                        note = note,
                                        onEdit = {
                                            editingNote = note
                                            draftPhone = note.phoneNumber
                                            draftName = note.contactName ?: ""
                                            draftTitle = note.title
                                            draftBody = note.body
                                            draftTag = note.tags.firstOrNull() ?: "Call"
                                            draftPinned = note.pinned
                                            showEditor = true
                                        },
                                        onTogglePin = { viewModel.togglePinNote(note) },
                                        onCall = { viewModel.placeCall(note.phoneNumber) },
                                        onDelete = { viewModel.deleteNote(note.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        ModalBottomSheet(
            onDismissRequest = { showEditor = false },
            containerColor = CyberSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (editingNote == null) "New Number Note" else "Edit Number Note",
                    style = MaterialTheme.typography.titleLarge,
                    color = VoltLime
                )
                OutlinedTextField(
                    value = draftPhone,
                    onValueChange = { draftPhone = it },
                    label = { Text("Phone Number *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("note_phone_input")
                )
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it },
                    label = { Text("Contact / Caller Name (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = draftTitle,
                    onValueChange = { draftTitle = it },
                    label = { Text("Note Title *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("note_title_input")
                )
                OutlinedTextField(
                    value = draftBody,
                    onValueChange = { draftBody = it },
                    label = { Text("Details (reminders, address, OTP/gate code...)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("note_body_input")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Call", "Callback", "Delivery", "VIP", "Work").forEach { tag ->
                        FilterChip(
                            selected = draftTag == tag,
                            onClick = { draftTag = tag },
                            label = { Text(tag) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Pin note to top & incoming Caller ID", color = TextPrimary)
                    Switch(checked = draftPinned, onCheckedChange = { draftPinned = it })
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            viewModel.polishNoteWithGemini(
                                rawNote = draftBody,
                                callerLabel = draftName.ifBlank { draftPhone.ifBlank { "Caller" } },
                                onPolished = { polished -> draftBody = polished }
                            )
                        },
                        enabled = polishState !is UiState.Loading,
                        modifier = Modifier.weight(1f).testTag("gemini_polish_note_button")
                    ) {
                        if (polishState is UiState.Loading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Gemini Polish")
                        }
                    }

                    Button(
                        onClick = {
                            viewModel.saveNote(
                                existingId = editingNote?.id,
                                phoneNumber = draftPhone,
                                contactName = draftName,
                                title = draftTitle,
                                body = draftBody,
                                tags = listOf(draftTag),
                                pinned = draftPinned
                            )
                            showEditor = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VoltLime, contentColor = ObsidianBg),
                        modifier = Modifier.weight(1f).testTag("save_note_button")
                    ) {
                        Text("Save Note", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun NumberNoteCard(
    note: NumberNote,
    onEdit: () -> Unit,
    onTogglePin: () -> Unit,
    onCall: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = CyberSurface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (note.pinned) VoltLime.copy(0.6f) else CyberBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onEdit)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(note.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(
                        text = listOfNotNull(note.contactName, note.phoneNumber).joinToString(" • "),
                        fontFamily = JetBrainsMonoFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyan
                    )
                }
                Row {
                    IconButton(onClick = onTogglePin) {
                        Icon(
                            Icons.Rounded.PushPin,
                            contentDescription = "Pin Note",
                            tint = if (note.pinned) VoltLime else TextSecondary
                        )
                    }
                    IconButton(onClick = onCall) {
                        Icon(Icons.Rounded.Call, contentDescription = "Call Number", tint = NeonEmerald)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete Note", tint = NeonCrimson)
                    }
                }
            }
            if (note.body.isNotBlank()) {
                Text(
                    text = note.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (note.tags.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    note.tags.forEach { tag ->
                        Text(
                            text = "#$tag",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonMagenta,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(CyberSurfaceElevated)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}
