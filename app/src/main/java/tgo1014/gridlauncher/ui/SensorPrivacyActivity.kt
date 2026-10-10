package tgo1014.gridlauncher.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tgo1014.gridlauncher.ui.theme.GridLauncherTheme

/** Required permission rationale destination for Android's granular health permissions. */
class SensorPrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GridLauncherTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Your sensor data", style = MaterialTheme.typography.headlineLarge)
                        Text("GridLauncher uses physical activity and heart-rate permissions to show readings from your device’s sensors on the Steps tile while the launcher is open.")
                        Text("Readings stay in memory on this device. GridLauncher does not save them to backups, write them to search, upload them, or share them. It does not read your Health Connect history.")
                        Text("The step counter reports steps since the device restarted. Distance and calories are estimates. Sensor availability depends on your device.")
                        Text("These permissions are optional. You can revoke them in Android Settings → Apps → GridLauncher → Permissions.")
                        TextButton(onClick = { finish() }) { Text("Done") }
                    }
                }
            }
        }
    }
}
