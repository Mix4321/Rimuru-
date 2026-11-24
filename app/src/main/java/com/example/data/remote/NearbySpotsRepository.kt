package com.example.data.remote

import com.example.data.model.SportType
import com.example.ui.components.haversineMeters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
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
    SWIMMING("สระว่ายน้ำ"),
    MARTIAL_WELLNESS("มวย / โยคะ / สตูดิโอ"),
    CYCLING_OUTDOOR("ปั่น / สเก็ต / กิจกรรมแจ้ง")
}

enum class TravelMode(
    val thaiLabel: String,
    val osrmProfile: String,
    val fossgisProfile: String,
    val googleNavMode: String,
    val fallbackSpeedMps: Double
) {
    WALK("เดิน", "foot", "foot", "w", 1.35),
    BIKE("จักรยาน", "bicycle", "bike", "b", 4.2),
    DRIVE("ขับรถ", "driving", "car", "d", 10.5)
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
    val suggestedSport: SportType = SportType.GENERAL,
    val phone: String? = null,
    val website: String? = null,
    val surfaceThai: String? = null,
    val hasLighting: Boolean? = null,
    val isIndoor: Boolean? = null,
    val feeInfoThai: String? = null,
    val accessThai: String? = null,
    val sportBadgesThai: List<String> = emptyList(),
    val operatorName: String? = null
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
    val lng: Double,
    val category: NearbySpotCategory? = null,
    val detailLabel: String? = null
)

data class CombinedPlaceSearchResult(
    val spots: List<WorkoutSpot>,
    val areaSuggestions: List<SearchPlaceSuggestion>
)

class NearbySpotsRepository {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Fetches real exercise spots from OpenStreetMap Overpass API with automatic
     * mirror failover and Komoot Photon + Nominatim backup aggregation so spots load reliably
     * even if Overpass is rate-limited or busy.
     */
    suspend fun fetchNearbySpots(
        userLat: Double,
        userLng: Double,
        radiusMeters: Int = UNLIMITED_RADIUS_METERS
    ): Result<List<WorkoutSpot>> = withContext(Dispatchers.IO) {
        runCatching {
            val collectedByKey = LinkedHashMap<String, WorkoutSpot>()
            var overpassError: Throwable? = null

            if (radiusMeters > 0) {
                val clamped = radiusMeters.coerceIn(500, MAX_QUERY_RADIUS_METERS)
                try {
                    val query = buildOverpassQuery(userLat, userLng, clamped)
                    val raw = executeOverpass(query)
                    parseOverpassSpots(rawJson = raw, originLat = userLat, originLng = userLng)
                        .filter { it.distanceMeters <= clamped + 500 }
                        .forEach { spot ->
                            collectedByKey.putIfAbsent(dedupKeyFor(spot.name, spot.lat, spot.lng), spot)
                        }
                } catch (t: Throwable) {
                    overpassError = t
                }
            } else {
                // Progressive unlimited search: expand outward until we collect nearby spots
                val rings = listOf(8_000, 25_000, 75_000, 120_000)
                for ((idx, ringRadius) in rings.withIndex()) {
                    try {
                        val query = buildOverpassQuery(userLat, userLng, ringRadius)
                        val raw = executeOverpass(query)
                        val parsed = parseOverpassSpots(rawJson = raw, originLat = userLat, originLng = userLng)
                        for (spot in parsed) {
                            collectedByKey.putIfAbsent(dedupKeyFor(spot.name, spot.lat, spot.lng), spot)
                        }
                        val targetCount = when (idx) {
                            0 -> 45
                            1 -> 30
                            2 -> 15
                            else -> 5
                        }
                        if (collectedByKey.size >= targetCount) break
                    } catch (t: Throwable) {
                        overpassError = t
                    }
                }
            }

            // Supplement via Photon & Nominatim so every nearby venue category is richly covered
            if (collectedByKey.size < 25) {
                val backupResult = runCatching {
                    fetchNearbyViaPhotonAndNominatim(userLat, userLng, radiusMeters)
                }
                backupResult.getOrNull()?.forEach { spot ->
                    collectedByKey.putIfAbsent(dedupKeyFor(spot.name, spot.lat, spot.lng), spot)
                }
                if (collectedByKey.isEmpty()) {
                    throw (overpassError ?: backupResult.exceptionOrNull()
                        ?: IllegalStateException("ไม่สามารถรวบรวมสถานที่ใกล้คุณได้"))
                }
            }

            collectedByKey.values.sortedBy { it.distanceMeters }
        }
    }

    /**
     * Searches real workout spots and places globally/regionally using:
     * 1) Komoot Photon GeoJSON search (fast Thai & English POI lookup)
     * 2) OpenStreetMap Nominatim search
     * 3) Overpass keyword regex search within 120 km
     */
    suspend fun searchWorkoutSpotsAndPlaces(
        query: String,
        originLat: Double?,
        originLng: Double?
    ): Result<CombinedPlaceSearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = query.trim()
            if (clean.length < 2) {
                return@runCatching CombinedPlaceSearchResult(emptyList(), emptyList())
            }

            val refLat = originLat ?: DEFAULT_THAILAND_LAT
            val refLng = originLng ?: DEFAULT_THAILAND_LNG

            coroutineScope {
                val photonDeferred = async {
                    runCatching {
                        val url = buildPhotonSearchUrl(clean, originLat, originLng, limit = 15)
                        val raw = getUrl(url)
                        parsePhotonWorkoutSpots(
                            rawJson = raw,
                            originLat = refLat,
                            originLng = refLng,
                            filterExerciseOnly = false
                        )
                    }
                }

                val nominatimDeferred = async {
                    runCatching {
                        val raw = getUrl(buildNominatimSearchUrl(clean, originLat, originLng))
                        parseNominatimResults(raw)
                    }
                }

                val overpassDeferred = async {
                    runCatching {
                        val opQuery = buildOverpassKeywordQuery(clean, refLat, refLng, radiusMeters = 120_000)
                        val raw = executeOverpass(opQuery)
                        parseOverpassSpots(rawJson = raw, originLat = refLat, originLng = refLng)
                    }
                }

                val photonRes = photonDeferred.await()
                val nomRes = nominatimDeferred.await()
                val ovRes = overpassDeferred.await()

                if (photonRes.isFailure && nomRes.isFailure && ovRes.isFailure) {
                    throw (nomRes.exceptionOrNull()
                        ?: photonRes.exceptionOrNull()
                        ?: ovRes.exceptionOrNull()
                        ?: IllegalStateException("ไม่สามารถค้นหาสถานที่ได้"))
                }

                val photonList = photonRes.getOrDefault(emptyList())
                val nominatimList = nomRes.getOrDefault(emptyList())
                val overpassList = ovRes.getOrDefault(emptyList())

                val mergedSpots = LinkedHashMap<String, WorkoutSpot>()
                for (spot in overpassList) {
                    mergedSpots.putIfAbsent(dedupKeyFor(spot.name, spot.lat, spot.lng), spot)
                }
                for (spot in photonList) {
                    mergedSpots.putIfAbsent(dedupKeyFor(spot.name, spot.lat, spot.lng), spot)
                }
                for (nom in nominatimList) {
                    val key = dedupKeyFor(nom.shortName, nom.lat, nom.lng)
                    if (!mergedSpots.containsKey(key)) {
                        val dist = haversineMeters(refLat, refLng, nom.lat, nom.lng).roundToInt().coerceAtLeast(1)
                        val cat = nom.category ?: inferCategoryFromText(nom.shortName + " " + nom.displayName)
                        val detail = nom.detailLabel ?: cat.thaiLabel
                        mergedSpots[key] = WorkoutSpot(
                            id = "nom_${key.hashCode()}",
                            name = nom.shortName,
                            category = cat,
                            categoryDetailThai = detail,
                            lat = nom.lat,
                            lng = nom.lng,
                            distanceMeters = dist,
                            addressSummary = nom.displayName.substringAfter(",", "").trim().ifBlank { null },
                            suggestedSport = inferSportFromCategory(cat, nom.shortName)
                        )
                    }
                }

                val sortedSpots = mergedSpots.values.sortedBy { it.distanceMeters }
                CombinedPlaceSearchResult(
                    spots = sortedSpots,
                    areaSuggestions = nominatimList
                )
            }
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
            val raw = getUrl(buildNominatimSearchUrl(clean, nearLat, nearLng))
            parseNominatimResults(raw)
        }
    }

    private fun buildNominatimSearchUrl(query: String, nearLat: Double?, nearLng: Double?): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val viewBoxParam = if (nearLat != null && nearLng != null) {
            val delta = 0.6
            val left = nearLng - delta
            val right = nearLng + delta
            val top = nearLat + delta
            val bottom = nearLat - delta
            "&viewbox=$left,$top,$right,$bottom&bounded=0"
        } else {
            ""
        }
        return "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=15&extratags=1&accept-language=th,en&q=$encoded$viewBoxParam"
    }

    suspend fun fetchNavigationRoute(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        travelMode: TravelMode
    ): Result<NavigationRouteResult> = withContext(Dispatchers.IO) {
        val candidateUrls = listOf(
            String.format(
                Locale.US,
                "https://routing.openstreetmap.de/routed-%s/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=simplified&geometries=geojson&steps=true",
                travelMode.fossgisProfile,
                originLng,
                originLat,
                destLng,
                destLat
            ),
            String.format(
                Locale.US,
                "https://router.project-osrm.org/route/v1/%s/%.6f,%.6f;%.6f,%.6f?overview=simplified&geometries=geojson&steps=true",
                travelMode.osrmProfile,
                originLng,
                originLat,
                destLng,
                destLat
            )
        )

        val fetched = withTimeoutOrNull(7_000L) {
            for (url in candidateUrls) {
                val parsed = runCatching {
                    val raw = getUrl(url, connectMs = 3_500, readMs = 4_500)
                    parseOsrmRoute(rawJson = raw, travelMode = travelMode)
                }.getOrNull()
                if (parsed != null) return@withTimeoutOrNull parsed
            }
            null
        }

        Result.success(
            fetched ?: buildDirectFallbackRoute(originLat, originLng, destLat, destLng, travelMode)
        )
    }

    private fun buildPhotonSearchUrl(
        query: String,
        nearLat: Double?,
        nearLng: Double?,
        limit: Int = 20
    ): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val bias = if (nearLat != null && nearLng != null) {
            String.format(Locale.US, "&lat=%.5f&lon=%.5f", nearLat, nearLng)
        } else {
            ""
        }
        return "https://photon.komoot.io/api/?q=$encoded&limit=$limit$bias"
    }

    private suspend fun fetchNearbyViaPhotonAndNominatim(
        lat: Double,
        lng: Double,
        radiusMeters: Int
    ): List<WorkoutSpot> = coroutineScope {
        val maxDistance = if (radiusMeters > 0) radiusMeters + 500 else MAX_QUERY_RADIUS_METERS
        val queries = listOf(
            "park",
            "fitness gym",
            "stadium sport",
            "badminton swimming",
            "yoga muay thai boxing"
        )
        val deferreds = queries.map { q ->
            async {
                runCatching {
                    val raw = getUrl(buildPhotonSearchUrl(q, lat, lng, limit = 20))
                    parsePhotonWorkoutSpots(
                        rawJson = raw,
                        originLat = lat,
                        originLng = lng,
                        filterExerciseOnly = true
                    )
                }
            }
        }

        val collected = LinkedHashMap<String, WorkoutSpot>()
        var anySuccess = false
        var lastErr: Throwable? = null
        for (def in deferreds) {
            val res = def.await()
            if (res.isSuccess) {
                anySuccess = true
                res.getOrNull()?.filter { it.distanceMeters <= maxDistance }?.forEach { spot ->
                    collected.putIfAbsent(dedupKeyFor(spot.name, spot.lat, spot.lng), spot)
                }
            } else {
                lastErr = res.exceptionOrNull()
            }
        }

        if (collected.size < 4) {
            val nomFallback = runCatching {
                val raw = getUrl(buildNominatimSearchUrl("park", lat, lng))
                parseNominatimResults(raw).mapNotNull { sug ->
                    val dist = haversineMeters(lat, lng, sug.lat, sug.lng).roundToInt().coerceAtLeast(1)
                    if (dist > maxDistance) return@mapNotNull null
                    val cat = sug.category ?: NearbySpotCategory.PARK_RUN
                    WorkoutSpot(
                        id = "nom_${dedupKeyFor(sug.shortName, sug.lat, sug.lng).hashCode()}",
                        name = sug.shortName,
                        category = cat,
                        categoryDetailThai = sug.detailLabel ?: cat.thaiLabel,
                        lat = sug.lat,
                        lng = sug.lng,
                        distanceMeters = dist,
                        addressSummary = sug.displayName.substringAfter(",", "").trim().ifBlank { null },
                        suggestedSport = inferSportFromCategory(cat, sug.shortName)
                    )
                }
            }
            if (nomFallback.isSuccess) {
                anySuccess = true
                nomFallback.getOrNull()?.forEach { spot ->
                    collected.putIfAbsent(dedupKeyFor(spot.name, spot.lat, spot.lng), spot)
                }
            } else if (lastErr == null) {
                lastErr = nomFallback.exceptionOrNull()
            }
        }

        if (!anySuccess && lastErr != null) {
            throw lastErr
        }
        collected.values.sortedBy { it.distanceMeters }
    }

    internal fun buildOverpassQuery(lat: Double, lng: Double, radiusMeters: Int): String {
        val latStr = String.format(Locale.US, "%.6f", lat)
        val lngStr = String.format(Locale.US, "%.6f", lng)
        return """
            [out:json][timeout:22];
            (
              nwr["leisure"~"^(fitness_centre|fitness_station|sports_centre|sports_hall|stadium|track|swimming_pool|pitch|recreation_ground|ice_rink|golf_course|bowling_alley|dance)$"]["access"!="private"](around:$radiusMeters,$latStr,$lngStr);
              nwr["amenity"~"^(gym|dojo|yoga_studio)$"](around:$radiusMeters,$latStr,$lngStr);
              nwr["leisure"~"^(park|nature_reserve)$"]["name"](around:$radiusMeters,$latStr,$lngStr);
              nwr["club"="sport"](around:$radiusMeters,$latStr,$lngStr);
              nwr["sport"]["access"!="private"](around:$radiusMeters,$latStr,$lngStr);
              way["highway"="cycleway"]["name"](around:$radiusMeters,$latStr,$lngStr);
              relation["route"~"^(running|bicycle|fitness_trail)$"](around:$radiusMeters,$latStr,$lngStr);
            );
            out center 350;
        """.trimIndent()
    }

    internal fun buildOverpassKeywordQuery(
        keyword: String,
        lat: Double,
        lng: Double,
        radiusMeters: Int
    ): String {
        val safeRegex = sanitizeOverpassRegex(keyword)
        val latStr = String.format(Locale.US, "%.6f", lat)
        val lngStr = String.format(Locale.US, "%.6f", lng)
        return """
            [out:json][timeout:18];
            (
              nwr["name"~"$safeRegex",i]["leisure"~"park|fitness_centre|fitness_station|sports_centre|sports_hall|stadium|track|swimming_pool|pitch|recreation_ground|golf_course|dance"](around:$radiusMeters,$latStr,$lngStr);
              nwr["name:th"~"$safeRegex",i]["leisure"~"park|fitness_centre|fitness_station|sports_centre|sports_hall|stadium|track|swimming_pool|pitch|recreation_ground|golf_course|dance"](around:$radiusMeters,$latStr,$lngStr);
              nwr["name:en"~"$safeRegex",i]["leisure"~"park|fitness_centre|fitness_station|sports_centre|sports_hall|stadium|track|swimming_pool|pitch|recreation_ground|golf_course|dance"](around:$radiusMeters,$latStr,$lngStr);
              nwr["name"~"$safeRegex",i]["amenity"~"^(gym|dojo|yoga_studio)$"](around:$radiusMeters,$latStr,$lngStr);
              nwr["name"~"$safeRegex",i]["club"="sport"](around:$radiusMeters,$latStr,$lngStr);
              nwr["sport"~"$safeRegex",i](around:${minOf(radiusMeters, 50_000)},$latStr,$lngStr);
            );
            out center 90;
        """.trimIndent()
    }

    private fun executeOverpass(query: String): String {
        val endpoints = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
            "https://overpass.private.coffee/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter"
        )
        var lastError: Throwable? = null
        val encoded = "data=" + URLEncoder.encode(query, "UTF-8")
        for (endpoint in endpoints) {
            try {
                val body = postForm(endpoint, encoded).trim()
                if (!body.startsWith("{")) {
                    throw IllegalStateException("รูปแบบข้อมูลจากแผนที่ไม่ถูกต้อง")
                }
                val root = json.parseToJsonElement(body).jsonObject
                val elements = root["elements"]?.jsonArray
                val remark = root["remark"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (elements.isNullOrEmpty() && remark.contains("error", ignoreCase = true)) {
                    throw IllegalStateException(remark)
                }
                return body
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw (lastError ?: IllegalStateException("ไม่สามารถดึงข้อมูลสถานที่ได้"))
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
            val clubTag = tags["club"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val highwayTag = tags["highway"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val routeTag = tags["route"]?.jsonPrimitive?.contentOrNull.orEmpty()

            val (category, detailLabel, fallbackName, suggestedSport) = classifySpot(
                leisure = leisure,
                amenity = amenity,
                sportTag = sportTag,
                clubTag = clubTag,
                highwayTag = highwayTag,
                routeTag = routeTag
            )

            // Skip unnamed generic parks/nature_reserves, or unnamed pitches with no sport tag
            if (rawName.isNullOrBlank() && (leisure == "park" || leisure == "nature_reserve")) continue
            if (rawName.isNullOrBlank() && leisure == "pitch" && sportTag.isBlank()) continue

            val sportBadges = parseSportBadgesThai(sportTag)
            val finalName = rawName ?: if (leisure == "pitch" && sportBadges.isNotEmpty()) {
                "สนาม${sportBadges.first()}สาธารณะ"
            } else {
                fallbackName
            }
            val dedupKey = dedupKeyFor(finalName, lat, lng)
            if (!seenKeys.add(dedupKey)) continue

            val distance = haversineMeters(originLat, originLng, lat, lng).roundToInt().coerceAtLeast(1)
            val houseNo = tags["addr:housenumber"]?.jsonPrimitive?.contentOrNull
            val street = tags["addr:street"]?.jsonPrimitive?.contentOrNull
            val subdistrict = tags["addr:subdistrict"]?.jsonPrimitive?.contentOrNull
            val district = tags["addr:district"]?.jsonPrimitive?.contentOrNull
                ?: tags["addr:city"]?.jsonPrimitive?.contentOrNull
            val postcode = tags["addr:postcode"]?.jsonPrimitive?.contentOrNull
            val address = listOfNotNull(houseNo, street, subdistrict, district, postcode)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(", ")
                .ifBlank { null }

            val openingHours = tags["opening_hours"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            val phone = (tags["phone"]?.jsonPrimitive?.contentOrNull
                ?: tags["contact:phone"]?.jsonPrimitive?.contentOrNull
                ?: tags["contact:mobile"]?.jsonPrimitive?.contentOrNull)?.takeIf { it.isNotBlank() }
            val website = (tags["website"]?.jsonPrimitive?.contentOrNull
                ?: tags["contact:website"]?.jsonPrimitive?.contentOrNull
                ?: tags["facebook"]?.jsonPrimitive?.contentOrNull
                ?: tags["contact:facebook"]?.jsonPrimitive?.contentOrNull)?.takeIf { it.isNotBlank() }
            val surfaceThai = translateSurfaceThai(tags["surface"]?.jsonPrimitive?.contentOrNull)
            val hasLighting = when (tags["lit"]?.jsonPrimitive?.contentOrNull?.lowercase(Locale.ROOT)) {
                "yes", "true", "24/7" -> true
                "no", "false" -> false
                else -> null
            }
            val isIndoor = when (tags["indoor"]?.jsonPrimitive?.contentOrNull?.lowercase(Locale.ROOT)) {
                "yes", "true" -> true
                "no", "false" -> false
                else -> null
            }
            val feeRaw = tags["fee"]?.jsonPrimitive?.contentOrNull?.lowercase(Locale.ROOT)
            val chargeRaw = tags["charge"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            val feeInfoThai = when {
                chargeRaw != null -> "ค่าบริการ: $chargeRaw"
                feeRaw == "no" -> "เข้าใช้บริการฟรี"
                feeRaw == "yes" -> "มีค่าบริการ"
                else -> null
            }
            val accessThai = when (tags["access"]?.jsonPrimitive?.contentOrNull?.lowercase(Locale.ROOT)) {
                "yes", "public" -> "เปิดสาธารณะ"
                "permissive" -> "เข้าใช้ได้ทั่วไป"
                "customers" -> "สำหรับสมาชิก/ลูกค้า"
                else -> null
            }
            val operatorName = tags["operator"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

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
                suggestedSport = suggestedSport,
                phone = phone,
                website = website,
                surfaceThai = surfaceThai,
                hasLighting = hasLighting,
                isIndoor = isIndoor,
                feeInfoThai = feeInfoThai,
                accessThai = accessThai,
                sportBadgesThai = sportBadges,
                operatorName = operatorName
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
        val rawPolyline = coordsArray.mapNotNull { ptEl ->
            val pair = ptEl as? JsonArray ?: return@mapNotNull null
            val lng = pair.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val lat = pair.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            GeoCoord(lat = lat, lng = lng)
        }
        val polyline = downsamplePolyline(rawPolyline, maxPoints = 350)
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

            val nomCategory = obj["category"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val nomType = obj["type"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val extratags = obj["extratags"] as? JsonObject
            val extraSport = extratags?.get("sport")?.jsonPrimitive?.contentOrNull.orEmpty()

            val classification = when {
                nomCategory == "leisure" || nomCategory == "amenity" || nomType in setOf(
                    "park", "fitness_centre", "fitness_station", "gym", "sports_centre", "stadium", "track", "swimming_pool", "pitch"
                ) -> classifySpot(
                    leisure = if (nomCategory == "leisure") nomType else "",
                    amenity = if (nomCategory == "amenity") nomType else "",
                    sportTag = extraSport
                )
                else -> null
            }

            SearchPlaceSuggestion(
                displayName = display,
                shortName = short,
                lat = lat,
                lng = lng,
                category = classification?.category,
                detailLabel = classification?.detailLabel
            )
        }
    }

    internal fun parsePhotonWorkoutSpots(
        rawJson: String,
        originLat: Double,
        originLng: Double,
        filterExerciseOnly: Boolean = true
    ): List<WorkoutSpot> {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val features = root["features"]?.jsonArray ?: return emptyList()
        val seenKeys = HashSet<String>()
        val result = mutableListOf<WorkoutSpot>()

        val allowedLeisureValues = setOf(
            "park", "fitness_centre", "fitness_station", "sports_centre", "sports_hall",
            "stadium", "track", "swimming_pool", "pitch", "recreation_ground",
            "ice_rink", "golf_course", "bowling_alley", "dance", "nature_reserve"
        )
        val allowedAmenityValues = setOf("gym", "dojo", "yoga_studio")

        for (featEl in features) {
            val feat = featEl as? JsonObject ?: continue
            val coords = feat["geometry"]?.jsonObject?.get("coordinates")?.jsonArray ?: continue
            val lng = coords.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: continue
            val lat = coords.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: continue
            val props = feat["properties"] as? JsonObject ?: continue

            val rawName = props["name"]?.jsonPrimitive?.contentOrNull?.trim()
                ?.takeIf { it.isNotBlank() } ?: continue
            val osmKey = props["osm_key"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val osmVal = props["osm_value"]?.jsonPrimitive?.contentOrNull.orEmpty()

            val isExerciseTag = (osmKey == "leisure" && osmVal in allowedLeisureValues) ||
                (osmKey == "amenity" && osmVal in allowedAmenityValues) ||
                (osmKey == "club" && osmVal == "sport") ||
                (osmKey == "sport") ||
                (osmKey == "highway" && osmVal == "cycleway")

            if (filterExerciseOnly && !isExerciseTag) continue

            val (category, detailLabel, _, suggestedSport) = if (isExerciseTag) {
                classifySpot(
                    leisure = if (osmKey == "leisure") osmVal else "",
                    amenity = if (osmKey == "amenity") osmVal else "",
                    sportTag = if (osmKey == "sport") osmVal else "",
                    clubTag = if (osmKey == "club") osmVal else "",
                    highwayTag = if (osmKey == "highway") osmVal else ""
                )
            } else {
                val inferredCat = inferCategoryFromText(rawName)
                SpotClassification(
                    category = inferredCat,
                    detailLabel = inferredCat.thaiLabel,
                    fallbackName = rawName,
                    suggestedSport = inferSportFromCategory(inferredCat, rawName)
                )
            }

            val dedupKey = dedupKeyFor(rawName, lat, lng)
            if (!seenKeys.add(dedupKey)) continue

            val dist = haversineMeters(originLat, originLng, lat, lng).roundToInt().coerceAtLeast(1)
            val street = props["street"]?.jsonPrimitive?.contentOrNull
            val district = props["district"]?.jsonPrimitive?.contentOrNull
                ?: props["locality"]?.jsonPrimitive?.contentOrNull
                ?: props["city"]?.jsonPrimitive?.contentOrNull
            val address = listOfNotNull(street, district)
                .filter { it.isNotBlank() }
                .joinToString(", ")
                .ifBlank { null }
            val osmId = props["osm_id"]?.jsonPrimitive?.contentOrNull ?: dedupKey.hashCode().toString()
            val sportBadges = if (osmKey == "sport") parseSportBadgesThai(osmVal) else emptyList()

            result += WorkoutSpot(
                id = "ph_${osmId}_$dist",
                name = rawName,
                category = category,
                categoryDetailThai = detailLabel,
                lat = lat,
                lng = lng,
                distanceMeters = dist,
                addressSummary = address,
                sportTags = if (osmKey == "sport") osmVal else null,
                suggestedSport = suggestedSport,
                sportBadgesThai = sportBadges
            )
        }

        return result.sortedBy { it.distanceMeters }
    }

    private fun downsamplePolyline(points: List<GeoCoord>, maxPoints: Int): List<GeoCoord> {
        if (points.size <= maxPoints) return points
        val step = (points.size - 1).toDouble() / (maxPoints - 1)
        val sampled = ArrayList<GeoCoord>(maxPoints)
        for (i in 0 until maxPoints - 1) {
            sampled += points[(i * step).toInt().coerceIn(0, points.lastIndex)]
        }
        sampled += points.last()
        return sampled
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

    private fun dedupKeyFor(name: String, lat: Double, lng: Double): String =
        "${name.lowercase(Locale.ROOT)}_${(lat * 1000).roundToInt()}_${(lng * 1000).roundToInt()}"

    private fun sanitizeOverpassRegex(input: String): String =
        input.replace(Regex("[\\\\\"\\[\\](){}.+*?^$|]"), " ").trim().replace(Regex("\\s+"), ".*")

    private fun parseSportBadgesThai(sportRaw: String): List<String> {
        if (sportRaw.isBlank()) return emptyList()
        return sportRaw.split(';', ',')
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotBlank() }
            .mapNotNull { token ->
                when (token) {
                    "running", "jogging", "athletics" -> "วิ่ง/กรีฑา"
                    "soccer", "football" -> "ฟุตบอล"
                    "futsal" -> "ฟุตซอล"
                    "basketball" -> "บาสเกตบอล"
                    "badminton" -> "แบดมินตัน"
                    "tennis" -> "เทนนิส"
                    "table_tennis" -> "ปิงปอง"
                    "volleyball", "beachvolleyball" -> "วอลเลย์บอล"
                    "sepak_takraw", "takraw" -> "ตะกร้อ"
                    "swimming" -> "ว่ายน้ำ"
                    "cycling", "bmx", "mtb" -> "ปั่นจักรยาน"
                    "fitness", "gym", "workout", "calisthenics", "crossfit", "weightlifting" -> "ฟิตเนส/เวท"
                    "muay_thai", "boxing", "kickboxing" -> "มวยไทย/มวยสากล"
                    "martial_arts", "karate", "taekwondo", "judo", "aikido" -> "ศิลปะการต่อสู้"
                    "yoga" -> "โยคะ"
                    "pilates" -> "พิลาทิส"
                    "climbing", "bouldering" -> "ปีนผาจำลอง"
                    "skateboard", "roller_skating", "skating" -> "สเก็ตบอร์ด/สเก็ต"
                    "golf" -> "กอล์ฟ"
                    "archery" -> "ยิงธนู"
                    "bowling" -> "โบว์ลิ่ง"
                    "rowing", "kayak", "canoe" -> "พายเรือ/คายัค"
                    else -> token.replace('_', ' ').replaceFirstChar { it.uppercase() }
                }
            }
            .distinct()
            .take(5)
    }

    private fun translateSurfaceThai(raw: String?): String? = when (raw?.trim()?.lowercase(Locale.ROOT)) {
        "asphalt" -> "พื้นยางมะตอย"
        "concrete", "paved" -> "พื้นคอนกรีต/ปูน"
        "tartan", "rubber" -> "ลู่วิ่งยางสังเคราะห์ (Tartan)"
        "grass" -> "สนามหญ้าจริง"
        "artificial_turf" -> "หญ้าเทียม"
        "wood", "hardwood" -> "พื้นไม้ปาร์เกต์"
        "clay" -> "คอร์ดดิน"
        "sand" -> "พื้นทราย"
        "acrylic", "plastic" -> "พื้นอะคริลิก/ยาง"
        "gravel", "fine_gravel" -> "ทางลูกรัง/กรวด"
        else -> null
    }

    private fun inferCategoryFromText(text: String): NearbySpotCategory {
        val lower = text.lowercase(Locale.ROOT)
        return when {
            lower.contains("สระ") || lower.contains("ว่ายน้ำ") || lower.contains("swim") || lower.contains("pool") ->
                NearbySpotCategory.SWIMMING
            lower.contains("มวย") || lower.contains("muay") || lower.contains("boxing") || lower.contains("โยคะ") || lower.contains("yoga") || lower.contains("pilates") ->
                NearbySpotCategory.MARTIAL_WELLNESS
            lower.contains("จักรยาน") || lower.contains("สเก็ต") || lower.contains("ปีนผา") || lower.contains("bike") || lower.contains("skate") || lower.contains("climb") ->
                NearbySpotCategory.CYCLING_OUTDOOR
            lower.contains("ฟิตเนส") || lower.contains("ยิม") || lower.contains("fitness") || lower.contains("gym") || lower.contains("jetts") || lower.contains("virgin") ->
                NearbySpotCategory.FITNESS_GYM
            lower.contains("สวน") || lower.contains("วิ่ง") || lower.contains("park") || lower.contains("track") ->
                NearbySpotCategory.PARK_RUN
            else -> NearbySpotCategory.SPORTS_COURT
        }
    }

    private fun inferSportFromCategory(category: NearbySpotCategory, name: String): SportType {
        val lower = name.lowercase(Locale.ROOT)
        return when {
            lower.contains("แบด") || lower.contains("badminton") || lower.contains("tennis") -> SportType.BADMINTON
            lower.contains("บาส") || lower.contains("basketball") -> SportType.BASKETBALL
            lower.contains("บอล") || lower.contains("football") || lower.contains("soccer") || lower.contains("futsal") -> SportType.FOOTBALL
            lower.contains("จักรยาน") || lower.contains("cycling") || lower.contains("bike") || category == NearbySpotCategory.CYCLING_OUTDOOR -> SportType.CYCLING
            lower.contains("โยคะ") || lower.contains("yoga") || lower.contains("pilates") -> SportType.GENERAL
            category == NearbySpotCategory.PARK_RUN -> SportType.RUNNING
            else -> SportType.GENERAL
        }
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
        sportTag: String,
        clubTag: String = "",
        highwayTag: String = "",
        routeTag: String = ""
    ): SpotClassification {
        val sportLower = sportTag.lowercase(Locale.ROOT)
        return when {
            leisure == "swimming_pool" || sportLower.contains("swim") -> SpotClassification(
                category = NearbySpotCategory.SWIMMING,
                detailLabel = "สระว่ายน้ำ / ศูนย์กีฬาทางน้ำ",
                fallbackName = "สระว่ายน้ำสาธารณะ",
                suggestedSport = SportType.GENERAL
            )
            amenity == "dojo" || amenity == "yoga_studio" || leisure == "dance" ||
                sportLower.contains("muay") || sportLower.contains("boxing") ||
                sportLower.contains("martial") || sportLower.contains("karate") ||
                sportLower.contains("taekwondo") || sportLower.contains("judo") ||
                sportLower.contains("yoga") || sportLower.contains("pilates") -> {
                val isYoga = amenity == "yoga_studio" || sportLower.contains("yoga") || sportLower.contains("pilates")
                val detail = when {
                    sportLower.contains("muay") || sportLower.contains("boxing") || amenity == "dojo" -> "ค่ายมวย / ศิลปะการต่อสู้"
                    isYoga -> "สตูดิโอโยคะ / พิลาทิส"
                    leisure == "dance" -> "สตูดิโอเต้น / แอโรบิก"
                    else -> "สตูดิโอฟิตเนส / โยคะ"
                }
                SpotClassification(
                    category = NearbySpotCategory.MARTIAL_WELLNESS,
                    detailLabel = detail,
                    fallbackName = detail,
                    suggestedSport = SportType.GENERAL
                )
            }
            highwayTag == "cycleway" || routeTag == "bicycle" ||
                sportLower.contains("cycling") || sportLower.contains("skateboard") ||
                sportLower.contains("climbing") || sportLower.contains("skating") ||
                sportLower.contains("kayak") || sportLower.contains("rowing") -> {
                val detail = when {
                    highwayTag == "cycleway" || routeTag == "bicycle" || sportLower.contains("cycling") -> "เลนจักรยาน / เส้นทางปั่น"
                    sportLower.contains("climbing") -> "ศูนย์ปีนผาจำลอง"
                    sportLower.contains("skate") -> "ลานสเก็ตบอร์ด / โรลเลอร์สเก็ต"
                    else -> "สถานที่กิจกรรมกลางแจ้ง"
                }
                SpotClassification(
                    category = NearbySpotCategory.CYCLING_OUTDOOR,
                    detailLabel = detail,
                    fallbackName = detail,
                    suggestedSport = SportType.CYCLING
                )
            }
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
            leisure == "track" || routeTag == "running" || routeTag == "fitness_trail" -> SpotClassification(
                category = NearbySpotCategory.PARK_RUN,
                detailLabel = "ลู่วิ่ง / เส้นทางวิ่ง",
                fallbackName = "ลู่วิ่งออกกำลังกาย",
                suggestedSport = SportType.RUNNING
            )
            leisure == "park" || leisure == "recreation_ground" || leisure == "nature_reserve" -> SpotClassification(
                category = NearbySpotCategory.PARK_RUN,
                detailLabel = if (leisure == "nature_reserve") "พื้นที่สีเขียว / เส้นทางเดินวิ่ง" else "สวนสาธารณะ",
                fallbackName = "สวนสาธารณะ",
                suggestedSport = SportType.RUNNING
            )
            leisure in setOf("stadium", "sports_centre", "sports_hall", "pitch", "ice_rink", "golf_course", "bowling_alley") ||
                clubTag == "sport" || sportTag.isNotBlank() -> {
                val mappedSport = when {
                    sportLower.contains("soccer") || sportLower.contains("football") || sportLower.contains("futsal") -> SportType.FOOTBALL
                    sportLower.contains("basketball") -> SportType.BASKETBALL
                    sportLower.contains("badminton") || sportLower.contains("tennis") -> SportType.BADMINTON
                    sportLower.contains("cycling") -> SportType.CYCLING
                    sportLower.contains("run") || sportLower.contains("athletics") -> SportType.RUNNING
                    else -> SportType.GENERAL
                }
                val detail = when (leisure) {
                    "stadium" -> "สนามกีฬา / สเตเดียม"
                    "sports_centre" -> "ศูนย์กีฬาครบวงจร"
                    "sports_hall" -> "อาคารกีฬาในร่ม"
                    "golf_course" -> "สนามกอล์ฟ / ไดรฟ์กอล์ฟ"
                    "ice_rink" -> "ลานสเก็ตน้ำแข็ง"
                    "bowling_alley" -> "โบว์ลิ่ง"
                    else -> if (clubTag == "sport") "สโมสรกีฬา" else "ลานกีฬา / สนามฝึก"
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

    private fun getUrl(
        urlString: String,
        connectMs: Int = 9_000,
        readMs: Int = 11_000
    ): String {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectMs
            readTimeout = readMs
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
        const val UNLIMITED_RADIUS_METERS = 0
        private const val MAX_QUERY_RADIUS_METERS = 150_000
        const val DEFAULT_THAILAND_LAT = 13.7563
        const val DEFAULT_THAILAND_LNG = 100.5018

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
