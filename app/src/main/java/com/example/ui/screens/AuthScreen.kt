package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.onGoogleSignInClicked
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceElevated
import com.example.ui.theme.NeonCrimson
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoltLime

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    onSkip: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    BackHandler(enabled = true) {
        onSkip()
    }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val transition = rememberInfiniteTransition(label = "authOrb")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "authPulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A1026), ObsidianBg, Color(0xFF160921))
                )
            )
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(144.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(
                        color = NeonCyan.copy(alpha = (1f - pulse) * 0.45f),
                        radius = size.minDimension * (0.34f + pulse * 0.14f),
                        center = center,
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                    drawCircle(
                        brush = Brush.linearGradient(listOf(NeonCyan, NeonMagenta, VoltLime)),
                        radius = size.minDimension * 0.34f,
                        center = center,
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(CyberSurfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Call,
                        contentDescription = "PrismCall Emblem",
                        tint = NeonCyan,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.auth_headline),
                    style = MaterialTheme.typography.displayLarge,
                    color = TextPrimary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.auth_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }

            Surface(
                color = CyberSurface.copy(alpha = 0.9f),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyberBorder, RoundedCornerShape(22.dp))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FeatureRow(Icons.Rounded.AutoAwesome, NeonCyan, "Built-in Gemini Quick Actions & Caller Lookup")
                    FeatureRow(Icons.Rounded.GraphicEq, NeonMagenta, "Real-Time Voice FX Studio (Male, Female, Old, Child, Baby)")
                    FeatureRow(Icons.Rounded.Shield, VoltLime, "Cloud-Synced Call Notepad & Unknown Caller Tags")
                }
            }

            errorMessage?.let { msg ->
                Surface(
                    color = NeonCrimson.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, NeonCrimson.copy(0.5f), RoundedCornerShape(14.dp))
                ) {
                    Text(
                        text = msg,
                        color = NeonCrimson,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Button(
                onClick = {
                    isLoading = true
                    errorMessage = null
                    onGoogleSignInClicked(
                        context = context,
                        credentialManager = credentialManager,
                        onAuthSuccess = {
                            isLoading = false
                            onAuthSuccess()
                        },
                        onAuthError = { err ->
                            isLoading = false
                            errorMessage = err
                        },
                        onAuthCancelled = {
                            isLoading = false
                        },
                        scope = scope
                    )
                },
                enabled = !isLoading,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonCyan,
                    contentColor = ObsidianBg
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("google_sign_in_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = ObsidianBg,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.sign_in_google),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            TextButton(
                onClick = onSkip,
                enabled = !isLoading,
                modifier = Modifier.testTag("skip_sign_in_button")
            ) {
                Text(
                    text = "Continue without signing in",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun FeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
    }
}
