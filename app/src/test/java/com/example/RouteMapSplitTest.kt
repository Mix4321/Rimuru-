package com.example

import com.example.data.model.RoutePoint
import com.example.ui.components.MoveMapStyle
import com.example.ui.components.RouteJsonCodec
import com.example.ui.components.computeDetailedKmSplits
import com.example.ui.components.computeKmSplitMarkers
import com.example.ui.components.haversineMeters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteMapSplitTest {

    @Test
    fun `haversine computes accurate street distance and km splits`() {
        // ~111.19 meters per 0.001 degree of latitude
        val p0 = RoutePoint(lat = 13.7500, lng = 100.5000, speedKmh = 8.5f, elapsedSec = 0)
        val p1 = RoutePoint(lat = 13.7600, lng = 100.5000, speedKmh = 9.0f, elapsedSec = 440) // ~1,111.9m
        val p2 = RoutePoint(lat = 13.7700, lng = 100.5000, speedKmh = 11.5f, elapsedSec = 860) // ~2,223.9m

        val d01 = haversineMeters(p0.lat, p0.lng, p1.lat, p1.lng)
        assertTrue("Expected ~1112m, got $d01", d01 in 1100.0..1125.0)

        val splits = computeKmSplitMarkers(listOf(p0, p1, p2))
        assertEquals(2, splits.size)
        assertEquals(1, splits[0].kmNumber)
        assertEquals(2, splits[1].kmNumber)
        assertTrue(splits[0].lat in p0.lat..p1.lat)
        assertTrue(splits[1].lat in p1.lat..p2.lat)
    }

    @Test
    fun `computeDetailedKmSplits computes full km splits, trailing partial leg, and fastest badge`() {
        // 0m -> ~1112m (400s, slower) -> ~2224m (700s, faster!) -> ~2557m (820s, trailing ~557m)
        val points = listOf(
            RoutePoint(lat = 13.7500, lng = 100.5000, speedKmh = 9.0f, elapsedSec = 0),
            RoutePoint(lat = 13.7600, lng = 100.5000, speedKmh = 10.0f, elapsedSec = 400),
            RoutePoint(lat = 13.7700, lng = 100.5000, speedKmh = 13.0f, elapsedSec = 700),
            RoutePoint(lat = 13.7730, lng = 100.5000, speedKmh = 10.5f, elapsedSec = 820)
        )

        val splits = computeDetailedKmSplits(points)
        assertEquals(3, splits.size)

        // Split 1: full 1 km
        assertEquals(1, splits[0].splitIndex)
        assertFalse(splits[0].isPartialLeg)
        assertEquals(1000, splits[0].segmentMeters)
        assertFalse(splits[0].isFastest)

        // Split 2: full 1 km and faster than split 1
        assertEquals(2, splits[1].splitIndex)
        assertFalse(splits[1].isPartialLeg)
        assertEquals(1000, splits[1].segmentMeters)
        assertTrue(splits[1].paceSecondsPerKm < splits[0].paceSecondsPerKm)
        assertTrue(splits[1].isFastest)

        // Split 3: trailing ~557m partial leg
        assertEquals(3, splits[2].splitIndex)
        assertTrue(splits[2].isPartialLeg)
        assertTrue(splits[2].segmentMeters in 540..575)
        assertFalse(splits[2].isFastest)
    }

    @Test
    fun `computeDetailedKmSplits handles short distance below 1km and sub-100m threshold`() {
        val shortValid = listOf(
            RoutePoint(lat = 13.7500, lng = 100.5000, speedKmh = 6.0f, elapsedSec = 0),
            RoutePoint(lat = 13.7520, lng = 100.5000, speedKmh = 6.0f, elapsedSec = 130) // ~222m
        )
        val shortSplits = computeDetailedKmSplits(shortValid)
        assertEquals(1, shortSplits.size)
        assertTrue(shortSplits[0].isPartialLeg)
        assertTrue(shortSplits[0].segmentMeters in 215..230)

        val tiny = listOf(
            RoutePoint(lat = 13.7500, lng = 100.5000, speedKmh = 5.0f, elapsedSec = 0),
            RoutePoint(lat = 13.7504, lng = 100.5000, speedKmh = 5.0f, elapsedSec = 30) // ~44m
        )
        assertTrue(computeDetailedKmSplits(tiny).isEmpty())
    }

    @Test
    fun `route json codec round trips points accurately`() {
        val original = listOf(
            RoutePoint(13.7563, 100.5018, 5.4f, 0),
            RoutePoint(13.7570, 100.5024, 10.2f, 45)
        )
        val encoded = RouteJsonCodec.encode(original)
        val decoded = RouteJsonCodec.decode(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `map styles cycle completely`() {
        val first = MoveMapStyle.STREET
        val second = first.next()
        val third = second.next()
        assertEquals(MoveMapStyle.CLEAN_LIGHT, second)
        assertEquals(MoveMapStyle.TOPO, third)
        assertEquals(MoveMapStyle.STREET, third.next())
    }
}
