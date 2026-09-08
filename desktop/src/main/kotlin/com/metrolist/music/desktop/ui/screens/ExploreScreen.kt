package com.metrolist.music.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Surfing
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class MoodCategory(
    val name: String,
    val description: String,
    val icon: ImageVector,
    val gradient: List<Color>
)

val MoodCategories = listOf(
    MoodCategory("Chill & Relax", "Unwind with mellow beats", Icons.Default.SelfImprovement, listOf(Color(0xFF2E3192), Color(0xFF1BFFFF))),
    MoodCategory("Workout & Energy", "Power your training session", Icons.Default.FitnessCenter, listOf(Color(0xFFD4145A), Color(0xFFFBB03B))),
    MoodCategory("Deep Focus", "Boost productivity & flow", Icons.Default.Psychology, listOf(Color(0xFF662D8C), Color(0xFFED1E79))),
    MoodCategory("Feel Good", "Uplifting sunny vibes", Icons.Default.WbSunny, listOf(Color(0xFFFF512F), Color(0xFFDD2476))),
    MoodCategory("Gaming & Synthwave", "Electronic & cyberpunk atmospheres", Icons.Default.SportsEsports, listOf(Color(0xFF8A2387), Color(0xFFE94057))),
    MoodCategory("Party & Dance", "High-energy festival anthems", Icons.Default.ElectricBolt, listOf(Color(0xFFFF0844), Color(0xFFFFB199))),
    MoodCategory("Sleep & Ambient", "Drift into restful soundscapes", Icons.Default.Nightlight, listOf(Color(0xFF0F2027), Color(0xFF2C5364))),
    MoodCategory("Top Charts Global", "The world's most streamed hits", Icons.Default.TrendingUp, listOf(Color(0xFF11998E), Color(0xFF38EF7D))),
    MoodCategory("Romance & Soul", "Heartfelt ballads & R&B", Icons.Default.Favorite, listOf(Color(0xFFB06AB3), Color(0xFF4568DC))),
    MoodCategory("Summer & Beach", "Sun-soaked acoustic rhythms", Icons.Default.Surfing, listOf(Color(0xFFF857A6), Color(0xFFFF5858)))
)

@Composable
fun ExploreScreen(
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(28.dp)
    ) {
        Text(
            text = "Explore Moods & Genres",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Dive into curated music vibes tailored to what you are doing",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 240.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(MoodCategories) { category ->
                MoodCategoryCard(
                    category = category,
                    onClick = { onCategoryClick(category.name) }
                )
            }
        }
    }
}

@Composable
fun MoodCategoryCard(
    category: MoodCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(130.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(category.gradient))
            .clickable { onClick() }
            .padding(18.dp)
    ) {
        Column(modifier = Modifier.align(Alignment.TopStart)) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = category.description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp
            )
        }

        Icon(
            imageVector = category.icon,
            contentDescription = category.name,
            tint = Color.White.copy(alpha = 0.25f),
            modifier = Modifier.size(56.dp).align(Alignment.BottomEnd)
        )
    }
}
