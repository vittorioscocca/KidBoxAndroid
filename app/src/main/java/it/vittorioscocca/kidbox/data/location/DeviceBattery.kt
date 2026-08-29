package it.vittorioscocca.kidbox.data.location

import android.content.Context
import android.os.BatteryManager

/**
 * La carica del dispositivo, letta al volo quando si spedisce una posizione.
 *
 * Non è un servizio e non osserva niente: nessun receiver registrato, nessun
 * timer, nessuna scrittura propria. Viaggia dentro l'aggiornamento di
 * coordinate che c'è già, così la cadenza delle scritture di geolocalizzazione
 * resta esattamente quella decisa altrove.
 */
data class DeviceBatterySnapshot(
    /** 0…100. `null` quando il sistema non la espone. */
    val percentage: Int?,
    val isCharging: Boolean,
)

object DeviceBattery {
    fun snapshot(context: Context): DeviceBatterySnapshot {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            ?: return DeviceBatterySnapshot(percentage = null, isCharging = false)
        val level = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        // Fuori da 0…100 significa "non lo so": su alcuni dispositivi il valore
        // torna Integer.MIN_VALUE finché il framework non l'ha campionato.
        val percentage = level.takeIf { it in 0..100 }
        return DeviceBatterySnapshot(
            percentage = percentage,
            isCharging = runCatching { manager.isCharging }.getOrDefault(false),
        )
    }
}
