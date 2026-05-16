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

// ── Screens s obrázkami ───────────────────────────────────────────────────────

val SCREENS = linkedMapOf(
    "Domov"         to "screen-Lxye9UzYirbdU8gT6zIZ",
    "Filmy"         to "screen-sDYvdxFDr6YuXBrJNq4Pf",
    "Seriály"       to "screen-6u1OuNZpEZJ5mvFAr_kwA",
    "Spravodajstvo" to "screen-uTwlSTUjxtlq9fJaT9L_2",
    "Šport"         to "screen-jNBx-DFZSx6PazsFvlPol",
    "Deti"          to "screen-vmptBEawEZaKP5VzokZeE"
)

val SCREEN_IMAGES = mapOf(
    "screen-Lxye9UzYirbdU8gT6zIZ" to "https://raw.githubusercontent.com/Udavac/JoJ_Play-Obrazky/main/domov.jpg",
    "screen-sDYvdxFDr6YuXBrJNq4Pf" to "https://raw.githubusercontent.com/Udavac/JoJ_Play-Obrazky/main/film.jpg",
    "screen-6u1OuNZpEZJ5mvFAr_kwA" to "https://raw.githubusercontent.com/Udavac/JoJ_Play-Obrazky/main/serial.jpg",
    "screen-uTwlSTUjxtlq9fJaT9L_2" to "https://raw.githubusercontent.com/Udavac/JoJ_Play-Obrazky/main/news.jpg",
    "screen-jNBx-DFZSx6PazsFvlPol" to "https://raw.githubusercontent.com/Udavac/JoJ_Play-Obrazky/main/sport.jpg",
    "screen-vmptBEawEZaKP5VzokZeE" to "https://raw.githubusercontent.com/Udavac/JoJ_Play-Obrazky/main/deti.jpg"
)

// ── Live channels s EPG ID pre sms.cz ────────────────────────────────────────

val LIVE_CHANNELS = listOf(
    MediaItem(
        id = "33-joj", type = ItemType.CHANNEL, name = "JOJ",
        preview = "https://cnt.iptv.joj.sk/contentserver/contents/101/categories/screenshot/1.jpg",
        logo = "https://kabelko.sk/images/logo_tv_radio/tv/jj/joj_tv_sk.png",
        description = "JOJ"
    ),
    MediaItem(
        id = "206-joj-plus", type = ItemType.CHANNEL, name = "JOJ Plus",
        preview = "https://cnt.iptv.joj.sk/contentserver/contents/102/categories/screenshot/1.jpg",
        logo = "https://kabelko.sk/images/logo_tv_radio/tv/jj/joj-plus-sk.png",
        description = "JOJ+Plus"
    ),
    MediaItem(
        id = "718-wau", type = ItemType.CHANNEL, name = "JOJ Krimi",
        preview = "https://cnt.iptv.joj.sk/contentserver/contents/103/categories/screenshot/1.jpg",
        logo = "https://www.lyngsat.com/logo/tv/jj/joj-krimi-sk.png",
        description = "WAU"
    ),
    MediaItem(
        id = "15441-joj-24", type = ItemType.CHANNEL, name = "JOJ 24",
        preview = "https://cnt.iptv.joj.sk/contentserver/contents/111/categories/screenshot/1.jpg",
        logo = "https://kabelko.sk/images/logo_tv_radio/tv/jj/joj-24-sk.png",
        description = "JOJ+24"
    ),
    MediaItem(
        id = "15291-joj-sport", type = ItemType.CHANNEL, name = "JOJ Šport",
        preview = "https://cnt.iptv.joj.sk/contentserver/contents/110/categories/screenshot/1.jpg",
        logo = "https://kabelko.sk/images/logo_tv_radio/tv/jj/joj-sport-sk.png",
        description = "JOJ+%8Aport"
    ),
    MediaItem(
        id = "15611-joj-sport-2", type = ItemType.CHANNEL, name = "JOJ Šport 2",
        preview = "https://cnt.iptv.joj.sk/contentserver/contents/118/categories/screenshot/1.jpg",
        logo = "https://www.lyngsat.com/logo/tv/jj/joj-sport-2-sk.png",
        description = "JOJ+%8Aport+2"
    ),
    MediaItem(
        id = "15509-joj-svet", type = ItemType.CHANNEL, name = "JOJ Svet",
        preview = "https://cnt.iptv.joj.sk/contentserver/contents/114/categories/screenshot/1.jpg",
        logo = "https://www.lyngsat.com/logo/tv/jj/joj-svet-sk.png",
        description = "JOJ+Svet"
    ),
    MediaItem(
        id = "999-jojko", type = ItemType.CHANNEL, name = "Jojko",
        preview = "https://cnt.iptv.joj.sk/contentserver/contents/104/categories/screenshot/1.jpg",
        logo = "https://kabelko.sk/images/logo_tv_radio/tv/jj/jojko-sk.png",
        description = "Jojko"
    ),
    MediaItem(
        id = "1023-joj-cinema", type = ItemType.CHANNEL, name = "JOJ Cinema",
        preview = "https://cnt.iptv.joj.sk/contentserver/contents/105/categories/screenshot/1.jpg",
        logo = "https://kabelko.sk/images/logo_tv_radio/tv/jj/joj_tv_sk_cinema.png",
        description = "JOJ+Cinema"
    ),
    MediaItem(
        id = "1149-cs-film", type = ItemType.CHANNEL, name = "CS Film",
        preview = "https://cnt.iptv.joj.sk/contentserver/contents/106/categories/screenshot/1.jpg",
        logo = "https://kabelko.sk/images/logo_tv_radio/tv/cc/cs-film-cz.png",
        description = "CS+Film"
    ),
    MediaItem(
        id = "1187-cs-history", type = ItemType.CHANNEL, name = "CS History",
        preview = "https://cnt.iptv.joj.sk/contentserver/contents/107/categories/screenshot/1.jpg",
        logo = "https://kabelko.sk/images/logo_tv_radio/tv/cc/cs-history-cz.png",
        description = "CS+History"
    ),
    MediaItem(
        id = "717-cs-mystery", type = ItemType.CHANNEL, name = "CS Mystery",
        preview = "https://cnt.iptv.joj.sk/contentserver/contents/108/categories/screenshot/1.jpg",
        logo = "https://kabelko.sk/images/logo_tv_radio/tv/cc/cs-mystery-cz.png",
        description = "CS+Mystery"
    )
)

// ── Play response ─────────────────────────────────────────────────────────────

data class PlayResult(val url: String?, val isDash: Boolean = true, val errorMessage: String? = null)
