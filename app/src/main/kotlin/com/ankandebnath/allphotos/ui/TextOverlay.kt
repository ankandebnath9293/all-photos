package com.ankandebnath.allphotos.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ankandebnath.allphotos.data.Media
import com.ankandebnath.allphotos.logic.Smart
import com.ankandebnath.allphotos.logic.SmartAction
import com.ankandebnath.allphotos.media.Analyze
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Text read from a photo, with the size of the picture it was read from. */
class FoundText(val lines: List<Analyze.Line>, val w: Int, val h: Int)

/**
 * Tap-to-copy: the lines of text in a photo are outlined; tap one (or a few) to copy, share, call
 * a number, open a link or write an email. Everything is read on the phone.
 */
@Composable
fun TextOverlay(m: Media, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val found by produceState<FoundText?>(null, m.id) {
        value = withContext(Dispatchers.IO) {
            val b = Analyze.forReading(ctx, m) ?: return@withContext FoundText(emptyList(), 1, 1)
            try {
                FoundText(Analyze.lines(b).orEmpty(), b.width, b.height)
            } finally {
                b.recycle()
            }
        }
    }
    var picked by remember(m.id) { mutableStateOf<Set<Int>>(emptySet()) }
    val shade by animateFloatAsState(if (found != null) 0.45f else 0.2f, label = "shade")
    val accent = MaterialTheme.colorScheme.primary

    Box(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val f = found
            val screen = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
            val fit = Frame.fitted(screen, if (f != null && f.h > 0) f.w.toFloat() / f.h else m.aspect)
            val sx = if (f != null) fit.width / f.w else 1f
            val boxes = remember(f, fit) {
                f?.lines?.map { l ->
                    val pad = 4f
                    Rect(fit.left + l.box.left * sx - pad, fit.top + l.box.top * sx - pad, fit.left + l.box.right * sx + pad, fit.top + l.box.bottom * sx + pad)
                }.orEmpty()
            }
            Canvas(
                Modifier.fillMaxSize().pointerInput(boxes) {
                    detectTapGestures { p ->
                        val i = boxes.indexOfFirst { it.contains(p) }
                        if (i >= 0) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            picked = if (i in picked) picked - i else picked + i
                        } else {
                            picked = emptySet()
                        }
                    }
                },
            ) {
                drawRect(Color.Black.copy(alpha = shade))
                boxes.forEachIndexed { i, r ->
                    val on = i in picked
                    drawRoundRect(
                        color = if (on) accent.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.22f),
                        topLeft = Offset(r.left, r.top),
                        size = Size(r.width, r.height),
                        cornerRadius = CornerRadius(8f),
                    )
                }
            }
        }

        // Done: back to the photo.
        androidx.compose.material3.FilledTonalIconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp),
        ) { Icon(Icons.Rounded.Close, "Close text") }

        val f = found
        if (f == null) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                LoadingIndicator()
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.inverseSurface) {
                    Text("Reading text…", color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }
        } else if (f.lines.isEmpty()) {
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.inverseSurface, modifier = Modifier.align(Alignment.Center)) {
                Text("No text found", color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
            }
        }

        val chosen = if (f == null) "" else (if (picked.isEmpty()) f.lines.indices.toList() else picked.sorted()).joinToString("\n") { f.lines[it].text }
        val smart = remember(chosen) { if (picked.isEmpty()) emptyList() else Smart.find(chosen) }
        AnimatedVisibility(
            f != null && f.lines.isNotEmpty(),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp),
        ) {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 8.dp, modifier = Modifier.padding(horizontal = 16.dp)) {
                Column(Modifier.padding(14.dp).widthIn(max = 520.dp)) {
                    Text(
                        if (picked.isEmpty()) "Tap text to pick it, or copy everything" else chosen,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (picked.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Gap(10.dp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(Icons.Rounded.ContentCopy, if (picked.isEmpty()) "Copy all" else "Copy") {
                            ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("text", chosen))
                            Toasts.show("Copied")
                        }
                        Chip(Icons.Rounded.Share, "Share") {
                            runCatching {
                                ctx.startActivity(
                                    Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, chosen), null)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }
                        if (picked.isNotEmpty() && f != null && picked.size < f.lines.size) {
                            Chip(Icons.Rounded.SelectAll, "Select all") { picked = f.lines.indices.toSet() }
                        }
                        smart.forEach { a ->
                            when (a.kind) {
                                SmartAction.Kind.PHONE -> Chip(Icons.Rounded.Call, "Call ${a.value}") {
                                    runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Smart.dialable(a.value))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                }
                                SmartAction.Kind.LINK -> Chip(Icons.AutoMirrored.Rounded.OpenInNew, "Open ${a.value.take(28)}") {
                                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://" + a.value)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                }
                                SmartAction.Kind.EMAIL -> Chip(Icons.Rounded.Email, "Email") {
                                    runCatching { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + a.value)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        leadingIcon = { Icon(icon, null, modifier = Modifier.padding(0.dp)) },
        colors = AssistChipDefaults.assistChipColors(),
    )
}
