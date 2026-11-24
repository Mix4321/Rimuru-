package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SportsHandball
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.WorkoutCatalog
import com.example.data.model.WorkoutCategory
import com.example.data.model.WorkoutDifficulty
import com.example.data.model.WorkoutProgram
import com.example.ui.components.StaggeredEntrance
import com.example.ui.components.bounceClickable
import com.example.ui.theme.DeepForestGreen
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.NeonLime
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianSurface

@Composable
fun WorkoutCatalogScreen(
    onSelectProgram: (WorkoutProgram) -> Unit,
    onOpenLiveSportsPicker: () -> Unit,
    onOpenNearbySpots: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf(WorkoutCategory.ALL) }

    val visiblePrograms = remember(selectedCategory) {
        if (selectedCategory == WorkoutCategory.ALL) {
            WorkoutCatalog.programs
        } else {
            WorkoutCatalog.programs.filter { it.category == selectedCategory }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(ObsidianBlack, DeepForestGreen.copy(alpha = 0.22f), ObsidianBlack)
                )
            ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header + Sports Tech Quick Actions
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "GREEN PULSE WORKOUTS",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonLime,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "โปรแกรมฝึกซ้อมระดับโปร",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "เลือกโหมดฝึก Strength, Cardio, HIIT และ Yoga พร้อมระบบจับเวลา AI & GPS",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenLiveSportsPicker,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, EmeraldPulse.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("open_live_sports_button")
                    ) {
                        Icon(Icons.Rounded.SportsHandball, contentDescription = null, tint = NeonLime, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("8 กีฬา AI/GPS", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                    OutlinedButton(
                        onClick = onOpenNearbySpots,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("workout_nearby_spots_button")
                    ) {
                        Icon(Icons.Rounded.Explore, contentDescription = null, tint = EmeraldPulse, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("สถานที่ใกล้ฉัน", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        // Exercise Categories Chips (All, Strength, Cardio, HIIT, Yoga)
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(WorkoutCategory.entries, key = { it.name }) { category ->
                    val selected = selectedCategory == category
                    FilterChip(
                        selected = selected,
                        onClick = { selectedCategory = category },
                        label = {
                            Text(
                                text = "${category.title} • ${category.thaiSubtitle}",
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = ObsidianSurface,
                            labelColor = Color.White.copy(alpha = 0.8f),
                            selectedContainerColor = NeonLime,
                            selectedLabelColor = ObsidianBlack
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = EmeraldPulse.copy(alpha = 0.35f),
                            selectedBorderColor = NeonLime
                        ),
                        modifier = Modifier.testTag("category_chip_${category.name}")
                    )
                }
            }
        }

        // Athletic Photography Workout Cards
        itemsIndexed(visiblePrograms, key = { _, p -> p.id }) { index, program ->
            StaggeredEntrance(index = index) {
                WorkoutPhotoCard(
                    program = program,
                    onClick = { onSelectProgram(program) }
                )
            }
        }
    }
}

@Composable
private fun WorkoutPhotoCard(
    program: WorkoutProgram,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, EmeraldPulse.copy(alpha = 0.38f)),
        colors = CardDefaults.cardColors(containerColor = ObsidianElevated),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(238.dp)
            .clip(RoundedCornerShape(26.dp))
            .bounceClickable(onClick = onClick)
            .testTag("workout_program_card_${program.id}")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = program.imageRes),
                contentDescription = program.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Sports-tech dark emerald overlay gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                ObsidianBlack.copy(alpha = 0.2f),
                                ObsidianBlack.copy(alpha = 0.52f),
                                ObsidianBlack.copy(alpha = 0.95f)
                            )
                        )
                    )
            )

            // Top Badges: Category & Difficulty Level
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = EmeraldPulse
                ) {
                    Text(
                        text = program.category.title.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = ObsidianBlack,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                DifficultyPill(difficulty = program.difficulty)
            }

            // Bottom Glass Info Overlay
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = program.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = program.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MetaStatTag(
                            icon = Icons.Rounded.Schedule,
                            text = "${program.durationMinutes} min",
                            tint = NeonLime
                        )
                        MetaStatTag(
                            icon = Icons.Rounded.LocalFireDepartment,
                            text = "${program.estimatedCalories} kcal",
                            tint = EmeraldPulse
                        )
                        MetaStatTag(
                            icon = Icons.Rounded.FitnessCenter,
                            text = "${program.exercises.size} ท่า",
                            tint = Color.White.copy(alpha = 0.88f)
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = NeonLime,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = "เปิดรายละเอียด",
                                tint = ObsidianBlack,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DifficultyPill(difficulty: WorkoutDifficulty) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = ObsidianBlack.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Bolt,
                contentDescription = null,
                tint = NeonLime,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = difficulty.label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun MetaStatTag(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
    }
}
