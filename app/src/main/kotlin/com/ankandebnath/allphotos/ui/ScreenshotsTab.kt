package com.ankandebnath.allphotos.ui

import android.content.Intent
import android.provider.Settings as SysSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ankandebnath.allphotos.capture.Apps
import com.ankandebnath.allphotos.capture.WatchService
import com.ankandebnath.allphotos.data.Db
import com.ankandebnath.allphotos.data.Library
import com.ankandebnath.allphotos.data.Media
import com.ankandebnath.allphotos.data.Prefs
import com.ankandebnath.allphotos.logic.Fmt
import com.ankandebnath.allphotos.logic.Links
import com.ankandebnath.allphotos.logic.Rules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Screenshots that came from one app or one website. */
class ShotGroup(val key: String, val title: String, val items: List<Media>, val bytes: Long)

/** "3d" in the corner of screenshots that will be cleaned up within a week. */
@Composable
fun BoxScope.ExpiryBadge(m: Media) {
    val ctx = LocalContext.current
    val s by remember { Prefs.get(ctx) }.settings.collectAsState()
    val important by remember { Db.get(ctx).important }.collectAsState()
    if (!s.shotAutoTrash || !m.isScreenshot || m.id in important) return
    val left = Rules.daysLeft(m.taken, System.currentTimeMillis(), s.shotDays)
    if (left in 0..7) {
        SmallTag(
            if (left <= 1) "Today" else "${left}d",
            Modifier.align(Alignment.BottomStart).padding(5.dp),
            bg = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f),
            fg = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

fun shotGroups(ctx: android.content.Context, shots: List<Media>, bySite: Boolean): List<ShotGroup> {
    val src = Db.get(ctx).sources.value
    val map = LinkedHashMap<String, MutableList<Media>>()
    for (m in shots) {
        val s = src[m.id]
        val key = if (bySite) Links.domain(s?.url) ?: continue else s?.pkg ?: ""
        map.getOrPut(key) { ArrayList() } += m
    }
    return map.map { (k, list) ->
        val title = if (bySite) k else if (k.isEmpty()) "Unknown app" else Apps.label(ctx, k)
        ShotGroup(k, title, list, list.sumOf { it.size })
    }.sortedWith(compareBy<ShotGroup> { it.key.isEmpty() }.thenByDescending { it.items.size })
}

@Composable
fun ScreenshotsTab(lib: Library, app: AppState, actions: Actions, state: LazyGridState, bottom: Dp) {
    val ctx = LocalContext.current
    val s by remember { Prefs.get(ctx) }.settings.collectAsState()
    val important by remember { Db.get(ctx).important }.collectAsState()
    val sources by remember { Db.get(ctx).sources }.collectAsState()
    var seg by rememberSaveable { mutableIntStateOf(0) }
    var watching by remember { mutableStateOf(WatchService.enabled(ctx)) }
    LifecycleResumeEffect(Unit) {
        watching = WatchService.enabled(ctx)
        onPauseOrDispose { }
    }
    val shots = lib.screenshots
    val segments = listOf("Recent", "Apps", "Websites", "Important")
    val sub = buildString {
        append("${shots.size} · ${Fmt.size(shots.sumOf { it.size })}")
        if (s.shotAutoTrash) append(" · cleaned after ${s.shotDays} days")
    }
    val header: @Composable () -> Unit = {
        Column {
            Segmented(segments, seg) { seg = it }
            if (!watching) {
                Gap(12.dp)
                WatchBanner()
            }
        }
    }
    val trailing: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        SmallIconButton(Icons.Rounded.Settings, "Settings") { app.open(Dest.Settings) }
    }
    val badge: @Composable BoxScope.(Media) -> Unit = { m -> ExpiryBadge(m) }

    when (seg) {
        0 -> GridPage(
            shots, "Screenshots", sub, app, actions, bottom, state = state, trailing = trailing, extra = header, badge = badge,
            empty = { EmptyState(Icons.Rounded.Screenshot, "No screenshots", "Screenshots you take will show up here.") },
        )
        3 -> {
            val imp = remember(shots, important) { shots.filter { it.id in important } }
            GridPage(
                imp, "Screenshots", sub, app, actions, bottom, trailing = trailing, extra = header,
                empty = {
                    EmptyState(
                        Icons.Rounded.Screenshot, "Nothing marked yet",
                        "Mark a screenshot as important and it stays forever. Everything else is cleaned up after ${s.shotDays} days.",
                    )
                },
            )
        }
        else -> {
            val bySite = seg == 2
            val groups by produceState(emptyList<ShotGroup>(), shots, sources, bySite) {
                value = withContext(Dispatchers.Default) { shotGroups(ctx.applicationContext, shots, bySite) }
            }
            ShotGroupList(groups, bySite, sub, header, trailing, app, actions, bottom, important)
        }
    }
}

@Composable
private fun WatchBanner() {
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    Panel(color = c.tertiaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.TravelExplore, MaterialShapes.Cookie6Sided.toShape(), c.tertiary, c.onTertiary, 44.dp)
            GapW(14.dp)
            Column(Modifier.weight(1f)) {
                Text("See where each screenshot came from", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = c.onTertiaryContainer)
                Text(
                    "Turn on \"All Photos screenshot source\" to save the app and website with every new screenshot.",
                    style = MaterialTheme.typography.bodySmall, color = c.onTertiaryContainer,
                )
            }
        }
        Gap(10.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { openAccessibility(ctx) }) { Text("Turn on") }
            TextButton(onClick = { openAppInfo(ctx) }) { Text("Blocked? Allow it") }
        }
    }
}

fun openAccessibility(ctx: android.content.Context) {
    runCatching { ctx.startActivity(Intent(SysSettings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** App info, where "Allow restricted settings" lives for apps installed outside the Play Store. */
fun openAppInfo(ctx: android.content.Context) {
    runCatching {
        ctx.startActivity(
            Intent(SysSettings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", ctx.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

@Composable
private fun ShotGroupList(
    groups: List<ShotGroup>,
    bySite: Boolean,
    sub: String,
    header: @Composable () -> Unit,
    trailing: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
    app: AppState,
    actions: Actions,
    bottom: Dp,
    important: Set<Long>,
) {
    val list = rememberLazyListState()
    LazyColumn(
        state = list,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottom + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageHeader("Screenshots", sub, trailing = trailing, extra = header) }
        if (groups.isEmpty()) {
            item {
                EmptyState(
                    if (bySite) Icons.Rounded.Language else Icons.Rounded.Screenshot,
                    if (bySite) "No websites yet" else "No apps yet",
                    if (bySite) "Screenshots from a browser are grouped by website here."
                    else "Screenshots are grouped by the app they were taken in.",
                )
            }
        }
        items(groups, key = { it.key }) { g ->
            ShotGroupCard(g, bySite, important, onOpen = {
                app.open(Dest.Group(if (bySite) SetKind.SHOT_SITE else SetKind.SHOT_APP, g.title, g.key))
            }, onDeleteAll = {
                actions.trash(g.items.filter { it.id !in important })
            }, onView = { i -> app.view(g.items, i) })
        }
    }
}

@Composable
private fun ShotGroupCard(g: ShotGroup, bySite: Boolean, important: Set<Long>, onOpen: () -> Unit, onDeleteAll: () -> Unit, onView: (Int) -> Unit) {
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    val icon by produceState(Apps.icon(ctx, null), g.key) {
        if (!bySite && g.key.isNotEmpty()) value = withContext(Dispatchers.IO) { Apps.icon(ctx, g.key) }
    }
    val px = with(LocalDensity.current) { 96.dp.roundToPx() }
    val keep = g.items.count { it.id in important }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.surfaceContainer).clickable(onClick = onOpen).padding(vertical = 14.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (bySite || g.key.isEmpty()) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(c.secondaryContainer), contentAlignment = Alignment.Center) {
                    Icon(if (bySite) Icons.Rounded.Language else Icons.Rounded.Screenshot, null, tint = c.onSecondaryContainer)
                }
            } else {
                AppIcon(icon, 40.dp)
            }
            GapW(12.dp)
            Column(Modifier.weight(1f)) {
                Text(g.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${g.items.size} · ${Fmt.size(g.bytes)}" + if (keep > 0) " · $keep important" else "",
                    style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
                )
            }
            if (g.items.size > keep) {
                TextButton(onClick = onDeleteAll) {
                    Icon(Icons.Rounded.AutoDelete, null, modifier = Modifier.size(18.dp))
                    GapW(6.dp)
                    Text("Delete all")
                }
            }
        }
        Gap(10.dp)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(g.items.take(12).size) { i ->
                val m = g.items[i]
                Box(Modifier.size(width = 72.dp, height = 128.dp).clip(RoundedCornerShape(14.dp)).clickable { onView(i) }) {
                    Thumb(m, px, Modifier.fillMaxSize())
                    if (m.id in important) StarBadge(Modifier.align(Alignment.TopStart).padding(4.dp))
                }
            }
        }
    }
}
