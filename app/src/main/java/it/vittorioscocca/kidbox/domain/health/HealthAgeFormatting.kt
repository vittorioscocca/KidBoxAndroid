package it.vittorioscocca.kidbox.domain.health

import java.util.Calendar

object HealthAgeFormatting {
    fun ageDescriptionFromBirth(epochMillis: Long): String {
        val birth = Calendar.getInstance().apply { timeInMillis = epochMillis }
        val now = Calendar.getInstance()
        var years = now.get(Calendar.YEAR) - birth.get(Calendar.YEAR)
        var months = now.get(Calendar.MONTH) - birth.get(Calendar.MONTH)
        if (months < 0) {
            years--
            months += 12
        }
        return when {
            years > 0 -> "$years ann${if (years == 1) "o" else "i"}"
            months > 0 -> "$months mes${if (months == 1) "e" else "i"}"
            else -> "Neonato"
        }
    }
}
