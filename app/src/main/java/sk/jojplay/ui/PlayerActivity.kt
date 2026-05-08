package sk.jojplay.ui

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
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import sk.jojplay.R
import sk.jojplay.data.TokenManager
import java.text.SimpleDateFormat
import java.util.*

class PlayerActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var progressBar: ProgressBar
    private lateinit var epgTitle: TextView
    private lateinit var epgTime: TextView
    private lateinit var epgProgress: android.widget.ProgressBar
    private lateinit var epgContainer: View

    private var player: ExoPlayer? = null
    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideControls() }
    private val epgHandler = Handler(Looper.getMainLooper())
    private var epgRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        playerView   = findViewById(R.id.playerView)
        progressBar  = findViewById(R.id.playerProgress)
        epgTitle     = findViewById(R.id.epgTitle)
        epgTime      = findViewById(R.id.epgTime)
        epgProgress  = findViewById(R.id.epgProgress)
        epgContainer = findViewById(R.id.epgContainer)

        playerView.controllerShowTimeoutMs = 3000
        playerView.controllerAutoShow = false

        val url        = intent.getStringExtra("url") ?: run { finish(); return }
        val isDash     = intent.getBooleanExtra("isDash", true)
        val channelId  = intent.getStringExtra("channelId")
        val channelName = intent.getStringExtra("channelName") ?: ""

        initPlayer(url, isDash)
        scheduleHideControls()

        // Načítaj EPG ak ide o live kanál
        if (channelId != null) {
            loadEpg(channelId, channelName)
        }
    }

    // ── EPG ───────────────────────────────────────────────────────────────────

    private fun loadEpg(channelId: String, channelName: String) {
        val token = TokenManager(this).let {
            // Pokus o token z cache — nevolá coroutine lebo potrebujeme len cached
            android.content.SharedPreferences::class
        }
        val prefs = getSharedPreferences("jojplay_prefs", MODE_PRIVATE)
        val cachedToken = prefs.getString("token", null) ?: return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val now = System.currentTimeMillis()
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                // Firestore query pre EPG daného kanála
                val url = "https://firestore.googleapis.com/v1/projects/tivio-production/databases/(default)/documents:runQuery"
                val body = """
                {
                  "structuredQuery": {
                    "from": [{"collectionId": "epgItems"}],
                    "where": {
                      "compositeFilter": {
                        "op": "AND",
                        "filters": [
                          {"fieldFilter": {"field": {"fieldPath": "tvChannelId"}, "op": "EQUAL", "value": {"referenceValue": "projects/tivio-production/databases/(default)/documents/organizations/dEpbY0V54AE34rFO7dB2/tvChannels/$channelId"}}},
                          {"fieldFilter": {"field": {"fieldPath": "since"}, "op": "LESS_THAN_OR_EQUAL", "value": {"timestampValue": "${SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).apply{timeZone=TimeZone.getTimeZone("UTC")}.format(Date())}"}}},
                          {"fieldFilter": {"field": {"fieldPath": "till"}, "op": "GREATER_THAN", "value": {"timestampValue": "${SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).apply{timeZone=TimeZone.getTimeZone("UTC")}.format(Date())}"}}},
                          {"fieldFilter": {"field": {"fieldPath": "publishedStatus"}, "op": "EQUAL", "value": {"stringValue": "PUBLISHED"}}}
                        ]
                      }
                    },
                    "limit": 1
                  }
                }
                """.trimIndent()

                val req = Request.Builder().url(url)
                    .post(okhttp3.RequestBody.create("application/json".toMediaType(), body))
                    .addHeader("Authorization", "Bearer $cachedToken")
                    .addHeader("Content-Type", "application/json")
                    .build()

                val resp = client.newCall(req).execute()
                val json = org.json.JSONArray(resp.body?.string() ?: return@launch)

                if (json.length() == 0) return@launch
                val doc = json.getJSONObject(0).optJSONObject("document") ?: return@launch
                val fields = doc.getJSONObject("fields")

                val title = fields.optJSONObject("name")
                    ?.optJSONObject("mapValue")?.optJSONObject("fields")
                    ?.optJSONObject("sk")?.optString("stringValue")
                    ?: fields.optJSONObject("name")?.optString("stringValue")
                    ?: channelName

                val sinceStr = fields.optJSONObject("since")?.optString("timestampValue") ?: ""
                val tillStr  = fields.optJSONObject("till")?.optString("timestampValue") ?: ""

                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val localSdf = SimpleDateFormat("HH:mm", Locale.getDefault())

                val since = runCatching { sdf.parse(sinceStr) }.getOrNull()
                val till  = runCatching { sdf.parse(tillStr) }.getOrNull()

                val sinceLocal = since?.let { localSdf.format(it) } ?: ""
                val tillLocal  = till?.let  { localSdf.format(it) } ?: ""

                val progressPercent = if (since != null && till != null) {
                    val total = till.time - since.time
                    val elapsed = System.currentTimeMillis() - since.time
                    ((elapsed.toFloat() / total) * 100).toInt().coerceIn(0, 100)
                } else 0

                withContext(Dispatchers.Main) {
                    epgContainer.visibility = View.VISIBLE
                    epgTitle.text = title
                    epgTime.text = if (sinceLocal.isNotEmpty()) "$sinceLocal – $tillLocal" else ""
                    epgProgress.progress = progressPercent

                    // Obnov EPG každú minútu
                    epgRunnable = Runnable { loadEpg(channelId, channelName) }
                    epgHandler.postDelayed(epgRunnable!!, 60_000)
                }
            } catch (e: Exception) {
                // EPG zlyhalo — skryj ticho
            }
        }
    }

    private fun String.toMediaType() = okhttp3.MediaType.Companion.parse(this)!!

    // ── Player ────────────────────────────────────────────────────────────────

    private fun hideControls() { playerView.hideController() }

    private fun scheduleHideControls() {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, 3000)
    }

    private fun initPlayer(url: String, isDash: Boolean) {
        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView.player = exo
            val dsf = DefaultHttpDataSource.Factory()
                .setUserAgent("JojPlay/2.0 Android")
                .setAllowCrossProtocolRedirects(true)
            val src = when {
                isDash || url.contains(".mpd") -> DashMediaSource.Factory(dsf).createMediaSource(MediaItem.fromUri(Uri.parse(url)))
                url.contains(".m3u8") -> HlsMediaSource.Factory(dsf).createMediaSource(MediaItem.fromUri(Uri.parse(url)))
                else -> ProgressiveMediaSource.Factory(dsf).createMediaSource(MediaItem.fromUri(Uri.parse(url)))
            }
            exo.setMediaSource(src)
            exo.prepare()
            exo.playWhenReady = true
            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    progressBar.visibility = if (state == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
                    if (state == Player.STATE_READY) scheduleHideControls()
                }
                override fun onPlayerError(error: PlaybackException) {
                    Toast.makeText(this@PlayerActivity, "Chyba: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                    finish()
                }
            })
        }
    }

    override fun onPause()   { super.onPause();   player?.pause() }
    override fun onResume()  { super.onResume();  player?.play()  }
    override fun onDestroy() {
        super.onDestroy()
        hideHandler.removeCallbacks(hideRunnable)
        epgRunnable?.let { epgHandler.removeCallbacks(it) }
        player?.release(); player = null
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_DPAD_CENTER -> {
                player?.let { if (it.isPlaying) it.pause() else it.play() }
                scheduleHideControls(); true
            }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { finish(); true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                player?.seekTo((player!!.currentPosition + 10_000).coerceAtMost(player!!.duration))
                scheduleHideControls(); true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                player?.seekTo((player!!.currentPosition - 10_000).coerceAtLeast(0))
                scheduleHideControls(); true
            }
            else -> { playerView.showController(); scheduleHideControls(); super.onKeyDown(keyCode, event) }
        }
    }
}
