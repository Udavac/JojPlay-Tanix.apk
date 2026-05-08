package sk.jojplay.data

// ── Auth ──────────────────────────────────────────────────────────────────────

data class FirebaseAuthRequest(
    val email: String,
    val password: String,
    val returnSecureToken: Boolean = true,
    val tenantId: String = "XEpbY0V54AE34rFO7dB2-i9m04"
)

data class FirebaseAuthResponse(
    val idToken: String?,
    val error: FirebaseError?
)

data class FirebaseError(val message: String)

// ── Generic media item ────────────────────────────────────────────────────────

enum class ItemType { VIDEO, SERIES, SEASON, CATEGORY, CHANNEL }

data class MediaItem(
    val id: String,
    val type: ItemType,
    val name: String,
    val description: String? = null,
    val image: String? = null,
    val season: Int? = null,
    val preview: String? = null,
    val logo: String? = null
)

// ── Screens ───────────────────────────────────────────────────────────────────

val SCREENS = linkedMapOf(
    "Domov"         to "screen-Lxye9UzYirbdU8gT6zIZ",
    "Filmy"         to "screen-sDYvdxFDr6YuXBrJNq4Pf",
    "Seriály"       to "screen-6u1OuNZpEZJ5mvFAr_kwA",
    "Spravodajstvo" to "screen-uTwlSTUjxtlq9fJaT9L_2",
    "Šport"         to "screen-jNBx-DFZSx6PazsFvlPol",
    "Deti"          to "screen-vmptBEawEZaKP5VzokZeE"
)

// ── Live channels (rovnaké ID ako v Kodi plugine) ─────────────────────────────

val LIVE_CHANNELS = listOf(
    MediaItem(
        id = "LYyAwEjjqmj8kMY23Lqw", type = ItemType.CHANNEL, name = "JOJ",
        preview = "https://cnt.iptv.joj.sk/contentserver/contents/101/categories/screenshot/1.jpg",
        logo = "https://kabelko.sk/images/logo_tv_radio/tv/jj/joj_tv_sk.png"
    ),
    MediaItem(
        id = "60K9GwR6CLApIHVyNYOj", type = ItemType.CHANNEL, name = "JOJ Plus",
        preview = "https://cnt.iptv.joj.sk/contentserver/contents/102/categories/screenshot/1.jpg",
        logo = "https://kabelko.sk/images/logo_tv_radio/tv/jj/joj-plus-sk.png"
    ),
    MediaItem(
    id = "0D9v2CuujVAlLJJTyLWd", type = ItemType.CHANNEL, name = "JOJ Krimi",
    preview = "https://cnt.iptv.joj.sk/contentserver/contents/103/categories/screenshot/1.jpg",
    logo = "https://www.lyngsat.com/logo/tv/jj/joj-krimi-sk.png"
),
    MediaItem(
        id = "7tl6We5FhLyCfZcmSG6F", type = ItemType.CHANNEL, name = "JOJ 24",
        preview = "https://cnt.iptv.joj.sk/contentserver/contents/111/categories/screenshot/1.jpg",
        logo = "https://kabelko.sk/images/logo_tv_radio/tv/jj/joj-24-sk.png"
    ),
    MediaItem(
        id = "OE8iUSCBeLn8CIb0mL57", type = ItemType.CHANNEL, name = "JOJ Šport",
        preview = "https://cnt.iptv.joj.sk/contentserver/contents/110/categories/screenshot/1.jpg",
        logo = "https://kabelko.sk/images/logo_tv_radio/tv/jj/joj-sport-sk.png"
    ),
    MediaItem(
        id = "XA7YXR0HIuS4HGVgSZli", type = ItemType.CHANNEL, name = "JOJ Šport 2",
        preview = "https://cnt.iptv.joj.sk/contentserver/contents/118/categories/screenshot/1.jpg",
        logo = "https://www.lyngsat.com/logo/tv/jj/joj-sport-2-sk.png"
    ),
    MediaItem(
        id = "vSmKCe7UZp40PvCLXvtb", type = ItemType.CHANNEL, name = "JOJ Svet",
        preview = "https://cnt.iptv.joj.sk/contentserver/contents/114/categories/screenshot/1.jpg",
        logo = "https://www.lyngsat.com/logo/tv/jj/joj-svet-sk.png"
    ),
    MediaItem(
        id = "oALPnvtbTB4yuM4cLpnF", type = ItemType.CHANNEL, name = "JOJko",
        preview = "https://cnt.iptv.joj.sk/contentserver/contents/104/categories/screenshot/1.jpg",
        logo = "https://kabelko.sk/images/logo_tv_radio/tv/jj/jojko-sk.png"
    ),
    MediaItem(
        id = "7oGizuVUXRJGckpoVGUB", type = ItemType.CHANNEL, name = "JOJ Cinema",
        preview = "https://cnt.iptv.joj.sk/contentserver/contents/105/categories/screenshot/1.jpg",
        logo = "https://kabelko.sk/images/logo_tv_radio/tv/jj/joj_tv_sk_cinema.png"
    ),
    MediaItem(
        id = "aYB0bZQo5X5BuuaXl43H", type = ItemType.CHANNEL, name = "CS Film",
        preview = "https://cnt.iptv.joj.sk/contentserver/contents/106/categories/screenshot/1.jpg",
        logo = "https://raw.githubusercontent.com/MarhyCZ/picons/refs/heads/master/640/csfilm.png"
    ),
    MediaItem(
        id = "UY5IgHcFriJV4Dh54RCS", type = ItemType.CHANNEL, name = "CS History",
        preview = "https://cnt.iptv.joj.sk/contentserver/contents/107/categories/screenshot/1.jpg",
        logo = "https://kabelko.sk/images/logo_tv_radio/tv/cc/cs-history-cz.png"
    ),
    MediaItem(
        id = "cIM4bKpVNVziiCrs5LJk", type = ItemType.CHANNEL, name = "CS Mystery",
        preview = "https://cnt.iptv.joj.sk/contentserver/contents/108/categories/screenshot/1.jpg",
        logo = "https://kabelko.sk/images/logo_tv_radio/tv/cc/cs-mystery-cz.png"
    )
)

// ── Play response ─────────────────────────────────────────────────────────────

data class PlayResult(val url: String?, val isDash: Boolean = true, val errorMessage: String? = null)
