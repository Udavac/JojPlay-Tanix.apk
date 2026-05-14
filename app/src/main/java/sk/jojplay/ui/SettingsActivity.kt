package sk.jojplay.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.KeyEvent
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import sk.jojplay.R
import sk.jojplay.data.TokenManager

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val tokenManager = TokenManager(this)

        val emailInput       = findViewById<EditText>(R.id.inputEmail)
        val passInput        = findViewById<EditText>(R.id.inputPassword)
        val btnSave          = findViewById<Button>(R.id.btnSave)
        val btnCancel        = findViewById<Button>(R.id.btnCancel)
        val checkShowPass    = findViewById<CheckBox>(R.id.checkShowPassword)

        emailInput.setText(tokenManager.email)
        passInput.setText(tokenManager.password)

        // Zobraziť / skryť heslo
        checkShowPass.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                passInput.transformationMethod = HideReturnsTransformationMethod.getInstance()
            } else {
                passInput.transformationMethod = PasswordTransformationMethod.getInstance()
            }
            // Kurzor na koniec
            passInput.setSelection(passInput.text.length)
        }

        fun save() {
            val email = emailInput.text.toString().trim()
            val pass  = passInput.text.toString().trim()
            if (email.isBlank() || pass.isBlank()) {
                Toast.makeText(this, "Zadaj email aj heslo", Toast.LENGTH_SHORT).show()
                return
            }
            setResult(Activity.RESULT_OK, Intent().apply {
                putExtra("email", email)
                putExtra("password", pass)
            })
            finish()
        }

        btnSave.setOnClickListener { save() }
        btnCancel.setOnClickListener { setResult(Activity.RESULT_CANCELED); finish() }

        // TV diaľkový ovládač
        for (btn in listOf(btnSave, btnCancel)) {
            btn.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN &&
                    (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                    btn.performClick(); true
                } else false
            }
        }

        // Checkbox cez diaľkový ovládač
        checkShowPass.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN &&
                (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                checkShowPass.isChecked = !checkShowPass.isChecked
                true
            } else false
        }

        emailInput.requestFocus()
    }
}
