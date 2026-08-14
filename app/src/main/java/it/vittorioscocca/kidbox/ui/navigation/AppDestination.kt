package it.vittorioscocca.kidbox.ui.navigation

sealed class AppDestination(val route: String) {
    data object Splash : AppDestination("splash")
    data object Login : AppDestination("login")
    data object Onboarding : AppDestination("onboarding")
    data object WikiOnboarding : AppDestination("wiki_onboarding/{familyId}") {
        fun createRoute(familyId: String): String = "wiki_onboarding/$familyId"
    }

    data object Home : AppDestination("home")
    data object Profile : AppDestination("profile")
    data object Settings : AppDestination("settings")
    data object AutoFillSettings : AppDestination("autofill_settings")
    data object AiSettings : AppDestination("ai_settings")
    data object Plans : AppDestination("plans")
    data object StorageUsage : AppDestination("storage_usage")
    data object MessageSettings : AppDestination("message_settings")
    data object NotificationSettings : AppDestination("notification_settings")
    data object PrivacySettings : AppDestination("privacy_settings")
    data object SupportChat : AppDestination("support_chat")
    data object Theme : AppDestination("theme")
    data object Language : AppDestination("language")
    data object UsageGuide : AppDestination("usage_guide")
    data object Suggestions : AppDestination("suggestions")
    data object InviteCode : AppDestination("invite_code")
    data object JoinFamily : AppDestination("join_family")
    data object EditFamily : AppDestination("edit_family")
    data object EditChild : AppDestination("edit_child/{childId}") {
        fun createRoute(childId: String): String = "edit_child/$childId"
    }
    data object FamilyPhotos : AppDestination("family_photos/{familyId}?initialAlbumId={initialAlbumId}") {
        fun createRoute(familyId: String, initialAlbumId: String? = null): String {
            val base = "family_photos/$familyId"
            return if (initialAlbumId.isNullOrBlank()) {
                base
            } else {
                val enc = java.net.URLEncoder.encode(initialAlbumId, Charsets.UTF_8.name())
                "$base?initialAlbumId=$enc"
            }
        }
    }

    data object PhotoAlbumDetail : AppDestination(
        "photo_album/{familyId}/{albumId}?albumTitle={albumTitle}&isTripAlbum={isTripAlbum}",
    ) {
        fun createRoute(
            familyId: String,
            albumId: String,
            albumTitle: String,
            isTripAlbum: Boolean = false,
        ): String {
            fun enc(value: String) =
                java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
            return "photo_album/$familyId/$albumId?albumTitle=${enc(albumTitle)}&isTripAlbum=$isTripAlbum"
        }
    }
    data object NotesHome : AppDestination("notes_home/{familyId}") {
        fun createRoute(familyId: String): String = "notes_home/$familyId"
    }
    data object NoteDetail : AppDestination("note_detail/{familyId}/{noteId}?isNewNote={isNewNote}") {
        fun createRoute(
            familyId: String,
            noteId: String,
            isNewNote: Boolean = false,
        ): String {
            val base = "note_detail/$familyId/$noteId"
            return if (isNewNote) "$base?isNewNote=true" else base
        }
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
    data object ClinicalRecord : AppDestination("health/{familyId}/{childId}/clinical-record") {
        fun route(familyId: String, childId: String) =
            "health/$familyId/$childId/clinical-record"
    }
    data object HealthConnectApp : AppDestination("health/{familyId}/{childId}/health-connect-app") {
        fun route(familyId: String, childId: String) =
            "health/$familyId/$childId/health-connect-app"
    }
    data object MedicalVisits : AppDestination("health/{familyId}/{childId}/visits") {
        fun route(familyId: String, childId: String) =
            "health/$familyId/$childId/visits"
    }

    /**
     * Chat AI sulle visite in elenco (stessa [VisitAiChatScreen] con isListMode).
     * Registrare prima di [MedicalVisitDetail] (altrimenti "list_ai_chat" matcha come [visitId]).
     */
    data object VisitsListAiChat : AppDestination(
        "health/{familyId}/{childId}/visits/list_ai_chat?subjectName={subjectName}&visitIdsJson={visitIdsJson}&isListMode={isListMode}",
    ) {
        fun route(
            familyId: String,
            childId: String,
            subjectName: String,
            visitIdsJson: String,
            isListMode: Boolean = true,
        ): String {
            fun e(s: String) = java.net.URLEncoder.encode(s, Charsets.UTF_8.name())
            return "health/$familyId/$childId/visits/list_ai_chat" +
                "?subjectName=${e(subjectName)}&visitIdsJson=${e(visitIdsJson)}&isListMode=$isListMode"
        }
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

    /** Deve essere registrata prima di [MedicalVisitDetail] (segmento letterale dopo [visitId]). */
    data object VisitAiChat : AppDestination(
        "health/{familyId}/{childId}/visits/{visitId}/visit_ai_chat?subjectName={subjectName}&visitTitle={visitTitle}&visitDate={visitDate}&diagnosis={diagnosis}&notes={notes}",
    ) {
        fun route(
            familyId: String,
            childId: String,
            visitId: String,
            subjectName: String,
            visitTitle: String,
            visitDate: String,
            diagnosis: String,
            notes: String,
        ): String {
            fun e(s: String) = java.net.URLEncoder.encode(s, Charsets.UTF_8.name())
            return "health/$familyId/$childId/visits/$visitId/visit_ai_chat" +
                "?subjectName=${e(subjectName)}&visitTitle=${e(visitTitle)}&visitDate=${e(visitDate)}&diagnosis=${e(diagnosis)}&notes=${e(notes)}"
        }
    }
    data object MedicalExams : AppDestination("health/{familyId}/{childId}/exams") {
        fun route(familyId: String, childId: String) = "health/$familyId/$childId/exams"
    }

    /** Prima di [MedicalExamDetail] (segmento letterale, non [examId]). */
    data object ExamsListAiChat : AppDestination(
        "health/{familyId}/{childId}/exams/list_ai_chat?subjectName={subjectName}&examIdsJson={examIdsJson}&isListMode={isListMode}",
    ) {
        fun route(
            familyId: String,
            childId: String,
            subjectName: String,
            examIdsJson: String,
            isListMode: Boolean = true,
        ): String {
            fun e(s: String) = java.net.URLEncoder.encode(s, Charsets.UTF_8.name())
            return "health/$familyId/$childId/exams/list_ai_chat" +
                "?subjectName=${e(subjectName)}&examIdsJson=${e(examIdsJson)}&isListMode=$isListMode"
        }
    }

    data object MedicalExamDetail : AppDestination("health/{familyId}/{childId}/exams/{examId}") {
        fun route(familyId: String, childId: String, examId: String) =
            "health/$familyId/$childId/exams/$examId"
    }

    /** Prima di [MedicalExamDetail] (segmento letterale dopo [examId]). */
    data object ExamAiChat : AppDestination(
        "health/{familyId}/{childId}/exams/{examId}/exam_ai_chat?subjectName={subjectName}&examName={examName}&examStatus={examStatus}&deadline={deadline}&preparation={preparation}&resultText={resultText}&notes={notes}&attachmentsSummary={attachmentsSummary}",
    ) {
        fun route(
            familyId: String,
            childId: String,
            examId: String,
            subjectName: String,
            examName: String,
            examStatus: String,
            deadline: String,
            preparation: String,
            resultText: String,
            notes: String,
            attachmentsSummary: String,
        ): String {
            fun e(s: String) = java.net.URLEncoder.encode(s, Charsets.UTF_8.name())
            return "health/$familyId/$childId/exams/$examId/exam_ai_chat" +
                "?subjectName=${e(subjectName)}&examName=${e(examName)}&examStatus=${e(examStatus)}" +
                "&deadline=${e(deadline)}&preparation=${e(preparation)}&resultText=${e(resultText)}" +
                "&notes=${e(notes)}&attachmentsSummary=${e(attachmentsSummary)}"
        }
    }
    data object MedicalExamForm : AppDestination("health/{familyId}/{childId}/exams/form?examId={examId}") {
        fun routeNew(familyId: String, childId: String) = "health/$familyId/$childId/exams/form"
        fun routeEdit(familyId: String, childId: String, examId: String) =
            "health/$familyId/$childId/exams/form?examId=$examId"
    }
    data object Vaccines : AppDestination("health/{familyId}/{childId}/vaccines") {
        fun route(familyId: String, childId: String) = "health/$familyId/$childId/vaccines"
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
    data object HealthAIChat : AppDestination(
        "health/{familyId}/{childId}/ai-chat?subjectName={subjectName}&visitIdsJson={visitIdsJson}&examIdsJson={examIdsJson}&treatmentIdsJson={treatmentIdsJson}&vaccineIdsJson={vaccineIdsJson}",
    ) {
        fun route(familyId: String, childId: String) =
            "health/$familyId/$childId/ai-chat?subjectName=&visitIdsJson=&examIdsJson=&treatmentIdsJson=&vaccineIdsJson="

        fun routeWithContext(
            familyId: String,
            childId: String,
            subjectName: String,
            visitIdsJson: String,
            examIdsJson: String,
            treatmentIdsJson: String,
            vaccineIdsJson: String,
        ): String {
            fun e(s: String) = java.net.URLEncoder.encode(s, Charsets.UTF_8.name())
            return "health/$familyId/$childId/ai-chat" +
                "?subjectName=${e(subjectName)}&visitIdsJson=${e(visitIdsJson)}" +
                "&examIdsJson=${e(examIdsJson)}&treatmentIdsJson=${e(treatmentIdsJson)}&vaccineIdsJson=${e(vaccineIdsJson)}"
        }
    }
    data object Chat : AppDestination("chat")
    data object ChatMediaGallery : AppDestination("chat_media_gallery/{familyId}") {
        fun createRoute(familyId: String): String = "chat_media_gallery/$familyId"
    }
    data object ExpensesHome : AppDestination(
        "expenses_home/{familyId}?highlightExpenseId={highlightExpenseId}&initialCategoryId={initialCategoryId}",
    ) {
        fun createRoute(
            familyId: String,
            highlightExpenseId: String? = null,
            initialCategoryId: String? = null,
        ): String {
            val base = "expenses_home/$familyId"
            val params = buildList {
                if (!highlightExpenseId.isNullOrBlank()) add("highlightExpenseId=$highlightExpenseId")
                if (!initialCategoryId.isNullOrBlank()) {
                    val enc = java.net.URLEncoder.encode(initialCategoryId, Charsets.UTF_8.name())
                    add("initialCategoryId=$enc")
                }
            }
            return if (params.isEmpty()) base else "$base?${params.joinToString("&")}"
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
    data object GeofenceList : AppDestination("geofence_list/{familyId}") {
        fun createRoute(familyId: String): String = "geofence_list/$familyId"
    }
    data object GeofenceEdit : AppDestination("geofence_edit/{familyId}?geofenceId={geofenceId}") {
        fun createRoute(familyId: String, geofenceId: String? = null): String {
            val base = "geofence_edit/$familyId"
            return if (geofenceId.isNullOrBlank()) base else "$base?geofenceId=$geofenceId"
        }
    }
    data object WalletHome : AppDestination("wallet_home/{familyId}") {
        fun createRoute(familyId: String): String = "wallet_home/$familyId"
    }
    data object WalletDetail : AppDestination("wallet_detail/{familyId}/{ticketId}") {
        fun createRoute(familyId: String, ticketId: String): String =
            "wallet_detail/$familyId/$ticketId"
    }
    data object WalletDocumentDetail : AppDestination("wallet_document_detail/{familyId}/{documentId}") {
        fun createRoute(familyId: String, documentId: String): String =
            "wallet_document_detail/$familyId/$documentId"
    }
    data object PasswordsHome : AppDestination("passwords_home/{familyId}") {
        fun createRoute(familyId: String): String = "passwords_home/$familyId"
    }

    data object PasswordsImportExport : AppDestination("passwords_import_export/{familyId}?familyName={familyName}") {
        fun createRoute(familyId: String, familyName: String = ""): String {
            val enc = java.net.URLEncoder.encode(familyName, Charsets.UTF_8.name())
            return "passwords_import_export/$familyId?familyName=$enc"
        }
    }

    data object PasswordsSecurity : AppDestination("passwords_security/{familyId}") {
        fun createRoute(familyId: String): String = "passwords_security/$familyId"
    }

    data object PasswordsSettings : AppDestination("passwords_settings/{familyId}") {
        fun createRoute(familyId: String): String = "passwords_settings/$familyId"
    }

    data object PasswordsGroups : AppDestination("passwords_groups/{familyId}") {
        fun createRoute(familyId: String): String = "passwords_groups/$familyId"
    }

    data object PasswordGroupDetail : AppDestination("password_group_detail/{familyId}?groupId={groupId}") {
        fun createRouteForNew(familyId: String): String = "password_group_detail/$familyId"

        fun createRouteForEdit(familyId: String, groupId: String): String {
            val encodedGroupId = java.net.URLEncoder.encode(groupId, Charsets.UTF_8.name())
            return "password_group_detail/$familyId?groupId=$encodedGroupId"
        }
    }

    data object PasswordsAdd : AppDestination("passwords_add/{familyId}") {
        fun createRoute(familyId: String): String = "passwords_add/$familyId"
    }

    data object PasswordsEdit : AppDestination("passwords_edit/{familyId}/{passwordId}") {
        fun createRoute(familyId: String, passwordId: String): String =
            "passwords_edit/$familyId/$passwordId"
    }

    data object PasswordDetail : AppDestination("password_detail/{familyId}/{passwordId}") {
        fun createRoute(familyId: String, passwordId: String): String = "password_detail/$familyId/$passwordId"
    }

    data object Pets : AppDestination("pets/{familyId}") {
        fun createRoute(familyId: String): String = "pets/$familyId"
    }

    data object PetDetail : AppDestination("pet_detail/{familyId}/{petId}") {
        fun createRoute(familyId: String, petId: String): String = "pet_detail/$familyId/$petId"
    }

    data object PetTreatmentDetail : AppDestination("pet_detail/{familyId}/{petId}/treatment/{treatmentId}") {
        fun route(familyId: String, petId: String, treatmentId: String): String =
            "pet_detail/$familyId/$petId/treatment/$treatmentId"
    }

    data object PetTreatmentForm : AppDestination("pet_detail/{familyId}/{petId}/treatment/form?treatmentId={treatmentId}") {
        fun routeNew(familyId: String, petId: String): String = "pet_detail/$familyId/$petId/treatment/form"
        fun routeEdit(familyId: String, petId: String, treatmentId: String): String =
            "pet_detail/$familyId/$petId/treatment/form?treatmentId=$treatmentId"
    }

    data object HomeItems : AppDestination("home_items/{familyId}") {
        fun createRoute(familyId: String): String = "home_items/$familyId"
    }

    data object HomeItemDetail : AppDestination("home_item_detail/{familyId}/{itemId}") {
        fun createRoute(familyId: String, itemId: String): String = "home_item_detail/$familyId/$itemId"
    }

    data object HousePaymentDetail : AppDestination("house_payment_detail/{familyId}/{paymentId}") {
        fun createRoute(familyId: String, paymentId: String): String =
            "house_payment_detail/$familyId/$paymentId"
    }

    data object Vehicles : AppDestination("vehicles/{familyId}") {
        fun createRoute(familyId: String): String = "vehicles/$familyId"
    }

    data object VehicleDetail : AppDestination("vehicle_detail/{familyId}/{vehicleId}") {
        fun createRoute(familyId: String, vehicleId: String): String = "vehicle_detail/$familyId/$vehicleId"
    }

    data object VehicleInterventions : AppDestination("vehicle_interventions/{familyId}/{vehicleId}") {
        fun createRoute(familyId: String, vehicleId: String): String = "vehicle_interventions/$familyId/$vehicleId"
    }

    data object AskExpert : AppDestination("ask_expert")
    data object AiChat : AppDestination("ai_chat/{familyId}") {
        fun createRoute(familyId: String): String = "ai_chat/$familyId"
    }
    data object PlanningAiChat : AppDestination("planning_ai_chat/{familyId}?familyName={familyName}") {
        fun createRoute(familyId: String, familyName: String): String {
            val encodedName = java.net.URLEncoder.encode(familyName, Charsets.UTF_8.name())
            return "planning_ai_chat/$familyId?familyName=$encodedName"
        }
    }
    data object FamilySettings : AppDestination("family_settings")

    data object TravelList : AppDestination("travel/{familyId}") {
        fun createRoute(familyId: String): String = "travel/$familyId"
    }

    data object TravelAllTrips : AppDestination("travel/{familyId}/all") {
        fun createRoute(familyId: String): String = "travel/$familyId/all"
    }

    data object TravelDiscover : AppDestination("travel/{familyId}/discover") {
        fun createRoute(familyId: String): String = "travel/$familyId/discover"
    }

    data object TravelDestinationDetail : AppDestination("travel/{familyId}/destination/{destinationId}") {
        fun createRoute(familyId: String, destinationId: String): String =
            "travel/$familyId/destination/$destinationId"
    }

    data object TravelWizard : AppDestination("travel/{familyId}/wizard?destination={destination}") {
        fun createRoute(familyId: String, destinationName: String? = null): String {
            val encoded = destinationName?.let {
                java.net.URLEncoder.encode(it, Charsets.UTF_8.name())
            }.orEmpty()
            return "travel/$familyId/wizard?destination=$encoded"
        }
    }

    data object TravelProposal : AppDestination("travel/{familyId}/proposal") {
        fun createRoute(familyId: String): String = "travel/$familyId/proposal"
    }

    data object TravelDetail : AppDestination("travel/{familyId}/detail/{tripId}") {
        fun createRoute(familyId: String, tripId: String): String = "travel/$familyId/detail/$tripId"
    }

    data object TravelCategoryResults : AppDestination("travel/{familyId}/detail/{tripId}/places/{kind}") {
        fun createRoute(familyId: String, tripId: String, kind: String): String =
            "travel/$familyId/detail/$tripId/places/$kind"
    }

    data object TravelPlacesMap : AppDestination("travel/{familyId}/detail/{tripId}/places/{kind}/map") {
        fun createRoute(familyId: String, tripId: String, kind: String): String =
            "travel/$familyId/detail/$tripId/places/$kind/map"
    }

    data object TravelPlaceInfo : AppDestination("travel/{familyId}/place-info/{placeName}") {
        fun createRoute(familyId: String, placeName: String): String =
            "travel/$familyId/place-info/${java.net.URLEncoder.encode(placeName, Charsets.UTF_8.name())}"
    }

    data object TravelPlaceDetail : AppDestination(
        "travel/{familyId}/place?placeName={placeName}&locationContext={locationContext}&scheduleBadge={scheduleBadge}&time={time}&staySummary={staySummary}&costSummary={costSummary}&nextStopTitle={nextStopTitle}",
    ) {
        fun createRoute(
            familyId: String,
            placeName: String,
            locationContext: String,
            scheduleBadge: String,
            time: String = "",
            staySummary: String = "",
            costSummary: String = "",
            nextStopTitle: String? = null,
        ): String {
            fun e(s: String) = java.net.URLEncoder.encode(s, Charsets.UTF_8.name())
            return buildString {
                append("travel/$familyId/place")
                append("?placeName=${e(placeName)}")
                append("&locationContext=${e(locationContext)}")
                append("&scheduleBadge=${e(scheduleBadge)}")
                append("&time=${e(time)}")
                append("&staySummary=${e(staySummary)}")
                append("&costSummary=${e(costSummary)}")
                append("&nextStopTitle=${e(nextStopTitle.orEmpty())}")
            }
        }

        fun createRoute(familyId: String, context: it.vittorioscocca.kidbox.ui.screens.travel.TravelItineraryStopContext): String =
            createRoute(
                familyId = familyId,
                placeName = context.placeName,
                locationContext = context.locationContext,
                scheduleBadge = context.scheduleBadge,
                time = context.time,
                staySummary = context.staySummary,
                costSummary = context.costSummary,
                nextStopTitle = context.nextStopTitle,
            )
    }
}
