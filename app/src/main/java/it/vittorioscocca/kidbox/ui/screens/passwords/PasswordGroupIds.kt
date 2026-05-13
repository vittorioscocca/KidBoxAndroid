package it.vittorioscocca.kidbox.ui.screens.passwords

/** Allineato a iOS `PasswordGroupsService.groupId(familyId:slug:)`. */
object PasswordGroupIds {
    const val UNASSIGNED_SLUG = "unassigned"

    fun id(familyId: String, slug: String): String = "kb.password.group.$familyId.$slug"
}
