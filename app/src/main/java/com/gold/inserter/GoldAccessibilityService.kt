package com.gold.inserter

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.lang.ref.WeakReference

class GoldAccessibilityService : AccessibilityService() {

    companion object {

        private var serviceInstance:
                WeakReference<GoldAccessibilityService>? = null

        private var codes: List<String> = emptyList()
        private var currentIndex = 0
        private var running = false
        private var paused = false

        fun start(newCodes: List<String>) {

            val service = serviceInstance?.get() ?: return

            codes = newCodes
            currentIndex = 0
            running = true
            paused = false

            service.avviaSequenza()
        }

        fun pause() {

            paused = true
        }

        fun restart() {

            val service = serviceInstance?.get() ?: return

            currentIndex = 0
            running = true
            paused = false

            service.avviaSequenza()
        }
    }

    private val handler =
        Handler(Looper.getMainLooper())

    private var articoloNonTrovatoGestito = false

    override fun onServiceConnected() {

        super.onServiceConnected()

        serviceInstance =
            WeakReference(this)
    }

    override fun onDestroy() {

        handler.removeCallbacksAndMessages(null)

        serviceInstance?.clear()

        super.onDestroy()
    }

    override fun onAccessibilityEvent(
        event: AccessibilityEvent?
    ) {

        if (!running || paused) {
            return
        }

        val root =
            rootInActiveWindow ?: return

        /*
         * Controlliamo se GOLD ha mostrato
         * "Articolo non trovato".
         */
        if (
            contieneTesto(
                root,
                "Articolo non trovato"
            )
        ) {

            if (!articoloNonTrovatoGestito) {

                articoloNonTrovatoGestito = true

                /*
                 * Premiamo OK.
                 */
                premiOK(root)

                /*
                 * Dopo OK torniamo al pulsante rosso.
                 */
                handler.postDelayed({

                    if (running && !paused) {

                        premiPulsanteRosso()

                        handler.postDelayed({

                            articoloNonTrovatoGestito =
                                false

                        }, 1000)
                    }

                }, 700)
            }

            return
        }
    }

    override fun onInterrupt() {

        paused = true
    }

    private fun avviaSequenza() {

        if (codes.isEmpty()) {
            return
        }

        handler.removeCallbacksAndMessages(null)

        /*
         * Partiamo dal pulsante rosso.
         */
        handler.postDelayed({

            if (running && !paused) {

                premiPulsanteRosso()
            }

        }, 500)
    }

    private fun premiOK(
        node: AccessibilityNodeInfo?
    ): Boolean {

        if (node == null) {
            return false
        }

        val testo =
            node.text?.toString() ?: ""

        val descrizione =
            node.contentDescription
                ?.toString() ?: ""

        if (
            testo.equals(
                "OK",
                ignoreCase = true
            ) ||
            descrizione.equals(
                "OK",
                ignoreCase = true
            )
        ) {

            if (node.isClickable) {

                return node.performAction(
                    AccessibilityNodeInfo.ACTION_CLICK
                )
            }
        }

        for (i in 0 until node.childCount) {

            val child =
                node.getChild(i)

            if (premiOK(child)) {
                return true
            }
        }

        return false
    }

    private fun contieneTesto(
        node: AccessibilityNodeInfo?,
        testoCercato: String
    ): Boolean {

        if (node == null) {
            return false
        }

        val testo =
            node.text?.toString() ?: ""

        val descrizione =
            node.contentDescription
                ?.toString() ?: ""

        if (
            testo.contains(
                testoCercato,
                ignoreCase = true
            )
        ) {
            return true
        }

        if (
            descrizione.contains(
                testoCercato,
                ignoreCase = true
            )
        ) {
            return true
        }

        for (i in 0 until node.childCount) {

            val child =
                node.getChild(i)

            if (
                contieneTesto(
                    child,
                    testoCercato
                )
            ) {
                return true
            }
        }

        return false
    }

    private fun premiPulsanteRosso() {

        val metrics =
            resources.displayMetrics

        val width =
            metrics.widthPixels.toFloat()

        val height =
            metrics.heightPixels.toFloat()

        /*
         * Posizione approssimativa del pulsante
         * rosso dello Zebra.
         */
        val x =
            width * 0.875f

        val y =
            height * 0.865f

        val path =
            Path()

        path.moveTo(x, y)

        val gesture =
            GestureDescription.Builder()
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
