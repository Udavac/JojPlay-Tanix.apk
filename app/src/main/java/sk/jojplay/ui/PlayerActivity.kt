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
import androidx.media3.common.TrackSelectionOverride
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
    private val TZ = TimeZone.getTimeZone("Europe/Bratislava")
    private val epgHideHandler = Handler(Looper.getMainLooper())
    private val epgHideRunnable = Runnable { epgContainer.visibility = View.GONE }
    private var isLiveChannel = false
    private var currentSpeed = 1.0f
    private var currentAudioTrack = 0

    private val SMS_CHANNEL_MAP = mapOf(
        "33-joj"            to "JOJ",
        "206-joj-plus"      to "JOJ+Plus",
        "718-wau"           to "JOJ+Krimi",
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

        val url         = intent.getStringExtra("url") ?: run { finish(); return }
        val isDash      = intent.getBooleanExtra("isDash", true)
        val channelId   = intent.getStringExtra("channelId")
        val channelName = intent.getStringExtra("channelName") ?: ""
        isLiveChannel   = channelId != null && SMS_CHANNEL_MAP.containsKey(channelId)

        playerView.controllerShowTimeoutMs = 3000
        playerView.controllerAutoShow = false
        // Vypni ExoPlayer settings – použijeme vlastný dialóg
        playerView.setShowNextButton(false)
        playerView.setShowPreviousButton(false)
        playerView.setShowFastForwardButton(!isLiveChannel)
        playerView.setShowRewindButton(!isLiveChannel)
        playerView.setShowShuffleButton(false)
        playerView.setShowSubtitleButton(false)

        initPlayer(url, isDash)
        epgContainer.visibility = View.GONE

        if (channelId != null) {
            val smsId = SMS_CHANNEL_MAP[channelId]
            if (smsId != null) loadEpgFromSms(smsId, channelName)
        }
    }

    // ── Settings dialóg ───────────────────────────────────────────────────────

    private fun openSettingsDialog() {
        val exo = player ?: return

        // Získaj audio stopy
        val audioTracks = mutableListOf<Pair<String, Int>>()
        val tracks = exo.currentTracks
        var audioGroupIndex = 0
        for (group in tracks.groups) {
            if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val lang = format.language ?: "Automaticky"
                    val bitrate = if (format.bitrate > 0) " ${format.bitrate / 8000},${(format.bitrate / 800) % 10} MB/s" else ""
                    val label = if (lang == "und" || lang == "Automaticky") "Automaticky" else "$lang$bitrate"
                    audioTracks.add(Pair(label, audioGroupIndex))
                    audioGroupIndex++
                }
            }
        }
        if (audioTracks.isEmpty()) {
            audioTracks.add(Pair("Automaticky", 0))
        }

        val dialog = PlayerSettingsDialog(
            context = this,
            currentSpeed = currentSpeed,
            audioTracks = audioTracks,
            currentAudioTrack = currentAudioTrack,
            onSpeedSelected = { speed ->
                currentSpeed = speed
                exo.setPlaybackSpeed(speed)
            },
            onAudioSelected = { index ->
                currentAudioTrack = index
                // Nastav audio stopu
                try {
                    val trackGroups = exo.currentTracks.groups
                    var audioIdx = 0
                    for (group in trackGroups) {
                        if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                            for (i in 0 until group.length) {
                                if (audioIdx == index) {
                                    exo.trackSelectionParameters = exo.trackSelectionParameters
                                        .buildUpon()
                                        .setOverrideForType(
                                            TrackSelectionOverride(group.mediaTrackGroup, i)
                                        )
                                        .build()
                                    return@PlayerSettingsDialog
                                }
                                audioIdx++
                            }
                        }
                    }
                } catch (e: Exception) { }
            }
        )
        dialog.show()
    }

    // ── EPG ───────────────────────────────────────────────────────────────────

    private fun showEpg() {
        epgContainer.visibility = View.VISIBLE
        epgHideHandler.removeCallbacks(epgHideRunnable)
        epgHideHandler.postDelayed(epgHideRunnable, 5000L)
    }

    private fun loadEpgFromSms(smsId: String, channelName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()
                val dateSdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { timeZone = TZ }
                val date = dateSdf.format(Date(now))
                val url = "https://m.tv.sms.cz/index.php?den=$date&stanice=$smsId&cas=0"

                val req = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
                    .build()

                val html = client.newCall(req).execute().use { resp ->
                    val bytes = resp.body?.bytes() ?: return@use ""
                    String(bytes, charset("windows-1250"))
                }

                val timeSdf = SimpleDateFormat("HH:mm", Locale.getDefault()).apply { timeZone = TZ }
                val timeRegex = Regex("""<span[^>]*>(\d{2}\.\d{2})</span>""")
                val titleRegex = Regex("""<a class='nazev'[^>]*>[^<]*<div[^>]*>([^<]+)""")

                val times = timeRegex.findAll(html).map { it.groupValues[1] }.toList()
                val titles = titleRegex.findAll(html)
                    .map { it.groupValues[1].replace("&amp;", "&").replace("&nbsp;", " ").trim() }
                    .filter { it.length >= 2 }.toList()

                val allPrograms = mutableListOf<Pair<Long, String>>()
                val baseCal = Calendar.getInstance(TZ).also { it.timeInMillis = now }
                val year = baseCal.get(Calendar.YEAR)
                val month = baseCal.get(Calendar.MONTH)
                val day = baseCal.get(Calendar.DAY_OF_MONTH)
                var lastMs = -1L

                for (i in 0 until minOf(times.size, titles.size)) {
                    runCatching {
                        val parts = times[i].split(".")
                        val cal = Calendar.getInstance(TZ)
                        cal.set(year, month, day, parts[0].toInt(), parts[1].toInt(), 0)
                        cal.set(Calendar.MILLISECOND, 0)
                        if (lastMs >= 0 && cal.timeInMillis < lastMs) cal.add(Calendar.DAY_OF_MONTH, 1)
                        lastMs = cal.timeInMillis
                        allPrograms.add(Pair(cal.timeInMillis, titles[i]))
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
                    showEpg()
                }

                val refreshDelay = if (nextStartMs > now) {
                    minOf((nextStartMs - now) + 10_000L, 10 * 60 * 1000L)
                } else 10 * 60 * 1000L
                val refresh = Runnable { loadEpgFromSms(smsId, channelName) }
                epgRefreshRunnable = refresh
                epgHandler.postDelayed(refresh, refreshDelay)

            } catch (e: Exception) { }
        }
    }

    // ── Controller štýl ───────────────────────────────────────────────────────

    private fun applyControllerStyle() {
        try {
            val timeBar = playerView.findViewById<androidx.media3.ui.DefaultTimeBar>(
                androidx.media3.ui.R.id.exo_progress
            )
            timeBar?.setPlayedColor(0xFFE30613.toInt())
            timeBar?.setUnplayedColor(0xFF555555.toInt())
            timeBar?.setBufferedColor(0xFFAA0000.toInt())
            timeBar?.setScrubberColor(0xFFE30613.toInt())
        } catch (e: Exception) { }

        // Červené zvýraznenie ozubeného kolesa
        try {
            val settings = playerView.findViewById<android.widget.ImageButton>(
                androidx.media3.ui.R.id.exo_settings
            )
            settings?.setOnFocusChangeListener { v, hasFocus ->
                v.setBackgroundColor(
                    if (hasFocus) 0xFFE30613.toInt() else android.graphics.Color.TRANSPARENT
                )
            }
            // Nahraď ExoPlayer settings naším dialógom
            settings?.setOnClickListener { openSettingsDialog() }
        } catch (e: Exception) { }
    }

    // ── Player ────────────────────────────────────────────────────────────────

    private fun scheduleHide() {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, 4000)
    }

    private fun showController() {
        playerView.showController()
        playerView.postDelayed({ applyControllerStyle() }, 100L)
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

            playerView.setControllerVisibilityListener(
                androidx.media3.ui.PlayerView.ControllerVisibilityListener { visibility ->
                    if (visibility == View.VISIBLE) {
                        playerView.postDelayed({ applyControllerStyle() }, 100L)
                    }
                }
            )

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
        epgHideHandler.removeCallbacks(epgHideRunnable)
        player?.release(); player = null
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                showEpg()
                if (!isLiveChannel) showController()
                true
            }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> {
                openSettingsDialog()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                player?.let { if (it.isPlaying) it.pause() else it.play() }; true
            }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                epgContainer.visibility = View.GONE
                finish(); true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (isLiveChannel) true
                else { player?.seekTo((player!!.currentPosition + 10_000).coerceAtMost(player!!.duration)); true }
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (isLiveChannel) true
                else { player?.seekTo((player!!.currentPosition - 10_000).coerceAtLeast(0)); true }
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
}
