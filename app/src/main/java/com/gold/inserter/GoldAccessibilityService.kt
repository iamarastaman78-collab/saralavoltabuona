package com.gold.inserter

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
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
    private var inserimentoInCorso = false

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
                inserimentoInCorso = false

                premiOK(root)

                handler.postDelayed({

                    if (running && !paused) {

                        passaAlProssimoCodice()
                    }

                }, 700)
            }

            return
        }

        /*
         * Cerchiamo il campo nel quale GOLD
         * chiede di inserire il codice.
         */
        if (!inserimentoInCorso) {

            val campoCodice =
                trovaCampoCodice(root)

            if (campoCodice != null) {

                inserimentoInCorso = true

                inserisciCodice(campoCodice)
            }
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

        articoloNonTrovatoGestito = false
        inserimentoInCorso = false

        handler.postDelayed({

            if (running && !paused) {

                premiPulsanteRosso()
            }

        }, 500)
    }

    private fun trovaCampoCodice(
        root: AccessibilityNodeInfo
    ): AccessibilityNodeInfo? {

        /*
         * Il campo della schermata GOLD è un EditText.
         */
        if (
            root.className?.toString()
                ?.contains("EditText", ignoreCase = true) == true
        ) {

            return root
        }

        /*
         * Cerchiamo anche tramite il testo
         * visualizzato nel campo.
         */
        val testo =
            root.text?.toString() ?: ""

        val hint =
            root.hintText?.toString() ?: ""

        if (
            testo.contains(
                "Inserire un codice",
                ignoreCase = true
            ) ||
            hint.contains(
                "Inserire un codice",
                ignoreCase = true
            )
        ) {

            return root
        }

        for (i in 0 until root.childCount) {

            val child =
                root.getChild(i)

            if (child != null) {

                val trovato =
                    trovaCampoCodice(child)

                if (trovato != null) {

                    return trovato
                }
            }
        }

        return null
    }

    private fun inserisciCodice(
        campo: AccessibilityNodeInfo
    ) {

        if (currentIndex >= codes.size) {

            running = false
            return
        }

        val codice =
            codes[currentIndex].trim()

        if (codice.isEmpty()) {

            passaAlProssimoCodice()
            return
        }

        /*
         * Inseriamo direttamente il codice nel campo.
         * Non simuliamo la pressione dei singoli tasti.
         */
        val arguments =
            android.os.Bundle()

        arguments.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            codice
        )

        val inserito =
            campo.performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT,
                arguments
            )

        if (!inserito) {

            inserimentoInCorso = false
            return
        }

        /*
         * Aspettiamo un attimo e poi premiamo
         * la freccia blu della tastiera.
         */
        handler.postDelayed({

            if (running && !paused) {

                premiInvio(campo)
            }

        }, 300)
    }

    private fun premiInvio(
        campo: AccessibilityNodeInfo
    ) {

        /*
         * Sulla schermata GOLD che abbiamo fotografato,
         * la freccia blu è il pulsante INVIO della tastiera.
         */

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

            campo.performAction(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id
            )

        } else {

            /*
             * Su Android più vecchi proviamo comunque
             * a cliccare il campo per lasciare che GOLD
             * gestisca l'invio.
             */
            campo.performAction(
                AccessibilityNodeInfo.ACTION_CLICK
            )
        }

        /*
         * Se l'articolo viene trovato, GOLD dovrebbe
         * procedere senza mostrare il messaggio
         * "Articolo non trovato".
         *
         * Dopo un breve intervallo passiamo comunque
         * al codice successivo.
         */
        handler.postDelayed({

            if (
                running &&
                !paused &&
                !articoloNonTrovatoGestito
            ) {

                passaAlProssimoCodice()
            }

        }, 1500)
    }

    private fun passaAlProssimoCodice() {

        if (!running || paused) {
            return
        }

        currentIndex++

        articoloNonTrovatoGestito = false
        inserimentoInCorso = false

        if (currentIndex >= codes.size) {

            running = false
            return
        }

        /*
         * Torniamo alla schermata GOLD e premiamo
         * nuovamente il pulsante rosso.
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
         * Posizione del pulsante rosso
         * nella schermata GOLD.
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
