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

    @Test
    fun unlimitedRadiusConstant_isZero() {
        assertEquals(0, NearbySpotsRepository.UNLIMITED_RADIUS_METERS)
    }

    @Test
    fun buildOverpassKeywordQuery_sanitizesRegexAndTargetsWideRadius() {
        val query = repo.buildOverpassKeywordQuery(
            keyword = "สวนลุมพินี (Lumpini+Park)",
            lat = 13.7305,
            lng = 100.5318,
            radiusMeters = 120_000
        )

        assertTrue(query.contains("around:120000,13.730500,100.531800"))
        assertTrue(query.contains("สวนลุมพินี.*Lumpini.*Park"))
        assertTrue(query.contains("\"name:th\"~"))
        assertTrue(query.contains("\"amenity\"~\"^(gym|dojo|yoga_studio)$\""))
    }

    @Test
    fun parseNominatimResults_extractsCategoryAndCoordinates() {
        val sampleNominatim = """
            [
              {
                "name": "สวนเบญจกิติ",
                "display_name": "สวนเบญจกิติ, คลองเตย, กรุงเทพมหานคร",
                "lat": "13.7294",
                "lon": "100.5582",
                "category": "leisure",
                "type": "park",
                "extratags": {
                  "sport": "running"
                }
              },
              {
                "name": "Jetts Fitness Asoke",
                "display_name": "Jetts Fitness Asoke, วัฒนา, กรุงเทพมหานคร",
                "lat": "13.7370",
                "lon": "100.5605",
                "category": "leisure",
                "type": "fitness_centre",
                "extratags": {}
              },
              {
                "name": "จตุจักร",
                "display_name": "จตุจักร, กรุงเทพมหานคร",
                "lat": "13.8282",
                "lon": "100.5597",
                "category": "boundary",
                "type": "administrative"
              }
            ]
        """.trimIndent()

        val suggestions = repo.parseNominatimResults(sampleNominatim)
        assertEquals(3, suggestions.size)

        assertEquals("สวนเบญจกิติ", suggestions[0].shortName)
        assertEquals(NearbySpotCategory.PARK_RUN, suggestions[0].category)
        assertEquals(13.7294, suggestions[0].lat, 0.0001)

        assertEquals("Jetts Fitness Asoke", suggestions[1].shortName)
        assertEquals(NearbySpotCategory.FITNESS_GYM, suggestions[1].category)

        assertEquals("จตุจักร", suggestions[2].shortName)
        assertEquals(null, suggestions[2].category)
    }

    @Test
    fun buildOverpassQuery_usesCanonicalOutCenterSyntax() {
        val query = repo.buildOverpassQuery(13.7563, 100.5018, 8000)
        assertTrue(query.contains("out center 350;"))
        assertTrue(!query.contains("out center tags"))
        assertTrue(query.contains("around:8000,13.756300,100.501800"))
        assertTrue(query.contains("yoga_studio"))
        assertTrue(query.contains("cycleway"))
    }

    @Test
    fun parseOverpassSpots_extractsDetailedVenueMetadataAndCategories() {
        val detailedJson = """
            {
              "elements": [
                {
                  "type": "node",
                  "id": 3001,
                  "lat": 13.7320,
                  "lon": 100.5330,
                  "tags": {
                    "name": "ค่ายมวยไทยศิษย์ยอด",
                    "amenity": "dojo",
                    "sport": "muay_thai;boxing",
                    "indoor": "yes",
                    "lit": "yes",
                    "fee": "yes",
                    "phone": "02-123-4567",
                    "website": "https://example.com/muaythai",
                    "surface": "wood"
                  }
                },
                {
                  "type": "way",
                  "id": 3002,
                  "center": { "lat": 13.7390, "lon": 100.5450 },
                  "tags": {
                    "name": "ทางปั่นจักรยานสวนเบญจกิติ",
                    "highway": "cycleway",
                    "surface": "asphalt",
                    "fee": "no",
                    "lit": "yes"
                  }
                }
              ]
            }
        """.trimIndent()

        val spots = repo.parseOverpassSpots(detailedJson, 13.7310, 100.5318)
        assertEquals(2, spots.size)

        val muay = spots[0]
        assertEquals(NearbySpotCategory.MARTIAL_WELLNESS, muay.category)
        assertEquals(true, muay.isIndoor)
        assertEquals(true, muay.hasLighting)
        assertEquals("มีค่าบริการ", muay.feeInfoThai)
        assertEquals("02-123-4567", muay.phone)
        assertEquals("พื้นไม้ปาร์เกต์", muay.surfaceThai)
        assertTrue(muay.sportBadgesThai.contains("มวยไทย/มวยสากล"))

        val bike = spots[1]
        assertEquals(NearbySpotCategory.CYCLING_OUTDOOR, bike.category)
        assertEquals("เข้าใช้บริการฟรี", bike.feeInfoThai)
        assertEquals("พื้นยางมะตอย", bike.surfaceThai)
    }

    @Test
    fun parsePhotonWorkoutSpots_filtersAndSortsExerciseSpots() {
        val photonJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "geometry": { "type": "Point", "coordinates": [100.5420, 13.7380] },
                  "properties": {
                    "osm_id": 2001,
                    "osm_key": "leisure",
                    "osm_value": "park",
                    "name": "สวนลุมพินี",
                    "street": "ถนนพระราม 4",
                    "district": "ปทุมวัน"
                  }
                },
                {
                  "type": "Feature",
                  "geometry": { "type": "Point", "coordinates": [100.5330, 13.7310] },
                  "properties": {
                    "osm_id": 2002,
                    "osm_key": "amenity",
                    "osm_value": "parking",
                    "name": "Park Hotel Parking"
                  }
                },
                {
                  "type": "Feature",
                  "geometry": { "type": "Point", "coordinates": [100.5320, 13.7312] },
                  "properties": {
                    "osm_id": 2003,
                    "osm_key": "leisure",
                    "osm_value": "fitness_centre",
                    "name": "Virgin Active Silom"
                  }
                }
              ]
            }
        """.trimIndent()

        val filtered = repo.parsePhotonWorkoutSpots(
            rawJson = photonJson,
            originLat = 13.7310,
            originLng = 100.5318,
            filterExerciseOnly = true
        )
        assertEquals(2, filtered.size)
        assertEquals("Virgin Active Silom", filtered[0].name)
        assertEquals(NearbySpotCategory.FITNESS_GYM, filtered[0].category)
        assertEquals("สวนลุมพินี", filtered[1].name)
        assertEquals(NearbySpotCategory.PARK_RUN, filtered[1].category)
        assertEquals("ถนนพระราม 4, ปทุมวัน", filtered[1].addressSummary)
    }
}
