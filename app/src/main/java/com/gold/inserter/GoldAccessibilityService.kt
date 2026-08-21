package com.gold.inserter

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class GoldAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    private var articoloNonTrovatoGestito = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {

        if (event == null) return

        val root = rootInActiveWindow ?: return

        // Cerchiamo la finestra "Articolo non trovato"
        if (contieneTesto(root, "Articolo non trovato")) {

            if (!articoloNonTrovatoGestito) {

                articoloNonTrovatoGestito = true

                // Prima premiamo OK
                premiOK(root)

                // Aspettiamo che la finestra sparisca
                // e poi ripartiamo dal pulsante rosso
                handler.postDelayed({

                    premiPulsanteRosso()

                    // Permettiamo di gestire un eventuale
                    // nuovo "Articolo non trovato"
                    handler.postDelayed({
                        articoloNonTrovatoGestito = false
                    }, 1000)

                }, 700)
            }

            return
        }
    }

    override fun onInterrupt() {
        // Il servizio è stato interrotto
    }

    private fun contieneTesto(
        node: AccessibilityNodeInfo?,
        testo: String
    ): Boolean {

        if (node == null) return false

        val testoNodo = node.text?.toString() ?: ""
        val descrizione = node.contentDescription?.toString() ?: ""

        if (testoNodo.contains(testo, ignoreCase = true)) {
            return true
        }

        if (descrizione.contains(testo, ignoreCase = true)) {
            return true
        }

        for (i in 0 until node.childCount) {

            val child = node.getChild(i)

            if (contieneTesto(child, testo)) {
                return true
            }
        }

        return false
    }

    private fun premiOK(node: AccessibilityNodeInfo?): Boolean {

        if (node == null) return false

        val testo = node.text?.toString() ?: ""
        val descrizione = node.contentDescription?.toString() ?: ""

        if (
            testo.equals("OK", ignoreCase = true) ||
            descrizione.equals("OK", ignoreCase = true)
        ) {

            if (node.isClickable) {
                return node.performAction(
                    AccessibilityNodeInfo.ACTION_CLICK
                )
            }
        }

        for (i in 0 until node.childCount) {

            val child = node.getChild(i)

            if (premiOK(child)) {
                return true
            }
        }

        return false
    }

    private fun premiPulsanteRosso() {

        val displayMetrics = resources.displayMetrics

        val width = displayMetrics.widthPixels.toFloat()
        val height = displayMetrics.heightPixels.toFloat()

        /*
         * Il pulsante rosso dello Zebra è nella parte
         * bassa destra dello schermo.
         *
         * Usiamo coordinate relative allo schermo,
         * così non dipendiamo dalla risoluzione esatta.
         */

        val x = width * 0.875f
        val y = height * 0.865f

        val path = Path()

        path.moveTo(x, y)

        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    0,
                    100
                )
            )
            .build()

        dispatchGesture(
            gesture,
            null,
            null
        )
    }
}
