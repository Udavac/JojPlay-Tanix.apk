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

    private val TZ = TimeZone.getTimeZone("Europe/Bratislava")

    private val epgHideHandler = Handler(Looper.getMainLooper())
    private val epgHideRunnable = Runnable { epgContainer.visibility = View.GONE }

    private var isLiveChannel = false

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
        // Live TV ak má channelId (kanál) – archív/videá nemajú
        isLiveChannel = channelId != null && SMS_CHANNEL_MAP.containsKey(channelId)"

        initPlayer(url, isDash)
        epgContainer.visibility = View.GONE

        if (channelId != null) {
            val smsId = SMS_CHANNEL_MAP[channelId]
            if (smsId != null) {
                loadEpgFromSms(smsId, channelName)
            }
        }
    }

    private fun loadEpgFromSms(smsId: String, channelName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Aktuálny čas
                val now = System.currentTimeMillis()

                // Bratislavský kalendár pre dnešok
                val nowCal = Calendar.getInstance(TZ)
                nowCal.timeInMillis = now

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

                // Parsuj časy a názvy
                val timeRegex = Regex("""<span[^>]*>(\d{2}\.\d{2})</span>""")
                val titleRegex = Regex("""<a class='nazev'[^>]*>[^<]*<div[^>]*>([^<]+)""")

                val times = timeRegex.findAll(html).map { it.groupValues[1] }.toList()
                val titles = titleRegex.findAll(html)
                    .map { it.groupValues[1].replace("&amp;", "&").replace("&nbsp;", " ").trim() }
                    .filter { it.length >= 2 }
                    .toList()

                // Zostav zoznam programov s presným timestamp
                val allPrograms = mutableListOf<Pair<Long, String>>()
                val count = minOf(times.size, titles.size)

                // Základ: dnešný dátum v Bratislave
                val baseCal = Calendar.getInstance(TZ)
                baseCal.timeInMillis = now
                val year  = baseCal.get(Calendar.YEAR)
                val month = baseCal.get(Calendar.MONTH)
                val day   = baseCal.get(Calendar.DAY_OF_MONTH)

                var lastMs = -1L

                for (i in 0 until count) {
                    runCatching {
                        val parts = times[i].split(".")
                        val hour = parts[0].toInt()
                        val min  = parts[1].toInt()

                        val cal = Calendar.getInstance(TZ)
                        cal.set(year, month, day, hour, min, 0)
                        cal.set(Calendar.MILLISECOND, 0)

                        // Ak timestamp klesol (polnoc), posuň o deň vpred
                        if (lastMs >= 0 && cal.timeInMillis < lastMs) {
                            cal.add(Calendar.DAY_OF_MONTH, 1)
                        }

                        lastMs = cal.timeInMillis
                        allPrograms.add(Pair(cal.timeInMillis, titles[i]))
                    }
                }

                // Nájdi aktuálny program – posledný ktorého čas je <= now
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
                    epgHideHandler.removeCallbacks(epgHideRunnable)
                    epgHideHandler.postDelayed(epgHideRunnable, 5000L)
                }

                // Obnov keď aktuálny program skončí
                // Obnov keď program skončí + každú minútu pre istotu
                val refreshDelay = if (nextStartMs > now) {
                    minOf((nextStartMs - now) + 10_000L, 60_000L)
                } else {
                    60_000L
                }
                val refresh = Runnable { loadEpgFromSms(smsId, channelName) }
                epgRefreshRunnable = refresh
                epgHandler.postDelayed(refresh, refreshDelay)

            } catch (e: Exception) {
                // Tiché zlyhanie
            }
        }
    }

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

            playerView.post {
                // Červená farba pre progress bar
                try {
                    val progressBar = playerView.findViewById<android.widget.ProgressBar>(
                        androidx.media3.ui.R.id.exo_progress
                    )
                    progressBar?.progressTintList = android.content.res.ColorStateList.valueOf(0xFFE30613.toInt())
                    progressBar?.progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0xFF555555.toInt())
                } catch (e: Exception) { }

                // Pre live TV skry tlačidlá pretáčania
                if (isLiveChannel) {
                    listOf(
                        androidx.media3.ui.R.id.exo_prev,
                        androidx.media3.ui.R.id.exo_next,
                        androidx.media3.ui.R.id.exo_rew,
                        androidx.media3.ui.R.id.exo_ffwd
                    ).forEach { id ->
                        try {
                            playerView.findViewById<android.widget.ImageButton>(id)?.visibility = View.GONE
                        } catch (e: Exception) { }
                    }
                }
            }
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
                epgContainer.visibility = View.VISIBLE
                epgHideHandler.removeCallbacks(epgHideRunnable)
                epgHideHandler.postDelayed(epgHideRunnable, 5000L)
                showController()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                player?.let { if (it.isPlaying) it.pause() else it.play() }; true
            }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> {
                // Otvor ExoPlayer settings (ozubené koleso) cez diaľkový
                try {
                    val settingsButton = playerView.findViewById<android.widget.ImageButton>(
                        androidx.media3.ui.R.id.exo_settings
                    )
                    settingsButton?.performClick()
                } catch (e: Exception) { }
                true
            }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                epgContainer.visibility = View.GONE
                finish(); true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                // Pretáčanie len pre archív/videá, nie pre live TV
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
