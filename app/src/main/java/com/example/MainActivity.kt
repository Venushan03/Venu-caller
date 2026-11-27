package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.firebase.configureAppCheck
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ContactsScreen
import com.example.ui.screens.DialerScreen
import com.example.ui.screens.NotesScreen
import com.example.ui.screens.VoiceStudioScreen
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceElevated
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PrismCallTheme
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoltLime
import com.example.viewmodel.PrismCallViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Must run before setContent, ViewModels, or any Firebase calls
        configureAppCheck(this, intent)
        enableEdgeToEdge()

        val initialTel = extractTelFromIntent(intent)
        setContent {
            PrismCallTheme {
                AppNavigation(initialDialNumber = initialTel)
            }
        }
    }

    private fun extractTelFromIntent(intent: Intent?): String? {
        val data = intent?.data ?: return null
        return if (data.scheme == "tel") data.schemeSpecificPart else null
    }
}

private data class PrismTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val PrismTabs = listOf(
    PrismTab("dialer", R.string.tab_dialer, Icons.Rounded.Dialpad, Icons.Outlined.Dialpad),
    PrismTab("contacts", R.string.tab_contacts, Icons.Rounded.Contacts, Icons.Outlined.Contacts),
    PrismTab("notes", R.string.tab_notes, Icons.Rounded.EditNote, Icons.Outlined.EditNote),
    PrismTab("studio", R.string.tab_studio, Icons.Rounded.GraphicEq, Icons.Outlined.GraphicEq)
)

@Composable
fun AppNavigation(
    initialDialNumber: String? = null,
    auth: FirebaseAuth = Firebase.auth
) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser
    var showOptionalSignIn by rememberSaveable { mutableStateOf(false) }

    if (showOptionalSignIn && user == null) {
        AuthScreen(
            onAuthSuccess = { showOptionalSignIn = false },
            onSkip = { showOptionalSignIn = false }
        )
    } else {
        key(user?.uid ?: "local") {
            PrismCallHome(
                currentUserId = user?.uid,
                userDisplayName = user?.displayName ?: user?.email ?: "Local Mode",
                isSignedIn = user != null,
                initialDialNumber = initialDialNumber,
                onRequestOptionalSignIn = { showOptionalSignIn = true }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrismCallHome(
    currentUserId: String?,
    userDisplayName: String,
    isSignedIn: Boolean,
    initialDialNumber: String?,
    onRequestOptionalSignIn: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: PrismCallViewModel = viewModel(
        key = currentUserId ?: "local",
        factory = PrismCallViewModel.provideFactory(currentUserId)
    )

    LaunchedEffect(initialDialNumber) {
        if (!initialDialNumber.isNullOrBlank()) {
            viewModel.setDialedNumber(initialDialNumber)
        }
    }

    var currentTab by rememberSaveable { mutableStateOf("dialer") }
    var prefillNoteRequest by remember { mutableStateOf<Pair<String, String?>?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()

    LaunchedEffect(toastMessage) {
        toastMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeToast()
        }
    }

    if (currentTab != "dialer") {
        BackHandler {
            currentTab = "dialer"
        }
    }

    val openQuickNote: (String, String?) -> Unit = { number, draft ->
        prefillNoteRequest = number to draft
        currentTab = "notes"
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(ObsidianBg)) {
        val useRail = maxWidth >= 700.dp

        Scaffold(
            containerColor = ObsidianBg,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = ObsidianBg,
                        titleContentColor = TextPrimary
                    ),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(NeonCyan, NeonMagenta)))
                                    .border(1.dp, VoltLime.copy(0.7f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isSignedIn) {
                                        userDisplayName.firstOrNull()?.uppercaseChar()?.toString() ?: "P"
                                    } else "P",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = ObsidianBg,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (isSignedIn) userDisplayName else "Offline Notes • Gemini Ready",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NeonCyan
                                )
                            }
                        }
                    },
                    actions = {
                        if (isSignedIn) {
                            IconButton(
                                onClick = { signOut(credentialManager, scope) },
                                modifier = Modifier.testTag("sign_out_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Logout,
                                    contentDescription = stringResource(R.string.sign_out),
                                    tint = TextSecondary
                                )
                            }
                        } else {
                            androidx.compose.material3.TextButton(
                                onClick = onRequestOptionalSignIn,
                                modifier = Modifier.testTag("optional_cloud_sync_button")
                            ) {
                                Text("Cloud Sync", color = VoltLime, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = CyberSurface,
                        tonalElevation = 0.dp
                    ) {
                        PrismTabs.forEach { tab ->
                            val selected = currentTab == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab.route },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = ObsidianBg,
                                    selectedTextColor = NeonCyan,
                                    indicatorColor = NeonCyan,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag("nav_tab_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = CyberSurface,
                        modifier = Modifier.fillMaxHeight().border(1.dp, CyberBorder)
                    ) {
                        Spacer(Modifier.weight(0.1f))
                        PrismTabs.forEach { tab ->
                            val selected = currentTab == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab.route },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = ObsidianBg,
                                    selectedTextColor = NeonCyan,
                                    indicatorColor = NeonCyan,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag("nav_rail_${tab.route}")
                            )
                        }
                        Spacer(Modifier.weight(1f))
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    when (currentTab) {
                        "dialer" -> DialerScreen(
                            viewModel = viewModel,
                            onNavigateTab = { currentTab = it },
                            onOpenQuickNote = openQuickNote
                        )
                        "contacts" -> ContactsScreen(
                            viewModel = viewModel,
                            onOpenNoteForNumber = openQuickNote
                        )
                        "notes" -> NotesScreen(
                            viewModel = viewModel,
                            prefillNoteRequest = prefillNoteRequest,
                            onConsumePrefill = { prefillNoteRequest = null }
                        )
                        "studio" -> VoiceStudioScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
