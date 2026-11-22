package com.unicornemulator.app

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.WeakHashMap

enum class UnicornThemeMode(val label: String, val icon: String) {
    LIGHT("Light", "☀"),
    DARK("Dark", "☾"),
    BLACK("Black", "●");

    fun next(): UnicornThemeMode = when (this) {
        LIGHT -> DARK
        DARK -> BLACK
        BLACK -> LIGHT
    }
}

private val UnicornLightColors = lightColorScheme(
    primary = Color(0xFF673AB7),
    onPrimary = Color.White,
    background = Color(0xFFF4F3F8),
    onBackground = Color(0xFF181820),
    surface = Color(0xFFE7E5F0),
    onSurface = Color(0xFF181820)
)

private val UnicornDarkColors = darkColorScheme(
    primary = Color(0xFFBB86FC),
    onPrimary = Color(0xFF140033),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1A1A22),
    onSurface = Color(0xFFE6E1E5)
)

private val UnicornBlackColors = darkColorScheme(
    primary = Color(0xFFBB86FC),
    onPrimary = Color(0xFF140033),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF2F0F7),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFF2F0F7)
)

private fun colorSchemeForMode(mode: UnicornThemeMode): ColorScheme = when (mode) {
    UnicornThemeMode.LIGHT -> UnicornLightColors
    UnicornThemeMode.DARK -> UnicornDarkColors
    UnicornThemeMode.BLACK -> UnicornBlackColors
}

private const val PREFS_NAME = "unicorn_emulator_prefs"
private const val KEY_THEME_MODE = "theme_mode"
private val ALLOWED_GB_EXTENSIONS = setOf("gb", "gbc")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val view = LocalView.current
            var themeMode by remember { mutableStateOf(loadThemeMode(context)) }

            SideEffect {
                val window = this@MainActivity.window
                val isLight = themeMode == UnicornThemeMode.LIGHT
                val statusScrim = when (themeMode) {
                    UnicornThemeMode.LIGHT -> AndroidColor.parseColor("#F4F3F8")
                    UnicornThemeMode.DARK -> AndroidColor.parseColor("#121212")
                    UnicornThemeMode.BLACK -> AndroidColor.BLACK
                }
                val navScrim = when (themeMode) {
                    UnicornThemeMode.LIGHT -> AndroidColor.parseColor("#F4F3F8")
                    UnicornThemeMode.DARK -> AndroidColor.parseColor("#121212")
                    UnicornThemeMode.BLACK -> AndroidColor.BLACK
                }

                enableEdgeToEdge(
                    statusBarStyle = if (isLight) {
                        SystemBarStyle.light(statusScrim, statusScrim)
                    } else {
                        SystemBarStyle.dark(statusScrim)
                    },
                    navigationBarStyle = if (isLight) {
                        SystemBarStyle.light(navScrim, navScrim)
                    } else {
                        SystemBarStyle.dark(navScrim)
                    }
                )

                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = isLight
                controller.isAppearanceLightNavigationBars = isLight
            }

            MaterialTheme(colorScheme = colorSchemeForMode(themeMode)) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    EmulatorScreen(
                        themeMode = themeMode,
                        onCycleTheme = {
                            val nextMode = themeMode.next()
                            themeMode = nextMode
                            saveThemeMode(context, nextMode)
                        }
                    )
                }
            }
        }
    }
}

private fun resolveAppVersionLabel(context: Context): String {
    val rawVersion = try {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        packageInfo.versionName?.trim().orEmpty()
    } catch (_: Exception) {
        ""
    }

    if (rawVersion.isEmpty()) return "v0.1.0"
    return if (rawVersion.startsWith("v", ignoreCase = true)) rawVersion else "v$rawVersion"
}

private fun loadThemeMode(context: Context): UnicornThemeMode {
    val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_THEME_MODE, UnicornThemeMode.DARK.name)
    return runCatching { UnicornThemeMode.valueOf(saved ?: UnicornThemeMode.DARK.name) }
        .getOrDefault(UnicornThemeMode.DARK)
}

private fun saveThemeMode(context: Context, mode: UnicornThemeMode) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_THEME_MODE, mode.name)
        .apply()
}

private fun resolveGambatteCorePath(context: Context): String {
    return "${context.applicationInfo.nativeLibraryDir}/libgambatte_libretro_android.so"
}

private fun resolveDisplayName(context: Context, uri: Uri): String? {
    return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
    }
}

private fun extractGbExtension(fileName: String): String? {
    val ext = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return if (ext in ALLOWED_GB_EXTENSIONS) ext else null
}

private fun sanitizeLibraryFileName(rawName: String, ext: String): String {
    val base = rawName.substringBeforeLast('.', missingDelimiterValue = rawName)
        .replace(Regex("""[^\p{L}\p{N}._ -]"""), "_")
        .trim()
        .ifEmpty { "rom" }
    return "$base.$ext"
}

private fun getLibraryRoms(context: Context): List<File> {
    val romsDir = File(context.filesDir, "roms")
    if (!romsDir.exists() || !romsDir.isDirectory) return emptyList()
    return romsDir.listFiles()
        ?.filter { file ->
            file.isFile &&
                    file.length() > 0 &&
                    file.canRead() &&
                    file.extension.lowercase() in ALLOWED_GB_EXTENSIONS
        }
        ?.sortedByDescending { it.lastModified() }
        ?: emptyList()
}

private fun purgeLibraryRoms(context: Context): Int {
    val romsDir = File(context.filesDir, "roms")
    if (!romsDir.exists() || !romsDir.isDirectory) return 0
    var deletedCount = 0
    romsDir.listFiles()
        ?.filter { file ->
            file.isFile && file.extension.lowercase() in ALLOWED_GB_EXTENSIONS
        }
        ?.forEach { file ->
            if (file.delete()) {
                deletedCount++
            } else {
                Log.w("Unicorn", "Failed to delete ROM during purge: ${file.absolutePath}")
            }
        }
    return deletedCount
}

private fun logSaveFiles(context: Context, reason: String) {
    val savesDir = File(context.filesDir, "saves")
    val files = savesDir.listFiles()?.joinToString { "${it.name} (${it.length()}B)" } ?: "none"
    Log.d("Unicorn", "SRAM check [$reason]: dir=${savesDir.absolutePath}, files=$files")
}

private val tornDownRetroViews: MutableSet<GLRetroView> =
    Collections.newSetFromMap(WeakHashMap())

private fun teardownRetroView(
    context: Context,
    view: GLRetroView?,
    lifecycleOwner: LifecycleOwner,
    reason: String
) {
    if (view == null) return
    synchronized(tornDownRetroViews) {
        if (!tornDownRetroViews.add(view)) {
            Log.d("Unicorn", "teardownRetroView skipped (already torn down): reason=$reason")
            return
        }
    }

    logSaveFiles(context, "before destroy ($reason)")
    try {
        lifecycleOwner.lifecycle.removeObserver(view)
    } catch (e: Exception) {
        Log.w("Unicorn", "removeObserver failed during $reason: ${e.message}")
    }

    try {
        view.onPause()
    } catch (e: Exception) {
        Log.w("Unicorn", "onPause failed during $reason: ${e.message}")
    }

    try {
        view.onDestroy()
    } catch (e: Exception) {
        Log.e("Unicorn", "onDestroy failed during $reason", e)
    }
    logSaveFiles(context, "after destroy ($reason)")
}

private fun dispatchRetroKey(view: GLRetroView, action: Int, keyCode: Int) {
    try {
        view.sendKeyEvent(action, keyCode, 0)
        Log.d("Unicorn", "sendKeyEvent(action=$action, keyCode=$keyCode, port=0)")
    } catch (e: Exception) {
        Log.e("Unicorn", "sendKeyEvent(action, keyCode, 0) failed, falling back to dispatchKeyEvent", e)
        view.dispatchKeyEvent(KeyEvent(action, keyCode))
    }
}

@Composable
fun EmulatorScreen(
    themeMode: UnicornThemeMode,
    onCycleTheme: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    val appVersionLabel = remember(context) { resolveAppVersionLabel(context) }

    var romPath by remember { mutableStateOf<String?>(null) }
    var libraryRoms by remember { mutableStateOf<List<File>>(emptyList()) }
    var statusMessage by remember { mutableStateOf("No GB / GBC ROM loaded yet") }
    val retroViewHolder = remember { arrayOfNulls<GLRetroView>(1) }
    var showEjectDialog by remember { mutableStateOf(false) }
    var showPurgeDialog by remember { mutableStateOf(false) }

    val isBlackMode = themeMode == UnicornThemeMode.BLACK
    val cardColor = when (themeMode) {
        UnicornThemeMode.LIGHT -> Color(0xFFD9D6E6)
        UnicornThemeMode.DARK -> Color(0xFF252532)
        UnicornThemeMode.BLACK -> Color(0xFF0A0A0E)
    }
    val controlSurfaceColor = when (themeMode) {
        UnicornThemeMode.LIGHT -> Color(0xFFE5E2F0)
        UnicornThemeMode.DARK -> Color(0xFF1A1A22)
        UnicornThemeMode.BLACK -> Color(0xFF000000)
    }
    val dialogContainerColor = when (themeMode) {
        UnicornThemeMode.LIGHT -> Color(0xFFF4F3F8)
        UnicornThemeMode.DARK -> Color(0xFF1E1E28)
        UnicornThemeMode.BLACK -> Color(0xFF000000)
    }
    val dialogTextColor = when (themeMode) {
        UnicornThemeMode.LIGHT -> Color(0xFF181820)
        UnicornThemeMode.DARK -> Color(0xFFE6E1E5)
        UnicornThemeMode.BLACK -> Color(0xFFF2F0F7)
    }
    val dialogSecondaryTextColor = when (themeMode) {
        UnicornThemeMode.LIGHT -> Color(0xFF424252)
        UnicornThemeMode.DARK -> Color(0xFFC9C4D0)
        UnicornThemeMode.BLACK -> Color(0xFFB8B4C4)
    }
    val borderStrokeModifier = if (isBlackMode) {
        Modifier.border(1.dp, Color(0xFF242430), RoundedCornerShape(12.dp))
    } else {
        Modifier
    }

    LaunchedEffect(Unit) {
        libraryRoms = getLibraryRoms(context)
        logSaveFiles(context, "startup")
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val currentView = retroViewHolder[0] ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    try {
                        currentView.onPause()
                        logSaveFiles(context, "lifecycle $event")
                    } catch (e: Exception) {
                        Log.w("Unicorn", "Error pausing GLRetroView on $event", e)
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    try {
                        currentView.onResume()
                    } catch (e: Exception) {
                        Log.w("Unicorn", "Error resuming GLRetroView on ON_RESUME", e)
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val romPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { pickedUri ->
            val originalName = resolveDisplayName(context, pickedUri)
                ?: pickedUri.lastPathSegment
                ?: ""
            val ext = extractGbExtension(originalName)

            if (ext == null) {
                Log.w("Unicorn", "Rejected unsupported file: $originalName (only .gb and .gbc are supported)")
                statusMessage = "Only .gb and .gbc ROMs are supported"
                return@let
            }

            val targetFileName = sanitizeLibraryFileName(originalName, ext)
            val copiedRom = copyUriToLibrary(context, pickedUri, targetFileName)

            if (copiedRom != null && copiedRom.exists() && copiedRom.length() > 0 && copiedRom.canRead()) {
                teardownRetroView(context, retroViewHolder[0], lifecycleOwner, "load new ROM")
                retroViewHolder[0] = null
                copiedRom.setLastModified(System.currentTimeMillis())
                libraryRoms = getLibraryRoms(context)
                Log.d("Unicorn", "ROM added to library: ${copiedRom.absolutePath} (${copiedRom.length()} bytes)")
                statusMessage = ""
                romPath = copiedRom.absolutePath
            } else {
                Log.w("Unicorn", "Failed to copy ROM or ROM file was empty")
                statusMessage = "Failed to load ROM file"
            }
        }
    }

    if (showEjectDialog) {
        AlertDialog(
            onDismissRequest = { showEjectDialog = false },
            containerColor = dialogContainerColor,
            titleContentColor = dialogTextColor,
            textContentColor = dialogSecondaryTextColor,
            modifier = if (isBlackMode) {
                Modifier.border(1.dp, Color(0xFF242430), RoundedCornerShape(28.dp))
            } else {
                Modifier
            },
            title = {
                Text(
                    text = "Eject ROM",
                    color = dialogTextColor,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to eject?",
                    color = dialogSecondaryTextColor
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEjectDialog = false
                        val viewToTeardown = retroViewHolder[0]
                        retroViewHolder[0] = null
                        romPath = null
                        teardownRetroView(context, viewToTeardown, lifecycleOwner, "eject ROM")
                        libraryRoms = getLibraryRoms(context)
                        statusMessage = "No GB / GBC ROM loaded yet"
                        Log.d("Unicorn", "ROM ejected by user (SRAM saves preserved)")
                    }
                ) {
                    Text(
                        text = "Eject",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showEjectDialog = false }) {
                    Text(
                        text = "Cancel",
                        color = dialogTextColor
                    )
                }
            }
        )
    }

    if (showPurgeDialog) {
        AlertDialog(
            onDismissRequest = { showPurgeDialog = false },
            containerColor = dialogContainerColor,
            titleContentColor = dialogTextColor,
            textContentColor = dialogSecondaryTextColor,
            modifier = if (isBlackMode) {
                Modifier.border(1.dp, Color(0xFF242430), RoundedCornerShape(28.dp))
            } else {
                Modifier
            },
            title = {
                Text(
                    text = "Purge Loaded Games",
                    color = dialogTextColor,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Remove all ${libraryRoms.size} loaded ROM(s) from the library? In-game saves are kept.",
                    color = dialogSecondaryTextColor
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPurgeDialog = false
                        val viewToTeardown = retroViewHolder[0]
                        retroViewHolder[0] = null
                        romPath = null
                        teardownRetroView(context, viewToTeardown, lifecycleOwner, "purge ROMs")
                        val removed = purgeLibraryRoms(context)
                        libraryRoms = getLibraryRoms(context)
                        statusMessage = "No GB / GBC ROM loaded yet"
                        Log.d("Unicorn", "Purged $removed ROM(s) from library (SRAM saves preserved)")
                    }
                ) {
                    Text(
                        text = "Purge",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showPurgeDialog = false }) {
                    Text(
                        text = "Cancel",
                        color = dialogTextColor
                    )
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = "Unicorn SP",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = appVersionLabel,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            OutlinedButton(
                onClick = onCycleTheme,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onBackground
                )
            ) {
                Text(
                    text = "${themeMode.icon} ${themeMode.label}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.TopCenter
        ) {
            val currentPath = romPath
            if (currentPath != null) {
                key(currentPath) {
                    AndroidView(
                        factory = { ctx ->
                            val romFile = File(currentPath)
                            val corePath = resolveGambatteCorePath(ctx)
                            val coreFile = File(corePath)
                            val bytes = if (romFile.exists() && romFile.length() > 0) romFile.readBytes() else null
                            val savesDir = File(ctx.filesDir, "saves").apply { mkdirs() }

                            Log.d(
                                "Unicorn",
                                "Core path: ${coreFile.absolutePath}, exists=${coreFile.exists()}, bytes=${bytes?.size ?: -1}, savesDir=${savesDir.absolutePath}"
                            )

                            val retroData = GLRetroViewData(ctx).apply {
                                coreFilePath = coreFile.absolutePath
                                gameFilePath = null
                                gameFileBytes = bytes
                                systemDirectory = File(ctx.filesDir, "system").apply { mkdirs() }.absolutePath
                                savesDirectory = savesDir.absolutePath
                            }

                            GLRetroView(ctx, retroData).apply {
                                isFocusable = true
                                isFocusableInTouchMode = true
                                onCreate(lifecycleOwner)
                                retroViewHolder[0] = this
                            }
                        },
                        update = { view ->
                            retroViewHolder[0] = view
                        },
                        onRelease = { view ->
                            if (retroViewHolder[0] === view) {
                                retroViewHolder[0] = null
                            }
                            teardownRetroView(context, view, lifecycleOwner, "AndroidView onRelease")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(10f / 9f)
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(controlSurfaceColor, RoundedCornerShape(12.dp))
                        .then(borderStrokeModifier)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (libraryRoms.isEmpty()) statusMessage else "Loaded Games",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (libraryRoms.isNotEmpty()) {
                            TextButton(
                                onClick = { showPurgeDialog = true }
                            ) {
                                Text(
                                    text = "Purge",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (libraryRoms.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Pick a .gb or .gbc ROM below to add it to your library",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(libraryRoms, key = { it.absolutePath }) { romFile ->
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = cardColor
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            romFile.setLastModified(System.currentTimeMillis())
                                            libraryRoms = getLibraryRoms(context)
                                            statusMessage = ""
                                            retroViewHolder[0] = null
                                            romPath = romFile.absolutePath
                                            Log.d("Unicorn", "Launched ROM from library: ${romFile.absolutePath}")
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = romFile.nameWithoutExtension,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = romFile.extension.uppercase(),
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        GameBoySpControls(
            themeMode = themeMode,
            onKeyEvent = { action, keyCode ->
                val view = retroViewHolder[0]
                if (view != null) {
                    dispatchRetroKey(view, action, keyCode)
                } else {
                    Log.w("Unicorn", "Key event ignored: no active GLRetroView (action=$action, keyCode=$keyCode)")
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (romPath == null) {
            Button(
                onClick = { romPicker.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Select GB / GBC ROM")
            }
        } else {
            OutlinedButton(
                onClick = { showEjectDialog = true },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onBackground
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Eject ROM")
            }
        }
    }
}

@Composable
private fun GameBoySpControls(
    themeMode: UnicornThemeMode,
    onKeyEvent: (action: Int, keyCode: Int) -> Unit
) {
    val deckBackground = when (themeMode) {
        UnicornThemeMode.LIGHT -> Color(0xFFE5E2F0)
        UnicornThemeMode.DARK -> Color(0xFF1A1A22)
        UnicornThemeMode.BLACK -> Color(0xFF000000)
    }
    val deckBorder = if (themeMode == UnicornThemeMode.BLACK) {
        Modifier.border(1.dp, Color(0xFF242430), RoundedCornerShape(16.dp))
    } else {
        Modifier
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(deckBackground, RoundedCornerShape(16.dp))
            .then(deckBorder)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SpPadButton(
                    label = "▲",
                    keyCode = KeyEvent.KEYCODE_DPAD_UP,
                    themeMode = themeMode,
                    onKeyEvent = onKeyEvent
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SpPadButton(
                        label = "◀",
                        keyCode = KeyEvent.KEYCODE_DPAD_LEFT,
                        themeMode = themeMode,
                        onKeyEvent = onKeyEvent
                    )
                    Spacer(modifier = Modifier.size(48.dp))
                    SpPadButton(
                        label = "▶",
                        keyCode = KeyEvent.KEYCODE_DPAD_RIGHT,
                        themeMode = themeMode,
                        onKeyEvent = onKeyEvent
                    )
                }
                SpPadButton(
                    label = "▼",
                    keyCode = KeyEvent.KEYCODE_DPAD_DOWN,
                    themeMode = themeMode,
                    onKeyEvent = onKeyEvent
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SpActionButton(
                    label = "B",
                    keyCode = KeyEvent.KEYCODE_BUTTON_B,
                    containerColor = if (themeMode == UnicornThemeMode.LIGHT) Color(0xFF4A4A5C) else Color(0xFF3A3A48),
                    onKeyEvent = onKeyEvent,
                    modifier = Modifier.padding(top = 18.dp)
                )
                SpActionButton(
                    label = "A",
                    keyCode = KeyEvent.KEYCODE_BUTTON_A,
                    containerColor = Color(0xFF7E43D6),
                    onKeyEvent = onKeyEvent,
                    modifier = Modifier.padding(bottom = 18.dp)
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpSmallButton(
                label = "SELECT",
                keyCode = KeyEvent.KEYCODE_BUTTON_SELECT,
                themeMode = themeMode,
                onKeyEvent = onKeyEvent
            )
            SpSmallButton(
                label = "START",
                keyCode = KeyEvent.KEYCODE_BUTTON_START,
                themeMode = themeMode,
                onKeyEvent = onKeyEvent
            )
        }
    }
}

@Composable
private fun SpPadButton(
    label: String,
    keyCode: Int,
    themeMode: UnicornThemeMode,
    onKeyEvent: (action: Int, keyCode: Int) -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val idleColor = when (themeMode) {
        UnicornThemeMode.LIGHT -> Color(0xFF343444)
        UnicornThemeMode.DARK -> Color(0xFF2D2D3A)
        UnicornThemeMode.BLACK -> Color(0xFF14141C)
    }
    val activeColor = when (themeMode) {
        UnicornThemeMode.LIGHT -> Color(0xFF5A5A75)
        UnicornThemeMode.DARK -> Color(0xFF6B6B80)
        UnicornThemeMode.BLACK -> Color(0xFF38384A)
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .background(
                color = if (pressed) activeColor else idleColor,
                shape = RoundedCornerShape(10.dp)
            )
            .pointerInput(keyCode) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    onKeyEvent(KeyEvent.ACTION_DOWN, keyCode)
                    waitForUpOrCancellation()
                    pressed = false
                    onKeyEvent(KeyEvent.ACTION_UP, keyCode)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SpActionButton(
    label: String,
    keyCode: Int,
    containerColor: Color,
    onKeyEvent: (action: Int, keyCode: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(58.dp)
            .background(
                color = if (pressed) containerColor.copy(alpha = 0.65f) else containerColor,
                shape = CircleShape
            )
            .pointerInput(keyCode) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    onKeyEvent(KeyEvent.ACTION_DOWN, keyCode)
                    waitForUpOrCancellation()
                    pressed = false
                    onKeyEvent(KeyEvent.ACTION_UP, keyCode)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SpSmallButton(
    label: String,
    keyCode: Int,
    themeMode: UnicornThemeMode,
    onKeyEvent: (action: Int, keyCode: Int) -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val idleColor = when (themeMode) {
        UnicornThemeMode.LIGHT -> Color(0xFF343444)
        UnicornThemeMode.DARK -> Color(0xFF2D2D3A)
        UnicornThemeMode.BLACK -> Color(0xFF14141C)
    }
    val activeColor = when (themeMode) {
        UnicornThemeMode.LIGHT -> Color(0xFF5A5A75)
        UnicornThemeMode.DARK -> Color(0xFF5A5A6E)
        UnicornThemeMode.BLACK -> Color(0xFF38384A)
    }

    Box(
        modifier = Modifier
            .width(76.dp)
            .height(30.dp)
            .background(
                color = if (pressed) activeColor else idleColor,
                shape = RoundedCornerShape(15.dp)
            )
            .pointerInput(keyCode) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    onKeyEvent(KeyEvent.ACTION_DOWN, keyCode)
                    waitForUpOrCancellation()
                    pressed = false
                    onKeyEvent(KeyEvent.ACTION_UP, keyCode)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color(0xFFE0E0EC),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun copyUriToLibrary(context: Context, uri: Uri, fileName: String): File? {
    return try {
        val romsDir = File(context.filesDir, "roms").apply { mkdirs() }
        val outFile = File(romsDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
                output.flush()
            }
        } ?: return null

        if (outFile.exists() && outFile.length() > 0) outFile else null
    } catch (e: Exception) {
        Log.e("Unicorn", "Error copying URI to library", e)
        null
    }
}