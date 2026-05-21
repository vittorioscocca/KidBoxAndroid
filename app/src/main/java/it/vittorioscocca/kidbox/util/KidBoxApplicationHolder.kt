package it.vittorioscocca.kidbox.util

import android.content.Context

/** Context applicazione per handler crash (senza leak di Activity). */
object KidBoxApplicationHolder {
    @Volatile
    var applicationContext: Context? = null
}
