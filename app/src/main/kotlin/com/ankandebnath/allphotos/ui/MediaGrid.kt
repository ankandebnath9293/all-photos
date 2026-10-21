package com.ankandebnath.allphotos.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BurstMode
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import com.ankandebnath.allphotos.data.Media
import com.ankandebnath.allphotos.logic.Row as GridRow
import com.ankandebnath.allphotos.logic.Timeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Which items are selected. Immutable sets keep updates cheap to compare. */
@Stable
class Selection {
    var ids by mutableStateOf<Set<Long>>(emptySet())
    val active: Boolean get() = ids.isNotEmpty()

    fun toggle(id: Long) {
        ids = if (id in ids) ids - id else ids + id
    }

    fun set(list: Collection<Long>, on: Boolean) {
        ids = if (on) ids + list else ids - list.toSet()
    }

    fun clear() {
        ids = emptySet()
    }

    fun pick(items: List<Media>): List<Media> {
        val s = ids
        return items.filter { it.id in s }
    }
}

/**
 * The photo grid used everywhere: date headings, pinch to change the size, long-press and drag to
 * select, and a fast-scroll handle with the month.
 */
@Composable
fun MediaGrid(
    items: List<Media>,
    state: LazyGridState,
    cols: Int,
    selection: Selection,
    important: Set<Long>,
    modifier: Modifier = Modifier,
    onCols: ((Int) -> Unit)? = null,
    headers: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    top: (@Composable () -> Unit)? = null,
    badge: (@Composable BoxScope.(Media) -> Unit)? = null,
    /** Photos on top of a stack of similar shots, with how many are in the stack. */
    stacks: Map<Long, Int> = emptyMap(),
    /** Space under the status bar, so the fast-scroll handle never hides behind it. */
    topInset: androidx.compose.ui.unit.Dp = 0.dp,
    /** Receives a function that scrolls a photo into view (used when the viewer closes). */
    registerReveal: ((suspend (Long) -> Unit) -> Unit)? = null,
    onOpen: (Int) -> Unit,
) {
    val byMonth = cols >= 5
    val motion = LocalMotion.current
    val haptics = LocalHapticFeedback.current
    val rows by produceState(initialValue = emptyList<GridRow>(), items, byMonth, headers) {
        value = withContext(Dispatchers.Default) {
            if (headers) {
                Timeline.rows(LongArray(items.size) { items[it].taken }, LongArray(items.size) { items[it].id }, byMonth)
            } else {
                items.mapIndexed { i, m -> GridRow.Cell(i, m.id) }
            }
        }
    }
    val offset = if (top != null) 1 else 0
    val density = LocalDensity.current
    val latestRows by rememberUpdatedState(rows)
    LaunchedEffect(registerReveal, state) {
        registerReveal?.invoke { id ->
            val target = HeroBounds.alias[id] ?: id
            val idx = latestRows.indexOfFirst { it is GridRow.Cell && it.id == target }
            if (idx >= 0) {
                val gi = idx + offset
                val info = state.layoutInfo
                val vis = info.visibleItemsInfo.firstOrNull { it.index == gi }
                val fully = vis != null && vis.offset.y >= info.viewportStartOffset + 40 && vis.offset.y + vis.size.height <= info.viewportEndOffset - 160
                if (!fully) state.scrollToItem(gi, -(info.viewportSize.height / 3))
            }
        }
    }
    val scope = rememberCoroutineScope()
    var autoScroll by remember { mutableFloatStateOf(0f) }
    val currentCols by rememberUpdatedState(cols)
    val currentOnCols by rememberUpdatedState(onCols)

    LaunchedEffect(autoScroll) {
        if (autoScroll == 0f) return@LaunchedEffect
        while (isActive) {
            state.scrollBy(autoScroll)
            delay(12)
        }
    }

    BoxWithConstraints(modifier) {
        val cellPx = with(density) { (maxWidth / cols).toPx() }.roundToInt()
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            state = state,
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // Two fingers: zoom the grid in or out a step at a time.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var zoom = 1f
                        do {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val change = currentOnCols
                            if (change != null && ev.changes.count { it.pressed } >= 2) {
                                zoom *= ev.calculateZoom()
                                ev.changes.forEach { it.consume() }
                                if (zoom > 1.28f && currentCols > 2) {
                                    change(currentCols - 1)
                                    zoom = 1f
                                } else if (zoom < 0.78f && currentCols < 6) {
                                    change(currentCols + 1)
                                    zoom = 1f
                                }
                            }
                        } while (ev.changes.any { it.pressed })
                    }
                }
                .pointerInput(rows, items) {
                    // Long-press then drag: select everything between the first and the current photo.
                    var start = -1
                    var current = -1
                    var base = emptySet<Long>()
                    var adding = true
                    fun itemAt(p: Offset): Int? {
                        val hit = state.layoutInfo.visibleItemsInfo.firstOrNull {
                            IntRect(it.offset, it.size).contains(p.round())
                        } ?: return null
                        val r = rows.getOrNull(hit.index - offset) as? GridRow.Cell ?: return null
                        return r.index
                    }
                    detectDragGesturesAfterLongPress(
                        onDragStart = { p ->
                            val i = itemAt(p)
                            if (i != null) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                start = i
                                current = i
                                base = selection.ids
                                adding = items[i].id !in base || !selection.active
                                selection.set(listOf(items[i].id), true)
                            }
                        },
                        onDragEnd = { start = -1; autoScroll = 0f },
                        onDragCancel = { start = -1; autoScroll = 0f },
                        onDrag = { change, _ ->
                            if (start < 0) return@detectDragGesturesAfterLongPress
                            val edge = with(density) { 72.dp.toPx() }
                            val y = change.position.y
                            autoScroll = when {
                                y > size.height - edge -> 14f * ((y - (size.height - edge)) / edge).coerceIn(0.2f, 1.6f) * density.density
                                y < edge -> -14f * ((edge - y) / edge).coerceIn(0.2f, 1.6f) * density.density
                                else -> 0f
                            }
                            val i = itemAt(change.position)
                            if (i != null && i != current) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                current = i
                                val range = minOf(start, i)..maxOf(start, i)
                                val ids = range.map { items[it].id }
                                selection.ids = if (adding) base + ids else base - ids.toSet()
                            }
                        },
                    )
                },
        ) {
            if (top != null) {
                item(key = "top", span = { GridItemSpan(maxLineSpan) }, contentType = "top") { top() }
            }
            items(
                count = rows.size,
                key = { rows[it].key },
                span = { if (rows[it] is GridRow.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
                contentType = { if (rows[it] is GridRow.Header) "h" else "c" },
            ) { i ->
                when (val r = rows[i]) {
                    is GridRow.Header -> DateHeader(r, items, selection)
                    is GridRow.Cell -> {
                        val m = items.getOrNull(r.index)
                        if (m != null) {
                            Cell(
                                m = m,
                                px = cellPx,
                                selected = m.id in selection.ids,
                                selecting = selection.active,
                                important = m.id in important,
                                stack = stacks[m.id] ?: 0,
                                badge = badge,
                                modifier = Modifier.animateItem(fadeInSpec = null, placementSpec = motion.placement, fadeOutSpec = null),
                                onClick = { if (selection.active) selection.toggle(m.id) else onOpen(r.index) },
                                onLongClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selection.set(listOf(m.id), true)
                                },
                            )
                        }
                    }
                }
            }
        }
        if (items.size > 80) {
            FastScroller(
                state, items, rows, offset,
                Modifier.align(Alignment.CenterEnd).padding(top = topInset + 12.dp, bottom = contentPadding.calculateBottomPadding() + 8.dp),
            )
        }
    }
}

@Composable
private fun DateHeader(h: GridRow.Header, items: List<Media>, selection: Selection) {
    val ids = remember(h, items) { (h.from until (h.from + h.count).coerceAtMost(items.size)).map { items[it].id } }
    val all = selection.active && ids.all { it in selection.ids }
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = if (h.big) 22.dp else 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            h.title,
            style = if (h.big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
            fontWeight = if (h.big) FontWeight.Bold else FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        AnimatedVisibility(selection.active, enter = fadeIn(), exit = fadeOut()) {
            Icon(
                if (all) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                if (all) "Unselect day" else "Select day",
                tint = if (all) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp).clip(CircleShape).combinedClickable(onClick = { selection.set(ids, !all) }).padding(2.dp),
            )
        }
    }
}

@Composable
private fun Cell(
    m: Media,
    px: Int,
    selected: Boolean,
    selecting: Boolean,
    important: Boolean,
    stack: Int,
    badge: (@Composable BoxScope.(Media) -> Unit)?,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val inset by animateDpAsState(if (selected) 10.dp else 0.dp, label = "inset")
    val corner by animateDpAsState(if (selected) 18.dp else 3.dp, label = "corner")
    val c = MaterialTheme.colorScheme
    Box(
        modifier
            .aspectRatio(1f)
            .heroSource(m.id)
            .graphicsLayer { alpha = if (HeroBounds.hidden == m.id) 0f else 1f }
            .background(if (selected) c.secondaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(Modifier.fillMaxSize().padding(inset).clip(RoundedCornerShape(corner))) {
            Thumb(m, px, Modifier.fillMaxSize())
            if (m.isVideo) VideoBadge(m.durationMs, Modifier.align(Alignment.BottomEnd).padding(5.dp))
            if (stack > 1) StackBadge(stack, Modifier.align(Alignment.TopEnd).padding(5.dp))
            if (important) StarBadge(Modifier.align(Alignment.TopStart).padding(5.dp))
            badge?.invoke(this, m)
        }
        if (selecting) {
            val tint = if (selected) c.primary else Color.White
            Icon(
                if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                null,
                tint = tint,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp)
                    .then(if (selected) Modifier.background(c.onPrimary, CircleShape) else Modifier),
            )
        }
    }
}

/** "4" with a stack icon, on a photo that has similar shots tucked under it. */
@Composable
private fun StackBadge(n: Int, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.BurstMode, null, tint = Color.White, modifier = Modifier.size(13.dp))
        GapW(3.dp)
        Text("$n", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

/** A handle on the right edge that jumps through the years, showing the month while dragging. */
@Composable
private fun FastScroller(state: LazyGridState, items: List<Media>, rows: List<GridRow>, offset: Int, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var dragFrac by remember { mutableFloatStateOf(0f) }
    val total = rows.size + offset
    val frac by remember(total) {
        derivedStateOf { if (total <= 1) 0f else (state.firstVisibleItemIndex.toFloat() / (total - 1)).coerceIn(0f, 1f) }
    }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(state.isScrollInProgress, dragging) {
        if (state.isScrollInProgress || dragging) visible = true
        else {
            delay(1400)
            visible = false
        }
    }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, label = "scroller")
    val shown = if (dragging) dragFrac else frac
    BoxWithConstraints(modifier.fillMaxHeight().width(56.dp).graphicsLayer { this.alpha = alpha }) {
        val h = constraints.maxHeight.toFloat()
        val thumbH = with(LocalDensity.current) { 52.dp.toPx() }
        val y = (h - thumbH) * shown
        val month = remember(shown, rows) {
            val idx = ((total - 1) * shown).roundToInt() - offset
            val r = rows.getOrNull(idx.coerceIn(0, (rows.size - 1).coerceAtLeast(0)))
            val itemIndex = when (r) {
                is GridRow.Cell -> r.index
                is GridRow.Header -> r.from
                null -> 0
            }
            items.getOrNull(itemIndex)?.let { Timeline.bubble(it.taken) }.orEmpty()
        }
        if (dragging && month.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 4.dp,
                modifier = Modifier.align(Alignment.TopEnd).offset { IntOffset(-(64 * density).roundToInt(), y.roundToInt()) },
            ) {
                Text(
                    month, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), maxLines = 1, softWrap = false,
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, y.roundToInt()) }
                .padding(end = 4.dp)
                .size(width = 28.dp, height = 52.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = if (dragging) 1f else 0.85f))
                .border(2.dp, MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), RoundedCornerShape(50))
                .pointerInput(total) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            dragging = true
                            dragFrac = frac
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                        onVerticalDrag = { change, dy ->
                            change.consume()
                            dragFrac = (dragFrac + dy / (h - thumbH)).coerceIn(0f, 1f)
                            val target = ((total - 1) * dragFrac).roundToInt()
                            scope.launch { state.scrollToItem(target) }
                        },
                    )
                },
        )
    }
}
