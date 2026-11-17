package com.example.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.example.data.model.WorkoutSession
import com.example.domain.DateStreakUtils
import com.example.ui.components.RouteJsonCodec
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.max

object WorkoutShareHelper {

    fun shareWorkoutCard(
        context: Context,
        session: WorkoutSession,
        streakDays: Int
    ): Result<Unit> = runCatching {
        val bitmap = renderPosterBitmap(session, streakDays)
        val shareDir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(shareDir, "move_workout_${session.id.take(12)}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
        }
        bitmap.recycle()

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareText = buildString {
            append("ฉันเพิ่งออกกำลังกาย ${session.sport.thaiName} ด้วยแอป MOVE! ")
            append("เวลา ${DateStreakUtils.formatMinutesThai(session.durationSeconds)} ")
            append("• เผาผลาญ ${session.caloriesBurned} kcal")
            if (session.repsCount > 0) append(" • ${session.repsCount} ครั้ง")
            if (session.distanceMeters > 0) append(" • ${DateStreakUtils.formatDistanceKm(session.distanceMeters)}")
            if (streakDays > 0) append(" • Streak $streakDays วัน")
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, shareText)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "แชร์ผลกิจกรรม MOVE"))
    }

    private fun renderPosterBitmap(session: WorkoutSession, streakDays: Int): Bitmap {
        val w = 1080
        val h = 1350
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                intArrayOf(
                    Color.parseColor("#0A2E1E"),
                    Color.parseColor("#1B4332"),
                    Color.parseColor("#0F2D21")
                ),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        // Decorative glowing accents
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(38, 116, 198, 157)
        }
        canvas.drawCircle(940f, 180f, 240f, glowPaint)
        canvas.drawCircle(160f, 1160f, 210f, glowPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#74C69D")
            textSize = 42f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("MOVE • SMART ACTIVITY TRACKER", 72f, 108f, titlePaint)

        val sportPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 78f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(session.sport.thaiName, 72f, 210f, sportPaint)

        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 255, 255, 255)
            textSize = 36f
        }
        canvas.drawText(
            "${DateStreakUtils.formatDateTimeThai(session.startedAtMillis)} • ${session.mode.thaiLabel}",
            72f,
            268f,
            datePaint
        )

        // Hero Duration & Calories Card
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(42, 255, 255, 255)
        }
        canvas.drawRoundRect(RectF(72f, 320f, w - 72f, 590f), 36f, 36f, cardPaint)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B7E4C7")
            textSize = 34f
        }
        val bigValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 96f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val amberValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFB703")
            textSize = 92f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        canvas.drawText("ระยะเวลา", 116f, 390f, labelPaint)
        canvas.drawText(DateStreakUtils.formatDurationClock(session.durationSeconds), 116f, 505f, bigValuePaint)

        canvas.drawText("เผาผลาญ", 590f, 390f, labelPaint)
        canvas.drawText("${session.caloriesBurned} kcal", 590f, 505f, amberValuePaint)

        // Secondary stats grid
        val statRows = buildList {
            if (session.steps > 0) add("จำนวนก้าว" to "${session.steps} ก้าว")
            if (session.repsCount > 0) add("จำนวนครั้ง" to "${session.repsCount} ครั้ง")
            if (session.distanceMeters > 0) add("ระยะทาง" to DateStreakUtils.formatDistanceKm(session.distanceMeters))
            if (session.avgFormScore > 0) add("คะแนนฟอร์ม AI" to "${session.avgFormScore}%")
            add("Streak ต่อเนื่อง" to "${max(1, streakDays)} วัน")
        }.take(4)

        var gridY = 630f
        statRows.chunked(2).forEach { pair ->
            pair.forEachIndexed { idx, (lbl, valStr) ->
                val left = if (idx == 0) 72f else 560f
                val right = if (idx == 0) 520f else w - 72f
                canvas.drawRoundRect(RectF(left, gridY, right, gridY + 170f), 28f, 28f, cardPaint)
                canvas.drawText(lbl, left + 36f, gridY + 62f, labelPaint)
                val vPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#74C69D")
                    textSize = 52f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                canvas.drawText(valStr, left + 36f, gridY + 130f, vPaint)
            }
            gridY += 196f
        }

        // Draw GPS Route Preview if available
        val routePoints = RouteJsonCodec.decode(session.routeJson)
        if (routePoints.size >= 2) {
            val mapTop = max(gridY + 16f, 1020f)
            val mapRect = RectF(72f, mapTop, w - 72f, h - 105f)
            canvas.drawRoundRect(mapRect, 28f, 28f, cardPaint)

            val minLat = routePoints.minOf { it.lat }
            val maxLat = routePoints.maxOf { it.lat }
            val minLng = routePoints.minOf { it.lng }
            val maxLng = routePoints.maxOf { it.lng }
            val latSpan = max(1e-5, maxLat - minLat)
            val lngSpan = max(1e-5, maxLng - minLng)
            val padW = mapRect.width() - 80f
            val padH = mapRect.height() - 60f
            val scale = minOf(padW / lngSpan.toFloat(), padH / latSpan.toFloat())
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#74C69D")
                strokeWidth = 10f
                strokeCap = Paint.Cap.ROUND
            }
            for (i in 0 until routePoints.lastIndex) {
                val p1 = routePoints[i]
                val p2 = routePoints[i + 1]
                val x1 = mapRect.left + 40f + ((p1.lng - minLng).toFloat() * scale)
                val y1 = mapRect.bottom - 30f - ((p1.lat - minLat).toFloat() * scale)
                val x2 = mapRect.left + 40f + ((p2.lng - minLng).toFloat() * scale)
                val y2 = mapRect.bottom - 30f - ((p2.lat - minLat).toFloat() * scale)
                canvas.drawLine(x1, y1, x2, y2, linePaint)
            }
        }

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 255, 255, 255)
            textSize = 30f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            String.format(Locale.US, "สร้างจาก MOVE • %.0f kcal/เซสชัน", session.caloriesBurned.toFloat()),
            w / 2f,
            h - 42f,
            footerPaint
        )
        return bmp
    }
}
