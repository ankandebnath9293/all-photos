package com.ankandebnath.allphotos.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
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
 * The floating bottom bar: a pill of tabs that the photos scroll behind, with the search button
 * beside it. The chosen tab grows to show its name.
 */
@Composable
fun FloatingNav(tabs: List<NavTab>, selected: Int, onSelect: (Int) -> Unit, onSearch: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val motion = LocalMotion.current
    val haptics = LocalHapticFeedback.current
    Row(modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            shape = RoundedCornerShape(50),
            color = c.surfaceContainerHigh.copy(alpha = 0.94f),
            shadowElevation = 10.dp,
            tonalElevation = 2.dp,
        ) {
            Row(Modifier.height(62.dp).padding(horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                tabs.forEachIndexed { i, t ->
                    val on = i == selected
                    val bg by animateColorAsState(if (on) c.secondaryContainer else c.secondaryContainer.copy(alpha = 0f), motion.quick(), label = "tabbg")
                    val fg by animateColorAsState(if (on) c.onSecondaryContainer else c.onSurfaceVariant, motion.quick(), label = "tabfg")
                    Row(
                        Modifier
                            .height(52.dp)
                            .clip(RoundedCornerShape(50))
                            .background(bg)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = ripple()) {
                                if (!on) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(i)
                            }
                            .animateContentSize(motion.quick())
                            .padding(horizontal = if (on) 16.dp else 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (on) t.on else t.off, t.label, tint = fg)
                        AnimatedVisibility(on, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                            Text(t.label, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
        Surface(
            shape = CircleShape,
            color = c.primaryContainer,
            shadowElevation = 10.dp,
            modifier = Modifier.size(58.dp).clip(CircleShape).clickable(onClick = onSearch),
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Search, "Search", tint = c.onPrimaryContainer, modifier = Modifier.size(26.dp)) }
        }
    }
}
