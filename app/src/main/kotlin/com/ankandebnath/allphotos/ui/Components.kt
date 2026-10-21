package com.ankandebnath.allphotos.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ankandebnath.allphotos.data.Media
import com.ankandebnath.allphotos.logic.Fmt
import com.ankandebnath.allphotos.media.Thumbs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun GapW(w: Dp) = Spacer(Modifier.width(w))

@Composable
fun IconBadge(icon: ImageVector, shape: Shape, container: Color, content: Color, size: Dp = 44.dp) {
    Box(Modifier.size(size).clip(shape).background(container), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = content, modifier = Modifier.size(size * 0.5f))
    }
}

/** A photo or video thumbnail that loads in the background and fades in. */
@Composable
fun Thumb(m: Media, px: Int, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val ctx = LocalContext.current
    val size = Thumbs.bucket(px)
    var img by remember(m.id, m.modified, size) { mutableStateOf(Thumbs.cached(m, size)) }
    val loaded = img
    val sharp = loaded != null && minOf(loaded.width, loaded.height) >= size * 0.7f
    if (!sharp) {
        LaunchedEffect(m.id, m.modified, size) {
            Thumbs.load(ctx.applicationContext, m, size)?.let { img = it }
        }
    }
    val alpha by animateFloatAsState(if (img != null) 1f else 0f, tween(180), label = "thumb")
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        img?.let {
            Image(it, null, Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }, contentScale = contentScale)
        }
    }
}

@Composable
fun VideoBadge(durationMs: Long, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.45f)).padding(start = 3.dp, end = 6.dp, top = 1.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp))
        Text(Fmt.duration(durationMs), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun StarBadge(modifier: Modifier = Modifier) {
    Box(modifier.size(20.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Star, null, tint = Color(0xFFFFC94A), modifier = Modifier.size(14.dp))
    }
}

@Composable
fun SmallTag(text: String, modifier: Modifier = Modifier, bg: Color = Color.Black.copy(alpha = 0.45f), fg: Color = Color.White) {
    Text(
        text, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** A rounded card in the page's surface colour. */
@Composable
fun Panel(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, color: Color = MaterialTheme.colorScheme.surfaceContainer, content: @Composable ColumnScope.() -> Unit) {
    val base = modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(color)
    Column((if (onClick != null) base.clickable(onClick = onClick) else base).padding(18.dp), content = content)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold, modifier = modifier.padding(start = 6.dp, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
fun SwitchRow(title: String, sub: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(enabled = enabled) { onChange(!checked) }.padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        GapW(12.dp)
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** Connected toggle buttons, the expressive take on a segmented control. */
@Composable
fun Segmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { i, text ->
            ToggleButton(
                checked = i == selected,
                onCheckedChange = { onSelect(i) },
                modifier = Modifier.weight(1f),
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 6.dp),
            ) {
                Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** The rounded search field at the top of a page. */
@Composable
fun SearchPill(value: String, onValue: (String) -> Unit, hint: String, modifier: Modifier = Modifier, onFocus: (() -> Unit)? = null) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(50)).background(c.surfaceContainerHigh).padding(start = 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = c.onSurfaceVariant)
        GapW(12.dp)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(hint, color = c.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onValue,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.onSurface),
                cursorBrush = SolidColor(c.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onFocus?.invoke() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            IconButton(onClick = { onValue("") }) { Icon(Icons.Rounded.Close, "Clear", tint = c.onSurfaceVariant) }
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, sub: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val c = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconBadge(icon, MaterialShapes.Cookie9Sided.toShape(), c.secondaryContainer, c.onSecondaryContainer, 88.dp)
        Gap(18.dp)
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Gap(6.dp)
        Text(sub, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) {
            Gap(18.dp)
            action()
        }
    }
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().heightIn(min = 160.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
}

@Composable
fun AppIcon(img: ImageBitmap?, size: Dp, modifier: Modifier = Modifier) {
    if (img != null) Image(img, null, modifier.size(size).clip(CircleShape))
    else Box(modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest))
}

/** Short messages at the bottom of the screen ("Moved to trash"). No buttons, they just fade away. */
object Toasts {
    data class Msg(val text: String, val stamp: Long = System.nanoTime())

    val current = MutableStateFlow<Msg?>(null)

    fun show(text: String) {
        current.value = Msg(text)
    }
}

@Composable
fun BoxScope.ToastHost(bottom: Dp) {
    val msg by Toasts.current.collectAsState()
    var shown by remember { mutableStateOf<Toasts.Msg?>(null) }
    LaunchedEffect(msg) {
        val m = msg ?: return@LaunchedEffect
        shown = m
        delay(2200)
        if (Toasts.current.value == m) Toasts.current.value = null
        shown = null
    }
    AnimatedVisibility(
        visible = shown != null,
        enter = fadeIn(tween(160)) + slideInVertically { it / 2 } + scaleIn(initialScale = 0.9f),
        exit = fadeOut(tween(220)) + slideOutVertically { it / 3 } + scaleOut(targetScale = 0.95f),
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottom),
    ) {
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.inverseSurface, shadowElevation = 6.dp) {
            Text(
                shown?.text.orEmpty(), color = MaterialTheme.colorScheme.inverseOnSurface,
                style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
}
