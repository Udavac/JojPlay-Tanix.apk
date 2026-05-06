package sk.jojplay.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import sk.jojplay.R
import sk.jojplay.data.ItemType
import sk.jojplay.data.MediaItem

class MainActivity : AppCompatActivity() {

    private val vm: BrowseViewModel by viewModels()

    private lateinit var recycler: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorView: TextView
    private lateinit var titleView: TextView
    private lateinit var adapter: MediaAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recycler = findViewById(R.id.recycler)
        progressBar = findViewById(R.id.progressBar)
        errorView = findViewById(R.id.errorView)
        titleView = findViewById(R.id.titleView)

        val cols = if (resources.configuration.screenWidthDp >= 600) 4 else 3
        recycler.layoutManager = GridLayoutManager(this, cols)

        adapter = MediaAdapter { item -> vm.onItemClick(item) }
        recycler.adapter = adapter

        // Settings button
        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            showSettingsDialog()
        }

        if (!vm.isConfigured()) {
            showSettingsDialog()
        }

        // Observe browse state
        lifecycleScope.launch {
            vm.browseState.collectLatest { state ->
                when (state) {
                    is BrowseState.Loading -> {
                        progressBar.visibility = View.VISIBLE
                        recycler.visibility = View.GONE
                        errorView.visibility = View.GONE
                    }
                    is BrowseState.Success -> {
                        progressBar.visibility = View.GONE
                        errorView.visibility = View.GONE
                        recycler.visibility = View.VISIBLE
                        titleView.text = state.title
                        adapter.submitList(state.items)
                    }
                    is BrowseState.Error -> {
                        progressBar.visibility = View.GONE
                        recycler.visibility = View.GONE
                        errorView.visibility = View.VISIBLE
                        errorView.text = state.message
                    }
                }
            }
        }

        // Observe play state
        lifecycleScope.launch {
            vm.playState.collectLatest { state ->
                when (state) {
                    is PlayState.Ready -> {
                        vm.clearPlayState()
                        val intent = Intent(this@MainActivity, PlayerActivity::class.java).apply {
                            putExtra("url", state.url)
                            putExtra("isDash", state.isDash)
                        }
                        startActivity(intent)
                    }
                    is PlayState.Error -> {
                        vm.clearPlayState()
                        Toast.makeText(this@MainActivity, state.message, Toast.LENGTH_LONG).show()
                    }
                    is PlayState.Loading -> {
                        Toast.makeText(this@MainActivity, "Načítavam stream...", Toast.LENGTH_SHORT).show()
                    }
                    else -> {}
                }
            }
        }

        // Observe search dialog
        lifecycleScope.launch {
            vm.searchVisible.collectLatest { visible ->
                if (visible) showSearchDialog()
            }
        }

        vm.loadRoot()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            if (vm.navigateBack()) return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun showSettingsDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null)
        val emailInput = view.findViewById<EditText>(R.id.inputEmail)
        val passInput = view.findViewById<EditText>(R.id.inputPassword)

        emailInput.setText(vm.getEmail())
        passInput.setText(vm.getPassword())

        AlertDialog.Builder(this)
            .setTitle("Prihlasovacie údaje JOJ Play")
            .setView(view)
            .setPositiveButton("Uložiť") { _, _ ->
                val email = emailInput.text.toString().trim()
                val pass = passInput.text.toString().trim()
                if (email.isNotBlank() && pass.isNotBlank()) {
                    vm.saveCredentials(email, pass)
                    vm.loadRoot()
                } else {
                    Toast.makeText(this, "Zadaj email a heslo", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Zrušiť", null)
            .show()
    }

    private fun showSearchDialog() {
        val input = EditText(this).apply {
            hint = "Hľadaj filmy, seriály..."
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(this)
            .setTitle("Vyhľadávanie")
            .setView(input)
            .setPositiveButton("Hľadaj") { _, _ ->
                vm.submitSearch(input.text.toString().trim())
            }
            .setNegativeButton("Zrušiť") { _, _ ->
                vm.dismissSearch()
            }
            .setOnCancelListener { vm.dismissSearch() }
            .show()
    }
}

// ── Adapter ───────────────────────────────────────────────────────────────────

class MediaAdapter(private val onClick: (MediaItem) -> Unit) :
    RecyclerView.Adapter<MediaAdapter.VH>() {

    private val items = mutableListOf<MediaItem>()

    fun submitList(newItems: List<MediaItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_media, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position], onClick)
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val image: ImageView = view.findViewById(R.id.itemImage)
        private val logo: ImageView = view.findViewById(R.id.itemLogo)
        private val title: TextView = view.findViewById(R.id.itemTitle)
        private val badge: TextView = view.findViewById(R.id.itemBadge)

        fun bind(item: MediaItem, onClick: (MediaItem) -> Unit) {
            title.text = item.name

            // Badge
            badge.visibility = when (item.type) {
                ItemType.SERIES -> { badge.text = "SERIÁL"; View.VISIBLE }
                ItemType.SEASON -> { badge.text = item.name; View.VISIBLE }
                ItemType.CHANNEL -> { badge.text = "LIVE"; View.VISIBLE }
                else -> View.GONE
            }

            // Main image
            val imageUrl = when (item.type) {
                ItemType.CHANNEL -> item.preview
                else -> item.image
            }
            if (!imageUrl.isNullOrEmpty()) {
                Glide.with(itemView).load(imageUrl)
                    .placeholder(android.R.drawable.ic_media_play)
                    .centerCrop()
                    .into(image)
            } else {
                image.setImageResource(android.R.drawable.ic_media_play)
            }

            // Channel logo overlay
            if (item.type == ItemType.CHANNEL && !item.logo.isNullOrEmpty()) {
                logo.visibility = View.VISIBLE
                Glide.with(itemView).load(item.logo).into(logo)
            } else {
                logo.visibility = View.GONE
            }

            // Category / search items — show icon only
            if (item.type == ItemType.CATEGORY && item.image == null) {
                image.setImageResource(android.R.drawable.ic_menu_compass)
            }

            itemView.setOnClickListener { onClick(item) }
            itemView.isFocusable = true
            itemView.isFocusableInTouchMode = true
        }
    }
}
