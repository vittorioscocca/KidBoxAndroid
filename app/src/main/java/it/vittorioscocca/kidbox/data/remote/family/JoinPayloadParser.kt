package it.vittorioscocca.kidbox.data.remote.family

import android.net.Uri
import android.util.Log

private const val TAG = "JoinPayloadParser"

/**
 * Estrae il codice membership dal payload QR / testo, allineato a iOS `JoinPayloadParser.swift`.
 *
 * Formati supportati:
 * 1) Deep link: `kidbox://join?familyId=…&inviteId=…&secret=…&code=XXXX`
 * 2) Stringa plain: solo il codice (4–32 caratteri, senza `://` né `?`)
 */
object JoinPayloadParser {

    /**
     * Estrae il parametro `code` (case-insensitive sul nome) da un URL kidbox://join.
     */
    fun extractInviteCode(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        val uri = Uri.parse(trimmed)
        if (uri.scheme == "kidbox" && uri.host == "join") {
            for (name in uri.queryParameterNames) {
                if (name.equals("code", ignoreCase = true)) {
                    val code = uri.getQueryParameter(name)?.takeIf { it.isNotBlank() }
                    if (code != null) {
                        Log.i(TAG, "extractInviteCode: code from kidbox URL")
                        return code
                    }
                }
            }
        }

        if (trimmed.length in 4..32 &&
            !trimmed.contains("://") &&
            !trimmed.contains('?')
        ) {
            Log.i(TAG, "extractInviteCode: plain code")
            return trimmed
        }

        Log.d(TAG, "extractInviteCode: no code found")
        return null
    }
}
