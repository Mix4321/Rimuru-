package com.example

import com.example.data.model.SportType
import com.example.data.remote.NearbySpotCategory
import com.example.data.remote.NearbySpotsRepository
import com.example.data.remote.TravelMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbySpotsParsingTest {

    private val repo = NearbySpotsRepository()

    @Test
    fun parseOverpassSpots_sortsByNearestDistanceAndMapsCategories() {
        val sampleJson = """
            {
              "elements": [
                {
                  "type": "way",
                  "id": 1001,
                  "center": { "lat": 13.7380, "lon": 100.5420 },
                  "tags": {
                    "name:th": "สวนลุมพินี",
                    "leisure": "park",
                    "opening_hours": "04:30-21:00"
                  }
                },
                {
                  "type": "node",
                  "id": 1002,
                  "lat": 13.7310,
                  "lon": 100.5320,
                  "tags": {
                    "name": "Bangkok Fitness Hub",
                    "leisure": "fitness_centre",
                    "addr:street": "Rama IV Rd"
                  }
                },
                {
                  "type": "node",
                  "id": 1003,
                  "lat": 13.7350,
                  "lon": 100.5350,
                  "tags": {
                    "name": "Badminton Club",
                    "leisure": "sports_centre",
                    "sport": "badminton"
                  }
                }
              ]
            }
        """.trimIndent()

        // Origin very close to 1002 (13.7310, 100.5320)
        val spots = repo.parseOverpassSpots(
            rawJson = sampleJson,
            originLat = 13.7305,
            originLng = 100.5318
        )

        assertEquals(3, spots.size)
        // Nearest first
        assertEquals("Bangkok Fitness Hub", spots[0].name)
        assertEquals(NearbySpotCategory.FITNESS_GYM, spots[0].category)
        assertEquals("Rama IV Rd", spots[0].addressSummary)

        assertEquals("Badminton Club", spots[1].name)
        assertEquals(NearbySpotCategory.SPORTS_COURT, spots[1].category)
        assertEquals(SportType.BADMINTON, spots[1].suggestedSport)

        assertEquals("สวนลุมพินี", spots[2].name)
        assertEquals(NearbySpotCategory.PARK_RUN, spots[2].category)
        assertEquals(SportType.RUNNING, spots[2].suggestedSport)
        assertEquals("04:30-21:00", spots[2].openingHours)
        assertTrue(spots[0].distanceMeters <= spots[1].distanceMeters)
        assertTrue(spots[1].distanceMeters <= spots[2].distanceMeters)
    }

    @Test
    fun parseOsrmRoute_parsesPolylineAndThaiSteps() {
        val sampleOsrm = """
            {
              "code": "Ok",
              "routes": [
                {
                  "distance": 850.4,
                  "duration": 620.0,
                  "geometry": {
                    "coordinates": [
                      [100.5318, 13.7305],
                      [100.5330, 13.7320],
                      [100.5350, 13.7350]
                    ]
                  },
                  "legs": [
                    {
                      "steps": [
                        {
                          "distance": 400.0,
                          "duration": 300.0,
                          "name": "ถนนพระราม 4",
                          "maneuver": {
                            "type": "depart",
                            "location": [100.5318, 13.7305]
                          }
                        },
                        {
                          "distance": 450.0,
                          "duration": 320.0,
                          "name": "ซอยสารสิน",
                          "maneuver": {
                            "type": "turn",
                            "modifier": "left",
                            "location": [100.5330, 13.7320]
                          }
                        },
                        {
                          "distance": 0.0,
                          "duration": 0.0,
                          "name": "",
                          "maneuver": {
                            "type": "arrive",
                            "location": [100.5350, 13.7350]
                          }
                        }
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val route = repo.parseOsrmRoute(sampleOsrm, TravelMode.WALK)
        assertNotNull(route)
        assertEquals(850, route!!.totalDistanceMeters)
        assertEquals(3, route.polyline.size)
        assertEquals(3, route.steps.size)
        assertTrue(route.steps[0].instructionThai.contains("ถนนพระราม 4"))
        assertTrue(route.steps[1].instructionThai.contains("เลี้ยวซ้าย"))
        assertTrue(route.steps[2].instructionThai.contains("ถึงสถานที่"))
    }
}
