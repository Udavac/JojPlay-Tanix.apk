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

// ── Dátové triedy ─────────────────────────────────────────────────────────────

data class SettingsItem(
    val label: String,
    val isSelected: Boolean = false,
    val isHeader: Boolean = false
)

// ── Adaptér ───────────────────────────────────────────────────────────────────

class SettingsAdapter(
    private val items: List<SettingsItem>,
    private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<SettingsAdapter.VH>() {

    private var focusedPosition = -1

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_settings, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position], position, focusedPosition == position, onItemClick) { pos ->
            val old = focusedPosition
            focusedPosition = pos
            notifyItemChanged(old)
            notifyItemChanged(pos)
        }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvLabel: TextView = view.findViewById(R.id.settingsItemLabel)
        private val tvCheck: TextView = view.findViewById(R.id.settingsItemCheck)

        fun bind(
            item: SettingsItem,
            position: Int,
            isFocused: Boolean,
            onItemClick: (Int) -> Unit,
            onFocus: (Int) -> Unit
        ) {
            tvLabel.text = item.label
            tvCheck.visibility = if (item.isSelected) View.VISIBLE else View.INVISIBLE

            // Červené pozadie pri focuse, tmavé pri výbere
            itemView.setBackgroundColor(when {
                isFocused -> 0xFFE30613.toInt()
                item.isSelected -> 0x33E30613.toInt()
                item.isHeader -> 0xFF2A2A2A.toInt()
                else -> android.graphics.Color.TRANSPARENT
            })

            itemView.isFocusable = !item.isHeader
            itemView.isFocusableInTouchMode = !item.isHeader

            itemView.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) onFocus(position)
            }

            itemView.setOnClickListener {
                if (!item.isHeader) onItemClick(position)
            }

            itemView.setOnKeyListener { _, keyCode, event ->
                if (!item.isHeader &&
                    event.action == KeyEvent.ACTION_DOWN &&
                    (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                    onItemClick(position)
                    true
                } else false
            }
        }
    }
}

// ── Hlavný dialóg ─────────────────────────────────────────────────────────────

class PlayerSettingsDialog(
    context: Context,
    private val currentSpeed: Float,
    private val audioTracks: List<Pair<String, Int>>, // label, trackIndex
    private val currentAudioTrack: Int,
    private val onSpeedSelected: (Float) -> Unit,
    private val onAudioSelected: (Int) -> Unit
) : Dialog(context, R.style.SettingsDialogTheme) {

    private val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    private val speedLabels = listOf("0,25 ×", "0,5 ×", "0,75 ×", "Normálne", "1,25 ×", "1,5 ×", "2 ×")

    // Stav – ktorá sekcia je aktívna
    private var currentSection = SECTION_MAIN
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

    private fun showMainMenu() {
        currentSection = SECTION_MAIN
        val speedLabel = speedLabels.getOrElse(speeds.indexOf(currentSpeed)) { "Normálne" }
        val audioLabel = audioTracks.getOrNull(currentAudioTrack)?.first ?: "Automaticky"

        val items = listOf(
            SettingsItem("Rýchlosť  ›  $speedLabel"),
            SettingsItem("Zvuk  ›  $audioLabel")
        )

        recycler.adapter = SettingsAdapter(items) { pos ->
            when (pos) {
                0 -> showSpeedMenu()
                1 -> showAudioMenu()
            }
        }
        focusFirst()
    }

    private fun showSpeedMenu() {
        currentSection = SECTION_SPEED
        val items = mutableListOf<SettingsItem>()
        items.add(SettingsItem("‹  Rýchlosť", isHeader = false))
        speeds.forEachIndexed { i, speed ->
            items.add(SettingsItem(speedLabels[i], isSelected = speed == currentSpeed))
        }

        recycler.adapter = SettingsAdapter(items) { pos ->
            if (pos == 0) {
                showMainMenu()
            } else {
                val selected = speeds[pos - 1]
                onSpeedSelected(selected)
                dismiss()
            }
        }
        focusFirst()
    }

    private fun showAudioMenu() {
        currentSection = SECTION_AUDIO
        val items = mutableListOf<SettingsItem>()
        items.add(SettingsItem("‹  Zvuk", isHeader = false))
        audioTracks.forEachIndexed { i, track ->
            items.add(SettingsItem(track.first, isSelected = i == currentAudioTrack))
        }

        recycler.adapter = SettingsAdapter(items) { pos ->
            if (pos == 0) {
                showMainMenu()
            } else {
                onAudioSelected(pos - 1)
                dismiss()
            }
        }
        focusFirst()
    }

    private fun focusFirst() {
        recycler.post {
            recycler.layoutManager?.findViewByPosition(0)?.requestFocus()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val lm = recycler.layoutManager as? LinearLayoutManager
        val adapter = recycler.adapter ?: return super.onKeyDown(keyCode, event)
        val count = adapter.itemCount

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                val focused = recycler.focusedChild
                val pos = recycler.getChildAdapterPosition(focused ?: return true)
                val next = (pos - 1).coerceAtLeast(0)
                recycler.layoutManager?.findViewByPosition(next)?.requestFocus()
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                val focused = recycler.focusedChild
                val pos = recycler.getChildAdapterPosition(focused ?: return true)
                val next = (pos + 1).coerceAtMost(count - 1)
                recycler.layoutManager?.findViewByPosition(next)?.requestFocus()
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                // Ignoruj – nevyhod z dialógu
                return true
            }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                if (currentSection != SECTION_MAIN) {
                    showMainMenu()
                    return true
                }
                dismiss()
                return true
            }
        }
        return true
    }

}
