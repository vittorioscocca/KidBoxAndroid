package it.vittorioscocca.kidbox.data.sync

import com.google.firebase.Timestamp
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyEntity
import it.vittorioscocca.kidbox.util.KBLog
import java.io.File

private const val TAG = "HeroPhotoMerge"

/**
 * Margine sul confronto delle date della foto di famiglia.
 *
 * Dopo un caricamento fatto da questo dispositivo il timestamp locale è
 * `System.currentTimeMillis()` (orologio del telefono) mentre quello su
 * Firestore è un `serverTimestamp()`. Senza margine, uno scarto di pochi
 * secondi fra i due orologi basterebbe a far riscaricare la foto che abbiamo
 * appena caricato noi.
 */
private const val HERO_CLOCK_SKEW_TOLERANCE_MS = 5_000L

/** Campi della foto di famiglia risolti dopo il confronto locale/remoto. */
data class HeroPhotoFields(
    val localPath: String?,
    val updatedAtEpochMillis: Long?,
    val scale: Double?,
    val offsetX: Double?,
    val offsetY: Double?,
)

/**
 * Decide quale versione della foto di famiglia tenere, confrontando le date.
 *
 * Prima di questa funzione entrambi i punti di sync riportavano `heroPhotoLocalPath`
 * e il timestamp identici dal locale, ignorando il remoto. Siccome l'unica
 * condizione di download in `HomeViewModel` è `localPath == null`, un file già
 * in cache significava non riscaricare mai: la foto cambiata da un altro membro
 * non arrivava, e si restava sulla vecchia a tempo indefinito.
 *
 * Quando la versione remota è più recente il file locale viene eliminato: la
 * condizione già esistente fa ripartire il download da sola, senza che le
 * schermate debbano saperne nulla.
 *
 * Il ritaglio segue la foto — scaricare l'immagine nuova lasciando scale e
 * offset della precedente la mostrerebbe inquadrata male — ma se il remoto non
 * porta quei campi si tiene il locale, per non azzerare un ritaglio valido.
 *
 * @param remote dati grezzi del documento `families/{familyId}`
 * @param local riga Room corrente, `null` alla prima sincronizzazione
 */
fun resolveHeroPhotoFields(
    remote: Map<String, Any>,
    local: KBFamilyEntity?,
    familyId: String,
): HeroPhotoFields {
    val remoteUpdatedAt = (remote["heroPhotoUpdatedAt"] as? Timestamp)?.toDate()?.time
    val localUpdatedAt = local?.heroPhotoUpdatedAtEpochMillis

    val remoteIsNewer = remoteUpdatedAt != null &&
        remoteUpdatedAt - (localUpdatedAt ?: 0L) > HERO_CLOCK_SKEW_TOLERANCE_MS

    if (!remoteIsNewer) {
        return HeroPhotoFields(
            localPath = local?.heroPhotoLocalPath,
            updatedAtEpochMillis = localUpdatedAt,
            scale = local?.heroPhotoScale,
            offsetX = local?.heroPhotoOffsetX,
            offsetY = local?.heroPhotoOffsetY,
        )
    }

    local?.heroPhotoLocalPath?.let { path ->
        runCatching { File(path).delete() }
        KBLog.sync.debug(
            "hero: versione remota più recente (remote=$remoteUpdatedAt local=$localUpdatedAt), cache invalidata familyId=$familyId",
            TAG,
        )
    }

    return HeroPhotoFields(
        localPath = null,
        updatedAtEpochMillis = remoteUpdatedAt,
        scale = (remote["heroPhotoScale"] as? Number)?.toDouble() ?: local?.heroPhotoScale,
        offsetX = (remote["heroPhotoOffsetX"] as? Number)?.toDouble() ?: local?.heroPhotoOffsetX,
        offsetY = (remote["heroPhotoOffsetY"] as? Number)?.toDouble() ?: local?.heroPhotoOffsetY,
    )
}
