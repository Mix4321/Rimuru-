package com.example.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.SportsBasketball
import androidx.compose.material.icons.rounded.SportsHandball
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.SportsTennis
import androidx.compose.ui.graphics.vector.ImageVector

enum class SportType(
    val thaiName: String,
    val subtitle: String,
    val icon: ImageVector,
    val kcalPerMinute: Double,
    val supportsSteps: Boolean,
    val supportsDistance: Boolean,
    val stepsPerMinute: Double,
    val metersPerMinute: Double
) {
    RUNNING(
        thaiName = "วิ่ง",
        subtitle = "จับระยะทาง ก้าว และเผาผลาญสูง",
        icon = Icons.AutoMirrored.Rounded.DirectionsRun,
        kcalPerMinute = 9.8,
        supportsSteps = true,
        supportsDistance = true,
        stepsPerMinute = 155.0,
        metersPerMinute = 145.0
    ),
    WALKING(
        thaiName = "เดิน",
        subtitle = "เดินเพื่อสุขภาพ นับก้าวและระยะทาง",
        icon = Icons.AutoMirrored.Rounded.DirectionsWalk,
        kcalPerMinute = 4.2,
        supportsSteps = true,
        supportsDistance = true,
        stepsPerMinute = 108.0,
        metersPerMinute = 80.0
    ),
    JUMP_ROPE(
        thaiName = "กระโดดเชือก",
        subtitle = "คาร์ดิโอความเข้มข้นสูง นับจังหวะ",
        icon = Icons.Rounded.SportsHandball,
        kcalPerMinute = 11.5,
        supportsSteps = true,
        supportsDistance = false,
        stepsPerMinute = 120.0,
        metersPerMinute = 0.0
    ),
    FOOTBALL(
        thaiName = "ฟุตบอล",
        subtitle = "เคลื่อนที่เร็ว นับก้าวและระยะทางสนาม",
        icon = Icons.Rounded.SportsSoccer,
        kcalPerMinute = 8.8,
        supportsSteps = true,
        supportsDistance = true,
        stepsPerMinute = 128.0,
        metersPerMinute = 110.0
    ),
    BASKETBALL(
        thaiName = "บาสเกตบอล",
        subtitle = "สปรินต์และกระโดด เผาผลาญต่อเนื่อง",
        icon = Icons.Rounded.SportsBasketball,
        kcalPerMinute = 8.2,
        supportsSteps = true,
        supportsDistance = true,
        stepsPerMinute = 118.0,
        metersPerMinute = 95.0
    ),
    BADMINTON(
        thaiName = "แบดมินตัน",
        subtitle = "ฟุตเวิร์กและการเคลื่อนไหวรอบคอร์ท",
        icon = Icons.Rounded.SportsTennis,
        kcalPerMinute = 6.8,
        supportsSteps = true,
        supportsDistance = false,
        stepsPerMinute = 105.0,
        metersPerMinute = 0.0
    ),
    CYCLING(
        thaiName = "ปั่นจักรยาน",
        subtitle = "จับระยะทางปั่นและแคลอรีต่อเนื่อง",
        icon = Icons.AutoMirrored.Rounded.DirectionsBike,
        kcalPerMinute = 7.8,
        supportsSteps = false,
        supportsDistance = true,
        stepsPerMinute = 0.0,
        metersPerMinute = 320.0
    ),
    GENERAL(
        thaiName = "ออกกำลังกายทั่วไป",
        subtitle = "เวทเทรนนิ่ง ยืดเหยียด หรือบอดี้เวท",
        icon = Icons.Rounded.FitnessCenter,
        kcalPerMinute = 6.0,
        supportsSteps = false,
        supportsDistance = false,
        stepsPerMinute = 0.0,
        metersPerMinute = 0.0
    );

    companion object {
        fun fromNameSafe(raw: String?): SportType =
            entries.firstOrNull { it.name == raw } ?: GENERAL
    }
}
