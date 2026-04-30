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
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBCalendarEventDao
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

    @Provides
    @Singleton
    fun provideKidBoxDatabase(
        @ApplicationContext context: Context,
    ): KidBoxDatabase = Room.databaseBuilder(
        context,
        KidBoxDatabase::class.java,
        "kidbox.db",
    ).addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
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
}
