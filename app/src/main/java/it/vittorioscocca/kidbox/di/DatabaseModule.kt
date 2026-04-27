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
            // Chat schema parity with iOS:
            // - contactPayloadJSON (for shared contact card)
            // - deletedForJSON    (per-user local hidden state mirror)
            db.execSQL("ALTER TABLE kb_chat_messages ADD COLUMN contactPayloadJSON TEXT")
            db.execSQL("ALTER TABLE kb_chat_messages ADD COLUMN deletedForJSON TEXT")
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
    ).addMigrations(MIGRATION_4_5)
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
}
