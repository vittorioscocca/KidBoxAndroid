package it.vittorioscocca.kidbox.domain.model

/** Lista todo per bambino — allineato a [KBTodoList] iOS. */
data class KBTodoList(
    val id: String,
    val familyId: String,
    val childId: String,
    val name: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val isDeleted: Boolean,
)
