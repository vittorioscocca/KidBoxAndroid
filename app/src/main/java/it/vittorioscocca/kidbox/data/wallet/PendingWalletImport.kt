package it.vittorioscocca.kidbox.data.wallet

import android.net.Uri
import java.util.concurrent.atomic.AtomicReference

/**
 * Holds a PDF [Uri] from ACTION_SEND until the Wallet screen can import it
 * (mirrors iOS pending share → wallet flow).
 */
object PendingWalletImport {
    private val pending = AtomicReference<Uri?>(null)

    fun set(uri: Uri?) {
        pending.set(uri)
    }

    fun peek(): Uri? = pending.get()

    fun take(): Uri? = pending.getAndSet(null)
}
