package com.ankandebnath.allphotos.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class NavTab(val label: String, val on: ImageVector, val off: ImageVector)

/**
 * The floating bottom bar: a pill of four fixed tabs that the photos scroll behind. Every tab keeps
 * its icon and name; the chosen one gets a soft highlight behind its icon.
 */
@Composable
fun FloatingNav(tabs: List<NavTab>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val motion = LocalMotion.current
    val haptics = LocalHapticFeedback.current
    Surface(
        shape = RoundedCornerShape(50),
        color = c.surfaceContainerHigh.copy(alpha = 0.95f),
        shadowElevation = 10.dp,
        tonalElevation = 2.dp,
        modifier = modifier.padding(horizontal = 20.dp).widthIn(max = 460.dp).fillMaxWidth(),
    ) {
        Row(Modifier.height(66.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEachIndexed { i, t ->
                val on = i == selected
                val bg by animateColorAsState(if (on) c.secondaryContainer else c.secondaryContainer.copy(alpha = 0f), motion.quick(), label = "tabbg")
                val fg by animateColorAsState(if (on) c.onSurface else c.onSurfaceVariant, motion.quick(), label = "tabfg")
                val pill by animateFloatAsState(if (on) 1f else 0.6f, motion.quick(), label = "tabpill")
                Column(
                    Modifier
                        .weight(1f)
                        .height(58.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = ripple()) {
                            if (!on) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(i)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .height(30.dp)
                            .width(56.dp)
                            .drawBehind {
                                val w = size.width * pill
                                drawRoundRect(bg, topLeft = Offset((size.width - w) / 2f, 0f), size = Size(w, size.height), cornerRadius = CornerRadius(size.height / 2f))
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(if (on) t.on else t.off, null, tint = if (on) c.onSecondaryContainer else fg, modifier = Modifier.size(22.dp))
                    }
                    Text(
                        t.label,
                        color = fg,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}
