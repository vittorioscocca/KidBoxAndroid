package it.vittorioscocca.kidbox.ui.navigation

sealed class AppDestination(val route: String) {
    data object Login : AppDestination("login")
    data object Onboarding : AppDestination("onboarding")
    data object WikiOnboarding : AppDestination("wiki_onboarding/{familyId}") {
        fun createRoute(familyId: String): String = "wiki_onboarding/$familyId"
    }

    data object Home : AppDestination("home")
    data object Profile : AppDestination("profile")
    data object Settings : AppDestination("settings")
    data object MessageSettings : AppDestination("message_settings")
    data object NotificationSettings : AppDestination("notification_settings")
    data object Theme : AppDestination("theme")
    data object InviteCode : AppDestination("invite_code")
    data object JoinFamily : AppDestination("join_family")
    data object EditFamily : AppDestination("edit_family")
    data object EditChild : AppDestination("edit_child/{childId}") {
        fun createRoute(childId: String): String = "edit_child/$childId"
    }
    data object FamilyPhotos : AppDestination("family_photos/{familyId}") {
        fun createRoute(familyId: String): String = "family_photos/$familyId"
    }
    data object NotesHome : AppDestination("notes_home/{familyId}") {
        fun createRoute(familyId: String): String = "notes_home/$familyId"
    }
    data object NoteDetail : AppDestination("note_detail/{familyId}/{noteId}") {
        fun createRoute(
            familyId: String,
            noteId: String,
        ): String = "note_detail/$familyId/$noteId"
    }
    data object Todo : AppDestination("todo")
    data object TodoList : AppDestination("todo_list/{familyId}/{childId}/{listId}?highlightTodoId={highlightTodoId}") {
        fun createRoute(
            familyId: String,
            childId: String,
            listId: String,
            highlightTodoId: String? = null,
        ): String {
            val base = "todo_list/$familyId/$childId/$listId"
            return if (highlightTodoId.isNullOrBlank()) base else "$base?highlightTodoId=$highlightTodoId"
        }
    }
    data object TodoSmart : AppDestination("todo_smart/{familyId}/{childId}/{kind}") {
        fun createRoute(
            familyId: String,
            childId: String,
            kind: it.vittorioscocca.kidbox.ui.screens.todo.TodoSmartKind,
        ): String = "todo_smart/$familyId/$childId/${kind.raw}"
    }
    data object ShoppingList : AppDestination("shopping_list/{familyId}") {
        fun createRoute(familyId: String): String = "shopping_list/$familyId"
    }
    data object Calendar : AppDestination("calendar/{familyId}") {
        fun createRoute(familyId: String): String = "calendar/$familyId"
    }
    data object PediatricChildSelector : AppDestination("pediatric_child_selector/{familyId}") {
        fun createRoute(familyId: String): String = "pediatric_child_selector/$familyId"
    }
    data object HealthHome : AppDestination("health/{familyId}/{childId}") {
        fun route(familyId: String, childId: String) = "health/$familyId/$childId"
    }
    data object MedicalRecord : AppDestination("health/{familyId}/{childId}/medical-record") {
        fun route(familyId: String, childId: String) =
            "health/$familyId/$childId/medical-record"
    }
    data object MedicalVisits : AppDestination("health/{familyId}/{childId}/visits") {
        fun route(familyId: String, childId: String) =
            "health/$familyId/$childId/visits"
    }
    data object MedicalVisitDetail : AppDestination("health/{familyId}/{childId}/visits/{visitId}") {
        fun route(familyId: String, childId: String, visitId: String) =
            "health/$familyId/$childId/visits/$visitId"
    }
    data object MedicalVisitForm : AppDestination("health/{familyId}/{childId}/visits/form?visitId={visitId}") {
        fun route(familyId: String, childId: String, visitId: String? = null): String {
            val base = "health/$familyId/$childId/visits/form"
            return if (visitId != null) "$base?visitId=$visitId" else base
        }
    }
    data object MedicalExams : AppDestination("health/{familyId}/{childId}/exams") {
        fun route(familyId: String, childId: String) = "health/$familyId/$childId/exams"
    }
    data object MedicalExamDetail : AppDestination("health/{familyId}/{childId}/exams/{examId}") {
        fun route(familyId: String, childId: String, examId: String) =
            "health/$familyId/$childId/exams/$examId"
    }
    data object MedicalExamForm : AppDestination("health/{familyId}/{childId}/exams/form?examId={examId}") {
        fun routeNew(familyId: String, childId: String) = "health/$familyId/$childId/exams/form"
        fun routeEdit(familyId: String, childId: String, examId: String) =
            "health/$familyId/$childId/exams/form?examId=$examId"
    }
    data object Vaccines : AppDestination("health/{familyId}/{childId}/vaccines") {
        fun route(familyId: String, childId: String) = "health/$familyId/$childId/vaccines"
    }
    data object VaccineDetail : AppDestination("health/{familyId}/{childId}/vaccines/{vaccineId}") {
        fun route(familyId: String, childId: String, vaccineId: String) =
            "health/$familyId/$childId/vaccines/$vaccineId"
    }
    data object VaccineForm : AppDestination("health/{familyId}/{childId}/vaccines/form?vaccineId={vaccineId}") {
        fun routeNew(familyId: String, childId: String) = "health/$familyId/$childId/vaccines/form"
        fun routeEdit(familyId: String, childId: String, vaccineId: String) =
            "health/$familyId/$childId/vaccines/form?vaccineId=$vaccineId"
    }
    data object Treatments : AppDestination("health/{familyId}/{childId}/treatments") {
        fun route(familyId: String, childId: String) = "health/$familyId/$childId/treatments"
    }
    data object TreatmentDetail : AppDestination("health/{familyId}/{childId}/treatments/{treatmentId}") {
        fun route(familyId: String, childId: String, treatmentId: String) =
            "health/$familyId/$childId/treatments/$treatmentId"
    }
    data object TreatmentForm : AppDestination("health/{familyId}/{childId}/treatments/form?treatmentId={treatmentId}") {
        fun routeNew(familyId: String, childId: String) = "health/$familyId/$childId/treatments/form"
        fun routeEdit(familyId: String, childId: String, treatmentId: String) =
            "health/$familyId/$childId/treatments/form?treatmentId=$treatmentId"
    }
    data object HealthTimeline : AppDestination("health/{familyId}/{childId}/timeline") {
        fun route(familyId: String, childId: String) = "health/$familyId/$childId/timeline"
    }
    data object Chat : AppDestination("chat")
    data object ChatMediaGallery : AppDestination("chat_media_gallery/{familyId}") {
        fun createRoute(familyId: String): String = "chat_media_gallery/$familyId"
    }
    data object ExpensesHome : AppDestination("expenses_home/{familyId}?highlightExpenseId={highlightExpenseId}") {
        fun createRoute(
            familyId: String,
            highlightExpenseId: String? = null,
        ): String {
            val base = "expenses_home/$familyId"
            return if (highlightExpenseId.isNullOrBlank()) base else "$base?highlightExpenseId=$highlightExpenseId"
        }
    }
    data object DocumentsHome : AppDestination("documents_home/{familyId}?highlightDocumentId={highlightDocumentId}&folderId={folderId}") {
        fun createRoute(
            familyId: String,
            highlightDocumentId: String? = null,
            folderId: String = "root",
        ): String {
            val base = "documents_home/$familyId"
            val highlightPart = if (highlightDocumentId.isNullOrBlank()) "" else "highlightDocumentId=$highlightDocumentId&"
            return "$base?${highlightPart}folderId=${folderId.ifBlank { "root" }}"
        }
    }
    data object FamilyLocation : AppDestination("family_location/{familyId}") {
        fun createRoute(familyId: String): String = "family_location/$familyId"
    }
    data object AskExpert : AppDestination("ask_expert")
    data object FamilySettings : AppDestination("family_settings")
}
