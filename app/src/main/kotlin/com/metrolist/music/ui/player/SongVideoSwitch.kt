/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import com.metrolist.music.ui.theme.LocalNothingTheme

@Composable
fun SongVideoSwitch(
    isVideoMode: Boolean,
    isVideoAvailable: Boolean,
    onModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isNothing = LocalNothingTheme.current
    val unavailableMsg = stringResource(R.string.video_not_available)

    val containerBg = if (isNothing) {
        Color(0xFF141414).copy(alpha = 0.85f)
    } else {
        Color.Black.copy(alpha = 0.45f)
    }

    val selectedBg = Color.White

    val selectedIconColor = Color.Black
    val unselectedIconColor = Color.White.copy(alpha = 0.7f)

    BoxWithConstraints(
        modifier = modifier
            .width(88.dp)
            .height(34.dp)
            .clip(CircleShape)
            .background(containerBg)
            .border(
                BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                CircleShape,
            )
            .padding(2.5.dp),
    ) {
        val segmentWidth = maxWidth / 2

        val indicatorOffset by animateDpAsState(
            targetValue = if (isVideoMode) segmentWidth else 0.dp,
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 500f),
            label = "pill_indicator_offset",
        )

        // Sliding white indicator
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(selectedBg),
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Song (Audio) Tab
            val songColor by animateColorAsState(
                targetValue = if (!isVideoMode) selectedIconColor else unselectedIconColor,
                label = "song_icon_color",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onModeChange(false) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.headphones),
                    contentDescription = stringResource(R.string.player_switch_song),
                    tint = songColor,
                    modifier = Modifier.size(18.dp),
                )
            }

            // Video Tab
            val videoColor by animateColorAsState(
                targetValue = if (isVideoMode) selectedIconColor else unselectedIconColor,
                label = "video_icon_color",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .alpha(if (isVideoAvailable) 1f else 0.45f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (isVideoAvailable) {
                                onModeChange(true)
                            } else {
                                Toast.makeText(context, unavailableMsg, Toast.LENGTH_SHORT).show()
                            }
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.smart_display),
                    contentDescription = stringResource(R.string.player_switch_video),
                    tint = videoColor,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
