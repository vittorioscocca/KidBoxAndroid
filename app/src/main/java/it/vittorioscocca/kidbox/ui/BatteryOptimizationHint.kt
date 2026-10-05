package it.vittorioscocca.kidbox.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

private val BatteryTint = Color(0xFFD9822B)

/**
 * Risparmio energetico e notifiche. Su HyperOS/MIUI e simili, con la batteria
 * dell'app su «Risparmio energetico» il sistema può fermare KidBox in
 * background e le push arrivano tardi o non arrivano.
 *
 * Non chiediamo `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: Play lo ammette solo
 * per app che non possono usare FCM ad alta priorità, e noi la usiamo. Si
 * porta l'utente alla schermata giusta e decide lui.
 */
object BatteryOptimization {
    private const val PREFS = "kidbox_prefs"
    private const val KEY_DISMISSED = "kb_battery_hint_dismissed"

    /**
     * Produttori che fermano le app in background anche con le push FCM ad
     * alta priorità. Su Pixel, Samsung e Motorola il risparmio standard le
     * lascia passare: lì l'avviso sarebbe solo rumore. Xiaomi copre anche
     * Redmi e POCO (stesso `Build.MANUFACTURER`); tecno/infinix/itel sono
     * Transsion.
     */
    private val aggressiveManufacturers = setOf(
        "xiaomi", "huawei", "honor", "oppo", "realme", "oneplus", "vivo",
        "meizu", "asus", "tecno", "infinix", "itel",
    )

    /**
     * Vero se il sistema applica il risparmio energetico a KidBox su un
     * telefono dove questo blocca davvero le notifiche.
     */
    fun isRestricted(context: Context): Boolean {
        if (Build.MANUFACTURER.lowercase() !in aggressiveManufacturers) return false
        val pm = context.getSystemService(PowerManager::class.java) ?: return false
        return !pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun isDismissed(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DISMISSED, false)

    fun dismiss(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_DISMISSED, true) }
    }

    /**
     * Su Xiaomi la pagina «Dettagli batteria» dell'app (quella con «Nessuna
     * restrizione»), oggi in securitycenter; altrove le info dell'app, da
     * cui si arriva a «Batteria».
     * Ogni tentativo è protetto: le activity dei produttori cambiano nome
     * fra una versione e l'altra.
     */
    fun openSettings(context: Context) {
        val pkg = context.packageName
        val candidates = buildList {
            if (Build.MANUFACTURER.equals("xiaomi", ignoreCase = true)) {
                // HyperOS: «Dettagli batteria» dell'app (verificato il 05/10/2026
                // su HyperOS, 2510DRA23E). Si apre per componente, senza il
                // permesso REQUEST_IGNORE_BATTERY_OPTIMIZATIONS.
                add(
                    Intent()
                        .setClassName(
                            "com.miui.securitycenter",
                            "com.miui.powercenter.legacypowerrank.PowerDetailActivity",
                        )
                        .setData(Uri.fromParts("package", pkg, null)),
                )
                // MIUI più vecchie: su HyperOS questa activity non esiste più.
                add(
                    Intent("miui.intent.action.HIDDEN_APPS_CONFIG_ACTIVITY")
                        .setClassName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
                        .putExtra("package_name", pkg)
                        .putExtra("package_label", context.applicationInfo.loadLabel(context.packageManager).toString()),
                )
            }
            add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)))
            add(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
        for (intent in candidates) {
            val opened = runCatching {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            if (opened) return
        }
    }
}

/**
 * Stato del risparmio energetico riletto a ogni ON_RESUME: tornando dalle
 * impostazioni il banner sparisce da solo.
 */
@Composable
fun rememberBatteryRestricted(): Boolean {
    val context = LocalContext.current
    var restricted by remember { mutableStateOf(BatteryOptimization.isRestricted(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                restricted = BatteryOptimization.isRestricted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return restricted
}

/**
 * Banner compatto in Home. Solo se le notifiche sono accese (se sono spente
 * il problema è un altro, e lo dice Impostazioni → Notifiche) e finché
 * l'utente non lo chiude; la riga in Notifiche resta comunque.
 */
@Composable
fun BatteryOptimizationBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val kb = MaterialTheme.kidBoxColors
    val restricted = rememberBatteryRestricted()
    var dismissed by remember { mutableStateOf(BatteryOptimization.isDismissed(context)) }
    val notificationsOn = remember { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    if (!restricted || dismissed || !notificationsOn) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BatteryTint.copy(alpha = 0.12f))
            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.BatteryAlert, contentDescription = null, tint = BatteryTint, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.battery_hint_title),
                color = kb.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.battery_hint_body),
                color = kb.subtitle,
                fontSize = 12.sp,
                lineHeight = 15.sp,
            )
        }
        TextButton(onClick = { BatteryOptimization.openSettings(context) }) {
            Text(stringResource(R.string.battery_hint_action), color = BatteryTint, fontWeight = FontWeight.SemiBold)
        }
        IconButton(
            onClick = {
                BatteryOptimization.dismiss(context)
                dismissed = true
            },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.battery_hint_dismiss),
                tint = kb.subtitle,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
