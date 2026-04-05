package it.vittorioscocca.kidbox.domain.model

/** Tipi embedded in JSON / Data nella visita medica (iOS: KBMedicalVisitTypes). */
enum class KBDoctorSpecialization(val rawValue: String) {
    PEDIATRA("Pediatra"),
    MEDICO_BASE("Medico di Base"),
    DERMATOLOGO("Dermatologo"),
    ORTOPEDICO("Ortopedico"),
    OTORINO("Otorinolaringoiatra"),
    OCULISTA("Oculista"),
    UROLOGO("Urologo"),
    CARDIOLOGO("Cardiologo"),
    ALTRO("Altro"),
}

enum class KBTherapyType(val rawValue: String) {
    RIPOSO("Riposo"),
    FISIOTERAPIA("Fisioterapia"),
    DIETA("Dieta"),
    AEROSOL("Aerosol"),
    ALTRO("Altro"),
}

enum class KBVisitStatus(val rawValue: String) {
    PENDING("In attesa"),
    BOOKED("Prenotata"),
    COMPLETED("Eseguita"),
    RESULT_AVAILABLE("Risultato disponibile"),
}

data class KBPrescribedExam(
    val id: String,
    val name: String,
    val isUrgent: Boolean = false,
    val deadlineEpochMillis: Long? = null,
    val preparation: String? = null,
)

data class KBAsNeededDrug(
    val id: String,
    val drugName: String,
    val dosageValue: Double,
    val dosageUnit: String,
    val instructions: String? = null,
)

data class KBTravelDetails(
    val transportMode: String? = null,
    val distanceKm: Double? = null,
    val travelNotes: String? = null,
)
