package com.ankandebnath.allphotos.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings as SysSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ankandebnath.allphotos.Access
import com.ankandebnath.allphotos.capture.UsageSource
import com.ankandebnath.allphotos.capture.WatchService
import com.ankandebnath.allphotos.data.Db
import com.ankandebnath.allphotos.data.MediaRepo
import com.ankandebnath.allphotos.data.Prefs
import com.ankandebnath.allphotos.notify.Notifier
import com.ankandebnath.allphotos.work.Indexer
import com.ankandebnath.allphotos.work.Work

/** What the phone currently allows, refreshed whenever you come back to the app. */
class Grants(val storage: Boolean, val media: Boolean, val notifications: Boolean, val usage: Boolean, val watch: Boolean)

@Composable
fun rememberGrants(): Grants {
    val ctx = LocalContext.current
    fun read() = Grants(Access.storage(ctx), Access.manageMedia(ctx), Notifier.allowed(ctx), UsageSource.allowed(ctx), WatchService.enabled(ctx))
    var g by remember { mutableStateOf(read()) }
    LifecycleResumeEffect(Unit) {
        g = read()
        onPauseOrDispose { }
    }
    return g
}

fun openStorageAccess(ctx: Context) {
    runCatching {
        ctx.startActivity(
            Intent(SysSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.fromParts("package", ctx.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure {
        runCatching { ctx.startActivity(Intent(SysSettings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

fun openMediaManagement(ctx: Context) {
    runCatching {
        ctx.startActivity(
            Intent(SysSettings.ACTION_REQUEST_MANAGE_MEDIA, Uri.fromParts("package", ctx.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

fun openUsageAccess(ctx: Context) {
    runCatching {
        ctx.startActivity(
            Intent(SysSettings.ACTION_USAGE_ACCESS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure {
        runCatching { ctx.startActivity(Intent(SysSettings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

fun openNotificationSettings(ctx: Context) {
    runCatching {
        ctx.startActivity(
            Intent(SysSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(SysSettings.EXTRA_APP_PACKAGE, ctx.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

@Composable
fun SettingsScreen(app: AppState, bottom: Dp, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs.get(ctx) }
    val s by prefs.settings.collectAsState()
    val g = rememberGrants()
    val index by Indexer.progress.collectAsState()
    val lib by MediaRepo.library.collectAsState()
    var whatsNew by remember { mutableStateOf(false) }
    val counts = remember(lib, index.running) { Db.get(ctx).counts() }
    val photos = lib?.items?.count { !it.isVideo } ?: 0
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) openNotificationSettings(ctx)
    }
    val c = MaterialTheme.colorScheme
    val dayOptions = listOf(7, 14, 30, 60, 90)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = bottom + 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { PageHeader("Settings", "All Photos ${WhatsNew.NAME}", onBack = onBack) }

        item { SectionTitle("Look") }
        item {
            Panel {
                Text("Theme", style = MaterialTheme.typography.titleMedium)
                Gap(8.dp)
                Segmented(listOf("Phone", "Light", "Dark"), s.themeMode) { m -> prefs.update { it.copy(themeMode = m) } }
                Gap(10.dp)
                SwitchRow("Wallpaper colours", "Match the colours of your wallpaper", s.dynamicColor) { on -> prefs.update { it.copy(dynamicColor = on) } }
                if (!s.dynamicColor) {
                    Gap(4.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Palettes.all.forEachIndexed { i, p ->
                            val col = Color.hsl(p.hue, 0.6f, 0.55f)
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(col).clickable { prefs.update { it.copy(palette = i) } }
                                    .then(if (s.palette == i) Modifier.border(3.dp, c.onSurface, CircleShape) else Modifier),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (s.palette == i) androidx.compose.material3.Icon(Icons.Rounded.Check, p.name, tint = Color.White)
                            }
                        }
                    }
                }
                Gap(12.dp)
                Text("Photos per row", style = MaterialTheme.typography.titleMedium)
                Text("You can also pinch the grid", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                Gap(8.dp)
                Segmented(listOf("2", "3", "4", "5", "6"), s.gridCols - 2) { i -> prefs.update { it.copy(gridCols = i + 2) } }
            }
        }

        item { SectionTitle("Screenshots") }
        item {
            Panel {
                SwitchRow("Clean up old screenshots", "Moved to the trash unless marked important", s.shotAutoTrash) { on ->
                    prefs.update { it.copy(shotAutoTrash = on) }
                }
                if (s.shotAutoTrash) {
                    Text("Keep for", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
                    Segmented(dayOptions.map { "${it}d" }, dayOptions.indexOf(s.shotDays).coerceAtLeast(0)) { i ->
                        prefs.update { it.copy(shotDays = dayOptions[i]) }
                    }
                    Gap(6.dp)
                }
                SwitchRow("\"Mark as important\" notification", "Right after you take a screenshot. Screenshots taken close together share one.", s.shotNotify) { on ->
                    prefs.update { it.copy(shotNotify = on) }
                    if (on && !g.notifications) notif.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                Gap(6.dp)
                GrantRow("Screenshot source", "Saves the app and website with each new screenshot", g.watch) { openAccessibility(ctx) }
                if (!g.watch) {
                    Text(
                        "If the switch is greyed out: App info → ⋮ → Allow restricted settings, then try again.",
                        style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                    )
                    TextButton(onClick = { openAppInfo(ctx) }) { Text("Open App info") }
                }
                GrantRow("Usage access", "Finds the app for screenshots taken before", g.usage) { openUsageAccess(ctx) }
            }
        }

        item { SectionTitle("Chat apps") }
        item {
            Panel {
                SwitchRow("WhatsApp photos and videos", "Received and sent media; stickers are left alone", s.whatsappRule) { on -> prefs.update { it.copy(whatsappRule = on) } }
                SwitchRow("Telegram photos and videos", null, s.telegramRule) { on -> prefs.update { it.copy(telegramRule = on) } }
                Text("Move to trash after", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
                Segmented(dayOptions.map { "${it}d" }, dayOptions.indexOf(s.chatDays).coerceAtLeast(0)) { i ->
                    prefs.update { it.copy(chatDays = dayOptions[i]) }
                }
                Gap(8.dp)
                FilledTonalButton(onClick = { Work.cleanupNow(ctx); Toasts.show("Cleaning up…") }) { Text("Run clean-up now") }
            }
        }

        item { SectionTitle("Smart search") }
        item {
            Panel {
                Text(
                    if (photos == 0) "No photos yet" else "Text read in ${counts.second.coerceAtMost(photos + (lib?.screenshots?.size ?: 0))} of $photos photos",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (index.running) "${index.what}… ${index.done}/${index.total}" else "Reads the text and what's in each photo, on the phone. Nothing is uploaded.",
                    style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
                )
                Gap(6.dp)
                SwitchRow("Only while charging", "Keeps the phone cool and the battery full. New screenshots are always read right away.", s.indexOnlyCharging) { on ->
                    prefs.update { it.copy(indexOnlyCharging = on) }
                    Work.scheduleHeavy(ctx, on)
                }
                FilledTonalButton(onClick = { Work.indexAllNow(ctx); Toasts.show("Reading photos in the background") }) { Text("Read all photos now") }
            }
        }

        item { SectionTitle("Access") }
        item {
            Panel {
                GrantRow("All photos and videos", "Needed to show, clean and edit every file", g.storage) { openStorageAccess(ctx) }
                GrantRow("Media management", "Lets clean-up and deleting work without a pop-up each time", g.media) { openMediaManagement(ctx) }
                GrantRow("Notifications", "For the screenshot notification", g.notifications) {
                    notif.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }

        item { SectionTitle("About") }
        item {
            Panel {
                Text("All Photos ${WhatsNew.NAME}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Works completely offline. The app has no internet permission, so nothing ever leaves this phone.",
                    style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
                )
                TextButton(onClick = { whatsNew = true }) { Text("What's new") }
            }
        }
    }
    if (whatsNew) WhatsNewDialog { whatsNew = false }
}

@Composable
fun GrantRow(title: String, sub: String, granted: Boolean, onFix: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
        }
        if (granted) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(c.primaryContainer), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(Icons.Rounded.Check, "On", tint = c.onPrimaryContainer, modifier = Modifier.size(20.dp))
            }
        } else {
            FilledTonalButton(onClick = onFix) { Text("Turn on") }
        }
    }
}
