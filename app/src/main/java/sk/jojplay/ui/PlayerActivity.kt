package sk.jojplay.ui

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.ProgressBar
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
import sk.jojplay.R

class PlayerActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var progressBar: ProgressBar
    private var player: ExoPlayer? = null
    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideControls() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.playerView)
        progressBar = findViewById(R.id.playerProgress)

        // Skry controls automaticky po 3 sekundách
        playerView.controllerShowTimeoutMs = 3000
        playerView.controllerAutoShow = false

        val url = intent.getStringExtra("url") ?: run {
            Toast.makeText(this, "Chýba URL streamu", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val isDash = intent.getBooleanExtra("isDash", true)
        initPlayer(url, isDash)

        // Skry controls hneď po spustení
        scheduleHideControls()
    }

    private fun hideControls() {
        playerView.hideController()
    }

    private fun scheduleHideControls() {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, 3000)
    }

    private fun initPlayer(url: String, isDash: Boolean) {
        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView.player = exo

            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("JojPlay/2.0 Android")
                .setAllowCrossProtocolRedirects(true)

            val mediaSource = when {
                isDash || url.contains(".mpd") ->
                    DashMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(Uri.parse(url)))
                url.contains(".m3u8") ->
                    HlsMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(Uri.parse(url)))
                else ->
                    ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(Uri.parse(url)))
            }

            exo.setMediaSource(mediaSource)
            exo.prepare()
            exo.playWhenReady = true

            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    progressBar.visibility = when (state) {
                        Player.STATE_BUFFERING -> View.VISIBLE
                        Player.STATE_READY -> {
                            scheduleHideControls()
                            View.GONE
                        }
                        else -> View.GONE
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Toast.makeText(
                        this@PlayerActivity,
                        "Chyba: ${error.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            })
        }
    }

    override fun onPause() { super.onPause(); player?.pause() }
    override fun onResume() { super.onResume(); player?.play() }
    override fun onDestroy() {
        super.onDestroy()
        hideHandler.removeCallbacks(hideRunnable)
        player?.release()
        player = null
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_DPAD_CENTER -> {
                player?.let { if (it.isPlaying) it.pause() else it.play() }
                scheduleHideControls()
                true
            }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { finish(); true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                player?.seekTo((player!!.currentPosition + 10_000).coerceAtMost(player!!.duration))
                scheduleHideControls()
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                player?.seekTo((player!!.currentPosition - 10_000).coerceAtLeast(0))
                scheduleHideControls()
                true
            }
            else -> {
                // Akýkoľvek stisk zobrazí controls a skryje ich po 3s
                playerView.showController()
                scheduleHideControls()
                super.onKeyDown(keyCode, event)
            }
        }
    }
}
