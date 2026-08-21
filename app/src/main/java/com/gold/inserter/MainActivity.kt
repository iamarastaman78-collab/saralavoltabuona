package com.gold.inserter

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var progressText: TextView

    private var codes: List<String> = emptyList()

    companion object {
        private const val FILE_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createInterface()
    }

    private fun createInterface() {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val title = TextView(this).apply {
            text = "Sarà la volta buona"
            textSize = 28f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 30)
        }

        val description = TextView(this).apply {
            text = "Inserimento automatico articoli"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 30)
        }

        progressText = TextView(this).apply {
            text = "Codici caricati: 0"
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
        }

        statusText = TextView(this).apply {
            text = "Pronto"
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 30)
        }

        val loadButton = Button(this).apply {
            text = "CARICA FILE TXT"
            textSize = 18f

            setOnClickListener {
                openFilePicker()
            }
        }

        val startButton = Button(this).apply {
            text = "AVVIA"
            textSize = 18f

            setOnClickListener {

                if (codes.isEmpty()) {
                    statusText.text = "Prima carica un file TXT"
                    return@setOnClickListener
                }

                if (!isAccessibilityServiceEnabled()) {
                    statusText.text = "Attiva il servizio di accessibilità"
                    openAccessibilitySettings()
                    return@setOnClickListener
                }

                statusText.text = "Automazione avviata"

                GoldAccessibilityService.start(codes)
            }
        }

        val pauseButton = Button(this).apply {
            text = "PAUSA"
            textSize = 18f

            setOnClickListener {
                GoldAccessibilityService.pause()
                statusText.text = "Operazione in pausa"
            }
        }

        val resetButton = Button(this).apply {
            text = "RICOMINCIA"
            textSize = 18f

            setOnClickListener {

                if (codes.isEmpty()) {
                    statusText.text = "Nessun file caricato"
                    return@setOnClickListener
                }

                GoldAccessibilityService.restart()
                statusText.text = "Ripartito dal primo codice"
            }
        }

        root.addView(title)
        root.addView(description)
        root.addView(loadButton)
        root.addView(progressText)
        root.addView(statusText)
        root.addView(startButton)
        root.addView(pauseButton)
        root.addView(resetButton)

        setContentView(root)
    }

    private fun openFilePicker() {

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
        }

        startActivityForResult(
            intent,
            FILE_REQUEST_CODE
        )
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (
            requestCode == FILE_REQUEST_CODE &&
            resultCode == RESULT_OK &&
            data?.data != null
        ) {

            val uri = data.data!!

            readCodesFromFile(uri)
        }
    }

    private fun readCodesFromFile(uri: Uri) {

        try {

            val inputStream = contentResolver.openInputStream(uri)

            if (inputStream == null) {
                statusText.text = "Impossibile leggere il file"
                return
            }

            val text = inputStream
                .bufferedReader()
                .use { it.readText() }

            codes = text
                .lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            progressText.text =
                "Codici caricati: ${codes.size}"

            statusText.text =
                "File caricato correttamente"

        } catch (e: Exception) {

            statusText.text =
                "Errore nella lettura del file"
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {

        val expected =
            "$packageName/${GoldAccessibilityService::class.java.name}"

        val enabledServices =
            Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )

        return enabledServices
            ?.split(":")
            ?.any {
                it.equals(
                    expected,
                    ignoreCase = true
                )
            } == true
    }

    private fun openAccessibilitySettings() {

        val intent = Intent(
            Settings.ACTION_ACCESSIBILITY_SETTINGS
        )

        startActivity(intent)
    }
}
