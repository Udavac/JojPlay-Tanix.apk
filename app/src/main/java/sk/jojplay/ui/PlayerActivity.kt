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
    private val hideRunnable = Runnable { playerView.hideController() }
    private val epgHandler = Handler(Looper.getMainLooper())
    private var epgRefreshRunnable: Runnable? = null
    private val client = OkHttpClient()

    private val SMS_CHANNEL_MAP = mapOf(
        "33-joj"            to "JOJ",
        "206-joj-plus"      to "JOJ+Plus",
        "718-wau"           to "WAU",
        "15441-joj-24"      to "JOJ+24",
        "15291-joj-sport"   to "JOJ+%8Aport",
        "15611-joj-sport-2" to "JOJ+%8Aport+2",
        "15509-joj-svet"    to "JOJ+Svet",
        "999-jojko"         to "Jojko",
        "1023-joj-cinema"   to "JOJ+Cinema",
        "1149-cs-film"      to "CS+Film",
        "1187-cs-history"   to "CS+History",
        "717-cs-mystery"    to "CS+Mystery"
    )

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
        epgContainer.visibility = View.GONE

        if (channelId != null) {
            val smsId = SMS_CHANNEL_MAP[channelId]
            if (smsId != null) {
                loadEpgFromSms(smsId, channelName)
            }
        }
    }

    // ── EPG zo sms.cz ─────────────────────────────────────────────────────────

    private fun loadEpgFromSms(smsId: String, channelName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val url = "https://m.tv.sms.cz/index.php?den=$date&stanice=$smsId&cas=0"

                val req = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
                    .build()

                val html = client.newCall(req).execute().use { it.body?.string() ?: "" }

                val now = System.currentTimeMillis()
                val cal = Calendar.getInstance()
                val timeSdf = SimpleDateFormat("HH:mm", Locale.getDefault())

                // Správny regex podľa skutočnej štruktúry SMS HTML:
                // <span style='color: #9E913B'>05.30</span>
                // <a class='nazev' href='...'>Názov programu</a>
                val regex = Regex("""<span[^>]*>(\d{2}\.\d{2})</span>.*?<a class='nazev'[^>]*>\s*<div[^>]*>\s*([^<]{2,100}?)\s*<div""", RegexOption.DOT_MATCHES_ALL)
                val allPrograms = mutableListOf<Pair<Long, String>>()

                for (match in regex.findAll(html)) {
                    val timeStr = match.groupValues[1].replace(".", ":")
                    val title = match.groupValues[2]
                        .replace("&amp;", "&")
                        .replace("&nbsp;", " ")
                        .replace("&#[0-9]+;".toRegex(), "")
                        .trim()

                    if (title.isBlank() || title.length < 2) continue

                    runCatching {
                        val parts = timeStr.split(":")
                        cal.set(Calendar.HOUR_OF_DAY, parts[0].toInt())
                        cal.set(Calendar.MINUTE, parts[1].toInt())
                        cal.set(Calendar.SECOND, 0)
                        allPrograms.add(Pair(cal.timeInMillis, title))
                    }
                }

                var currentTitle = channelName
                var currentStart = ""
                var nextStart = ""
                var currentStartMs = 0L
                var nextStartMs = 0L

                for (i in allPrograms.indices) {
                    if (allPrograms[i].first <= now) {
                        currentTitle = allPrograms[i].second
                        currentStartMs = allPrograms[i].first
                        currentStart = timeSdf.format(Date(currentStartMs))
                        if (i + 1 < allPrograms.size) {
                            nextStartMs = allPrograms[i + 1].first
                            nextStart = timeSdf.format(Date(nextStartMs))
                        }
                    }
                }

                val progress = if (currentStartMs > 0 && nextStartMs > currentStartMs) {
                    val total = nextStartMs - currentStartMs
                    val elapsed = now - currentStartMs
                    ((elapsed.toFloat() / total) * 100).toInt().coerceIn(0, 100)
                } else 0

                withContext(Dispatchers.Main) {
                    epgTitle.text = currentTitle
                    epgTime.text = if (nextStart.isNotEmpty()) "$currentStart – $nextStart" else currentStart
                    epgBar.progress = progress
                    epgContainer.visibility = View.VISIBLE
                }

                // Obnov o 5 minút
                val refresh = Runnable { loadEpgFromSms(smsId, channelName) }
                epgRefreshRunnable = refresh
                epgHandler.postDelayed(refresh, 5 * 60 * 1000L)

            } catch (e: Exception) {
                // Tiché zlyhanie
            }
        }
    }

    // ── Player controls ───────────────────────────────────────────────────────

    private fun scheduleHide() {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, 4000)
    }

    private fun showController() {
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
                showController(); true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                player?.let { if (it.isPlaying) it.pause() else it.play() }; true
            }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                epgContainer.visibility = View.GONE
                finish(); true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                player?.seekTo((player!!.currentPosition + 10_000).coerceAtMost(player!!.duration)); true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                player?.seekTo((player!!.currentPosition - 10_000).coerceAtLeast(0)); true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
}
