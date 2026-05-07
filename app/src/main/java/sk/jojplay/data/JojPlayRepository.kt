package sk.jojplay.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class JojPlayRepository(private val token: String) {

    companion object {
        private const val ORG = "dEpbY0V54AE34rFO7dB2"
        private const val FIRESTORE = "https://firestore.googleapis.com/v1/projects/tivio-production/databases/(default)/documents"
        private const val FUNCTIONS = "https://europe-west3-tivio-production.cloudfunctions.net"
        private const val ALGOLIA_APP = "OL4UZ1QNHS"
        private const val ALGOLIA_KEY = "NTdiZTE4MWI4NGYzYWU0ZGE1ZDVlNWVmZWM2MGFkYWE4NWI2ODNhMTVmMTkxMTg2YWIwMzQwNmQzYzEzMDE2MHJlc3RyaWN0SW5kaWNlcz0lNUIlMjJ2aWRlb3MlMjIlMkMlMjJ2aWRlb3NfY3JlYXRlZF9kZXNjJTIyJTJDJTIydGFncyUyMiUyQyUyMnR2Q2hhbm5lbHMlMjIlNUQ="

        private val IMAGE_KEY_PRIORITY = listOf(
            "video_detail", "tag_detail", "cover", "tag_landscape_cover", "banner",
            "banner_mobile", "portrait", "tag_banner", "tag_banner_mobile",
            "tag_portrait_cover", "background_banner_mobile"
        )

        private val SKIP_ROWS = setOf("Live TV", "Pokračovať v sledovaní", "Môj Zoznam")
    }

    private val client = OkHttpClient()
    private val jsonType = "application/json".toMediaType()

    // ── Auth headers ──────────────────────────────────────────────────────────

    private fun authHeaders() = mapOf(
        "Content-Type" to "application/json",
        "Authorization" to "Bearer $token"
    )

    // ── Screen / Home ─────────────────────────────────────────────────────────

    suspend fun getItemsInScreen(screenId: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("data", JSONObject().apply {
                put("organizationId", ORG)
                put("screenId", screenId)
                put("offset", 0)
                put("limit", 100)
                put("initialTilesCount", 10)
            })
        }.toString()

        val response = post("$FUNCTIONS/getRowsInScreen3", payload, authHeaders())
        val result = response.getJSONObject("result")
        val items = result.getJSONArray("items")

        val out = mutableListOf<MediaItem>()
        val seen = mutableSetOf<String>()

        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            when (item.optString("rowComponent")) {
                "BANNER" -> {
                    val tilesItems = item.getJSONObject("tiles").getJSONArray("items")
                    if (tilesItems.length() == 0) continue
                    val tile = tilesItems.getJSONObject(0)
                    val id = tile.optString("id").takeIf { it.isNotEmpty() } ?: continue
                    if (id in seen) continue
                    seen.add(id)
                    val isVideo = tile.optString("itemType") == "VIDEO"
                    val specific = tile.optJSONObject("itemSpecificData")
                    val image = specific?.optJSONObject("assets")?.let { extractImage(it) }
                        ?: "https://assets.tivio.studio/videos/$id/cover"
                    out.add(MediaItem(
                        id = id,
                        type = if (isVideo) ItemType.VIDEO else ItemType.SERIES,
                        name = getLang(tile.opt("name")) ?: id,
                        description = specific?.let { getLang(it.opt("description")) },
                        image = image
                    ))
                }
              "ROW" -> {
    val name = getLang(item.opt("name")) ?: continue
    if (name in SKIP_ROWS) continue
    val path = item.optString("path", "")
    val catId = path.substringAfterLast("/")
    if (catId.isBlank() || catId in seen) continue
    seen.add(catId)
    // Skús získať obrázok z prvého tile v ROW
    val firstTile = item.optJSONObject("tiles")
        ?.getJSONArray("items")
        ?.takeIf { it.length() > 0 }
        ?.getJSONObject(0)
    val rowImage = firstTile?.optJSONObject("itemSpecificData")
        ?.optJSONObject("assets")?.let { extractImage(it) }
        ?: firstTile?.let {
            val id = it.optString("id")
            if (id.isNotEmpty()) "https://assets.tivio.studio/videos/$id/cover" else null
        }
    out.add(MediaItem(id = catId, type = ItemType.CATEGORY, name = name, image = rowImage))
}
    // ── Category ──────────────────────────────────────────────────────────────

    suspend fun getItemsInCategory(rowId: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val rowResp = get("$FIRESTORE/organizations/$ORG/rows/$rowId")
        val customItems = rowResp
            .getJSONObject("fields")
            .getJSONObject("customItems")
            .getJSONObject("arrayValue")
            .optJSONArray("values") ?: return@withContext emptyList()

        val videoRefs = mutableListOf<JSONObject>()
        val seriesRefs = mutableListOf<JSONObject>()

        for (i in 0 until customItems.length()) {
            val ref = customItems.getJSONObject(i)
                .getJSONObject("mapValue").getJSONObject("fields")
                .getJSONObject("itemRef").getString("referenceValue")
            when {
                "/videos/" in ref -> videoRefs.add(JSONObject().put("referenceValue", ref))
                "/tags/" in ref   -> seriesRefs.add(JSONObject().put("referenceValue", ref))
            }
        }

        val videoData = mutableListOf<JSONObject>()
        for (chunk in videoRefs.chunked(30)) {
            val q = buildVideoInQuery(chunk, includePublished = true)
            val res = postFirestore("", q)
            for (j in 0 until res.length()) {
                res.getJSONObject(j).optJSONObject("document")?.let { videoData.add(it) }
            }
        }

        val seriesData = mutableListOf<JSONObject>()
        for (chunk in seriesRefs.chunked(30)) {
            val q = buildTagInQuery(chunk)
            val res = postFirestore("/organizations/$ORG", q)
            for (j in 0 until res.length()) {
                res.getJSONObject(j).optJSONObject("document")?.let { seriesData.add(it) }
            }
        }

        val out = mutableListOf<MediaItem>()
        for (i in 0 until customItems.length()) {
            val ref = customItems.getJSONObject(i)
                .getJSONObject("mapValue").getJSONObject("fields")
                .getJSONObject("itemRef").getString("referenceValue")
            val itemId = ref.substringAfterLast("/")

            if ("/videos/" in ref) {
                val doc = videoData.find { it.getString("name") == ref } ?: continue
                out.add(parseVideoFirestore(doc))
            } else {
                val doc = seriesData.find { it.getString("name") == ref } ?: continue
                val fields = doc.getJSONObject("fields")
                val image = fields.optJSONObject("assets")?.let { extractImageFirestore(it) }
                    ?: "https://assets.tivio.studio/videos/$itemId/cover"
                out.add(MediaItem(
                    id = itemId,
                    type = ItemType.SERIES,
                    name = getLangFirestore(fields.opt("name")) ?: itemId,
                    description = getLangFirestore(fields.opt("description")),
                    image = image
                ))
            }
        }
        out
    }

    // ── Series → seasons ──────────────────────────────────────────────────────

    suspend fun getItemsInSeries(seriesId: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val resp = get("$FIRESTORE/organizations/$ORG/tags/$seriesId")
        val fields = resp.getJSONObject("fields")
        val metadata = fields.optJSONObject("metadata")
            ?.getJSONObject("arrayValue")?.optJSONArray("values") ?: return@withContext emptyList()

        val seasons = mutableListOf<MediaItem>()
        for (i in 0 until metadata.length()) {
            val entry = metadata.getJSONObject(i).getJSONObject("mapValue").getJSONObject("fields")
            if (entry.optJSONObject("key")?.optString("stringValue") == "availableSeasons") {
                val values = entry.getJSONObject("value").getJSONObject("arrayValue")
                    .optJSONArray("values") ?: continue
                for (j in 0 until values.length()) {
                    val sv = values.getJSONObject(j).getJSONObject("mapValue").getJSONObject("fields")
                    val seasonNum = sv.getJSONObject("seasonNumber").getString("integerValue").toInt()
                    seasons.add(MediaItem(
                        id = seriesId,
                        type = ItemType.SEASON,
                        name = "Séria $seasonNum",
                        season = seasonNum
                    ))
                }
                seasons.sortBy { it.season }
            }
        }
        seasons
    }

    // ── Season episodes ───────────────────────────────────────────────────────

    suspend fun getItemsInSeason(seriesId: String, seasonNumber: Int): List<MediaItem> = withContext(Dispatchers.IO) {
        val query = JSONObject().apply {
            put("structuredQuery", JSONObject().apply {
                put("from", JSONArray().put(JSONObject().put("collectionId", "videos")))
                put("where", JSONObject().apply {
                    put("compositeFilter", JSONObject().apply {
                        put("op", "AND")
                        put("filters", JSONArray().apply {
                            put(fieldFilter("tags", "ARRAY_CONTAINS_ANY", JSONObject().apply {
                                put("arrayValue", JSONObject().put("values", JSONArray().put(
                                    JSONObject().put("referenceValue",
                                        "projects/tivio-production/databases/(default)/documents/organizations/$ORG/tags/$seriesId")
                                )))
                            }))
                            put(fieldFilter("publishedStatus", "EQUAL", JSONObject().put("stringValue", "PUBLISHED")))
                            put(fieldFilter("transcodingStatus", "EQUAL", JSONObject().put("stringValue", "ENCODING_DONE")))
                            put(fieldFilter("seasonNumber", "EQUAL", JSONObject().put("integerValue", seasonNumber.toString())))
                        })
                    })
                })
                put("orderBy", JSONArray().apply {
                    put(JSONObject().apply { put("field", JSONObject().put("fieldPath", "episodeNumber")); put("direction", "ASCENDING") })
                    put(JSONObject().apply { put("field", JSONObject().put("fieldPath", "__name__")); put("direction", "ASCENDING") })
                })
            })
        }.toString()

        val results = postFirestore("", query)
        val out = mutableListOf<MediaItem>()
        for (i in 0 until results.length()) {
            results.getJSONObject(i).optJSONObject("document")?.let { out.add(parseVideoFirestore(it)) }
        }
        out
    }

    // ── Play ──────────────────────────────────────────────────────────────────

    suspend fun getPlayUrl(id: String, documentType: String): PlayResult = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("data", JSONObject().apply {
                put("id", id)
                put("documentType", documentType)
                put("capabilities", JSONArray().put(JSONObject().apply {
                    put("codec", "h264")
                    put("protocol", "dash")
                    put("encryption", "none")
                }))
            })
        }.toString()

        val resp = post("$FUNCTIONS/getSourceUrl", payload, authHeaders())

        if (resp.has("error")) {
            val msg = resp.getJSONObject("error").optString("message", "Neznáma chyba")
            return@withContext PlayResult(null, errorMessage = msg)
        }

        val result = resp.optJSONObject("result")
            ?: return@withContext PlayResult(null, errorMessage = "Prázdna odpoveď servera")

        val url = result.optString("url").takeIf { it.isNotEmpty() }
            ?: return@withContext PlayResult(null, errorMessage = "URL nie je dostupná")

        // Ak server vráti "internal" URL, ide o chránený stream
        if (url.startsWith("internal")) {
            return@withContext PlayResult(null, errorMessage = "Stream nie je dostupný (interný)")
        }

        PlayResult(url = url, isDash = true)
    }

    // ── Search ────────────────────────────────────────────────────────────────

    suspend fun search(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<MediaItem>()
        results += searchIndex(query, "videos", ItemType.VIDEO)
        results += searchIndex(query, "tags", ItemType.SERIES)
        results
    }

    private fun searchIndex(query: String, index: String, type: ItemType): List<MediaItem> {
        val filter = if (type == ItemType.VIDEO)
            "organizationPath:organizations/$ORG AND isDraft:false AND isDuplicate:false AND hide:false AND NOT contentType:SERIES"
        else
            "organizationPath:organizations/$ORG"

        val payload = JSONObject().apply {
            put("query", query)
            put("filters", filter)
            put("hitsPerPage", 5)
            put("page", 0)
        }.toString()

        val url = "https://ol4uz1qnhs-dsn.algolia.net/1/indexes/$index/query" +
                "?x-algolia-api-key=$ALGOLIA_KEY&x-algolia-application-id=$ALGOLIA_APP"

        val resp = post(url, payload, mapOf("Content-Type" to "application/json"))
        val hits = resp.getJSONArray("hits")
        val out = mutableListOf<MediaItem>()
        for (i in 0 until hits.length()) {
            val hit = hits.getJSONObject(i)
            val id = hit.getString("objectID")
            val image = hit.optJSONObject("assets")?.let { extractImage(it) }
                ?: "https://assets.tivio.studio/videos/$id/cover"
            out.add(MediaItem(
                id = id,
                type = type,
                name = getLang(hit.opt("name")) ?: id,
                description = getLang(hit.opt("description")),
                image = image
            ))
        }
        return out
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private fun get(url: String): JSONObject {
        val req = Request.Builder().url(url)
            .addHeader("Authorization", "Bearer $token").build()
        client.newCall(req).execute().use { resp ->
            return JSONObject(resp.body?.string() ?: throw Exception("Empty response"))
        }
    }

    private fun post(url: String, body: String, headers: Map<String, String>): JSONObject {
        val builder = Request.Builder().url(url).post(body.toRequestBody(jsonType))
        headers.forEach { (k, v) -> builder.addHeader(k, v) }
        client.newCall(builder.build()).execute().use { resp ->
            return JSONObject(resp.body?.string() ?: throw Exception("Empty response"))
        }
    }

    private fun postFirestore(path: String, body: String): JSONArray {
        val url = "$FIRESTORE$path:runQuery"
        val builder = Request.Builder().url(url).post(body.toRequestBody(jsonType))
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
        client.newCall(builder.build()).execute().use { resp ->
            return JSONArray(resp.body?.string() ?: throw Exception("Empty response"))
        }
    }

    // ── Query builders ────────────────────────────────────────────────────────

    private fun fieldFilter(field: String, op: String, value: JSONObject) = JSONObject().apply {
        put("fieldFilter", JSONObject().apply {
            put("field", JSONObject().put("fieldPath", field))
            put("op", op)
            put("value", value)
        })
    }

    private fun buildVideoInQuery(refs: List<JSONObject>, includePublished: Boolean): String {
        val filters = mutableListOf(
            fieldFilter("__name__", "IN", JSONObject().put("arrayValue", JSONObject().put("values", JSONArray(refs))))
        )
        if (includePublished)
            filters.add(fieldFilter("publishedStatus", "EQUAL", JSONObject().put("stringValue", "PUBLISHED")))

        return JSONObject().apply {
            put("structuredQuery", JSONObject().apply {
                put("from", JSONArray().put(JSONObject().put("collectionId", "videos")))
                put("where", JSONObject().apply {
                    put("compositeFilter", JSONObject().apply {
                        put("op", "AND")
                        put("filters", JSONArray(filters))
                    })
                })
                put("orderBy", JSONArray().put(
                    JSONObject().apply { put("field", JSONObject().put("fieldPath", "__name__")); put("direction", "ASCENDING") }
                ))
            })
        }.toString()
    }

    private fun buildTagInQuery(refs: List<JSONObject>): String {
        return JSONObject().apply {
            put("structuredQuery", JSONObject().apply {
                put("from", JSONArray().put(JSONObject().put("collectionId", "tags")))
                put("where", fieldFilter("__name__", "IN",
                    JSONObject().put("arrayValue", JSONObject().put("values", JSONArray(refs)))))
                put("orderBy", JSONArray().put(
                    JSONObject().apply { put("field", JSONObject().put("fieldPath", "__name__")); put("direction", "ASCENDING") }
                ))
            })
        }.toString()
    }

    // ── Parsing helpers ───────────────────────────────────────────────────────

    private fun parseVideoFirestore(doc: JSONObject): MediaItem {
        val id = doc.getString("name").substringAfterLast("/")
        val fields = doc.getJSONObject("fields")
        val name = getLangFirestore(fields.opt("name"))
            ?: fields.optJSONObject("episodeNumber")?.optString("integerValue")?.let { "Epizóda $it" }
            ?: "Epizóda"
        val image = fields.optJSONObject("assets")?.let { extractImageFirestore(it) }
            ?: "https://assets.tivio.studio/videos/$id/cover"
        return MediaItem(
            id = id,
            type = ItemType.VIDEO,
            name = name,
            description = getLangFirestore(fields.opt("description")),
            image = image
        )
    }

    private fun getLang(input: Any?): String? = when (input) {
        null -> null
        is String -> input.takeIf { it.isNotEmpty() }
        is JSONObject -> input.optString("sk").takeIf { it.isNotEmpty() }
            ?: input.optString("cs").takeIf { it.isNotEmpty() }
            ?: input.optString("en").takeIf { it.isNotEmpty() }
        else -> null
    }

    private fun getLangFirestore(input: Any?): String? {
        if (input == null) return null
        val obj = input as? JSONObject ?: return null
        if (obj.has("stringValue")) return obj.getString("stringValue").takeIf { it.isNotEmpty() }
        val map = obj.optJSONObject("mapValue")?.optJSONObject("fields") ?: return null
        return map.optJSONObject("sk")?.optString("stringValue")?.takeIf { it.isNotEmpty() }
            ?: map.optJSONObject("cs")?.optString("stringValue")?.takeIf { it.isNotEmpty() }
            ?: map.optJSONObject("en")?.optString("stringValue")?.takeIf { it.isNotEmpty() }
    }

    private fun extractImage(assets: JSONObject): String? {
        for (key in IMAGE_KEY_PRIORITY) {
            val tagData = assets.optJSONObject(key) ?: continue
            val at1 = tagData.optJSONObject("@1") ?: continue
            return at1.optString("background").takeIf { it.isNotEmpty() }
        }
        return null
    }

    private fun extractImageFirestore(assets: JSONObject): String? {
        val fields = assets.optJSONObject("mapValue")?.optJSONObject("fields") ?: return null
        for (key in IMAGE_KEY_PRIORITY) {
            val tagData = fields.optJSONObject(key)?.optJSONObject("mapValue")?.optJSONObject("fields") ?: continue
            val at1 = tagData.optJSONObject("@1")?.optJSONObject("mapValue")?.optJSONObject("fields") ?: continue
            return at1.optJSONObject("background")?.optString("stringValue")?.takeIf { it.isNotEmpty() }
        }
        return null
    }
}
