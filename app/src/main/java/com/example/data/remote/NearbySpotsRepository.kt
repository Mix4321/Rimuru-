package com.example.data.remote

import com.example.data.model.SportType
import com.example.ui.components.haversineMeters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.roundToInt

enum class NearbySpotCategory(val thaiLabel: String) {
    ALL("ทั้งหมด"),
    PARK_RUN("สวน & ลู่วิ่ง"),
    FITNESS_GYM("ฟิตเนส & ยิม"),
    SPORTS_COURT("สนามกีฬา"),
    SWIMMING("สระว่ายน้ำ")
}

enum class TravelMode(
    val thaiLabel: String,
    val osrmProfile: String,
    val googleNavMode: String,
    val fallbackSpeedMps: Double
) {
    WALK("เดิน", "foot", "w", 1.35),
    BIKE("จักรยาน", "bicycle", "b", 4.2),
    DRIVE("ขับรถ", "driving", "d", 10.5)
}

data class GeoCoord(
    val lat: Double,
    val lng: Double
)

data class WorkoutSpot(
    val id: String,
    val name: String,
    val category: NearbySpotCategory,
    val categoryDetailThai: String,
    val lat: Double,
    val lng: Double,
    val distanceMeters: Int,
    val addressSummary: String? = null,
    val openingHours: String? = null,
    val sportTags: String? = null,
    val suggestedSport: SportType = SportType.GENERAL
)

data class NavigationStep(
    val index: Int,
    val instructionThai: String,
    val streetName: String,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val maneuverType: String,
    val maneuverModifier: String?,
    val location: GeoCoord
)

data class NavigationRouteResult(
    val travelMode: TravelMode,
    val totalDistanceMeters: Int,
    val durationSeconds: Int,
    val polyline: List<GeoCoord>,
    val steps: List<NavigationStep>
)

data class SearchPlaceSuggestion(
    val displayName: String,
    val shortName: String,
    val lat: Double,
    val lng: Double
)

class NearbySpotsRepository {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchNearbySpots(
        userLat: Double,
        userLng: Double,
        radiusMeters: Int = 5000
    ): Result<List<WorkoutSpot>> = withContext(Dispatchers.IO) {
        runCatching {
            val clampedRadius = radiusMeters.coerceIn(500, 20000)
            val query = buildOverpassQuery(userLat, userLng, clampedRadius)
            val endpoints = listOf(
                "https://overpass-api.de/api/interpreter",
                "https://overpass.kumi.systems/api/interpreter"
            )

            var lastError: Throwable? = null
            var responseBody: String? = null
            for (endpoint in endpoints) {
                try {
                    responseBody = postForm(endpoint, "data=" + URLEncoder.encode(query, "UTF-8"))
                    if (!responseBody.isNullOrBlank()) break
                } catch (t: Throwable) {
                    lastError = t
                }
            }

            val raw = responseBody ?: throw (lastError ?: IllegalStateException("ไม่สามารถดึงข้อมูลสถานที่ได้"))
            parseOverpassSpots(rawJson = raw, originLat = userLat, originLng = userLng)
        }
    }

    suspend fun searchPlacesByKeyword(
        query: String,
        nearLat: Double? = null,
        nearLng: Double? = null
    ): Result<List<SearchPlaceSuggestion>> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = query.trim()
            if (clean.length < 2) return@runCatching emptyList()
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val viewBoxParam = if (nearLat != null && nearLng != null) {
                val delta = 0.25
                val left = nearLng - delta
                val right = nearLng + delta
                val top = nearLat + delta
                val bottom = nearLat - delta
                "&viewbox=$left,$top,$right,$bottom&bounded=0"
            } else {
                ""
            }
            val url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=8&accept-language=th,en&q=$encoded$viewBoxParam"
            val raw = getUrl(url)
            parseNominatimResults(raw)
        }
    }

    suspend fun fetchNavigationRoute(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        travelMode: TravelMode
    ): Result<NavigationRouteResult> = withContext(Dispatchers.IO) {
        runCatching {
            val url = String.format(
                Locale.US,
                "https://router.project-osrm.org/route/v1/%s/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson&steps=true",
                travelMode.osrmProfile,
                originLng,
                originLat,
                destLng,
                destLat
            )
            val raw = getUrl(url)
            parseOsrmRoute(rawJson = raw, travelMode = travelMode)
                ?: buildDirectFallbackRoute(originLat, originLng, destLat, destLng, travelMode)
        }.recoverCatching {
            buildDirectFallbackRoute(originLat, originLng, destLat, destLng, travelMode)
        }
    }

    internal fun buildOverpassQuery(lat: Double, lng: Double, radiusMeters: Int): String {
        val latStr = String.format(Locale.US, "%.6f", lat)
        val lngStr = String.format(Locale.US, "%.6f", lng)
        return """
            [out:json][timeout:18];
            (
              nwr["leisure"="fitness_centre"](around:$radiusMeters,$latStr,$lngStr);
              nwr["leisure"="fitness_station"](around:$radiusMeters,$latStr,$lngStr);
              nwr["amenity"="gym"](around:$radiusMeters,$latStr,$lngStr);
              nwr["leisure"="sports_centre"](around:$radiusMeters,$latStr,$lngStr);
              nwr["leisure"="stadium"](around:$radiusMeters,$latStr,$lngStr);
              nwr["leisure"="track"](around:$radiusMeters,$latStr,$lngStr);
              nwr["leisure"="swimming_pool"]["access"!="private"](around:$radiusMeters,$latStr,$lngStr);
              nwr["leisure"="park"]["name"](around:$radiusMeters,$latStr,$lngStr);
              nwr["leisure"="pitch"]["name"](around:$radiusMeters,$latStr,$lngStr);
            );
            out center tags 120;
        """.trimIndent()
    }

    internal fun parseOverpassSpots(
        rawJson: String,
        originLat: Double,
        originLng: Double
    ): List<WorkoutSpot> {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val elements = root["elements"]?.jsonArray ?: return emptyList()

        val seenKeys = HashSet<String>()
        val result = mutableListOf<WorkoutSpot>()

        for (el in elements) {
            val obj = el as? JsonObject ?: continue
            val tags = obj["tags"] as? JsonObject ?: continue

            val lat = obj["lat"]?.jsonPrimitive?.doubleOrNull
                ?: obj["center"]?.jsonObject?.get("lat")?.jsonPrimitive?.doubleOrNull
                ?: continue
            val lng = obj["lon"]?.jsonPrimitive?.doubleOrNull
                ?: obj["center"]?.jsonObject?.get("lon")?.jsonPrimitive?.doubleOrNull
                ?: continue

            val rawName = tags["name:th"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: tags["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: tags["name:en"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

            val leisure = tags["leisure"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val amenity = tags["amenity"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val sportTag = tags["sport"]?.jsonPrimitive?.contentOrNull.orEmpty()

            val (category, detailLabel, fallbackName, suggestedSport) = classifySpot(
                leisure = leisure,
                amenity = amenity,
                sportTag = sportTag
            )

            // Skip unnamed generic parks/pitches to keep results meaningful
            if (rawName.isNullOrBlank() && (leisure == "park" || leisure == "pitch")) continue

            val finalName = rawName ?: fallbackName
            val dedupKey = "${finalName.lowercase(Locale.ROOT)}_${(lat * 1000).roundToInt()}_${(lng * 1000).roundToInt()}"
            if (!seenKeys.add(dedupKey)) continue

            val distance = haversineMeters(originLat, originLng, lat, lng).roundToInt().coerceAtLeast(1)
            val street = tags["addr:street"]?.jsonPrimitive?.contentOrNull
            val district = tags["addr:subdistrict"]?.jsonPrimitive?.contentOrNull
                ?: tags["addr:district"]?.jsonPrimitive?.contentOrNull
                ?: tags["addr:city"]?.jsonPrimitive?.contentOrNull
            val address = listOfNotNull(street, district).filter { it.isNotBlank() }.joinToString(", ").ifBlank { null }
            val openingHours = tags["opening_hours"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

            val elementId = "${obj["type"]?.jsonPrimitive?.contentOrNull ?: "n"}_${obj["id"]?.jsonPrimitive?.contentOrNull ?: dedupKey}"

            result += WorkoutSpot(
                id = elementId,
                name = finalName,
                category = category,
                categoryDetailThai = detailLabel,
                lat = lat,
                lng = lng,
                distanceMeters = distance,
                addressSummary = address,
                openingHours = openingHours,
                sportTags = sportTag.takeIf { it.isNotBlank() },
                suggestedSport = suggestedSport
            )
        }

        return result.sortedBy { it.distanceMeters }
    }

    internal fun parseOsrmRoute(rawJson: String, travelMode: TravelMode): NavigationRouteResult? {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val code = root["code"]?.jsonPrimitive?.contentOrNull
        if (code != "Ok") return null

        val routes = root["routes"]?.jsonArray ?: return null
        val firstRoute = routes.firstOrNull()?.jsonObject ?: return null

        val totalDistance = firstRoute["distance"]?.jsonPrimitive?.doubleOrNull?.roundToInt() ?: 0
        val totalDuration = firstRoute["duration"]?.jsonPrimitive?.doubleOrNull?.roundToInt() ?: 0

        val coordsArray = firstRoute["geometry"]?.jsonObject?.get("coordinates")?.jsonArray ?: JsonArray(emptyList())
        val polyline = coordsArray.mapNotNull { ptEl ->
            val pair = ptEl as? JsonArray ?: return@mapNotNull null
            val lng = pair.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val lat = pair.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            GeoCoord(lat = lat, lng = lng)
        }
        if (polyline.size < 2) return null

        val steps = mutableListOf<NavigationStep>()
        val legs = firstRoute["legs"]?.jsonArray ?: JsonArray(emptyList())
        var stepCounter = 1
        for (legEl in legs) {
            val legObj = legEl as? JsonObject ?: continue
            val legSteps = legObj["steps"]?.jsonArray ?: continue
            for (stepEl in legSteps) {
                val stepObj = stepEl as? JsonObject ?: continue
                val stepDist = stepObj["distance"]?.jsonPrimitive?.doubleOrNull?.roundToInt() ?: 0
                val stepDur = stepObj["duration"]?.jsonPrimitive?.doubleOrNull?.roundToInt() ?: 0
                val street = stepObj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val maneuver = stepObj["maneuver"]?.jsonObject ?: continue
                val mType = maneuver["type"]?.jsonPrimitive?.contentOrNull ?: "continue"
                val mMod = maneuver["modifier"]?.jsonPrimitive?.contentOrNull
                val locArr = maneuver["location"]?.jsonArray
                val mLng = locArr?.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: polyline.first().lng
                val mLat = locArr?.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: polyline.first().lat

                steps += NavigationStep(
                    index = stepCounter++,
                    instructionThai = translateOsrmManeuverThai(mType, mMod, street),
                    streetName = street,
                    distanceMeters = stepDist,
                    durationSeconds = stepDur,
                    maneuverType = mType,
                    maneuverModifier = mMod,
                    location = GeoCoord(lat = mLat, lng = mLng)
                )
            }
        }

        // OSRM public server uses car profile even when foot/bike is requested on some demos;
        // scale duration cleanly if public endpoint returns unrealistically fast foot/bike ETA
        val adjustedDuration = adjustRealisticDuration(totalDistance, totalDuration, travelMode)

        return NavigationRouteResult(
            travelMode = travelMode,
            totalDistanceMeters = totalDistance,
            durationSeconds = adjustedDuration,
            polyline = polyline,
            steps = steps
        )
    }

    internal fun parseNominatimResults(rawJson: String): List<SearchPlaceSuggestion> {
        val array = json.parseToJsonElement(rawJson) as? JsonArray ?: return emptyList()
        return array.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val display = obj["display_name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val lat = obj["lat"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: return@mapNotNull null
            val lng = obj["lon"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: return@mapNotNull null
            val short = obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: display.substringBefore(",").trim()
            SearchPlaceSuggestion(
                displayName = display,
                shortName = short,
                lat = lat,
                lng = lng
            )
        }
    }

    private fun buildDirectFallbackRoute(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        travelMode: TravelMode
    ): NavigationRouteResult {
        val dist = haversineMeters(originLat, originLng, destLat, destLng).roundToInt().coerceAtLeast(1)
        val dur = (dist / travelMode.fallbackSpeedMps).roundToInt().coerceAtLeast(30)
        return NavigationRouteResult(
            travelMode = travelMode,
            totalDistanceMeters = dist,
            durationSeconds = dur,
            polyline = listOf(GeoCoord(originLat, originLng), GeoCoord(destLat, destLng)),
            steps = listOf(
                NavigationStep(
                    index = 1,
                    instructionThai = "มุ่งหน้าตรงไปยังจุดหมายปลายทาง",
                    streetName = "",
                    distanceMeters = dist,
                    durationSeconds = dur,
                    maneuverType = "depart",
                    maneuverModifier = null,
                    location = GeoCoord(originLat, originLng)
                ),
                NavigationStep(
                    index = 2,
                    instructionThai = "ถึงสถานที่ออกกำลังกายปลายทาง",
                    streetName = "",
                    distanceMeters = 0,
                    durationSeconds = 0,
                    maneuverType = "arrive",
                    maneuverModifier = null,
                    location = GeoCoord(destLat, destLng)
                )
            )
        )
    }

    private fun adjustRealisticDuration(distanceMeters: Int, rawDurationSec: Int, mode: TravelMode): Int {
        if (distanceMeters <= 0) return rawDurationSec.coerceAtLeast(0)
        val impliedMps = distanceMeters.toDouble() / rawDurationSec.coerceAtLeast(1)
        return when (mode) {
            TravelMode.WALK -> if (impliedMps > 2.2) (distanceMeters / 1.35).roundToInt() else rawDurationSec
            TravelMode.BIKE -> if (impliedMps > 8.5) (distanceMeters / 4.2).roundToInt() else rawDurationSec
            TravelMode.DRIVE -> rawDurationSec
        }.coerceAtLeast(30)
    }

    private data class SpotClassification(
        val category: NearbySpotCategory,
        val detailLabel: String,
        val fallbackName: String,
        val suggestedSport: SportType
    )

    private fun classifySpot(
        leisure: String,
        amenity: String,
        sportTag: String
    ): SpotClassification {
        val sportLower = sportTag.lowercase(Locale.ROOT)
        return when {
            leisure == "swimming_pool" || sportLower.contains("swim") -> SpotClassification(
                category = NearbySpotCategory.SWIMMING,
                detailLabel = "สระว่ายน้ำ",
                fallbackName = "สระว่ายน้ำสาธารณะ",
                suggestedSport = SportType.GENERAL
            )
            leisure == "fitness_centre" || amenity == "gym" -> SpotClassification(
                category = NearbySpotCategory.FITNESS_GYM,
                detailLabel = "ฟิตเนส / ยิม",
                fallbackName = "ศูนย์ฟิตเนส",
                suggestedSport = SportType.GENERAL
            )
            leisure == "fitness_station" -> SpotClassification(
                category = NearbySpotCategory.FITNESS_GYM,
                detailLabel = "ลานออกกำลังกายกลางแจ้ง",
                fallbackName = "จุดออกกำลังกายกลางแจ้ง",
                suggestedSport = SportType.GENERAL
            )
            leisure == "track" -> SpotClassification(
                category = NearbySpotCategory.PARK_RUN,
                detailLabel = "ลู่วิ่ง / เส้นทางวิ่ง",
                fallbackName = "ลู่วิ่งออกกำลังกาย",
                suggestedSport = SportType.RUNNING
            )
            leisure == "park" -> SpotClassification(
                category = NearbySpotCategory.PARK_RUN,
                detailLabel = "สวนสาธารณะ",
                fallbackName = "สวนสาธารณะ",
                suggestedSport = SportType.RUNNING
            )
            leisure == "stadium" || leisure == "sports_centre" || leisure == "pitch" -> {
                val mappedSport = when {
                    sportLower.contains("soccer") || sportLower.contains("football") -> SportType.FOOTBALL
                    sportLower.contains("basketball") -> SportType.BASKETBALL
                    sportLower.contains("badminton") || sportLower.contains("tennis") -> SportType.BADMINTON
                    sportLower.contains("cycling") -> SportType.CYCLING
                    sportLower.contains("run") || sportLower.contains("athletics") -> SportType.RUNNING
                    else -> SportType.GENERAL
                }
                val detail = when (leisure) {
                    "stadium" -> "สนามกีฬา / สเตเดียม"
                    "sports_centre" -> "ศูนย์กีฬาครบวงจร"
                    else -> "ลานกีฬา / สนามฝึก"
                }
                SpotClassification(
                    category = NearbySpotCategory.SPORTS_COURT,
                    detailLabel = detail,
                    fallbackName = detail,
                    suggestedSport = mappedSport
                )
            }
            else -> SpotClassification(
                category = NearbySpotCategory.FITNESS_GYM,
                detailLabel = "สถานที่ออกกำลังกาย",
                fallbackName = "จุดออกกำลังกาย",
                suggestedSport = SportType.GENERAL
            )
        }
    }

    private fun getUrl(urlString: String): String {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "MoveActivityTracker/1.0 (Android)")
            setRequestProperty("Accept", "application/json")
        }
        return readHttpResponse(conn)
    }

    private fun postForm(urlString: String, formBody: String): String {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 12_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "MoveActivityTracker/1.0 (Android)")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
        }
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(formBody)
            writer.flush()
        }
        return readHttpResponse(conn)
    }

    private fun readHttpResponse(conn: HttpURLConnection): String {
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.let {
                BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { reader -> reader.readText() }
            }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code: ${body.take(120)}")
            }
            return body
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        fun translateOsrmManeuverThai(type: String, modifier: String?, street: String): String {
            val roadSuffix = if (street.isNotBlank()) " เข้าสู่ $street" else ""
            val dir = when (modifier) {
                "left" -> "เลี้ยวซ้าย"
                "sharp left" -> "หักซ้าย"
                "slight left" -> "เบี่ยงซ้าย"
                "right" -> "เลี้ยวขวา"
                "sharp right" -> "หักขวา"
                "slight right" -> "เบี่ยงขวา"
                "uturn" -> "กลับรถ / ย้อนกลับ"
                "straight" -> "ตรงไป"
                else -> "ไปต่อ"
            }
            return when (type) {
                "depart" -> if (street.isNotBlank()) "เริ่มต้นออกเดินทางตาม $street" else "เริ่มต้นออกเดินทางตามเส้นทาง"
                "arrive" -> "ถึงสถานที่ออกกำลังกายปลายทางแล้ว!"
                "turn", "end of road", "new name", "fork" -> "$dir$roadSuffix"
                "roundabout", "rotary" -> "เข้าวงเวียนแล้วออกทางที่กำหนด$roadSuffix"
                "merge" -> "รวมเข้าสู่เส้นทางหลัก$roadSuffix"
                "continue" -> if (street.isNotBlank()) "ตรงไปตาม $street" else "ตรงไปตามเส้นทาง"
                else -> "$dir$roadSuffix"
            }
        }

        fun formatSpotDistanceThai(meters: Int): String = if (meters < 1000) {
            "$meters ม."
        } else {
            String.format(Locale.US, "%.1f กม.", meters / 1000.0)
        }

        fun formatEtaThai(seconds: Int): String {
            val mins = (seconds / 60.0).roundToInt().coerceAtLeast(1)
            return if (mins < 60) {
                "$mins นาที"
            } else {
                val h = mins / 60
                val m = mins % 60
                if (m == 0) "$h ชม." else "$h ชม. $m น."
            }
        }
    }
}
