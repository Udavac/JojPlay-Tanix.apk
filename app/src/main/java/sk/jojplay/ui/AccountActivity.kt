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

        // Email - vynútená ZELENÁ farba (hex: #00FF00)
        tvEmail.text = tokenManager.email.ifBlank { "Neprihlásený" }
        tvEmail.setTextColor(0xFF00FF00.toInt())

        // Stav a platnosť tokenu
        val expiry = tokenManager.tokenExpiry
        val now = System.currentTimeMillis()

        if (expiry > 0 && now < expiry) {
            // Token je platný -> text s kvačkou bude ZELENÝ
            tvStatus.text = "✅ Prihlásený"
            tvStatus.setTextColor(0xFF00FF00.toInt())
            
            // Čas platnosti -> ZELENÝ
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            tvExpiry.text = "Token platný do: ${sdf.format(Date(expiry))}"
            tvExpiry.setTextColor(0xFF00FF00.toInt())
        } else {
            // Token expiroval -> texty budú ČERVENÉ (hex: #FF0000)
            tvStatus.text = "⚠️ Token expirovaný"
            tvStatus.setTextColor(0xFFFF0000.toInt())
            
            tvExpiry.text = "Reštartuj apku pre obnovenie"
            tvExpiry.setTextColor(0xFFFF0000.toInt())
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
