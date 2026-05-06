package it.vittorioscocca.kidbox.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import it.vittorioscocca.kidbox.data.local.dao.KBAIConversationDao
import it.vittorioscocca.kidbox.data.local.dao.KBAIMessageDao
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBCalendarEventDao
import it.vittorioscocca.kidbox.data.local.dao.KBEventDao
import it.vittorioscocca.kidbox.data.local.dao.KBChatMessageDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyPhotoDao
import it.vittorioscocca.kidbox.data.local.dao.KBGroceryItemDao
import it.vittorioscocca.kidbox.data.local.dao.KBNoteDao
import it.vittorioscocca.kidbox.data.local.dao.KBExpenseDao
import it.vittorioscocca.kidbox.data.local.dao.KBExpenseCategoryDao
import it.vittorioscocca.kidbox.data.local.dao.KBDocumentDao
import it.vittorioscocca.kidbox.data.local.dao.KBDocumentCategoryDao
import it.vittorioscocca.kidbox.data.local.dao.KBPhotoAlbumDao
import it.vittorioscocca.kidbox.data.local.dao.KBSharedLocationDao
import it.vittorioscocca.kidbox.data.local.dao.KBMedicalVisitDao
import it.vittorioscocca.kidbox.data.local.dao.KBMedicalExamDao
import it.vittorioscocca.kidbox.data.local.dao.KBPediatricProfileDao
import it.vittorioscocca.kidbox.data.local.dao.KBVaccineDao
import it.vittorioscocca.kidbox.data.local.dao.KBTreatmentDao
import it.vittorioscocca.kidbox.data.local.dao.KBDoseLogDao
import it.vittorioscocca.kidbox.data.local.dao.KBTodoItemDao
import it.vittorioscocca.kidbox.data.local.dao.KBTodoListDao
import it.vittorioscocca.kidbox.data.local.dao.KBUserProfileDao
import it.vittorioscocca.kidbox.data.local.dao.WalletTicketDao
import it.vittorioscocca.kidbox.data.local.db.KidBoxDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE kb_chat_messages ADD COLUMN contactPayloadJSON TEXT")
            db.execSQL("ALTER TABLE kb_chat_messages ADD COLUMN deletedForJSON TEXT")
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE kb_medical_exams ADD COLUMN reminderOn INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE kb_vaccines ADD COLUMN name TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE kb_vaccines ADD COLUMN doctorName TEXT")
            db.execSQL("ALTER TABLE kb_vaccines ADD COLUMN location TEXT")
            db.execSQL("ALTER TABLE kb_vaccines ADD COLUMN reminderOn INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE kb_vaccines ADD COLUMN nextDoseDateEpochMillis INTEGER")
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS kb_ai_conversations (
                    id TEXT NOT NULL PRIMARY KEY,
                    familyId TEXT NOT NULL,
                    childId TEXT NOT NULL,
                    scopeId TEXT NOT NULL,
                    summary TEXT,
                    summarizedMessageCount INTEGER NOT NULL DEFAULT 0,
                    createdAtEpochMillis INTEGER NOT NULL,
                    updatedAtEpochMillis INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_kb_ai_conversations_scopeId ON kb_ai_conversations(scopeId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_kb_ai_conversations_familyId ON kb_ai_conversations(familyId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_kb_ai_conversations_childId ON kb_ai_conversations(childId)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS kb_ai_messages (
                    id TEXT NOT NULL PRIMARY KEY,
                    conversationId TEXT NOT NULL,
                    roleRaw TEXT NOT NULL,
                    content TEXT NOT NULL,
                    createdAtEpochMillis INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_kb_ai_messages_conversationId ON kb_ai_messages(conversationId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_kb_ai_messages_createdAtEpochMillis ON kb_ai_messages(createdAtEpochMillis)")
        }
    }

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS kb_treatments_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    familyId TEXT NOT NULL,
                    childId TEXT NOT NULL,
                    drugName TEXT NOT NULL,
                    activeIngredient TEXT,
                    dosageValue REAL NOT NULL,
                    dosageUnit TEXT NOT NULL,
                    isLongTerm INTEGER NOT NULL,
                    durationDays INTEGER NOT NULL,
                    startDateEpochMillis INTEGER NOT NULL,
                    endDateEpochMillis INTEGER,
                    dailyFrequency INTEGER NOT NULL,
                    scheduleTimesData TEXT NOT NULL,
                    isActive INTEGER NOT NULL,
                    notes TEXT,
                    reminderEnabled INTEGER NOT NULL,
                    isDeleted INTEGER NOT NULL,
                    createdAtEpochMillis INTEGER NOT NULL,
                    updatedAtEpochMillis INTEGER NOT NULL,
                    updatedBy TEXT,
                    createdBy TEXT,
                    syncStatus INTEGER NOT NULL,
                    lastSyncError TEXT,
                    syncStateRaw INTEGER NOT NULL,
                    FOREIGN KEY(familyId) REFERENCES kb_families(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_kb_treatments_new_familyId ON kb_treatments_new(familyId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_kb_treatments_new_childId ON kb_treatments_new(childId)")
            db.execSQL(
                """
                INSERT INTO kb_treatments_new (
                    id, familyId, childId, drugName, activeIngredient, dosageValue, dosageUnit,
                    isLongTerm, durationDays, startDateEpochMillis, endDateEpochMillis,
                    dailyFrequency, scheduleTimesData, isActive, notes, reminderEnabled,
                    isDeleted, createdAtEpochMillis, updatedAtEpochMillis, updatedBy, createdBy,
                    syncStatus, lastSyncError, syncStateRaw
                )
                SELECT
                    id, familyId, childId, drugName, activeIngredient, dosageValue, dosageUnit,
                    isLongTerm, durationDays, startDateEpochMillis, endDateEpochMillis,
                    dailyFrequency, scheduleTimesData, isActive, notes, reminderEnabled,
                    isDeleted, createdAtEpochMillis, updatedAtEpochMillis, updatedBy, createdBy,
                    syncStatus, lastSyncError, syncStateRaw
                FROM kb_treatments
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE kb_treatments")
            db.execSQL("ALTER TABLE kb_treatments_new RENAME TO kb_treatments")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS kb_dose_logs_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    familyId TEXT NOT NULL,
                    childId TEXT NOT NULL,
                    treatmentId TEXT NOT NULL,
                    dayNumber INTEGER NOT NULL,
                    slotIndex INTEGER NOT NULL,
                    scheduledTime TEXT NOT NULL,
                    takenAtEpochMillis INTEGER,
                    taken INTEGER NOT NULL,
                    isDeleted INTEGER NOT NULL,
                    createdAtEpochMillis INTEGER NOT NULL,
                    updatedAtEpochMillis INTEGER NOT NULL,
                    updatedBy TEXT,
                    syncStatus INTEGER NOT NULL,
                    lastSyncError TEXT,
                    FOREIGN KEY(familyId) REFERENCES kb_families(id) ON DELETE CASCADE,
                    FOREIGN KEY(treatmentId) REFERENCES kb_treatments(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_kb_dose_logs_new_familyId ON kb_dose_logs_new(familyId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_kb_dose_logs_new_childId ON kb_dose_logs_new(childId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_kb_dose_logs_new_treatmentId ON kb_dose_logs_new(treatmentId)")
            db.execSQL(
                """
                INSERT INTO kb_dose_logs_new (
                    id, familyId, childId, treatmentId, dayNumber, slotIndex, scheduledTime,
                    takenAtEpochMillis, taken, isDeleted, createdAtEpochMillis, updatedAtEpochMillis,
                    updatedBy, syncStatus, lastSyncError
                )
                SELECT
                    id, familyId, childId, treatmentId, dayNumber, slotIndex, scheduledTime,
                    takenAtEpochMillis, taken, isDeleted, createdAtEpochMillis, updatedAtEpochMillis,
                    updatedBy, syncStatus, lastSyncError
                FROM kb_dose_logs
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE kb_dose_logs")
            db.execSQL("ALTER TABLE kb_dose_logs_new RENAME TO kb_dose_logs")
        }
    }

    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("PRAGMA foreign_keys=OFF")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS kb_medical_visits_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    familyId TEXT NOT NULL,
                    childId TEXT NOT NULL,
                    dateEpochMillis INTEGER NOT NULL,
                    doctorName TEXT,
                    doctorSpecializationRaw TEXT,
                    travelDetailsJson TEXT,
                    reason TEXT NOT NULL,
                    diagnosis TEXT,
                    recommendations TEXT,
                    linkedTreatmentIdsJson TEXT NOT NULL,
                    linkedExamIdsJson TEXT NOT NULL,
                    asNeededDrugsJson TEXT,
                    therapyTypesJson TEXT NOT NULL,
                    prescribedExamsJson TEXT,
                    photoUrlsJson TEXT NOT NULL,
                    notes TEXT,
                    nextVisitDateEpochMillis INTEGER,
                    nextVisitReason TEXT,
                    visitStatusRaw TEXT,
                    reminderOn INTEGER NOT NULL,
                    nextVisitReminderOn INTEGER NOT NULL,
                    isDeleted INTEGER NOT NULL,
                    createdAtEpochMillis INTEGER NOT NULL,
                    updatedAtEpochMillis INTEGER NOT NULL,
                    updatedBy TEXT,
                    createdBy TEXT,
                    syncStateRaw INTEGER NOT NULL,
                    lastSyncError TEXT,
                    FOREIGN KEY(familyId) REFERENCES kb_families(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_kb_medical_visits_new_familyId ON kb_medical_visits_new(familyId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_kb_medical_visits_new_childId ON kb_medical_visits_new(childId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_kb_medical_visits_new_dateEpochMillis ON kb_medical_visits_new(dateEpochMillis)")
            db.execSQL(
                """
                INSERT INTO kb_medical_visits_new (
                    id, familyId, childId, dateEpochMillis, doctorName, doctorSpecializationRaw,
                    travelDetailsJson, reason, diagnosis, recommendations, linkedTreatmentIdsJson,
                    linkedExamIdsJson, asNeededDrugsJson, therapyTypesJson, prescribedExamsJson,
                    photoUrlsJson, notes, nextVisitDateEpochMillis, nextVisitReason, visitStatusRaw,
                    reminderOn, nextVisitReminderOn, isDeleted, createdAtEpochMillis, updatedAtEpochMillis,
                    updatedBy, createdBy, syncStateRaw, lastSyncError
                )
                SELECT
                    id, familyId, childId, dateEpochMillis, doctorName, doctorSpecializationRaw,
                    travelDetailsJson, reason, diagnosis, recommendations, linkedTreatmentIdsJson,
                    linkedExamIdsJson, asNeededDrugsJson, therapyTypesJson, prescribedExamsJson,
                    photoUrlsJson, notes, nextVisitDateEpochMillis, nextVisitReason, visitStatusRaw,
                    reminderOn, nextVisitReminderOn, isDeleted, createdAtEpochMillis, updatedAtEpochMillis,
                    updatedBy, createdBy, syncStateRaw, lastSyncError
                FROM kb_medical_visits
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE kb_medical_visits")
            db.execSQL("ALTER TABLE kb_medical_visits_new RENAME TO kb_medical_visits")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS kb_medical_exams_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    familyId TEXT NOT NULL,
                    childId TEXT NOT NULL,
                    name TEXT NOT NULL,
                    isUrgent INTEGER NOT NULL,
                    deadlineEpochMillis INTEGER,
                    preparation TEXT,
                    notes TEXT,
                    location TEXT,
                    statusRaw TEXT NOT NULL,
                    resultText TEXT,
                    resultDateEpochMillis INTEGER,
                    prescribingVisitId TEXT,
                    reminderOn INTEGER NOT NULL DEFAULT 0,
                    isDeleted INTEGER NOT NULL,
                    syncStateRaw INTEGER NOT NULL,
                    lastSyncError TEXT,
                    createdAtEpochMillis INTEGER NOT NULL,
                    updatedAtEpochMillis INTEGER NOT NULL,
                    updatedBy TEXT NOT NULL,
                    createdBy TEXT NOT NULL,
                    FOREIGN KEY(familyId) REFERENCES kb_families(id) ON DELETE CASCADE,
                    FOREIGN KEY(prescribingVisitId) REFERENCES kb_medical_visits(id) ON DELETE SET NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_kb_medical_exams_new_familyId ON kb_medical_exams_new(familyId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_kb_medical_exams_new_childId ON kb_medical_exams_new(childId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_kb_medical_exams_new_prescribingVisitId ON kb_medical_exams_new(prescribingVisitId)")
            db.execSQL(
                """
                INSERT INTO kb_medical_exams_new (
                    id, familyId, childId, name, isUrgent, deadlineEpochMillis, preparation, notes,
                    location, statusRaw, resultText, resultDateEpochMillis, prescribingVisitId, reminderOn,
                    isDeleted, syncStateRaw, lastSyncError, createdAtEpochMillis, updatedAtEpochMillis, updatedBy, createdBy
                )
                SELECT
                    id, familyId, childId, name, isUrgent, deadlineEpochMillis, preparation, notes,
                    location, statusRaw, resultText, resultDateEpochMillis, prescribingVisitId, reminderOn,
                    isDeleted, syncStateRaw, lastSyncError, createdAtEpochMillis, updatedAtEpochMillis, updatedBy, createdBy
                FROM kb_medical_exams
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE kb_medical_exams")
            db.execSQL("ALTER TABLE kb_medical_exams_new RENAME TO kb_medical_exams")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS kb_vaccines_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    familyId TEXT NOT NULL,
                    childId TEXT NOT NULL,
                    name TEXT NOT NULL,
                    vaccineTypeRaw TEXT NOT NULL,
                    statusRaw TEXT NOT NULL,
                    commercialName TEXT,
                    doseNumber INTEGER NOT NULL,
                    totalDoses INTEGER NOT NULL,
                    administeredDateEpochMillis INTEGER,
                    scheduledDateEpochMillis INTEGER,
                    lotNumber TEXT,
                    doctorName TEXT,
                    location TEXT,
                    administeredBy TEXT,
                    administrationSiteRaw TEXT,
                    notes TEXT,
                    reminderOn INTEGER NOT NULL,
                    nextDoseDateEpochMillis INTEGER,
                    isDeleted INTEGER NOT NULL,
                    createdAtEpochMillis INTEGER NOT NULL,
                    updatedAtEpochMillis INTEGER NOT NULL,
                    updatedBy TEXT,
                    createdBy TEXT,
                    syncStateRaw INTEGER NOT NULL,
                    lastSyncError TEXT,
                    FOREIGN KEY(familyId) REFERENCES kb_families(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_kb_vaccines_new_familyId ON kb_vaccines_new(familyId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_kb_vaccines_new_childId ON kb_vaccines_new(childId)")
            db.execSQL(
                """
                INSERT INTO kb_vaccines_new (
                    id, familyId, childId, name, vaccineTypeRaw, statusRaw, commercialName, doseNumber,
                    totalDoses, administeredDateEpochMillis, scheduledDateEpochMillis, lotNumber, doctorName,
                    location, administeredBy, administrationSiteRaw, notes, reminderOn, nextDoseDateEpochMillis,
                    isDeleted, createdAtEpochMillis, updatedAtEpochMillis, updatedBy, createdBy, syncStateRaw, lastSyncError
                )
                SELECT
                    id, familyId, childId, name, vaccineTypeRaw, statusRaw, commercialName, doseNumber,
                    totalDoses, administeredDateEpochMillis, scheduledDateEpochMillis, lotNumber, doctorName,
                    location, administeredBy, administrationSiteRaw, notes, reminderOn, nextDoseDateEpochMillis,
                    isDeleted, createdAtEpochMillis, updatedAtEpochMillis, updatedBy, createdBy, syncStateRaw, lastSyncError
                FROM kb_vaccines
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE kb_vaccines")
            db.execSQL("ALTER TABLE kb_vaccines_new RENAME TO kb_vaccines")

            db.execSQL("PRAGMA foreign_keys=ON")
        }
    }

    /**
     * Documents can reference a health [childId] that is not in [kb_children] (e.g. adult profile).
     * Drop FK(childId → kb_children) so treatment/visit/exam attachments insert cleanly.
     */
    private val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE kb_treatments ADD COLUMN prescribingVisitId TEXT")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_kb_treatments_prescribingVisitId ON kb_treatments(prescribingVisitId)")
        }
    }

    private val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `kb_wallet_tickets` ADD COLUMN `pdfThumbnailBase64` TEXT")
            db.execSQL("ALTER TABLE `kb_wallet_tickets` ADD COLUMN `syncStateRaw` INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `kb_wallet_tickets` (
                    `id` TEXT NOT NULL,
                    `familyId` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `kindRaw` TEXT NOT NULL,
                    `eventDateEpochMillis` INTEGER,
                    `eventEndDateEpochMillis` INTEGER,
                    `location` TEXT,
                    `seat` TEXT,
                    `bookingCode` TEXT,
                    `notes` TEXT,
                    `emitter` TEXT,
                    `pdfStorageURL` TEXT,
                    `pdfStorageBytes` INTEGER NOT NULL,
                    `pdfFileName` TEXT,
                    `addToAppleWalletURL` TEXT,
                    `barcodeText` TEXT,
                    `barcodeFormat` TEXT,
                    `createdBy` TEXT NOT NULL,
                    `createdByName` TEXT NOT NULL,
                    `updatedBy` TEXT NOT NULL,
                    `updatedByName` TEXT NOT NULL,
                    `createdAtEpochMillis` INTEGER NOT NULL,
                    `updatedAtEpochMillis` INTEGER NOT NULL,
                    `isDeleted` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`familyId`) REFERENCES `kb_families`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_kb_wallet_tickets_familyId` ON `kb_wallet_tickets` (`familyId`)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_kb_wallet_tickets_familyId_isDeleted` ON `kb_wallet_tickets` (`familyId`, `isDeleted`)",
            )
        }
    }

    private val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("PRAGMA foreign_keys=OFF")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS kb_documents_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    familyId TEXT NOT NULL,
                    childId TEXT,
                    categoryId TEXT,
                    localPath TEXT,
                    title TEXT NOT NULL,
                    fileName TEXT NOT NULL,
                    mimeType TEXT NOT NULL,
                    fileSize INTEGER NOT NULL,
                    storagePath TEXT NOT NULL,
                    downloadURL TEXT,
                    notes TEXT,
                    extractedText TEXT,
                    extractedTextUpdatedAtEpochMillis INTEGER,
                    extractionStatusRaw INTEGER NOT NULL,
                    extractionError TEXT,
                    createdAtEpochMillis INTEGER NOT NULL,
                    updatedAtEpochMillis INTEGER NOT NULL,
                    updatedBy TEXT NOT NULL,
                    isDeleted INTEGER NOT NULL,
                    syncStateRaw INTEGER NOT NULL,
                    lastSyncError TEXT,
                    FOREIGN KEY(familyId) REFERENCES kb_families(id) ON DELETE CASCADE,
                    FOREIGN KEY(categoryId) REFERENCES kb_document_categories(id) ON DELETE SET NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_kb_documents_new_familyId ON kb_documents_new(familyId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_kb_documents_new_childId ON kb_documents_new(childId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_kb_documents_new_categoryId ON kb_documents_new(categoryId)")
            db.execSQL(
                """
                INSERT INTO kb_documents_new (
                    id, familyId, childId, categoryId, localPath, title, fileName, mimeType, fileSize,
                    storagePath, downloadURL, notes, extractedText, extractedTextUpdatedAtEpochMillis,
                    extractionStatusRaw, extractionError, createdAtEpochMillis, updatedAtEpochMillis,
                    updatedBy, isDeleted, syncStateRaw, lastSyncError
                )
                SELECT
                    id, familyId, childId, categoryId, localPath, title, fileName, mimeType, fileSize,
                    storagePath, downloadURL, notes, extractedText, extractedTextUpdatedAtEpochMillis,
                    extractionStatusRaw, extractionError, createdAtEpochMillis, updatedAtEpochMillis,
                    updatedBy, isDeleted, syncStateRaw, lastSyncError
                FROM kb_documents
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE kb_documents")
            db.execSQL("ALTER TABLE kb_documents_new RENAME TO kb_documents")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_kb_documents_familyId ON kb_documents(familyId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_kb_documents_childId ON kb_documents(childId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_kb_documents_categoryId ON kb_documents(categoryId)")
            db.execSQL("PRAGMA foreign_keys=ON")
        }
    }

    @Provides
    @Singleton
    fun provideKidBoxDatabase(
        @ApplicationContext context: Context,
    ): KidBoxDatabase = Room.databaseBuilder(
        context,
        KidBoxDatabase::class.java,
        "kidbox.db",
    ).addMigrations(
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11,
        MIGRATION_11_12,
        MIGRATION_12_13,
        MIGRATION_13_14,
    )
        .fallbackToDestructiveMigration()
        .build()

    @Provides
    fun provideKBUserProfileDao(database: KidBoxDatabase): KBUserProfileDao = database.userProfileDao()

    @Provides
    fun provideKBFamilyDao(database: KidBoxDatabase): KBFamilyDao = database.familyDao()

    @Provides
    fun provideKBFamilyMemberDao(database: KidBoxDatabase): KBFamilyMemberDao = database.familyMemberDao()

    @Provides
    fun provideKBChildDao(database: KidBoxDatabase): KBChildDao = database.childDao()

    @Provides
    fun provideKBGroceryItemDao(database: KidBoxDatabase): KBGroceryItemDao = database.groceryItemDao()

    @Provides
    fun provideKBEventDao(database: KidBoxDatabase): KBEventDao = database.eventDao()

    @Provides
    fun provideKBNoteDao(database: KidBoxDatabase): KBNoteDao = database.noteDao()

    @Provides
    fun provideKBTodoListDao(database: KidBoxDatabase): KBTodoListDao = database.todoListDao()

    @Provides
    fun provideKBTodoItemDao(database: KidBoxDatabase): KBTodoItemDao = database.todoItemDao()

    @Provides
    fun provideKBCalendarEventDao(database: KidBoxDatabase): KBCalendarEventDao = database.calendarEventDao()

    @Provides
    fun provideKBDocumentDao(database: KidBoxDatabase): KBDocumentDao = database.documentDao()

    @Provides
    fun provideKBDocumentCategoryDao(database: KidBoxDatabase): KBDocumentCategoryDao = database.documentCategoryDao()

    @Provides
    fun provideKBExpenseDao(database: KidBoxDatabase): KBExpenseDao = database.expenseDao()

    @Provides
    fun provideKBExpenseCategoryDao(database: KidBoxDatabase): KBExpenseCategoryDao = database.expenseCategoryDao()

    @Provides
    fun provideKBChatMessageDao(database: KidBoxDatabase): KBChatMessageDao = database.chatMessageDao()

    @Provides
    fun provideKBFamilyPhotoDao(database: KidBoxDatabase): KBFamilyPhotoDao = database.familyPhotoDao()

    @Provides
    fun provideKBPhotoAlbumDao(database: KidBoxDatabase): KBPhotoAlbumDao = database.photoAlbumDao()

    @Provides
    fun provideKBSharedLocationDao(database: KidBoxDatabase): KBSharedLocationDao = database.sharedLocationDao()

    @Provides
    fun provideKBMedicalVisitDao(database: KidBoxDatabase): KBMedicalVisitDao =
        database.medicalVisitDao()

    @Provides
    fun provideKBMedicalExamDao(database: KidBoxDatabase): KBMedicalExamDao =
        database.medicalExamDao()

    @Provides
    fun provideKBPediatricProfileDao(database: KidBoxDatabase): KBPediatricProfileDao =
        database.pediatricProfileDao()

    @Provides
    fun provideKBVaccineDao(database: KidBoxDatabase): KBVaccineDao =
        database.vaccineDao()

    @Provides
    fun provideKBTreatmentDao(database: KidBoxDatabase): KBTreatmentDao =
        database.treatmentDao()

    @Provides
    fun provideKBDoseLogDao(database: KidBoxDatabase): KBDoseLogDao =
        database.doseLogDao()

    @Provides
    fun provideKBAIConversationDao(database: KidBoxDatabase): KBAIConversationDao =
        database.aiConversationDao()

    @Provides
    fun provideKBAIMessageDao(database: KidBoxDatabase): KBAIMessageDao =
        database.aiMessageDao()

    @Provides
    fun provideWalletTicketDao(database: KidBoxDatabase): WalletTicketDao =
        database.walletTicketDao()
}
