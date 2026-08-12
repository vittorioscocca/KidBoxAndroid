package it.vittorioscocca.kidbox.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Genera il Baseline Profile di KidBox.
 *
 * Il profilo dice ad ART quali classi e metodi compilare AOT all'installazione. Senza,
 * tutto il codice Compose gira interpretato/JIT alle prime esecuzioni di ogni schermata:
 * è la causa più comune di scatti "diffusi" in un'app Compose matura.
 *
 * Si esegue con:
 * ```
 * ./gradlew :app:generateReleaseBaselineProfile
 * ```
 * con un device o emulatore collegato (API 28+, preferibilmente rootato o `userdebug`
 * per risultati stabili). Il file prodotto finisce in
 * `app/src/release/generated/baselineProfiles/` e va committato.
 *
 * ## Copertura
 *
 * Il percorso di avvio è coperto sempre. I percorsi autenticati (Home, Chat) dipendono
 * dallo stato di login sul device di generazione: se la sessione è attiva vengono
 * catturati, altrimenti i blocchi sono no-op silenziosi e il profilo copre comunque
 * avvio + login. Per la copertura completa, fai login sul device **prima** di lanciare
 * la generazione.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(packageName = PACKAGE_NAME) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle(IDLE_TIMEOUT_MS)

        // Da qui in poi tutto è best-effort: se la UI non è quella attesa (utente non
        // loggato, onboarding in corso, dialog di permessi) non deve far fallire la
        // generazione — il profilo di avvio è già stato raccolto.
        runCatching { scrollHomeAndOpenChat() }
    }

    /**
     * Scorre la Home e prova ad aprire la Chat, che è la schermata con il costo di
     * composizione più alto e quella che beneficia di più della compilazione AOT.
     */
    private fun androidx.benchmark.macro.MacrobenchmarkScope.scrollHomeAndOpenChat() {
        device.findObject(By.scrollable(true))?.let { scrollable ->
            repeat(SCROLL_PASSES) {
                scrollable.scroll(Direction.DOWN, SCROLL_PERCENT)
                device.waitForIdle(IDLE_TIMEOUT_MS)
            }
            scrollable.scroll(Direction.UP, 1f)
        }

        // La chat è raggiungibile dalla tab bar; il descriptor cambia con la lingua, quindi
        // si tenta per contenuto testuale e si abbandona in silenzio se non c'è.
        val chatEntry = device.wait(
            Until.findObject(By.textContains("Chat")),
            FIND_TIMEOUT_MS,
        ) ?: return
        chatEntry.click()
        device.waitForIdle(IDLE_TIMEOUT_MS)

        // Scroll della lista messaggi: è il percorso che ci interessa di più.
        device.findObject(By.scrollable(true))?.let { messages ->
            repeat(SCROLL_PASSES) {
                messages.scroll(Direction.UP, SCROLL_PERCENT)
                device.waitForIdle(IDLE_TIMEOUT_MS)
            }
        }
    }

    private companion object {
        const val PACKAGE_NAME = "it.vittorioscocca.kidbox"
        const val IDLE_TIMEOUT_MS = 3_000L
        const val FIND_TIMEOUT_MS = 5_000L
        const val SCROLL_PASSES = 3
        const val SCROLL_PERCENT = 0.8f
    }
}
