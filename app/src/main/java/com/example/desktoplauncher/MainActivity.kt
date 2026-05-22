package io.github.desktopmodelauncher

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

    override fun onResume() {
        super.onResume()
        hideSystemUi()
    }

    private fun hideSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }
}

data class LaunchableApp(val label: String, val packageName: String)

@Composable
fun DesktopLauncherScreen(packageManager: PackageManager) {
    val context = LocalContext.current
    val apps = remember {
        packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filter { packageManager.getLaunchIntentForPackage(it.packageName) != null }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
            .map { LaunchableApp(it.loadLabel(packageManager).toString(), it.packageName) }
            .toList()
    }

    val activeApps = remember { mutableStateListOf<LaunchableApp>() }
    var appMenuOpen by remember { mutableStateOf(false) }
    var desktopMenuOpen by remember { mutableStateOf(false) }
    var wallpaperMode by remember { mutableStateOf("Aurora") }
    var desktopColor by remember { mutableStateOf(Color(0xFF0F172A)) }

    LaunchedEffect(Unit) {
        refreshRecentApps(context, apps, activeApps)
    }

    Scaffold(
        bottomBar = {
            Taskbar(
                apps = activeApps,
                onOpenMenu = { appMenuOpen = true },
                onLaunchApp = { launchApp(it.packageName) },
                onBack = { AccessibilityActionService.triggerBack() },
                onHome = { minimizeAllApps(context) }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .combinedClickable(
                    onClick = { },
                    onLongClick = { desktopMenuOpen = true }
                )
                .background(Brush.verticalGradient(listOf(desktopColor, desktopColor.copy(alpha = 0.92f))))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Desktop Mode Launcher", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Long-press desktop for customization.", color = Color(0xFFBFDBFE))
                PermissionPanel(context)
            }

            DropdownMenu(expanded = desktopMenuOpen, onDismissRequest = { desktopMenuOpen = false }) {
                DropdownMenuItem(text = { Text("Wallpaper: Aurora") }, onClick = { wallpaperMode = "Aurora"; desktopColor = Color(0xFF0F172A) })
                DropdownMenuItem(text = { Text("Wallpaper: Dusk") }, onClick = { wallpaperMode = "Dusk"; desktopColor = Color(0xFF312E81) })
                DropdownMenuItem(text = { Text("Pick color: Graphite") }, onClick = { desktopColor = Color(0xFF1F2937) })
                DropdownMenuItem(text = { Text("Pick color: Forest") }, onClick = { desktopColor = Color(0xFF14532D) })
            }

            Text(
                text = "Theme: $wallpaperMode",
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                color = Color.White
            )

            AnimatedVisibility(visible = appMenuOpen) {
                AppMenuDialog(apps = apps, onDismiss = { appMenuOpen = false }, onLaunch = {
                    appMenuOpen = false
                    launchApp(it.packageName)
                    refreshRecentApps(context, apps, activeApps)
                })
            }
        }
    }
}

@Composable
private fun PermissionPanel(context: Context) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp)) {
        PermissionButton("Enable overlay permission") {
            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
        }
        PermissionButton("Enable accessibility service") {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        PermissionButton("Enable usage access") {
            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }
}

@Composable
private fun PermissionButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick) { Text(label) }
}

@Composable
private fun Taskbar(apps: List<LaunchableApp>, onOpenMenu: () -> Unit, onLaunchApp: (LaunchableApp) -> Unit, onBack: () -> Unit, onHome: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().background(Color(0xDD020617)).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onOpenMenu) { Box(modifier = Modifier.size(34.dp).background(Color(0xFF2563EB), CircleShape), contentAlignment = Alignment.Center) { Text("≡", color = Color.White) } }
        IconButton(onClick = onBack) { Box(modifier = Modifier.size(34.dp).background(Color(0xFF334155), CircleShape), contentAlignment = Alignment.Center) { Text("←", color = Color.White) } }
        IconButton(onClick = onHome) { Box(modifier = Modifier.size(34.dp).background(Color(0xFF334155), CircleShape), contentAlignment = Alignment.Center) { Text("⌂", color = Color.White) } }
        apps.forEach { app ->
            Card(modifier = Modifier.combinedClickable(onClick = { onLaunchApp(app) }, onLongClick = { }), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)), shape = RoundedCornerShape(12.dp)) {
                Text(app.label.take(12), color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
            }
        }
    }
}

@Composable
private fun AppMenuDialog(apps: List<LaunchableApp>, onDismiss: () -> Unit, onLaunch: (LaunchableApp) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Apps", color = Color.White)
                Spacer(modifier = Modifier.height(12.dp))
                LazyColumn(modifier = Modifier.height(460.dp)) {
                    items(apps) { app ->
                        Row(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { onLaunch(app) }, onLongClick = { }).padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(app.label, color = Color(0xFFE2E8F0))
                        }
                    }
                }
            }
        }
    }
}

private fun refreshRecentApps(context: Context, allApps: List<LaunchableApp>, target: MutableList<LaunchableApp>) {
    val usageAllowed = (context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager)
        .unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    val recentPackages = if (usageAllowed) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.appTasks.mapNotNull { it.taskInfo.baseIntent.component?.packageName }.distinct()
    } else emptyList()
    target.clear()
    target.addAll(allApps.filter { it.packageName in recentPackages })
}

private fun minimizeAllApps(context: Context) {
    context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun launchApp(packageName: String) {
    val host = appContextHolder ?: return
    val launchIntent = host.packageManager.getLaunchIntentForPackage(packageName) ?: return
    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
    val options = ActivityOptions.makeBasic()
    options.launchBounds = Rect(60, 80, 1200, 1800)
    try {
        host.startActivity(launchIntent, options.toBundle())
    } catch (_: Exception) {
        host.startActivity(launchIntent)
    }
}

private var appContextHolder: MainActivity? = null

@Composable
private fun DesktopLauncherTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    if (context is MainActivity) appContextHolder = context
    MaterialTheme(colorScheme = darkColorScheme(), content = content)
}
