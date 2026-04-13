package it.vittorioscocca.kidbox.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.dao.KBGroceryItemDao
import it.vittorioscocca.kidbox.data.local.dao.KBTodoItemDao
import it.vittorioscocca.kidbox.data.local.dao.KBTodoListDao
import it.vittorioscocca.kidbox.data.local.dao.KBUserProfileDao
import it.vittorioscocca.kidbox.data.local.db.KidBoxDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideKidBoxDatabase(
        @ApplicationContext context: Context,
    ): KidBoxDatabase = Room.databaseBuilder(
        context,
        KidBoxDatabase::class.java,
        "kidbox.db",
    ).fallbackToDestructiveMigration()
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
    fun provideKBTodoListDao(database: KidBoxDatabase): KBTodoListDao = database.todoListDao()

    @Provides
    fun provideKBTodoItemDao(database: KidBoxDatabase): KBTodoItemDao = database.todoItemDao()
}
