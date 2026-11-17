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
    val metValue: Double,
    val kcalPerMinute: Double,
    val supportsSteps: Boolean,
    val supportsDistance: Boolean,
    val supportsReps: Boolean,
    val strideMeters: Double,
    val defaultTrackingMode: TrackingMode
) {
    RUNNING(
        thaiName = "วิ่ง",
        subtitle = "จับก้าว ระยะทาง GPS และความเร็ว",
        icon = Icons.AutoMirrored.Rounded.DirectionsRun,
        metValue = 9.8,
        kcalPerMinute = 9.8,
        supportsSteps = true,
        supportsDistance = true,
        supportsReps = false,
        strideMeters = 0.95,
        defaultTrackingMode = TrackingMode.SENSOR_GPS
    ),
    WALKING(
        thaiName = "เดิน",
        subtitle = "เดินเพื่อสุขภาพ นับก้าวและระยะทางจริง",
        icon = Icons.AutoMirrored.Rounded.DirectionsWalk,
        metValue = 3.8,
        kcalPerMinute = 4.2,
        supportsSteps = true,
        supportsDistance = true,
        supportsReps = false,
        strideMeters = 0.72,
        defaultTrackingMode = TrackingMode.SENSOR_GPS
    ),
    JUMP_ROPE(
        thaiName = "กระโดดเชือก",
        subtitle = "จับจังหวะกระโดดด้วย AI และเซนเซอร์",
        icon = Icons.Rounded.SportsHandball,
        metValue = 11.0,
        kcalPerMinute = 11.5,
        supportsSteps = false,
        supportsDistance = false,
        supportsReps = true,
        strideMeters = 0.0,
        defaultTrackingMode = TrackingMode.CAMERA_AI
    ),
    FOOTBALL(
        thaiName = "ฟุตบอล",
        subtitle = "จับก้าว ระยะทางสนาม และการวิ่ง",
        icon = Icons.Rounded.SportsSoccer,
        metValue = 8.0,
        kcalPerMinute = 8.8,
        supportsSteps = true,
        supportsDistance = true,
        supportsReps = false,
        strideMeters = 0.85,
        defaultTrackingMode = TrackingMode.SENSOR_GPS
    ),
    BASKETBALL(
        thaiName = "บาสเกตบอล",
        subtitle = "จับก้าว การกระโดด และความเข้มข้น",
        icon = Icons.Rounded.SportsBasketball,
        metValue = 7.5,
        kcalPerMinute = 8.2,
        supportsSteps = true,
        supportsDistance = true,
        supportsReps = false,
        strideMeters = 0.85,
        defaultTrackingMode = TrackingMode.SENSOR_GPS
    ),
    BADMINTON(
        thaiName = "แบดมินตัน",
        subtitle = "จับฟุตเวิร์กและการเคลื่อนไหวคอร์ท",
        icon = Icons.Rounded.SportsTennis,
        metValue = 6.5,
        kcalPerMinute = 6.8,
        supportsSteps = true,
        supportsDistance = false,
        supportsReps = true,
        strideMeters = 0.75,
        defaultTrackingMode = TrackingMode.CAMERA_AI
    ),
    CYCLING(
        thaiName = "ปั่นจักรยาน",
        subtitle = "จับระยะทาง GPS และความเร็วปั่น",
        icon = Icons.AutoMirrored.Rounded.DirectionsBike,
        metValue = 7.5,
        kcalPerMinute = 7.8,
        supportsSteps = false,
        supportsDistance = true,
        supportsReps = false,
        strideMeters = 0.0,
        defaultTrackingMode = TrackingMode.SENSOR_GPS
    ),
    GENERAL(
        thaiName = "ออกกำลังกายทั่วไป",
        subtitle = "นับครั้งสควอท/วิดพื้น/บอดี้เวทด้วยกล้อง AI",
        icon = Icons.Rounded.FitnessCenter,
        metValue = 5.5,
        kcalPerMinute = 6.0,
        supportsSteps = false,
        supportsDistance = false,
        supportsReps = true,
        strideMeters = 0.0,
        defaultTrackingMode = TrackingMode.CAMERA_AI
    );

    companion object {
        fun fromNameSafe(raw: String?): SportType =
            entries.firstOrNull { it.name == raw } ?: GENERAL
    }
}
