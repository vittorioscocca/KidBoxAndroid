package it.vittorioscocca.kidbox.ui.screens.ai.planning

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PlanningAIChatActionsViewModel @Inject constructor(
    val reminderService: PlanningReminderService,
    val actionExecutor: PlanningActionExecutor,
) : ViewModel()
