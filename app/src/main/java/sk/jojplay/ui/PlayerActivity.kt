package sk.jojplay.ui

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import sk.jojplay.R
import java.text.SimpleDateFormat
import java.util.*

class PlayerActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var progressBar: ProgressBar
    private lateinit var epgContainer: View
    private lateinit var epgTitle: TextView
    private lateinit var epgTime: TextView
    private lateinit var epgBar: ProgressBar

    private var player: ExoPlayer? = null
    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideAll() }
    private val epgHandler = Handler(Looper.getMainLooper())
    private var epgRefreshRunnable: Runnable? = null
    private val client = OkHttpClient()

    companion object {
        private const val FIRESTORE = "https://firestore.googleapis.com/v1/projects/tivio-production/databases/(default)/documents"
        private const val ORG = "dEpbY0V54AE34rFO7dB2"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        playerView   = findViewById(R.id.playerView)
        progressBar  = findViewById(R.id.playerProgress)
        epgContainer = findViewById(R.id.epgContainer)
        epgTitle     = findViewById(R.id.epgTitle)
        epgTime      = findViewById(R.id.epgTime)
        epgBar       = findViewById(R.id.epgBar)

        playerView.controllerShowTimeoutMs = 3000
        playerView.controllerAutoShow = false

        val url         = intent.getStringExtra("url") ?: run { finish(); return }
        val isDash      = intent.getBooleanExtra("isDash", true)
        val channelId   = intent.getStringExtra("channelId")
        val channelName = intent.getStringExtra("channelName") ?: ""

        initPlayer(url, isDash)
        hideAll()

        if (channelId != null) {
            loadEpg(channelId, channelName)
        }
    }

    // ── EPG ───────────────────────────────────────────────────────────────────

    private fun loadEpg(tivioChannelId: String, channelName: String) {
        val token = getSharedPreferences("jojplay_prefs", Context.MODE_PRIVATE)
            .getString("token", null) ?: return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Krok 1: Načítaj tvChannel dokument z Firestore aby sme dostali správne ID
                val channelDoc = getFirestore(
                    "$FIRESTORE/organizations/$ORG/tvChannels/$tivioChannelId",
                    token
                )

                // Ak nenájdeme, skúsime priamo s tivioChannelId
                val tvChannelRef = "projects/tivio-production/databases/(default)/documents/organizations/$ORG/tvChannels/$tivioChannelId"

                // Krok 2: Načítaj aktuálny program z tvPrograms
                loadEpgProgram(tvChannelRef, channelName, token, tivioChannelId)

            } catch (e: Exception) {
                // Tiché zlyhanie
            }
        }
    }

    private suspend fun loadEpgProgram(tvChannelRef: String, channelName: String, token: String, tivioChannelId: String) {
        try {
            val now = System.currentTimeMillis()
            val utcSdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val nowStr = utcSdf.format(Date(now))

            val body = JSONObject().apply {
                put("structuredQuery", JSONObject().apply {
                    put("from", JSONArray().put(JSONObject().put("collectionId", "tvPrograms")))
                    put("where", JSONObject().apply {
                        put("compositeFilter", JSONObject().apply {
                            put("op", "AND")
                            put("filters", JSONArray().apply {
                                put(fieldFilter("tvChannelRef", "EQUAL",
                                    JSONObject().put("referenceValue", tvChannelRef)))
                                put(fieldFilter("from", "LESS_THAN_OR_EQUAL",
                                    JSONObject().put("timestampValue", nowStr)))
                                put(fieldFilter("to", "GREATER_THAN",
                                    JSONObject().put("timestampValue", nowStr)))
                            })
                        })
                    })
                    put("limit", 1)
                })
            }.toString()

            val req = Request.Builder()
                .url("$FIRESTORE:runQuery")
                .post(body.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $token")
                .build()

            val resp = client.newCall(req).execute()
            val json = JSONArray(resp.body?.string() ?: return)

            if (json.length() == 0) return
            val doc = json.getJSONObject(0).optJSONObject("document") ?: return
            val fields = doc.getJSONObject("fields")

            // Načítaj názov z video.name alebo priamo z name
            val videoFields = fields.optJSONObject("video")
                ?.optJSONObject("mapValue")?.optJSONObject("fields")

            val title = videoFields?.let { getLangFromFields(it, "name") }
                ?: getLangFromFields(fields, "name")
                ?: channelName

            val fromStr = fields.optJSONObject("from")?.optString("timestampValue") ?: ""
            val toStr   = fields.optJSONObject("to")?.optString("timestampValue") ?: ""

            val localSdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            val utcSdf2  = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }

            val fromDate = runCatching { utcSdf2.parse(fromStr) }.getOrNull()
            val toDate   = runCatching { utcSdf2.parse(toStr) }.getOrNull()

            val fromLocal = fromDate?.let { localSdf.format(it) } ?: ""
            val toLocal   = toDate?.let { localSdf.format(it) } ?: ""

            val progress = if (fromDate != null && toDate != null) {
                val total = toDate.time - fromDate.time
                val elapsed = now - fromDate.time
                ((elapsed.toFloat() / total) * 100).toInt().coerceIn(0, 100)
            } else 0

            withContext(Dispatchers.Main) {
                epgTitle.text = title
                epgTime.text = if (fromLocal.isNotEmpty()) "$fromLocal – $toLocal" else ""
                epgBar.progress = progress
            }

            // Obnov keď sa program skončí
            if (toDate != null) {
                val delay = (toDate.time - now + 5000).coerceAtLeast(60_000)
                val refresh = Runnable { 
                    CoroutineScope(Dispatchers.IO).launch {
                        loadEpgProgram(tvChannelRef, channelName, token, tivioChannelId)
                    }
                }
                epgRefreshRunnable = refresh
                epgHandler.postDelayed(refresh, delay)
            }

        } catch (e: Exception) {
            // Tiché zlyhanie
        }
    }

    private fun fieldFilter(field: String, op: String, value: JSONObject) = JSONObject().apply {
        put("fieldFilter", JSONObject().apply {
            put("field", JSONObject().put("fieldPath", field))
            put("op", op)
            put("value", value)
        })
    }

    private fun getLangFromFields(fields: JSONObject, key: String): String? {
        val field = fields.optJSONObject(key) ?: return null
        if (field.has("stringValue")) return field.getString("stringValue").takeIf { it.isNotEmpty() }
        val map = field.optJSONObject("mapValue")?.optJSONObject("fields") ?: return null
        return map.optJSONObject("sk")?.optString("stringValue")?.takeIf { it.isNotEmpty() }
            ?: map.optJSONObject("cs")?.optString("stringValue")?.takeIf { it.isNotEmpty() }
            ?: map.optJSONObject("en")?.optString("stringValue")?.takeIf { it.isNotEmpty() }
    }

    private fun getFirestore(url: String, token: String): JSONObject {
        val req = Request.Builder().url(url)
            .addHeader("Authorization", "Bearer $token").build()
        client.newCall(req).execute().use { resp ->
            return JSONObject(resp.body?.string() ?: "{}")
        }
    }

    // ── Player controls ───────────────────────────────────────────────────────

    private fun hideAll() {
        playerView.hideController()
        epgContainer.visibility = View.GONE
    }

    private fun scheduleHide() {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, 4000)
    }

    private fun showInfo() {
        epgContainer.visibility = View.VISIBLE
        playerView.showController()
        scheduleHide()
    }

    private fun initPlayer(url: String, isDash: Boolean) {
        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView.player = exo
            val dsf = DefaultHttpDataSource.Factory()
                .setUserAgent("JojPlay/2.0 Android")
                .setAllowCrossProtocolRedirects(true)
            val src = when {
                url.contains(".m3u8") ->
                    HlsMediaSource.Factory(dsf).createMediaSource(MediaItem.fromUri(Uri.parse(url)))
                isDash || url.contains(".mpd") ->
                    DashMediaSource.Factory(dsf).createMediaSource(MediaItem.fromUri(Uri.parse(url)))
                else ->
                    ProgressiveMediaSource.Factory(dsf).createMediaSource(MediaItem.fromUri(Uri.parse(url)))
            }
            exo.setMediaSource(src)
            exo.prepare()
            exo.playWhenReady = true
            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    progressBar.visibility = if (state == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
                }
                override fun onPlayerError(error: PlaybackException) {
                    Toast.makeText(this@PlayerActivity, "Chyba: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                    finish()
                }
            })
        }
    }

    override fun onPause()   { super.onPause();  player?.pause() }
    override fun onResume()  { super.onResume(); player?.play()  }
    override fun onDestroy() {
        super.onDestroy()
        hideHandler.removeCallbacks(hideRunnable)
        epgRefreshRunnable?.let { epgHandler.removeCallbacks(it) }
        player?.release(); player = null
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (epgContainer.visibility == View.VISIBLE) hideAll() else showInfo()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                player?.let { if (it.isPlaying) it.pause() else it.play() }
                true
            }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { finish(); true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                player?.seekTo((player!!.currentPosition + 10_000).coerceAtMost(player!!.duration))
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                player?.seekTo((player!!.currentPosition - 10_000).coerceAtLeast(0))
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
}
