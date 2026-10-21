package com.ankandebnath.allphotos.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ankandebnath.allphotos.App
import com.ankandebnath.allphotos.data.Prefs
import com.ankandebnath.allphotos.work.Work

/** First run: what the app does, and the few things it needs the phone to allow. */
@Composable
fun Onboarding(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs.get(ctx) }
    val s by prefs.settings.collectAsState()
    val g = rememberGrants()
    var step by rememberSaveable { mutableIntStateOf(0) }
    val last = 5
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { step++ }
    val c = MaterialTheme.colorScheme

    fun finish() {
        prefs.update { it.copy(onboarded = true) }
        (ctx.applicationContext as App).start()
        Work.schedule(ctx.applicationContext)
        onDone()
    }

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
        LinearWavyProgressIndicator(progress = { (step + 1) / (last + 1f) }, modifier = Modifier.fillMaxWidth())
        AnimatedContent(
            targetState = step,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = { (slideInHorizontally { it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 } + fadeOut()) },
            label = "onboarding",
        ) { st ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                when (st) {
                    0 -> Page(
                        Icons.Rounded.PhotoLibrary, 0, "All your photos. Nothing else.",
                        "Every photo and video on this phone, from any app or camera, iPhone files included. No ads, no account, no syncing. It all stays on the phone.",
                    )
                    1 -> Page(
                        Icons.Rounded.Folder, 1, "See every file",
                        "All Photos needs access to all files to show, clean up and edit every photo and video, including those inside WhatsApp and Telegram folders.",
                    ) {
                        Status(g.storage, "Access to all files")
                    }
                    2 -> Page(
                        Icons.Rounded.AutoDelete, 2, "Delete without pop-ups",
                        "Turn on Media management so clean-up and deleting just happen, without the phone asking each time. Deleted items stay in the trash for 30 days.",
                    ) {
                        Status(g.media, "Media management")
                    }
                    3 -> Page(
                        Icons.Rounded.Notifications, 3, "Keep the screenshots that matter",
                        "After you take a screenshot, a quiet notification lets you mark it important, so it's never cleaned up. Screenshots taken within 30 seconds share one.",
                    ) {
                        Status(g.notifications, "Notifications")
                    }
                    4 -> Page(
                        Icons.Rounded.TravelExplore, 4, "Know where screenshots came from",
                        "Turn on \"All Photos screenshot source\" in Accessibility to save the app and website with each screenshot. Usage access finds the app for older ones. Both are optional, and nothing leaves the phone.",
                    ) {
                        Status(g.watch, "Screenshot source")
                        Status(g.usage, "Usage access")
                        Gap(8.dp)
                        Text(
                            "If Android says the setting is restricted: App info → ⋮ → Allow restricted settings, then try again.",
                            style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, textAlign = TextAlign.Center,
                        )
                        TextButton(onClick = { openAppInfo(ctx) }) { Text("Open App info") }
                    }
                    else -> Page(
                        Icons.Rounded.AutoDelete, 0, "Clean-up, your way",
                        "Everything goes to the trash first and can be restored for 30 days. Files you mark important are never touched. You can change these any time in Cleanup.",
                    ) {
                        Panel {
                            SwitchRow("Old screenshots", "After 30 days", s.shotAutoTrash) { on -> prefs.update { it.copy(shotAutoTrash = on) } }
                            SwitchRow("WhatsApp photos and videos", "After 30 days", s.whatsappRule) { on -> prefs.update { it.copy(whatsappRule = on) } }
                            SwitchRow("Telegram photos and videos", "After 30 days", s.telegramRule) { on -> prefs.update { it.copy(telegramRule = on) } }
                        }
                        Gap(12.dp)
                        Text(
                            "Tip: the next time you open a photo from another app, choose All Photos and \"Always\" to make it your gallery.",
                            style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            if (step in 2..4) TextButton(onClick = { step++ }) { Text("Skip") } else Box(Modifier.size(1.dp))
            Button(
                onClick = {
                    when (step) {
                        1 -> if (g.storage) step++ else openStorageAccess(ctx)
                        2 -> if (g.media) step++ else openMediaManagement(ctx)
                        3 -> if (g.notifications) step++ else notif.launch(Manifest.permission.POST_NOTIFICATIONS)
                        4 -> if (!g.watch) openAccessibility(ctx) else if (!g.usage) openUsageAccess(ctx) else step++
                        last -> finish()
                        else -> step++
                    }
                },
            ) {
                Text(
                    when (step) {
                        0 -> "Get started"
                        1 -> if (g.storage) "Next" else "Allow access"
                        2 -> if (g.media) "Next" else "Turn on"
                        3 -> if (g.notifications) "Next" else "Allow"
                        4 -> if (!g.watch) "Turn on" else if (!g.usage) "Allow usage access" else "Next"
                        last -> "Start"
                        else -> "Next"
                    },
                )
            }
        }
    }
}

private val shapes = listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Sunny, MaterialShapes.Clover4Leaf, MaterialShapes.Cookie6Sided, MaterialShapes.Flower)

@Composable
private fun Page(icon: ImageVector, shape: Int, title: String, body: String, extra: (@Composable () -> Unit)? = null) {
    val c = MaterialTheme.colorScheme
    IconBadge(icon, shapes[shape % shapes.size].toShape(), c.primaryContainer, c.onPrimaryContainer, 120.dp)
    Gap(28.dp)
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Gap(12.dp)
    Text(body, style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant, textAlign = TextAlign.Center)
    if (extra != null) {
        Gap(24.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) { extra() }
    }
}

@Composable
private fun Status(ok: Boolean, label: String) {
    val c = MaterialTheme.colorScheme
    Text(
        (if (ok) "✓  " else "•  ") + label + if (ok) " is on" else " is off",
        color = if (ok) c.primary else c.onSurfaceVariant,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
}
