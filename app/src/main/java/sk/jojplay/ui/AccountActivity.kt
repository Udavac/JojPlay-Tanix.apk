package sk.jojplay.ui

import android.os.Bundle
import android.view.KeyEvent
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import sk.jojplay.R
import sk.jojplay.data.TokenManager
import java.text.SimpleDateFormat
import java.util.*

class AccountActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account)

        val tokenManager = TokenManager(this)

        val tvEmail  = findViewById<TextView>(R.id.tvAccountEmail)
        val tvStatus = findViewById<TextView>(R.id.tvAccountStatus)
        val tvExpiry = findViewById<TextView>(R.id.tvAccountExpiry)

        // Email
        tvEmail.text = tokenManager.email.ifBlank { "Neprihlásený" }

        // Stav a platnosť tokenu
        val expiry = tokenManager.tokenExpiry
        val now = System.currentTimeMillis()

        if (expiry > 0 && now < expiry) {
            tvStatus.text = "✅ Prihlásený"
            tvStatus.setTextColor(0xFF4CAF50.toInt())
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            tvExpiry.text = "Token platný do: ${sdf.format(Date(expiry))}"
        } else {
            tvStatus.text = "⚠️ Token expirovaný"
            tvStatus.setTextColor(0xFFE30613.toInt())
            tvExpiry.text = "Reštartuj apku pre obnovenie"
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
