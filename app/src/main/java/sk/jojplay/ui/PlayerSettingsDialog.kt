package sk.jojplay.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import sk.jojplay.R

data class SettingsItem(
    val label: String,
    val isSelected: Boolean = false
)

class PlayerSettingsDialog(
    context: Context,
    private val currentSpeed: Float,
    private val audioTracks: List<Pair<String, Int>>,
    private val currentAudioTrack: Int,
    private val onSpeedSelected: (Float) -> Unit,
    private val onAudioSelected: (Int) -> Unit
) : Dialog(context, R.style.SettingsDialogTheme) {

    private val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    private val speedLabels = listOf("0,25 ×", "0,5 ×", "0,75 ×", "Normálne", "1,25 ×", "1,5 ×", "2 ×")

    private var currentSection = SECTION_MAIN
    private var focusedPos = 0
    private var currentItems = listOf<SettingsItem>()
    private var currentOnClick: (Int) -> Unit = {}

    private lateinit var recycler: RecyclerView

    companion object {
        const val SECTION_MAIN = 0
        const val SECTION_SPEED = 1
        const val SECTION_AUDIO = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_player_settings)
        recycler = findViewById(R.id.settingsRecycler)
        recycler.layoutManager = LinearLayoutManager(context)
        showMainMenu()
    }

    private fun refresh() {
        recycler.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = currentItems.size
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_settings, parent, false)
                return object : RecyclerView.ViewHolder(v) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val item = currentItems[position]
                val label = holder.itemView.findViewById<TextView>(R.id.settingsItemLabel)
                val check = holder.itemView.findViewById<TextView>(R.id.settingsItemCheck)
                label.text = item.label
                check.visibility = if (item.isSelected) View.VISIBLE else View.INVISIBLE
                holder.itemView.setBackgroundColor(when {
                    position == focusedPos -> 0xFFE30613.toInt()
                    item.isSelected -> 0x33E30613.toInt()
                    else -> android.graphics.Color.TRANSPARENT
                })
            }
        }
    }

    private fun showMainMenu() {
        currentSection = SECTION_MAIN
        val speedLabel = speedLabels.getOrElse(speeds.indexOf(currentSpeed)) { "Normálne" }
        val audioLabel = audioTracks.getOrNull(currentAudioTrack)?.first ?: "Automaticky"
        currentItems = listOf(
            SettingsItem("Rýchlosť  ›  $speedLabel"),
            SettingsItem("Zvuk  ›  $audioLabel")
        )
        currentOnClick = { pos ->
            when (pos) {
                0 -> showSpeedMenu()
                1 -> showAudioMenu()
            }
        }
        focusedPos = 0
        refresh()
    }

    private fun showSpeedMenu() {
        currentSection = SECTION_SPEED
        val items = mutableListOf(SettingsItem("‹  Späť"))
        speeds.forEachIndexed { i, speed ->
            items.add(SettingsItem(speedLabels[i], isSelected = speed == currentSpeed))
        }
        currentItems = items
        currentOnClick = { pos ->
            if (pos == 0) showMainMenu()
            else { onSpeedSelected(speeds[pos - 1]); dismiss() }
        }
        focusedPos = speeds.indexOf(currentSpeed).let { if (it >= 0) it + 1 else 0 }
        refresh()
    }

    private fun showAudioMenu() {
        currentSection = SECTION_AUDIO
        val items = mutableListOf(SettingsItem("‹  Späť"))
        audioTracks.forEachIndexed { i, track ->
            items.add(SettingsItem(track.first, isSelected = i == currentAudioTrack))
        }
        currentItems = items
        currentOnClick = { pos ->
            if (pos == 0) showMainMenu()
            else { onAudioSelected(pos - 1); dismiss() }
        }
        focusedPos = currentAudioTrack + 1
        refresh()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (focusedPos > 0) {
                    focusedPos--
                    refresh()
                    recycler.scrollToPosition(focusedPos)
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (focusedPos < currentItems.size - 1) {
                    focusedPos++
                    refresh()
                    recycler.scrollToPosition(focusedPos)
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> true
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                currentOnClick(focusedPos)
                true
            }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                if (currentSection != SECTION_MAIN) showMainMenu()
                else dismiss()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
}
