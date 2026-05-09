package it.vittorioscocca.kidbox.data.remote

import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

/**
 * Dopo [StorageReference.putBytes] il client a volte riceve ancora OBJECT_NOT_FOUND su
 * [StorageReference.getDownloadUrl] (consistenza / token). Breve retry prima di fallire.
 */
suspend fun StorageReference.awaitDownloadUrlAfterWrite(maxAttempts: Int = 3): String {
    var last: Exception? = null
    repeat(maxAttempts) { attempt ->
        try {
            return downloadUrl.await().toString()
        } catch (e: Exception) {
            last = e
            val se = e.unwrapStorageException()
            val notFound = se?.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND
            if (notFound && attempt < maxAttempts - 1) {
                delay(400L * (attempt + 1))
            } else {
                throw e
            }
        }
    }
    throw last ?: IllegalStateException("downloadUrl")
}

/**
 * [StorageReference.getBytes] con preflight App Check e retry se l'oggetto non è ancora visibile
 * (es. messaggio chat appena sincronizzato da Firestore).
 */
suspend fun StorageReference.getBytesWithRetry(maxBytes: Long, maxAttempts: Int = 3): ByteArray {
    prefetchAppCheckTokenForStorage()
    var last: Exception? = null
    repeat(maxAttempts) { attempt ->
        try {
            return getBytes(maxBytes).await()
        } catch (e: Exception) {
            last = e
            val se = e.unwrapStorageException()
            val notFound = se?.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND
            if (notFound && attempt < maxAttempts - 1) {
                delay(400L * (attempt + 1))
            } else {
                throw Exception(e.userMessageForFirebaseStorage(), e)
            }
        }
    }
    throw Exception(last?.userMessageForFirebaseStorage() ?: "Download Storage fallito", last)
}

private tailrec fun Throwable.unwrapStorageException(): StorageException? = when (this) {
    is StorageException -> this
    else -> cause?.unwrapStorageException()
}

/**
 * Messaggio per UI ([Exception.localizedMessage]) quando Storage fallisce upload o lettura URL.
 */
fun Throwable.userMessageForFirebaseStorage(): String {
    val text = ((message ?: "") + " " + (cause?.message ?: "")).lowercase()
    if (text.contains("too many attempts") || text.contains("app check")) {
        return "Il servizio di verifica app è occupato o non configurato. " +
            "In sviluppo: Firebase Console → App Check → registra il token di debug. " +
            "Poi attendi un minuto e riprova."
    }
    val se = unwrapStorageException()
    return when (se?.errorCode) {
        StorageException.ERROR_OBJECT_NOT_FOUND ->
            "File non trovato su Firebase Storage (percorso errato, non ancora disponibile o permessi). " +
                "Controlla connessione, regole Storage e App Check in Firebase Console."
        StorageException.ERROR_NOT_AUTHORIZED ->
            "Permesso negato su Firebase Storage (controlla le regole)."
        StorageException.ERROR_NOT_AUTHENTICATED ->
            "Sessione scaduta: esci e accedi di nuovo, poi riprova."
        else -> se?.message ?: message ?: "Errore Firebase Storage"
    }
}
