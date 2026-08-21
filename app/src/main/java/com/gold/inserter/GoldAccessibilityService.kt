```kotlin
package com.gold.inserter

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.lang.ref.WeakReference

class GoldAccessibilityService : AccessibilityService() {

    companion object {
        private var instance: WeakReference<GoldAccessibilityService>? = null

        fun start(codes: List<String>) {
            instance?.get()?.startAutomation(codes)
        }

        fun pause() {
            instance?.get()?.pauseAutomation()
        }

        fun restart() {
            instance?.get()?.restartAutomation()
        }
    }

    private val handler = Handler(Looper.getMainLooper())

    private var codes: List<String> = emptyList()
    private var currentIndex = 0
    private var running = false
    private var paused = false
    private var waitingForNextCode = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = WeakReference(this)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        instance?.clear()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!running || paused) return

        /*
         * Controlliamo continuamente la schermata di GOLD.
         *
         * Se appare "Articolo non trovato", premiamo automaticamente OK
         * e poi ripartiamo dal pulsante rosso dello scanner.
         */
        val root = rootInActiveWindow ?: return

        if (containsText(root, "Articolo non trovato")) {
            clickText(root, "OK")

            handler.postDelayed({
                if (running && !paused) {
                    clickScannerButton()
                }
            }, 400)

            return
        }

        /*
         * Se abbiamo appena premuto il pulsante rosso e GOLD ha aperto
         * il campo di ricerca, inseriamo il prossimo codice.
         */
        if (waitingForNextCode) {
            val editText = findEditText(root)

            if (editText != null) {
                waitingForNextCode = false

                handler.postDelayed({
                    if (running && !paused) {
                        processCurrentCode(editText)
                    }
                }, 200)
            }
        }
    }

    override fun onInterrupt() {
        paused = true
    }

    private fun startAutomation(newCodes: List<String>) {
        if (newCodes.isEmpty()) return

        codes = newCodes
        currentIndex = 0
        running = true
        paused = false
        waitingForNextCode = true

        handler.removeCallbacksAndMessages(null)

        /*
         * Il primo passo è premere il pulsante rosso.
         */
        handler.postDelayed({
            if (running && !paused) {
                clickScannerButton()
            }
        }, 500)
    }

    private fun pauseAutomation() {
        paused = true
        handler.removeCallbacksAndMessages(null)
    }

    private fun restartAutomation() {
        currentIndex = 0
        running = true
        paused = false
        waitingForNextCode = true

        handler.removeCallbacksAndMessages(null)

        handler.postDelayed({
            if (running && !paused) {
                clickScannerButton()
            }
        }, 500)
    }

    private fun processCurrentCode(editText: AccessibilityNodeInfo) {
        if (currentIndex >= codes.size) {
            running = false
            return
        }

        val code = codes[currentIndex].trim()

        if (code.isEmpty()) {
            currentIndex++
            waitingForNextCode = true
            clickScannerButton()
            return
        }

        /*
         * Inserisce il codice nel campo di GOLD.
         */
        val arguments = Bundle()

        arguments.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            code
        )

        editText.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            arguments
        )

        /*
         * Premiamo la conferma della tastiera / freccia blu.
         */
        handler.postDelayed({
            if (!running || paused) return@postDelayed

            pressEnter(editText)

            /*
             * Dopo la ricerca aspettiamo che GOLD mostri il risultato.
             */
            handler.postDelayed({
                if (!running || paused) return@postDelayed

                clickValidate()

            }, 700)

        }, 250)
    }

    private fun pressEnter(node: AccessibilityNodeInfo) {

        /*
         * Prima proviamo l'azione IME.
         */
        try {
            node.performAction(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id
            )
        } catch (_: Exception) {
            // Se GOLD non supporta l'azione, continuiamo.
        }
    }

    private fun clickValidate() {
        val root = rootInActiveWindow ?: return

        if (clickText(root, "VALIDARE")) {

            /*
             * Diamo a GOLD il tempo di elaborare la validazione.
             */
            handler.postDelayed({
                if (!running || paused) return@postDelayed

                /*
                 * Se compare "Articolo non trovato", onAccessibilityEvent()
                 * intercetterà il messaggio e farà OK.
                 *
                 * Altrimenti passiamo direttamente al pulsante rosso.
                 */
                val currentRoot = rootInActiveWindow

                if (currentRoot != null &&
                    containsText(currentRoot, "Articolo non trovato")
                ) {
                    clickText(currentRoot, "OK")

                    handler.postDelayed({
                        if (running && !paused) {
                            goToNextCode()
                        }
                    }, 400)

                } else {
                    goToNextCode()
                }

            }, 800)
        }
    }

    private fun goToNextCode() {
        currentIndex++

        if (currentIndex >= codes.size) {
            running = false
            return
        }

        waitingForNextCode = true

        handler.postDelayed({
            if (running && !paused) {
                clickScannerButton()
            }
        }, 300)
    }

    private fun clickScannerButton() {

        val root = rootInActiveWindow ?: return

        /*
         * Prima cerchiamo un elemento che GOLD espone come pulsante
         * dello scanner.
         */
        val scannerNode = findScannerNode(root)

        if (scannerNode != null) {
            scannerNode.performAction(
                AccessibilityNodeInfo.ACTION_CLICK
            )

            return
        }

        /*
         * Fallback:
         * se GOLD non espone il pulsante rosso nell'accessibility tree,
         * clicchiamo nella zona in basso a destra dove si trova
         * il pulsante rosso nelle schermate che ci hai mostrato.
         */
        val width = resources.displayMetrics.widthPixels
        val height = resources.displayMetrics.heightPixels

        val x = width * 0.88f
        val y = height * 0.86f

        clickAt(x, y)
    }

    private fun findScannerNode(
        root: AccessibilityNodeInfo
    ): AccessibilityNodeInfo? {

        val descriptions = listOf(
            "scanner",
            "scansione",
            "scansiona",
            "barcode",
            "codice"
        )

        val nodes = ArrayList<AccessibilityNodeInfo>()
        collectNodes(root, nodes)

        for (node in nodes) {

            val text = node.text?.toString()?.lowercase() ?: ""
            val description =
                node.contentDescription?.toString()?.lowercase() ?: ""

            val combined = "$text $description"

            if (descriptions.any { combined.contains(it) } &&
                node.isClickable
            ) {
                return node
            }
        }

        return null
    }

    private fun findEditText(
        root: AccessibilityNodeInfo
    ): AccessibilityNodeInfo? {

        val nodes = ArrayList<AccessibilityNodeInfo>()
        collectNodes(root, nodes)

        /*
         * Cerchiamo prima un normale campo EditText.
         */
        for (node in nodes) {
            if (node.className?.toString()
                    ?.contains("EditText", ignoreCase = true) == true
            ) {
                return node
            }
        }

        return null
    }

    private fun clickText(
        root: AccessibilityNodeInfo,
        wantedText: String
    ): Boolean {

        val nodes = ArrayList<AccessibilityNodeInfo>()
        collectNodes(root, nodes)

        for (node in nodes) {

            val text = node.text?.toString()?.trim() ?: ""
            val description =
                node.contentDescription?.toString()?.trim() ?: ""

            if (
                text.equals(wantedText, ignoreCase = true) ||
                description.equals(wantedText, ignoreCase = true)
            ) {

                if (node.isClickable) {
                    return node.performAction(
                        AccessibilityNodeInfo.ACTION_CLICK
                    )
                }

                /*
                 * Alcuni pulsanti hanno il testo su un figlio mentre
                 * il click è gestito dal genitore.
                 */
                var parent = node.parent

                while (parent != null) {

                    if (parent.isClickable) {
                        return parent.performAction(
                            AccessibilityNodeInfo.ACTION_CLICK
                        )
                    }

                    parent = parent.parent
                }
            }
        }

        return false
    }

    private fun containsText(
        root: AccessibilityNodeInfo,
        wantedText: String
    ): Boolean {

        val nodes = ArrayList<AccessibilityNodeInfo>()
        collectNodes(root, nodes)

        for (node in nodes) {

            val text = node.text?.toString() ?: ""
            val description =
                node.contentDescription?.toString() ?: ""

            if (
                text.contains(wantedText, ignoreCase = true) ||
                description.contains(wantedText, ignoreCase = true)
            ) {
                return true
            }
        }

        return false
    }

    private fun collectNodes(
        node: AccessibilityNodeInfo,
        result: MutableList<AccessibilityNodeInfo>
    ) {

        result.add(node)

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)

            if (child != null) {
                collectNodes(child, result)
            }
        }
    }

    private fun clickAt(x: Float, y: Float) {

        val path = Path()

        path.moveTo(x, y)

        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    0,
                    80
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
```
