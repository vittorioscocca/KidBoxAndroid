package it.vittorioscocca.kidbox.util

import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale

/**
 * Single source of truth for "which locale is the app currently showing content in".
 * Reflects the per-app language set via [AppCompatDelegate.setApplicationLocales]
 * (Impostazioni > Lingua), falling back to the system default when the user hasn't
 * overridden it. Every date/number formatter in the app should read this instead of
 * relying on the implicit JVM default locale, so a live in-app language switch is
 * reflected everywhere without an app restart.
 */
object KBLocale {
    fun current(): Locale {
        val applicationLocales = AppCompatDelegate.getApplicationLocales()
        return if (!applicationLocales.isEmpty) applicationLocales[0] ?: Locale.getDefault() else Locale.getDefault()
    }
}
