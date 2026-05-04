package it.vittorioscocca.kidbox.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import it.vittorioscocca.kidbox.data.local.dao.KBAIConversationDao
import it.vittorioscocca.kidbox.data.local.dao.KBAIMessageDao
import it.vittorioscocca.kidbox.data.local.dao.KBCalendarEventDao
import it.vittorioscocca.kidbox.data.local.dao.KBChatMessageDao
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBCustodyScheduleDao
import it.vittorioscocca.kidbox.data.local.dao.KBCustomDrugDao
import it.vittorioscocca.kidbox.data.local.dao.KBDocumentCategoryDao
import it.vittorioscocca.kidbox.data.local.dao.KBDocumentDao
import it.vittorioscocca.kidbox.data.local.dao.KBDoseLogDao
import it.vittorioscocca.kidbox.data.local.dao.KBEventDao
import it.vittorioscocca.kidbox.data.local.dao.KBExpenseCategoryDao
import it.vittorioscocca.kidbox.data.local.dao.KBExpenseDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyPhotoDao
import it.vittorioscocca.kidbox.data.local.dao.KBGroceryItemDao
import it.vittorioscocca.kidbox.data.local.dao.KBMedicalExamDao
import it.vittorioscocca.kidbox.data.local.dao.KBMedicalVisitDao
import it.vittorioscocca.kidbox.data.local.dao.KBNoteDao
import it.vittorioscocca.kidbox.data.local.dao.KBPediatricProfileDao
import it.vittorioscocca.kidbox.data.local.dao.KBPhotoAlbumDao
import it.vittorioscocca.kidbox.data.local.dao.KBRoutineCheckDao
import it.vittorioscocca.kidbox.data.local.dao.KBRoutineDao
import it.vittorioscocca.kidbox.data.local.dao.KBTodoItemDao
import it.vittorioscocca.kidbox.data.local.dao.KBUserProfileDao
import it.vittorioscocca.kidbox.data.local.dao.KBSharedLocationDao
import it.vittorioscocca.kidbox.data.local.dao.KBTodoListDao
import it.vittorioscocca.kidbox.data.local.dao.KBTreatmentDao
import it.vittorioscocca.kidbox.data.local.dao.KBVaccineDao
import it.vittorioscocca.kidbox.data.local.entity.KBAIConversationEntity
import it.vittorioscocca.kidbox.data.local.entity.KBAIMessageEntity
import it.vittorioscocca.kidbox.data.local.entity.KBCalendarEventEntity
import it.vittorioscocca.kidbox.data.local.entity.KBChatMessageEntity
import it.vittorioscocca.kidbox.data.local.entity.KBChildEntity
import it.vittorioscocca.kidbox.data.local.entity.KBCustodyScheduleEntity
import it.vittorioscocca.kidbox.data.local.entity.KBCustomDrugEntity
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentCategoryEntity
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.data.local.entity.KBDoseLogEntity
import it.vittorioscocca.kidbox.data.local.entity.KBEventEntity
import it.vittorioscocca.kidbox.data.local.entity.KBExpenseCategoryEntity
import it.vittorioscocca.kidbox.data.local.entity.KBExpenseEntity
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyEntity
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyMemberEntity
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyPhotoEntity
import it.vittorioscocca.kidbox.data.local.entity.KBGroceryItemEntity
import it.vittorioscocca.kidbox.data.local.entity.KBMedicalExamEntity
import it.vittorioscocca.kidbox.data.local.entity.KBMedicalVisitEntity
import it.vittorioscocca.kidbox.data.local.entity.KBNoteEntity
import it.vittorioscocca.kidbox.data.local.entity.KBPediatricProfileEntity
import it.vittorioscocca.kidbox.data.local.entity.KBPhotoAlbumEntity
import it.vittorioscocca.kidbox.data.local.entity.KBRoutineCheckEntity
import it.vittorioscocca.kidbox.data.local.entity.KBRoutineEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTodoItemEntity
import it.vittorioscocca.kidbox.data.local.entity.KBUserProfileEntity
import it.vittorioscocca.kidbox.data.local.entity.KBSharedLocationEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTodoListEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTreatmentEntity
import it.vittorioscocca.kidbox.data.local.entity.KBVaccineEntity

@Database(
    version = 11,
    exportSchema = false,
    entities = [
        KBUserProfileEntity::class,
        KBFamilyEntity::class,
        KBChildEntity::class,
        KBFamilyMemberEntity::class,
        KBEventEntity::class,
        KBCustodyScheduleEntity::class,
        KBRoutineEntity::class,
        KBRoutineCheckEntity::class,
        KBTodoListEntity::class,
        KBTodoItemEntity::class,
        KBGroceryItemEntity::class,
        KBNoteEntity::class,
        KBDocumentCategoryEntity::class,
        KBDocumentEntity::class,
        KBCalendarEventEntity::class,
        KBExpenseCategoryEntity::class,
        KBExpenseEntity::class,
        KBChatMessageEntity::class,
        KBFamilyPhotoEntity::class,
        KBPhotoAlbumEntity::class,
        KBMedicalVisitEntity::class,
        KBMedicalExamEntity::class,
        KBPediatricProfileEntity::class,
        KBVaccineEntity::class,
        KBTreatmentEntity::class,
        KBCustomDrugEntity::class,
        KBDoseLogEntity::class,
        KBSharedLocationEntity::class,
        KBAIConversationEntity::class,
        KBAIMessageEntity::class,
    ],
)
abstract class KidBoxDatabase : RoomDatabase() {
    abstract fun userProfileDao(): KBUserProfileDao
    abstract fun familyDao(): KBFamilyDao
    abstract fun childDao(): KBChildDao
    abstract fun familyMemberDao(): KBFamilyMemberDao
    abstract fun eventDao(): KBEventDao
    abstract fun custodyScheduleDao(): KBCustodyScheduleDao
    abstract fun routineDao(): KBRoutineDao
    abstract fun routineCheckDao(): KBRoutineCheckDao
    abstract fun todoListDao(): KBTodoListDao
    abstract fun todoItemDao(): KBTodoItemDao
    abstract fun groceryItemDao(): KBGroceryItemDao
    abstract fun noteDao(): KBNoteDao
    abstract fun documentCategoryDao(): KBDocumentCategoryDao
    abstract fun documentDao(): KBDocumentDao
    abstract fun calendarEventDao(): KBCalendarEventDao
    abstract fun expenseCategoryDao(): KBExpenseCategoryDao
    abstract fun expenseDao(): KBExpenseDao
    abstract fun chatMessageDao(): KBChatMessageDao
    abstract fun familyPhotoDao(): KBFamilyPhotoDao
    abstract fun photoAlbumDao(): KBPhotoAlbumDao
    abstract fun medicalVisitDao(): KBMedicalVisitDao
    abstract fun medicalExamDao(): KBMedicalExamDao
    abstract fun pediatricProfileDao(): KBPediatricProfileDao
    abstract fun vaccineDao(): KBVaccineDao
    abstract fun treatmentDao(): KBTreatmentDao
    abstract fun customDrugDao(): KBCustomDrugDao
    abstract fun doseLogDao(): KBDoseLogDao
    abstract fun sharedLocationDao(): KBSharedLocationDao
    abstract fun aiConversationDao(): KBAIConversationDao
    abstract fun aiMessageDao(): KBAIMessageDao
}
