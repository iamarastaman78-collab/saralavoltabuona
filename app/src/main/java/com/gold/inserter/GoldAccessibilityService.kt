package com.gold.inserter

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityEvent
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

            service.inizia()
        }

        fun pause() {
            paused = true
        }

        fun restart() {

            val service = serviceInstance?.get() ?: return

            currentIndex = 0
            running = true
            paused = false

            service.inizia()
        }
    }

    private val handler =
        Handler(Looper.getMainLooper())

    private var inserimentoInCorso = false
    private var ultimoCodiceInserito = -1
    private var tentativiRosso = 0

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

    override fun onInterrupt() {
        paused = true
    }

    override fun onAccessibilityEvent(
        event: AccessibilityEvent?
    ) {

        if (!running || paused) {
            return
        }

        controllaSchermata()
    }

    private fun inizia() {

        handler.removeCallbacksAndMessages(null)

        inserimentoInCorso = false
        ultimoCodiceInserito = -1
        tentativiRosso = 0

        /*
         * Primo tentativo di aprire il campo
         * tramite il pulsante rosso.
         */
        handler.postDelayed({

            if (running && !paused) {
                cercaCampo()
            }

        }, 800)
    }

    private fun controllaSchermata() {

        handler.removeCallbacksAndMessages(null)

        handler.postDelayed({

            if (!running || paused) {
                return@postDelayed
            }

            cercaCampo()

        }, 250)
    }

    private fun cercaCampo() {

        if (!running || paused) {
            return
        }

        val root =
            rootInActiveWindow

        if (root == null) {

            riprova()
            return
        }

        /*
         * Cerchiamo il campo EditText di GOLD.
         */
        val campo =
            trovaCampo(root)

        if (campo != null) {

            inserisciNelCampo(campo)

            return
        }

        /*
         * Se il campo non è ancora visibile,
         * proviamo ad aprire la schermata di scansione.
         */
        if (tentativiRosso < 3) {

            tentativiRosso++

            premiPulsanteScansione()

            handler.postDelayed({

                if (running && !paused) {
                    cercaCampo()
                }

            }, 700)

        } else {

            /*
             * Ricominciamo a cercare senza
             * continuare a martellare il pulsante.
             */
            tentativiRosso = 0

            handler.postDelayed({

                if (running && !paused) {
                    cercaCampo()
                }

            }, 1000)
        }
    }

    private fun trovaCampo(
        node: AccessibilityNodeInfo?
    ): AccessibilityNodeInfo? {

        if (node == null) {
            return null
        }

        val classe =
            node.className?.toString() ?: ""

        val testo =
            node.text?.toString() ?: ""

        val hint =
            node.hintText?.toString() ?: ""

        /*
         * Il campo di GOLD è un EditText.
         */
        if (
            classe.contains(
                "EditText",
                ignoreCase = true
            )
        ) {

            return node
        }

        /*
         * Controllo aggiuntivo tramite
         * il testo/hint mostrato da GOLD.
         */
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

            return node
        }

        for (i in 0 until node.childCount) {

            val child =
                node.getChild(i)

            val trovato =
                trovaCampo(child)

            if (trovato != null) {
                return trovato
            }
        }

        return null
    }

    private fun inserisciNelCampo(
        campo: AccessibilityNodeInfo
    ) {

        if (!running || paused) {
            return
        }

        if (currentIndex >= codes.size) {

            running = false
            return
        }

        /*
         * Evitiamo di reinserire lo stesso codice
         * mentre GOLD sta ancora elaborando.
         */
        if (
            inserimentoInCorso &&
            ultimoCodiceInserito == currentIndex
        ) {
            return
        }

        val codice =
            codes[currentIndex].trim()

        if (codice.isEmpty()) {

            passaAlProssimo()
            return
        }

        inserimentoInCorso = true
        ultimoCodiceInserito = currentIndex

        /*
         * Prima attiviamo realmente il campo.
         * Questo è proprio ciò che finora dovevi
         * fare manualmente.
         */
        campo.performAction(
            AccessibilityNodeInfo.ACTION_FOCUS
        )

        campo.performAction(
            AccessibilityNodeInfo.ACTION_CLICK
        )

        /*
         * Piccola pausa per permettere a GOLD
         * di attivare il campo.
         */
        handler.postDelayed({

            if (!running || paused) {
                return@postDelayed
            }

            val args =
                Bundle()

            args.putCharSequence(
                AccessibilityNodeInfo
                    .ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                codice
            )

            val risultato =
                campo.performAction(
                    AccessibilityNodeInfo.ACTION_SET_TEXT,
                    args
                )

            if (!risultato) {

                inserimentoInCorso = false
                ultimoCodiceInserito = -1

                handler.postDelayed({

                    if (running && !paused) {
                        cercaCampo()
                    }

                }, 500)

                return@postDelayed
            }

            /*
             * Codice inserito.
             * Ora inviamo ENTER.
             */
            handler.postDelayed({

                if (running && !paused) {
                    premiInvio()
                }

            }, 350)

        }, 250)
    }

    private fun premiInvio() {

        val root =
            rootInActiveWindow

        if (root == null) {
            passaAlProssimo()
            return
        }

        val campo =
            trovaCampo(root)

        if (campo != null) {

            /*
             * Prima proviamo l'azione IME.
             */
            campo.performAction(
                AccessibilityNodeInfo.AccessibilityAction
                    .ACTION_IME_ENTER.id
            )

        }

        /*
         * GOLD ha bisogno di tempo per elaborare
         * l'articolo.
         */
        handler.postDelayed({

            if (running && !paused) {

                passaAlProssimo()

            }

        }, 1800)
    }

    private fun passaAlProssimo() {

        if (!running || paused) {
            return
        }

        currentIndex++

        inserimentoInCorso = false
        ultimoCodiceInserito = -1
        tentativiRosso = 0

        if (currentIndex >= codes.size) {

            running = false
            return
        }

        /*
         * NON premiamo subito il rosso.
         *
         * Prima cerchiamo se GOLD ha già lasciato
         * disponibile il campo per il prossimo articolo.
         */
        handler.postDelayed({

            if (running && !paused) {

                cercaCampo()

            }

        }, 600)
    }

    private fun premiPulsanteScansione() {

        val root =
            rootInActiveWindow

        if (root != null) {

            /*
             * Prima cerchiamo un pulsante che GOLD
             * abbia identificato come scansione.
             */
            val trovato =
                trovaPulsanteScansione(root)

            if (trovato != null) {

                if (
                    trovato.performAction(
                        AccessibilityNodeInfo.ACTION_CLICK
                    )
                ) {
                    return
                }
            }
        }

        /*
         * Fallback: gesto sul pulsante rosso.
         *
         * La posizione precedente era troppo bassa.
         */
        val metrics =
            resources.displayMetrics

        val width =
            metrics.widthPixels.toFloat()

        val height =
            metrics.heightPixels.toFloat()

        val x =
            width * 0.82f

        val y =
            height * 0.57f

        val path =
            Path()

        path.moveTo(x, y)

        val gesture =
            GestureDescription.Builder()
                .addStroke(
                    GestureDescription.StrokeDescription(
                        path,
                        0,
                        120
                    )
                )
                .build()

        dispatchGesture(
            gesture,
            null,
            null
        )
    }

    private fun trovaPulsanteScansione(
        node: AccessibilityNodeInfo?
    ): AccessibilityNodeInfo? {

        if (node == null) {
            return null
        }

        val testo =
            node.text?.toString() ?: ""

        val descrizione =
            node.contentDescription
                ?.toString() ?: ""

        val combinato =
            "$testo $descrizione"

        if (
            combinato.contains(
                "scansion",
                ignoreCase = true
            ) ||
            combinato.contains(
                "scanner",
                ignoreCase = true
            ) ||
            combinato.contains(
                "scan",
                ignoreCase = true
            )
        ) {

            if (node.isClickable) {
                return node
            }
        }

        for (i in 0 until node.childCount) {

            val child =
                node.getChild(i)

            val trovato =
                trovaPulsanteScansione(child)

            if (trovato != null) {
                return trovato
            }
        }

        return null
    }

    private fun riprova() {

        handler.postDelayed({

            if (running && !paused) {
                cercaCampo()
            }

        }, 500)
    }
}
