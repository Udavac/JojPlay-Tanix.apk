package sk.jojplay.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import sk.jojplay.data.*

sealed class BrowseState {
    object Loading : BrowseState()
    data class Success(val items: List<MediaItem>, val title: String) : BrowseState()
    data class Error(val message: String) : BrowseState()
}

sealed class PlayState {
    object Idle : PlayState()
    object Loading : PlayState()
    data class Ready(
        val url: String,
        val isDash: Boolean,
        val channelId: String? = null,
        val channelName: String? = null
    ) : PlayState()
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

    private val _accountVisible = MutableStateFlow(false)
    val accountVisible: StateFlow<Boolean> = _accountVisible

    private val _settingsVisible = MutableStateFlow(false)
    val settingsVisible: StateFlow<Boolean> = _settingsVisible

    private data class StackEntry(val title: String, val loader: suspend () -> List<MediaItem>)
    private val backStack = mutableListOf<StackEntry>()

    private var _repo: JojPlayRepository? = null

    private suspend fun repo(): JojPlayRepository {
        _repo?.let { return it }
        val t = tokenManager.getToken()
            ?: throw Exception("Prihlásenie zlyhalo. Skontroluj email a heslo v nastaveniach.")
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

    fun loadRoot() {
        backStack.clear()
        val items = SCREENS.map { (name, id) ->
            MediaItem(
                id = id,
                type = ItemType.CATEGORY,
                name = name,
                image = SCREEN_IMAGES[id]
            )
        } + listOf(
            MediaItem(
                id = "__live__",
                type = ItemType.CHANNEL,
                name = "TV naživo",
                image = "https://raw.githubusercontent.com/Udavac/JoJ_Play-Obrazky/main/live stream.jpg"
            ),
            MediaItem(
                id = "__search__",
                type = ItemType.CATEGORY,
                name = "Vyhľadávanie",
                image = "https://raw.githubusercontent.com/Udavac/JoJ_Play-Obrazky/main/search.jpg"
            ),
            MediaItem(
                id = "__account__",
                type = ItemType.CATEGORY,
                name = "Môj účet",
                image = "https://raw.githubusercontent.com/Udavac/JoJ_Play-Obrazky/main/ucet.jpg"
            ),
            MediaItem(
                id = "__settings__",
                type = ItemType.CATEGORY,
                name = "Prihlásenie",
                image = "https://raw.githubusercontent.com/Udavac/JoJ_Play-Obrazky/main/prihlasenie.jpg"
            )
        )
        _browseState.value = BrowseState.Success(items, "JOJ Play")
    }

    fun onItemClick(item: MediaItem) {
        when {
            item.id == "__live__"      -> pushAndLoad("TV naživo") { LIVE_CHANNELS }
            item.id == "__search__"    -> _searchVisible.value = true
            item.id == "__account__"   -> _accountVisible.value = true
            item.id == "__settings__"  -> _settingsVisible.value = true
            item.type == ItemType.CATEGORY && SCREENS.containsValue(item.id) -> {
                val name = SCREENS.entries.find { it.value == item.id }?.key ?: item.name
                pushAndLoad(name) { repo().getItemsInScreen(item.id) }
            }
            item.type == ItemType.CATEGORY -> pushAndLoad(item.name) { repo().getItemsInCategory(item.id) }
            item.type == ItemType.SERIES -> pushAndLoad(item.name) {
                val seasons = repo().getItemsInSeries(item.id)
                when {
                    seasons.isEmpty() -> repo().getItemsInSeason(item.id, 1)
                    seasons.size == 1 -> repo().getItemsInSeason(item.id, seasons[0].season ?: 1)
                    else -> seasons
                }
            }
            item.type == ItemType.SEASON -> pushAndLoad(item.name) { repo().getItemsInSeason(item.id, item.season ?: 1) }
            item.type == ItemType.VIDEO -> playItem(item.id, "video", null, null)
            item.type == ItemType.CHANNEL -> playItem(item.id, "tvChannel", item.id, item.name)
        }
    }

    private fun pushAndLoad(title: String, loader: suspend () -> List<MediaItem>) {
        backStack.add(StackEntry(title, loader))
        viewModelScope.launch {
            _browseState.value = BrowseState.Loading
            try {
                val items = loader()
                _browseState.value = BrowseState.Success(items, title)
            } catch (e: Exception) {
                backStack.removeLastOrNull()
                _browseState.value = BrowseState.Error(e.message ?: "Chyba načítania")
            }
        }
    }

    fun navigateBack(): Boolean {
        if (backStack.isEmpty()) return false
        backStack.removeLastOrNull()
        if (backStack.isEmpty()) {
            loadRoot()
        } else {
            val entry = backStack.last()
            viewModelScope.launch {
                _browseState.value = BrowseState.Loading
                try {
                    _browseState.value = BrowseState.Success(entry.loader(), entry.title)
                } catch (e: Exception) {
                    _browseState.value = BrowseState.Error(e.message ?: "Chyba")
                }
            }
        }
        return true
    }

    fun dismissSearch()   { _searchVisible.value = false }
    fun dismissAccount()  { _accountVisible.value = false }
    fun dismissSettings() { _settingsVisible.value = false }

    fun submitSearch(query: String) {
        _searchVisible.value = false
        if (query.isBlank()) return
        pushAndLoad("Výsledky: $query") { repo().search(query) }
    }

    private fun playItem(id: String, docType: String, channelId: String?, channelName: String?) {
        viewModelScope.launch {
            _playState.value = PlayState.Loading
            try {
                val result = repo().getPlayUrl(id, docType)
                if (result.url != null) {
                    _playState.value = PlayState.Ready(result.url, result.isDash, channelId, channelName)
                } else {
                    val msg = result.errorMessage ?: ""
                    val isPremium = msg.contains("subscription", ignoreCase = true) ||
                                    msg.contains("premium", ignoreCase = true) ||
                                    msg.contains("monetized", ignoreCase = true) ||
                                    msg.contains("purchased", ignoreCase = true) ||
                                    msg.contains("SUBSCRIPTION_REQUIRED", ignoreCase = true) ||
                                    msg.contains("not entitled", ignoreCase = true) ||
                                    msg.contains("unauthorized", ignoreCase = true) ||
                                    msg.contains("403", ignoreCase = true)
                    _playState.value = PlayState.Error(
                        if (isPremium) "⭐ IBA PREMIUM" else msg.ifBlank { "Nepodarilo sa načítať URL" }
                    )
                }
            } catch (e: Exception) {
                _playState.value = PlayState.Error(e.message ?: "Chyba prehrávania")
            }
        }
    }

    fun clearPlayState() { _playState.value = PlayState.Idle }
}
