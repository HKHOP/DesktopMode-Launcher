package io.github.desktopmodelauncher

import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DesktopLauncherTheme {
                DesktopLauncherScreen(packageManager)
            }
        }
    }
}

data class LaunchableApp(
    val label: String,
    val packageName: String,
    val icon: android.graphics.drawable.Drawable?
)

@Composable
fun DesktopLauncherScreen(packageManager: PackageManager) {
    val apps = remember {
        packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filter { packageManager.getLaunchIntentForPackage(it.packageName) != null }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
            .map { appInfo ->
                LaunchableApp(
                    label = appInfo.loadLabel(packageManager).toString(),
                    packageName = appInfo.packageName,
                    icon = appInfo.loadIcon(packageManager)
                )
            }
            .toList()
    }

    var appMenuOpen by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            Taskbar(
                apps = apps.take(6),
                onOpenMenu = { appMenuOpen = true },
                onLaunchApp = { launchApp(it.packageName) }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF111827), Color(0xFF1F2937), Color(0xFF0F172A))
                    )
                )
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Desktop Mode Launcher",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Open apps in freeform/multi-window where supported.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFBFDBFE)
                )
            }

            AnimatedVisibility(visible = appMenuOpen) {
                AppMenuDialog(
                    apps = apps,
                    onDismiss = { appMenuOpen = false },
                    onLaunch = {
                        appMenuOpen = false
                        launchApp(it.packageName)
                    }
                )
            }
        }
    }
}

@Composable
private fun Taskbar(
    apps: List<LaunchableApp>,
    onOpenMenu: () -> Unit,
    onLaunchApp: (LaunchableApp) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xCC0B1220))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onOpenMenu) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(Color(0xFF2563EB), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("≡", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        apps.forEach { app ->
            Card(
                modifier = Modifier.clickable { onLaunchApp(app) },
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = app.label.take(10),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun AppMenuDialog(
    apps: List<LaunchableApp>,
    onDismiss: () -> Unit,
    onLaunch: (LaunchableApp) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Apps",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyColumn(modifier = Modifier.height(460.dp)) {
                    items(apps) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onLaunch(app) }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(app.label, color = Color(0xFFE2E8F0))
                            Spacer(modifier = Modifier.weight(1f))
                            Text("Open", color = Color(0xFF60A5FA))
                        }
                    }
                }
            }
        }
    }
}

private fun launchApp(packageName: String) {
    val host = appContextHolder ?: return
    val packageManager = host.packageManager
    val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return
    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    launchIntent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
    launchIntent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)

    val options = ActivityOptions.makeBasic()
    options.launchBounds = Rect(60, 80, 1200, 1800)

    try {
        appContextHolder?.startActivity(launchIntent, options.toBundle())
    } catch (_: Exception) {
        appContextHolder?.startActivity(launchIntent)
    }
}

private var appContextHolder: MainActivity? = null

@Composable
private fun DesktopLauncherTheme(content: @Composable () -> Unit) {
    val darkMode = true
    val colorScheme = if (darkMode) {
        darkColorScheme()
    } else {
        lightColorScheme()
    }
    val context = LocalContext.current
    if (context is MainActivity) {
        appContextHolder = context
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
