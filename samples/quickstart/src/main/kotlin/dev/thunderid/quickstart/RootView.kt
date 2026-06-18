package dev.thunderid.quickstart

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.thunderid.compose.LocalThunderID

@Composable
fun QuickstartApp(applicationId: String) {
    val thunder = LocalThunderID.current
    when {
        !thunder.isInitialized || thunder.isLoading -> LoadingScreen()
        thunder.error != null -> ErrorScreen(thunder.error!!)
        thunder.isSignedIn -> HomeScreen()
        else -> AuthScreen(applicationId = applicationId)
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("Starting ACME Booking…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ErrorScreen(message: String) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Configuration error: $message\n\nCheck your .env values.",
            textAlign = TextAlign.Center,
        )
    }
}
