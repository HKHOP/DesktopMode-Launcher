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
import android.provider.MediaStore
import android.provider.Settings
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Settings.canDrawOverlays(this)) {
            startService(Intent(this, TaskbarOverlayService::class.java))
        }
        setContent {
            DesktopLauncherTheme {
                DesktopLauncherScreen(packageManager)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        if (Settings.canDrawOverlays(this)) {
            startService(Intent(this, TaskbarOverlayService::class.java))
        }
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

@OptIn(ExperimentalFoundationApi::class)
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
    var wallpaperUri by remember { mutableStateOf<Uri?>(null) }
    var gradientPickerOpen by remember { mutableStateOf(false) }
    var gradientTop by remember { mutableStateOf(Color(0xFF0F172A)) }
    var gradientBottom by remember { mutableStateOf(Color(0xFF1D4ED8)) }

    val pickWallpaperLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        wallpaperUri = uri
    }

    LaunchedEffect(Unit) {
        refreshRecentApps(context, apps, activeApps)
    }

    Scaffold { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    onClick = { },
                    onLongClick = { desktopMenuOpen = true }
                )
        ) {
            if (wallpaperUri != null) {
                AsyncImage(
                    model = wallpaperUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(gradientTop, gradientBottom)))
                )
            }

            DropdownMenu(expanded = desktopMenuOpen, onDismissRequest = { desktopMenuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Pick wallpaper from gallery") },
                    onClick = {
                        desktopMenuOpen = false
                        pickWallpaperLauncher.launch("image/*")
                    }
                )
                DropdownMenuItem(
                    text = { Text("Open gradient color picker") },
                    onClick = {
                        desktopMenuOpen = false
                        gradientPickerOpen = true
                    }
                )
            }

            AnimatedVisibility(visible = appMenuOpen) {
                AppMenuDialog(apps = apps, onDismiss = { appMenuOpen = false }, onLaunch = {
                    appMenuOpen = false
                    launchApp(it.packageName)
                    refreshRecentApps(context, apps, activeApps)
                })
            }

            if (gradientPickerOpen) {
                GradientPickerDialog(
                    onDismiss = { gradientPickerOpen = false },
                    onSelect = { top, bottom ->
                        gradientTop = top
                        gradientBottom = bottom
                        wallpaperUri = null
                        gradientPickerOpen = false
                    }
                )
            }
        }
    }
}

@Composable
private fun GradientPickerDialog(onDismiss: () -> Unit, onSelect: (Color, Color) -> Unit) {
    val presets = listOf(
        Color(0xFF0F172A) to Color(0xFF1D4ED8),
        Color(0xFF312E81) to Color(0xFF7C3AED),
        Color(0xFF14532D) to Color(0xFF22C55E),
        Color(0xFF7F1D1D) to Color(0xFFEF4444)
    )
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Gradient color picker", color = Color.White)
                presets.forEach { pair ->
                    Button(onClick = { onSelect(pair.first, pair.second) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Apply gradient")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
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
