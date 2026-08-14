package it.vittorioscocca.kidbox.data.remote.family

import com.google.firebase.firestore.FirebaseFirestoreException

/** Risale la catena di `cause` cercando un PERMISSION_DENIED Firestore. */
fun isPermissionDenied(t: Throwable): Boolean {
    var e: Throwable? = t
    while (e != null) {
        val fs = e as? FirebaseFirestoreException
        if (fs?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) return true
        e = e.cause
    }
    return false
}
