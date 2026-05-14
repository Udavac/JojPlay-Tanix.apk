package sk.jojplay.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class TokenManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("jojplay_prefs", Context.MODE_PRIVATE)
    private val client = OkHttpClient()
    private val jsonType = "application/json".toMediaType()

    var email: String
        get() = prefs.getString("email", "") ?: ""
        set(v) = prefs.edit().putString("email", v).apply()

    var password: String
        get() = prefs.getString("password", "") ?: ""
        set(v) = prefs.edit().putString("password", v).apply()

    private var cachedToken: String? = prefs.getString("token", null)

    // Zmenené na val (verejné) pre AccountActivity
    var tokenExpiry: Long = prefs.getLong("token_expiry", 0L)
        private set

    suspend fun getToken(): String? {
        val now = System.currentTimeMillis()
        if (cachedToken != null && now < tokenExpiry) return cachedToken
        return refreshToken()
    }

    suspend fun refreshToken(): String? = withContext(Dispatchers.IO) {
        val e = email; val p = password
        if (e.isBlank() || p.isBlank()) return@withContext null
        val payload = JSONObject().apply {
            put("email", e)
            put("password", p)
            put("returnSecureToken", true)
            put("tenantId", "XEpbY0V54AE34rFO7dB2-i9m04")
        }.toString()
        val req = Request.Builder()
            .url("https://www.googleapis.com/identitytoolkit/v3/relyingparty/verifyPassword?key=AIzaSyB02udgMkNLADkLJ_w5YNBMR2VR1WHfusI")
            .post(payload.toRequestBody(jsonType))
            .addHeader("Content-Type", "application/json")
            .build()
        return@withContext try {
            client.newCall(req).execute().use { resp ->
                val json = JSONObject(resp.body?.string() ?: return@withContext null)
                if (json.has("error")) return@withContext null
                val token = json.optString("idToken").takeIf { it.isNotEmpty() }
                val expiry = System.currentTimeMillis() + 55 * 60 * 1000L
                cachedToken = token
                tokenExpiry = expiry
                prefs.edit()
                    .putString("token", token)
                    .putLong("token_expiry", expiry)
                    .apply()
                token
            }
        } catch (ex: Exception) {
            null
        }
    }

    fun clearToken() {
        cachedToken = null
        tokenExpiry = 0L
        prefs.edit().remove("token").remove("token_expiry").apply()
    }

    fun isConfigured() = email.isNotBlank() && password.isNotBlank()
}
