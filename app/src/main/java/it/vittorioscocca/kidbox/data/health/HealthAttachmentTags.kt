package it.vittorioscocca.kidbox.data.health

object VisitAttachmentTag {
    fun make(visitId: String): String = "visit:$visitId"
    fun matches(notes: String?, visitId: String): Boolean = notes == make(visitId)
}

object ExamAttachmentTag {
    fun make(examId: String): String = "exam:$examId"
    fun matches(notes: String?, examId: String): Boolean = notes == make(examId)
}

object TreatmentAttachmentTag {
    fun make(treatmentId: String): String = "treatment:$treatmentId"
    fun matches(notes: String?, treatmentId: String): Boolean = notes == make(treatmentId)
}
