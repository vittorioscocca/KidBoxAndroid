package it.vittorioscocca.kidbox.ui.screens.requests

import it.vittorioscocca.kidbox.data.local.entity.KBCalendarEventEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTodoItemEntity
import it.vittorioscocca.kidbox.data.local.mapper.decodeStringList
import it.vittorioscocca.kidbox.domain.calendar.occurrencesIn
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope

/**
 * «Chi è libero a quell'ora» nel dialog «Chiedi a…».
 *
 * Gli eventi del calendario non dicono chi partecipa (solo il figlio e chi li
 * ha creati), quindi non si attribuiscono a nessuno: si mostrano come
 * contesto, «in calendario intorno alle 16:30», e chi chiede giudica da sé.
 * Gli unici impegni davvero di una persona sono i to-do assegnati a lei:
 * quelli sì, accanto al suo nome. Gemello di `FamilyRequestAvailability.swift`.
 */
data class FamilyRequestAvailability(
    val around: Long,
    /** Eventi di famiglia nella finestra, non attribuiti a nessuno. */
    val events: List<Item>,
    /** uid → to-do assegnati a quel membro nella finestra. */
    val busy: Map<String, List<Item>>,
) {
    data class Item(
        val id: String,
        val title: String,
        val start: Long,
        val end: Long,
        val isAllDay: Boolean,
    )

    companion object {
        /** Un'ora prima e un'ora dopo la scadenza. */
        const val MARGIN_MS = 3_600_000L

        /** `null` senza una scadenza: non c'è un'ora da confrontare. */
        fun compute(
            around: Long?,
            todos: List<KBTodoItemEntity>,
            events: List<KBCalendarEventEntity>,
            currentUid: String?,
        ): FamilyRequestAvailability? {
            around ?: return null
            val from = around - MARGIN_MS
            val to = around + MARGIN_MS

            val eventItems = events
                .asSequence()
                .filter { !it.isDeleted }
                .filter {
                    KBVisibilityScope.isVisible(
                        scope = it.visibilityScope,
                        memberIds = decodeStringList(it.visibilityMemberIdsJson),
                        createdBy = it.createdBy,
                        currentUid = currentUid,
                    )
                }
                .flatMap { it.occurrencesIn(from, to).asSequence() }
                .map { Item("${it.id}|${it.startDateEpochMillis}", it.title, it.startDateEpochMillis, it.endDateEpochMillis, it.isAllDay) }
                .sortedWith(compareBy<Item>({ !it.isAllDay }, { it.start }))
                .take(4)
                .toList()

            val busy = todos
                .asSequence()
                // Un to-do «tutto il giorno» non è un impegno a un'ora precisa.
                .filter { !it.isDone && !it.isDeleted && it.dueHasTime }
                .filter { !it.assignedTo.isNullOrEmpty() }
                .filter { (it.dueAtEpochMillis ?: Long.MIN_VALUE) in from..to }
                .filter {
                    KBVisibilityScope.isVisible(
                        scope = it.visibilityScope,
                        memberIds = decodeStringList(it.visibilityMemberIdsJson),
                        createdBy = it.createdBy,
                        currentUid = currentUid,
                    )
                }
                .groupBy({ it.assignedTo!! }, { Item(it.id, it.title, it.dueAtEpochMillis!!, it.dueAtEpochMillis!!, false) })
                .mapValues { (_, items) -> items.sortedBy { it.start } }

            return FamilyRequestAvailability(around, eventItems, busy)
        }
    }
}
