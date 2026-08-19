package com.gold.inserter

import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var progressText: TextView

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
            setPadding(0, 20, 0, 40)
        }

        val description = TextView(this).apply {
            text = "Inserimento automatico articoli"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)
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
            setPadding(0, 20, 0, 40)
        }

        val startButton = Button(this).apply {
            text = "AVVIA"
            textSize = 18f

            setOnClickListener {
                statusText.text = "Funzione pronta per essere configurata"
            }
        }

        val pauseButton = Button(this).apply {
            text = "PAUSA"
            textSize = 18f

            setOnClickListener {
                statusText.text = "Operazione in pausa"
            }
        }

        val resetButton = Button(this).apply {
            text = "RICOMINCIA"
            textSize = 18f

            setOnClickListener {
                progressText.text = "Codici caricati: 0"
                statusText.text = "Pronto"
            }
        }

        root.addView(title)
        root.addView(description)
        root.addView(progressText)
        root.addView(statusText)
        root.addView(startButton)
        root.addView(pauseButton)
        root.addView(resetButton)

        setContentView(root)
    }
}
