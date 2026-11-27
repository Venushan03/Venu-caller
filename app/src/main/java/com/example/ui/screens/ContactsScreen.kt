package com.example.ui.screens

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import android.telecom.TelecomManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.CallDirection
import com.example.model.CallLogEntry
import com.example.model.CallerCategory
import com.example.model.CallerTag
import com.example.model.DeviceContact
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
import java.text.DateFormat
import java.util.Date

@Composable
fun ContactsScreen(
    viewModel: PrismCallViewModel,
    onOpenNoteForNumber: (String, String?) -> Unit
) {
    val context = LocalContext.current
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val callLogs by viewModel.callLogs.collectAsStateWithLifecycle()
    val tagsState by viewModel.callerTagsState.collectAsStateWithLifecycle()
    val hasContactsPerm by viewModel.hasContactsPerm.collectAsStateWithLifecycle()
    val hasCallLogPerm by viewModel.hasCallLogPerm.collectAsStateWithLifecycle()

    var selectedSegment by remember { mutableIntStateOf(0) } // 0 = Contacts, 1 = Call Log, 2 = Caller Tags & Blocked
    var searchQuery by remember { mutableStateOf("") }
    var qrTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var tagTargetNumber by remember { mutableStateOf<Pair<String, String>?>(null) }

    val telecom = remember(context) {
        context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
    }
    var isDefaultDialer by remember {
        mutableStateOf(telecom?.defaultDialerPackage == context.packageName)
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshPermissionsAndData()
    }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isDefaultDialer = telecom?.defaultDialerPackage == context.packageName
    }

    val callerTags = (tagsState as? UiState.Success)?.data.orEmpty()
    val starredContacts = remember(contacts) { contacts.filter { it.starred } }
    val filteredContacts = remember(contacts, searchQuery) {
        if (searchQuery.isBlank()) contacts
        else contacts.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                it.phoneNumber.contains(searchQuery, ignoreCase = true)
        }
    }
    val filteredLogs = remember(callLogs, searchQuery) {
        if (searchQuery.isBlank()) callLogs
        else callLogs.filter {
            (it.cachedName?.contains(searchQuery, ignoreCase = true) == true) ||
                it.number.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Default Dialer & Permissions Banner
            if (!isDefaultDialer || !hasContactsPerm || !hasCallLogPerm) {
                Surface(
                    color = CyberSurfaceElevated,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, NeonCyan.copy(0.45f), RoundedCornerShape(18.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Dialer & Contacts Access", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!hasContactsPerm || !hasCallLogPerm) {
                                Button(
                                    onClick = {
                                        val perms = mutableListOf(
                                            Manifest.permission.READ_CONTACTS,
                                            Manifest.permission.READ_CALL_LOG,
                                            Manifest.permission.CALL_PHONE
                                        )
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            perms.add(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                        permissionsLauncher.launch(perms.toTypedArray())
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = ObsidianBg),
                                    modifier = Modifier.testTag("grant_contacts_perm_button")
                                ) {
                                    Text("Grant Access", fontWeight = FontWeight.Bold)
                                }
                            }
                            if (!isDefaultDialer && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                OutlinedButton(
                                    onClick = {
                                        val roleMgr = context.getSystemService(RoleManager::class.java)
                                        if (roleMgr != null && roleMgr.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                                            roleLauncher.launch(roleMgr.createRequestRoleIntent(RoleManager.ROLE_DIALER))
                                        }
                                    },
                                    modifier = Modifier.testTag("set_default_dialer_button")
                                ) {
                                    Icon(Icons.Rounded.PhoneAndroid, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Set Default Dialer")
                                }
                            }
                        }
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search contacts, call history, or tags...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "Search", tint = NeonCyan) },
                trailingIcon = {
                    IconButton(onClick = { viewModel.openAddToDeviceContacts("") }) {
                        Icon(Icons.Rounded.PersonAdd, contentDescription = "Add Contact", tint = VoltLime)
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("contacts_search_input")
            )

            // Segment Chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Contacts (${contacts.size})", "Recents (${callLogs.size})", "Caller Tags (${callerTags.size})").forEachIndexed { idx, label ->
                    FilterChip(
                        selected = selectedSegment == idx,
                        onClick = { selectedSegment = idx },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonCyan.copy(alpha = 0.22f),
                            selectedLabelColor = NeonCyan
                        )
                    )
                }
            }

            // Starred Speed-Dial Orbit
            if (selectedSegment == 0 && starredContacts.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(starredContacts, key = { "star_${it.id}" }) { contact ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { viewModel.placeCall(contact.phoneNumber) }
                                .padding(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(NeonAmber.copy(alpha = 0.2f))
                                    .border(1.5.dp, NeonAmber, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = contact.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = NeonAmber
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = contact.name.take(10),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                when (selectedSegment) {
                    0 -> {
                        if (filteredContacts.isEmpty()) {
                            item {
                                EmptyHubCard(
                                    icon = Icons.Rounded.Contacts,
                                    title = if (hasContactsPerm) "No matching contacts" else "Contacts permission needed",
                                    subtitle = "Tap Grant Access above or add a new contact using the + button."
                                )
                            }
                        } else {
                            items(filteredContacts, key = { it.id }) { contact ->
                                val tag = callerTags.firstOrNull { it.normalizedNumber == contact.normalizedNumber }
                                ContactRowCard(
                                    contact = contact,
                                    tag = tag,
                                    onCall = { viewModel.placeCall(contact.phoneNumber) },
                                    onAddNote = { onOpenNoteForNumber(contact.phoneNumber, contact.name) },
                                    onQr = { qrTarget = contact.name to contact.phoneNumber },
                                    onTag = { tagTargetNumber = contact.phoneNumber to contact.name }
                                )
                            }
                        }
                    }
                    1 -> {
                        if (filteredLogs.isEmpty()) {
                            item {
                                EmptyHubCard(
                                    icon = Icons.Rounded.Call,
                                    title = if (hasCallLogPerm) "No recent calls" else "Call Log permission needed",
                                    subtitle = "Recent incoming, outgoing, and missed calls will appear here."
                                )
                            }
                        } else {
                            items(filteredLogs, key = { it.id }) { log ->
                                val tag = callerTags.firstOrNull { it.normalizedNumber == log.normalizedNumber }
                                CallLogRowCard(
                                    entry = log,
                                    tag = tag,
                                    onCall = { viewModel.placeCall(log.number) },
                                    onIdentifyOrTag = {
                                        tagTargetNumber = log.number to (tag?.displayName ?: log.cachedName ?: "Unknown Caller")
                                    },
                                    onSimulateIncoming = {
                                        viewModel.triggerSimulatedIncomingCall(context, log.number, log.cachedName)
                                    }
                                )
                            }
                        }
                    }
                    else -> {
                        item {
                            OutlinedButton(
                                onClick = { tagTargetNumber = "+1 800-555-0199" to "Custom Tag" },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.Shield, contentDescription = null, tint = NeonMagenta)
                                Spacer(Modifier.width(8.dp))
                                Text("Add Custom Caller ID Tag / Block Number")
                            }
                        }
                        items(callerTags, key = { it.id }) { tag ->
                            CallerTagCard(
                                tag = tag,
                                onCall = { viewModel.placeCall(tag.phoneNumber) },
                                onEdit = { tagTargetNumber = tag.phoneNumber to tag.displayName },
                                onDelete = { viewModel.deleteCallerTag(tag.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    qrTarget?.let { (name, number) ->
        QrContactDialog(name = name, number = number, onDismiss = { qrTarget = null })
    }

    tagTargetNumber?.let { (number, defaultName) ->
        CallerTagEditorDialog(
            phoneNumber = number,
            initialName = defaultName,
            onDismiss = { tagTargetNumber = null },
            onSave = { name, category, blocked ->
                viewModel.saveCallerTag(number, name, category, blocked)
                tagTargetNumber = null
            }
        )
    }
}

@Composable
private fun ContactRowCard(
    contact: DeviceContact,
    tag: CallerTag?,
    onCall: () -> Unit,
    onAddNote: () -> Unit,
    onQr: () -> Unit,
    onTag: () -> Unit
) {
    val accent = remember(contact.id) { DialKeyColors[kotlin.math.abs(contact.id.hashCode()) % DialKeyColors.size] }
    Surface(
        color = CyberSurface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onCall)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.2f))
                    .border(1.dp, accent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    contact.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    style = MaterialTheme.typography.titleLarge,
                    color = accent
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        contact.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (contact.starred) {
                        Icon(Icons.Rounded.Star, contentDescription = "Favorite", tint = NeonAmber, modifier = Modifier.size(14.dp))
                    }
                    tag?.let {
                        Text(
                            it.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = ObsidianBg,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(VoltLime)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(contact.phoneNumber, fontFamily = JetBrainsMonoFamily, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            IconButton(onClick = onAddNote) {
                Icon(Icons.Rounded.EditNote, contentDescription = "Add Note", tint = VoltLime)
            }
            IconButton(onClick = onQr) {
                Icon(Icons.Rounded.QrCode2, contentDescription = "QR Contact Card", tint = NeonCyan)
            }
            IconButton(onClick = onTag) {
                Icon(Icons.Rounded.Shield, contentDescription = "Caller Tag", tint = NeonMagenta)
            }
            IconButton(onClick = onCall) {
                Icon(Icons.Rounded.Call, contentDescription = "Call", tint = NeonEmerald)
            }
        }
    }
}

@Composable
private fun CallLogRowCard(
    entry: CallLogEntry,
    tag: CallerTag?,
    onCall: () -> Unit,
    onIdentifyOrTag: () -> Unit,
    onSimulateIncoming: () -> Unit
) {
    val (dirIcon, dirColor) = when (entry.direction) {
        CallDirection.OUTGOING -> Icons.AutoMirrored.Rounded.CallMade to NeonCyan
        CallDirection.MISSED, CallDirection.REJECTED -> Icons.AutoMirrored.Rounded.CallMissed to NeonCrimson
        CallDirection.INCOMING -> Icons.AutoMirrored.Rounded.CallReceived to NeonEmerald
    }
    val dateLabel = remember(entry.timestampMillis) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.timestampMillis))
    }

    Surface(
        color = CyberSurface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onCall)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(dirColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(dirIcon, contentDescription = entry.direction.name, tint = dirColor)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tag?.displayName ?: entry.cachedName ?: entry.number,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${entry.number} • $dateLabel • ${PhoneUtils.formatDuration(entry.durationSeconds)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onSimulateIncoming) {
                Icon(Icons.Rounded.PhoneAndroid, contentDescription = "Preview Caller ID", tint = NeonAmber)
            }
            IconButton(onClick = onIdentifyOrTag) {
                Icon(Icons.Rounded.Shield, contentDescription = "Tag or Block", tint = NeonMagenta)
            }
        }
    }
}

@Composable
private fun CallerTagCard(
    tag: CallerTag,
    onCall: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = CyberSurface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (tag.blocked) NeonCrimson.copy(0.6f) else CyberBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onEdit)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (tag.blocked) Icons.Rounded.Block else Icons.Rounded.Shield,
                contentDescription = null,
                tint = if (tag.blocked) NeonCrimson else NeonCyan
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(tag.displayName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(
                    "${tag.phoneNumber} • ${tag.category}" + if (tag.blocked) " • BLOCKED" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (tag.blocked) NeonCrimson else NeonCyan
                )
                tag.aiSummary?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = onCall) { Icon(Icons.Rounded.Call, contentDescription = "Call", tint = NeonEmerald) }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove Tag", tint = NeonCrimson) }
        }
    }
}

@Composable
private fun EmptyHubCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
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
            Icon(icon, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(38.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}

@Composable
private fun QrContactDialog(name: String, number: String, onDismiss: () -> Unit) {
    val payload = "BEGIN:VCARD\nVERSION:3.0\nFN:$name\nTEL:$number\nEND:VCARD"
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberSurface,
        title = { Text("QR Contact Card", color = NeonCyan) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Deterministic visual matrix generated from vCard hash
                Canvas(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(14.dp)
                ) {
                    val grid = 15
                    val cell = size.width / grid
                    val seed = payload.hashCode()
                    for (r in 0 until grid) {
                        for (c in 0 until grid) {
                            val isFinder = (r < 4 && c < 4) || (r < 4 && c >= grid - 4) || (r >= grid - 4 && c < 4)
                            val bit = ((seed ushr ((r * 7 + c * 13) % 31)) xor (r * 31 + c * 17)) and 1 == 1
                            if (isFinder || bit) {
                                drawRect(
                                    color = Color(0xFF06080F),
                                    topLeft = Offset(c * cell, r * cell),
                                    size = Size(cell * 0.92f, cell * 0.92f)
                                )
                            }
                        }
                    }
                }
                Text(name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(number, fontFamily = JetBrainsMonoFamily, color = VoltLime)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = NeonCyan) }
        }
    )
}

@Composable
private fun CallerTagEditorDialog(
    phoneNumber: String,
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String, CallerCategory, Boolean) -> Unit
) {
    var displayName by remember { mutableStateOf(initialName) }
    var selectedCategory by remember { mutableStateOf(CallerCategory.BUSINESS) }
    var blocked by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberSurface,
        title = { Text("Caller ID Tag & Blocker", color = NeonCyan) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Number: $phoneNumber", fontFamily = JetBrainsMonoFamily, color = TextSecondary)
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(CallerCategory.entries) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = {
                                selectedCategory = cat
                                if (cat == CallerCategory.SPAM) blocked = true
                            },
                            label = { Text(cat.label) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Block incoming calls", color = TextPrimary)
                    Switch(checked = blocked, onCheckedChange = { blocked = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (displayName.isNotBlank()) onSave(displayName, selectedCategory, blocked)
                }
            ) { Text("Save Tag") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
