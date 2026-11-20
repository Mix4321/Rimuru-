package com.example

import com.example.data.model.RoutePoint
import com.example.ui.components.MoveMapStyle
import com.example.ui.components.RouteJsonCodec
import com.example.ui.components.computeKmSplitMarkers
import com.example.ui.components.haversineMeters
import org.junit.Assert.assertEquals
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
