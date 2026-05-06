package sk.jojplay.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import sk.jojplay.data.*

sealed class NavEntry(val title: String, val loader: suspend () -> List<MediaItem>) {
    class Screen(title: String, id: String, repo: JojPlayRepository) :
        NavEntry(title, { repo.getItemsInScreen(id) })

    class Category(title: String, id: String, repo: JojPlayRepository) :
        NavEntry(title, { repo.getItemsInCategory(id) })

    class Series(title: String, id: String, repo: JojPlayRepository) :
        NavEntry(title, {
            val seasons = repo.getItemsInSeries(id)
            when {
                seasons.isEmpty() -> repo.getItemsInSeason(id, 1)
                seasons.size == 1 -> repo.getItemsInSeason(id, seasons[0].season ?: 1)
                else -> seasons
            }
        })

    class Season(title: String, seriesId: String, season: Int, repo: JojPlayRepository) :
        NavEntry(title, { repo.getItemsInSeason(seriesId, season) })

    class Static(title: String, items: List<MediaItem>) :
        NavEntry(title, { items })

    class Search(title: String, query: String, repo: JojPlayRepository) :
        NavEntry(title, { repo.search(query) })
}

sealed class BrowseState {
    object Loading : BrowseState()
    data class Success(val items: List<MediaItem>, val title: String) : BrowseState()
    data class Error(val message: String) : BrowseState()
}

sealed class PlayState {
    object Idle : PlayState()
    object Loading : PlayState()
    data class Ready(val url: String, val isDash: Boolean) : PlayState()
    data class Error(val message: String) : PlayState()
}

class BrowseViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenManager = TokenManager(application)

    private val _browseState = MutableStateFlow<BrowseState>(BrowseState.Loading)
    val browseState: StateFlow<BrowseState> = _browseState

    private val _playState = MutableStateFlow<PlayState>(PlayState.Idle)
    val playState: StateFlow<PlayState> = _playState

    private val _searchVisible = MutableStateFlow(false)
    val searchVisible: StateFlow<Boolean> = _searchVisible

    private val backStack = mutableListOf<NavEntry>()

    private var _repo: JojPlayRepository? = null

    private suspend fun repo(): JojPlayRepository {
        _repo?.let { return it }
        val t = tokenManager.getToken() ?: throw Exception("Prihlásenie zlyhalo. Skontroluj email a heslo v nastaveniach.")
        return JojPlayRepository(t).also { _repo = it }
    }

    fun isConfigured() = tokenManager.isConfigured()
    fun getEmail() = tokenManager.email
    fun getPassword() = tokenManager.password

    fun saveCredentials(email: String, password: String) {
        tokenManager.email = email
        tokenManager.password = password
        tokenManager.clearToken()
        _repo = null
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    fun loadRoot() {
        backStack.clear()
        // Show screen list (Domov, Filmy, ...)
        val items = SCREENS.map { (name, id) ->
            MediaItem(id = id, type = ItemType.CATEGORY, name = name)
        } + MediaItem(id = "__live__", type = ItemType.CHANNEL, name = "📺 TV naživo") +
          MediaItem(id = "__search__", type = ItemType.CATEGORY, name = "🔍 Vyhľadávanie")

        _browseState.value = BrowseState.Success(items, "JOJ Play")
    }

    fun onItemClick(item: MediaItem) {
        when {
            item.id == "__live__" -> {
                val entry = NavEntry.Static("TV naživo", LIVE_CHANNELS)
                navigate(entry)
            }
            item.id == "__search__" -> {
                _searchVisible.value = true
            }
            item.type == ItemType.CATEGORY && SCREENS.containsValue(item.id) -> {
                val name = SCREENS.entries.find { it.value == item.id }?.key ?: item.name
                val entry = NavEntry.Screen(name, item.id, null as JojPlayRepository? ?: return)
                navigateWithRepo { repo ->
                    NavEntry.Screen(name, item.id, repo)
                }
            }
            item.type == ItemType.CATEGORY -> {
                navigateWithRepo { repo -> NavEntry.Category(item.name, item.id, repo) }
            }
            item.type == ItemType.SERIES -> {
                navigateWithRepo { repo -> NavEntry.Series(item.name, item.id, repo) }
            }
            item.type == ItemType.SEASON -> {
                navigateWithRepo { repo ->
                    NavEntry.Season(item.name, item.id, item.season ?: 1, repo)
                }
            }
            item.type == ItemType.VIDEO -> {
                playItem(item.id, "video")
            }
            item.type == ItemType.CHANNEL -> {
                playItem(item.id, "tvChannel")
            }
        }
    }

    private fun navigateWithRepo(builder: suspend (JojPlayRepository) -> NavEntry) {
        viewModelScope.launch {
            _browseState.value = BrowseState.Loading
            try {
                val r = repo()
                val entry = builder(r)
                backStack.add(entry)
                loadCurrentEntry()
            } catch (e: Exception) {
                _browseState.value = BrowseState.Error(e.message ?: "Chyba")
            }
        }
    }

    private fun navigate(entry: NavEntry) {
        backStack.add(entry)
        viewModelScope.launch {
            _browseState.value = BrowseState.Loading
            try {
                val items = entry.loader()
                _browseState.value = BrowseState.Success(items, entry.title)
            } catch (e: Exception) {
                _browseState.value = BrowseState.Error(e.message ?: "Chyba")
            }
        }
    }

    fun navigateBack(): Boolean {
        if (backStack.isEmpty()) return false
        backStack.removeLastOrNull()
        if (backStack.isEmpty()) {
            loadRoot()
        } else {
            viewModelScope.launch {
                _browseState.value = BrowseState.Loading
                loadCurrentEntry()
            }
        }
        return true
    }

    private suspend fun loadCurrentEntry() {
        val entry = backStack.lastOrNull() ?: return
        try {
            val items = entry.loader()
            _browseState.value = BrowseState.Success(items, entry.title)
        } catch (e: Exception) {
            _browseState.value = BrowseState.Error(e.message ?: "Chyba")
        }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    fun dismissSearch() { _searchVisible.value = false }

    fun submitSearch(query: String) {
        _searchVisible.value = false
        if (query.isBlank()) return
        navigateWithRepo { repo -> NavEntry.Search("Výsledky: $query", query, repo) }
    }

    // ── Playback ──────────────────────────────────────────────────────────────

    private fun playItem(id: String, docType: String) {
        viewModelScope.launch {
            _playState.value = PlayState.Loading
            try {
                val result = repo().getPlayUrl(id, docType)
                if (result.url != null) {
                    _playState.value = PlayState.Ready(result.url, result.isDash)
                } else {
                    _playState.value = PlayState.Error(result.errorMessage ?: "Nepodarilo sa načítať URL")
                }
            } catch (e: Exception) {
                _playState.value = PlayState.Error(e.message ?: "Chyba prehrávania")
            }
        }
    }

    fun clearPlayState() { _playState.value = PlayState.Idle }
}
