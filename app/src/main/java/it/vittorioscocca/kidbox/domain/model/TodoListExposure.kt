package it.vittorioscocca.kidbox.domain.model

import it.vittorioscocca.kidbox.data.local.entity.KBTodoItemEntity
import it.vittorioscocca.kidbox.data.local.mapper.decodeStringList

/**
 * Decide se un membro può vedere la riga di una lista di To-Do.
 *
 * Regola: un membro vede una lista solo se contiene almeno un To-Do condiviso
 * con lui. Una lista con soli To-Do privati (es. tutti "solo io") non deve
 * comparire sul dispositivo degli altri.
 *
 * **Liste vuote**: le vede solo chi le ha create. Nasconderle a tutti sarebbe
 * stato scorretto — chi crea una lista se la vedrebbe sparire prima di poterci
 * mettere dentro qualcosa — mentre mostrarle a tutti rivelava l'esistenza di
 * liste che non contengono nulla di condiviso.
 *
 * **Liste senza autore**: quelle create prima dell'introduzione di `createdBy`
 * restano visibili a tutti. Applicare la regola nuova le avrebbe fatte sparire
 * a chiunque, comprese quelle in uso da tempo.
 */
object TodoListExposure {
    fun memberCanSeeListRow(
        listId: String,
        todosForChild: List<KBTodoItemEntity>,
        currentUid: String?,
        listCreatedBy: String? = null,
    ): Boolean {
        val uid = currentUid?.takeIf { it.isNotBlank() } ?: return false
        val activeInList = todosForChild.filter { it.listId == listId && !it.isDeleted }
        if (activeInList.isEmpty()) {
            val owner = listCreatedBy?.takeIf { it.isNotBlank() } ?: return true
            return owner == uid
        }
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
