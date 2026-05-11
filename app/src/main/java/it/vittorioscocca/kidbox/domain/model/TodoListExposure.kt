package it.vittorioscocca.kidbox.domain.model

import it.vittorioscocca.kidbox.data.local.entity.KBTodoItemEntity
import it.vittorioscocca.kidbox.data.local.mapper.decodeStringList

/**
 * Regola: una lista con **solo** To-Do non visibili agli altri membri (es. tutti "solo io")
 * non deve comparire nella Home dell'altro dispositivo/membro.
 *
 * - Lista senza To-Do attivi: resta visibile a tutti (lista nuova / vuota).
 * - Almeno un To-Do attivo nella lista deve essere visibile a [currentUid] affinché il membro veda la lista.
 */
object TodoListExposure {
    fun memberCanSeeListRow(
        listId: String,
        todosForChild: List<KBTodoItemEntity>,
        currentUid: String?,
    ): Boolean {
        val uid = currentUid?.takeIf { it.isNotBlank() } ?: return false
        val activeInList = todosForChild.filter { it.listId == listId && !it.isDeleted }
        if (activeInList.isEmpty()) return true
        return activeInList.any { todo ->
            KBVisibilityScope.isVisible(
                KBVisibilityScope.normalized(todo.visibilityScope),
                decodeStringList(todo.visibilityMemberIdsJson),
                todo.createdBy?.takeIf { it.isNotBlank() },
                uid,
            )
        }
    }
}
