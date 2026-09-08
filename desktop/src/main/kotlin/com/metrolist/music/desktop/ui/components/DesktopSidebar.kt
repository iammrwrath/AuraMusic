package com.metrolist.music.desktop.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.metrolist.music.desktop.data.DesktopStorage
import com.metrolist.music.desktop.ui.theme.AuraBorder
import com.metrolist.music.desktop.ui.theme.AuraNeonGradient
import com.metrolist.music.desktop.ui.theme.AuraPrimary
import com.metrolist.music.desktop.ui.theme.AuraSurfaceGlass
import kotlinx.coroutines.launch

enum class DesktopNavDestination(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    SEARCH("Search", Icons.Default.Search),
    EXPLORE("Explore", Icons.Default.Explore),
    LIBRARY("Library", Icons.Default.LibraryMusic),
    SETTINGS("Settings", Icons.Default.Settings),
}

@Composable
fun DesktopSidebar(
    selectedDestination: DesktopNavDestination,
    onDestinationSelected: (DesktopNavDestination) -> Unit,
    isExpanded: Boolean = true,
    onSignInClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val userData by DesktopStorage.userData.collectAsState()
    val animatedWidth by animateDpAsState(
        targetValue = if (isExpanded) 230.dp else 72.dp,
        animationSpec = tween(220)
    )

    Column(
        horizontalAlignment = if (isExpanded) Alignment.Start else Alignment.CenterHorizontally,
        modifier = modifier
            .width(animatedWidth)
            .fillMaxHeight()
            .background(AuraSurfaceGlass)
            .border(width = 1.dp, color = AuraBorder)
            .padding(vertical = 20.dp, horizontal = if (isExpanded) 14.dp else 8.dp)
    ) {
        // App Logo & Brand Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 28.dp, start = if (isExpanded) 4.dp else 0.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AuraNeonGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = "AuraMusic",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            if (isExpanded) {
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "AuraMusic",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "v13.8.4 Modern",
                        style = MaterialTheme.typography.labelSmall,
                        color = AuraPrimary
                    )
                }
            }
        }

        // Section label (Expanded only)
        if (isExpanded) {
            Text(
                text = "DISCOVER",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
            )
        }

        // Navigation Items
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            DesktopNavDestination.entries.forEach { destination ->
                val isSelected = destination == selectedDestination
                val backgroundColor = if (isSelected) {
                    AuraPrimary.copy(alpha = 0.15f)
                } else {
                    Color.Transparent
                }
                val contentColor = if (isSelected) {
                    AuraPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (isExpanded) Arrangement.Start else Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(backgroundColor)
                        .clickable { onDestinationSelected(destination) }
                        .padding(horizontal = if (isExpanded) 12.dp else 0.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = destination.label,
                            tint = contentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    if (isExpanded) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = destination.label,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Account Profile Card (Expanded vs Rail)
        if (userData.account.isLoggedIn) {
            if (isExpanded) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, AuraBorder, RoundedCornerShape(14.dp))
                        .padding(10.dp)
                ) {
                    if (!userData.account.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = userData.account.avatarUrl,
                            contentDescription = userData.account.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = AuraPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userData.account.name.ifBlank { "YouTube User" },
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = userData.account.channelHandle.ifBlank { "Connected" },
                            style = MaterialTheme.typography.labelSmall,
                            color = AuraPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                DesktopStorage.logout()
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Sign Out",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else {
                // Rail Profile Avatar
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    if (!userData.account.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = userData.account.avatarUrl,
                            contentDescription = userData.account.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = AuraPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        } else {
            if (isExpanded) {
                Button(
                    onClick = onSignInClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AuraPrimary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sign In",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            } else {
                IconButton(
                    onClick = onSignInClick,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AuraPrimary.copy(alpha = 0.2f))
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Sign In",
                        tint = AuraPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
