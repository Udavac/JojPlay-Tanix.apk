package sk.jojplay.ui

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

        recycler    = findViewById(R.id.recycler)
        progressBar = findViewById(R.id.progressBar)
        errorView   = findViewById(R.id.errorView)
        titleView   = findViewById(R.id.titleView)

        val cols = if (resources.configuration.screenWidthDp >= 600) 4 else 3
        recycler.layoutManager = GridLayoutManager(this, cols)
        recycler.setHasFixedSize(true)

        adapter = MediaAdapter { item -> vm.onItemClick(item) }
        recycler.adapter = adapter

        val btnSettings = findViewById<ImageButton>(R.id.btnSettings)
        btnSettings.setOnClickListener { openSettings() }
        btnSettings.setOnFocusChangeListener { v, hasFocus ->
            v.alpha = if (hasFocus) 1f else 0.7f
        }

        if (!vm.isConfigured()) openSettings()

        lifecycleScope.launch {
            vm.browseState.collectLatest { state ->
                when (state) {
                    is BrowseState.Loading -> {
                        progressBar.visibility = View.VISIBLE
                        recycler.visibility    = View.GONE
                        errorView.visibility   = View.GONE
                    }
                    is BrowseState.Success -> {
                        progressBar.visibility = View.GONE
                        errorView.visibility   = View.GONE
                        recycler.visibility    = View.VISIBLE
                        titleView.text = state.title
                        adapter.submitList(state.items)
                        recycler.post {
                            recycler.layoutManager?.findViewByPosition(0)?.requestFocus()
                        }
                    }
                    is BrowseState.Error -> {
                        progressBar.visibility = View.GONE
                        recycler.visibility    = View.GONE
                        errorView.visibility   = View.VISIBLE
                        errorView.text = state.message
                    }
                }
            }
        }

        lifecycleScope.launch {
            vm.playState.collectLatest { state ->
                when (state) {
                    is PlayState.Ready -> {
                        vm.clearPlayState()
                        startActivity(Intent(this@MainActivity, PlayerActivity::class.java).apply {
                            putExtra("url", state.url)
                            putExtra("isDash", state.isDash)
                            putExtra("channelId", state.channelId)
                            putExtra("channelName", state.channelName)
                        })
                    }
                    is PlayState.Error -> {
                        vm.clearPlayState()
                        Toast.makeText(this@MainActivity, state.message, Toast.LENGTH_LONG).show()
                    }
                    is PlayState.Loading -> {
                        Toast.makeText(this@MainActivity, "Načítavam...", Toast.LENGTH_SHORT).show()
                    }
                    else -> {}
                }
            }
        }

        lifecycleScope.launch {
            vm.searchVisible.collectLatest { visible ->
                if (visible) showSearchDialog()
            }
        }

        lifecycleScope.launch {
            vm.accountVisible.collectLatest { visible ->
                if (visible) {
                    vm.dismissAccount()
                    startActivity(Intent(this@MainActivity, AccountActivity::class.java))
                }
            }
        }

        vm.loadRoot()
    }

    private fun openSettings() {
        startActivityForResult(Intent(this, SettingsActivity::class.java), 1)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK) {
            val email = data?.getStringExtra("email") ?: return
            val pass  = data.getStringExtra("password") ?: return
            vm.saveCredentials(email, pass)
            vm.loadRoot()
        }
    }

    private fun showSearchDialog() {
        val input = EditText(this).apply {
            hint = "Hľadaj filmy, seriály..."
            setPadding(48, 24, 48, 24)
            isFocusableInTouchMode = true
            isFocusable = true
        }
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Vyhľadávanie")
            .setView(input)
            .setPositiveButton("Hľadaj") { _, _ -> vm.submitSearch(input.text.toString().trim()) }
            .setNegativeButton("Zrušiť") { _, _ -> vm.dismissSearch() }
            .setOnCancelListener { vm.dismissSearch() }
            .create()
        dialog.show()
        input.requestFocus()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            if (vm.navigateBack()) return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        recycler.post {
            recycler.layoutManager?.findViewByPosition(0)?.requestFocus()
        }
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_media, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position], onClick)
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val image: ImageView = view.findViewById(R.id.itemImage)
        private val logo:  ImageView = view.findViewById(R.id.itemLogo)
        private val title: TextView  = view.findViewById(R.id.itemTitle)
        private val badge: TextView  = view.findViewById(R.id.itemBadge)

        fun bind(item: MediaItem, onClick: (MediaItem) -> Unit) {
            title.text = item.name

            // Badge
            when (item.type) {
                ItemType.SERIES  -> { badge.text = "SERIÁL"; badge.visibility = View.VISIBLE }
                ItemType.CHANNEL -> { badge.text = "LIVE";   badge.visibility = View.VISIBLE }
                else             -> badge.visibility = View.GONE
            }

            // Image
            val imageUrl = when (item.type) {
                ItemType.CHANNEL -> item.image ?: item.preview
                else -> item.image
            }
            if (!imageUrl.isNullOrEmpty()) {
                Glide.with(itemView)
                    .load(imageUrl)
                    .fitCenter()
                    .placeholder(R.drawable.placeholder)
                    .into(image)
            } else {
                image.setImageResource(R.drawable.placeholder)
            }

            // Channel logo overlay – len pre kanály s preview
            if (item.type == ItemType.CHANNEL && !item.logo.isNullOrEmpty() && !item.preview.isNullOrEmpty()) {
                logo.visibility = View.VISIBLE
                Glide.with(itemView).load(item.logo).fitCenter().into(logo)
            } else {
                logo.visibility = View.GONE
            }

            itemView.setOnClickListener { onClick(item) }
            itemView.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN &&
                    (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                    onClick(item); true
                } else false
            }

            itemView.isFocusable = true
            itemView.isFocusableInTouchMode = true
            itemView.setOnFocusChangeListener { v, hasFocus ->
                v.scaleX = if (hasFocus) 1.08f else 1f
                v.scaleY = if (hasFocus) 1.08f else 1f
                v.elevation = if (hasFocus) 12f else 2f
            }
        }
    }
}
