package com.example.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubjectAnchorTrackerTest {

    @Test
    fun `acquires lock after 5 consistent frames`() {
        val tracker = SubjectAnchorTracker()
        val imgW = 480f
        val imgH = 640f

        for (i in 1..4) {
            val accepted = tracker.validateAndTrack(
                centerX = 240f,
                centerY = 300f,
                bodyScale = 120f,
                imgW = imgW,
                imgH = imgH,
                now = 1_000L + i * 50L
            )
            assertTrue(accepted)
            assertFalse("Should not lock before 5th consistent frame", tracker.isLocked)
        }

        val acceptedFifth = tracker.validateAndTrack(
            centerX = 242f,
            centerY = 302f,
            bodyScale = 120f,
            imgW = imgW,
            imgH = imgH,
            now = 1_250L
        )
        assertTrue(acceptedFifth)
        assertTrue("Should lock on 5th consistent frame", tracker.isLocked)
        assertEquals(240f, tracker.anchorCenterX, 5f)
        assertEquals(300f, tracker.anchorCenterY, 5f)
    }

    @Test
    fun `rejects passerby running across frame and preserves primary anchor with active ROI`() {
        val tracker = SubjectAnchorTracker()
        val imgW = 480f
        val imgH = 640f
        var t = 10_000L

        // Lock onto primary athlete on the left-center side
        repeat(5) {
            t += 40L
            assertTrue(tracker.validateAndTrack(170f, 320f, 110f, imgW, imgH, t))
        }
        assertTrue(tracker.isLocked)
        val savedX = tracker.anchorCenterX
        val savedY = tracker.anchorCenterY

        // Passerby sprints across on the right side (x = 415)
        t += 40L
        val passerbyAccepted = tracker.validateAndTrack(415f, 310f, 125f, imgW, imgH, t)
        assertFalse("Foreign passerby must be rejected", passerbyAccepted)
        assertTrue("ROI crop should activate immediately", tracker.shouldUseRoiCrop())
        assertTrue(tracker.roiFocusFramesRemaining >= 20)
        assertTrue(tracker.isRecentlyBlockingForeign(t + 200L))
        assertEquals(savedX, tracker.anchorCenterX, 0.01f)
        assertEquals(savedY, tracker.anchorCenterY, 0.01f)

        // Primary athlete reappears inside locked zone -> accepted immediately
        t += 40L
        val athleteAccepted = tracker.validateAndTrack(174f, 322f, 112f, imgW, imgH, t)
        assertTrue(athleteAccepted)
    }

    @Test
    fun `rejects close-up person walking directly in front of camera via scale jump`() {
        val tracker = SubjectAnchorTracker()
        val imgW = 480f
        val imgH = 640f
        var t = 20_000L

        repeat(5) {
            t += 40L
            assertTrue(tracker.validateAndTrack(240f, 320f, 100f, imgW, imgH, t))
        }
        assertTrue(tracker.isLocked)

        // Someone walks very close to the lens, appearing 2x larger in torso scale
        t += 40L
        val closeupHijackAccepted = tracker.validateAndTrack(245f, 325f, 210f, imgW, imgH, t)
        assertFalse("Sudden >1.68x body scale jump should be blocked", closeupHijackAccepted)
        assertTrue(tracker.shouldUseRoiCrop())
    }

    @Test
    fun `tap to lock unmirrors front camera coordinates and re-locks in 2 frames`() {
        val tracker = SubjectAnchorTracker()
        val imgW = 480f
        val imgH = 640f
        // Lock initially at far left (x=100)
        var t = 30_000L
        repeat(5) {
            t += 40L
            tracker.validateAndTrack(100f, 320f, 110f, imgW, imgH, t)
        }
        assertTrue(tracker.isLocked)

        // User taps near the left edge on a mirrored 480x640 view -> unmirrored image X is near right edge (~380)
        tracker.lockAtScreenTap(
            tapX = 100f,
            tapY = 320f,
            viewW = 480f,
            viewH = 640f,
            isMirrored = true,
            imgW = imgW,
            imgH = imgH
        )
        assertFalse(tracker.isLocked)
        assertTrue(tracker.shouldUseRoiCrop())

        val roi = tracker.computeRoiRect(imgW, imgH)
        assertNotNull(roi)
        assertTrue(roi!![0] >= 0f && roi[2] <= imgW)
        assertTrue(roi[1] >= 0f && roi[3] <= imgH)

        // Candidate near tapped right side (~380) locks in just 2 frames
        assertTrue(tracker.validateAndTrack(380f, 320f, 115f, imgW, imgH, t + 40L))
        assertFalse(tracker.isLocked)
        assertTrue(tracker.validateAndTrack(382f, 321f, 115f, imgW, imgH, t + 80L))
        assertTrue("Tap-selected subject should lock within 2 frames", tracker.isLocked)
    }

    @Test
    fun `allows clean re-acquire after primary user absent continuously for over 4_2 seconds`() {
        val tracker = SubjectAnchorTracker()
        val imgW = 480f
        val imgH = 640f
        var t = 40_000L

        repeat(5) {
            t += 40L
            assertTrue(tracker.validateAndTrack(140f, 300f, 110f, imgW, imgH, t))
        }
        assertTrue(tracker.isLocked)

        // User walks completely to a new position (> 4.2s later)
        t += 4_500L
        val acceptedNewArea = tracker.validateAndTrack(360f, 320f, 110f, imgW, imgH, t)
        assertTrue("Should re-acquire cleanly after 4.2s timeout", acceptedNewArea)
        assertFalse(tracker.isLocked)

        tracker.clearLock()
        assertFalse(tracker.shouldUseRoiCrop())
    }
}
