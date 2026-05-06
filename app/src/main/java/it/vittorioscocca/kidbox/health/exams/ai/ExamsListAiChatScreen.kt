package it.vittorioscocca.kidbox.health.exams.ai

import androidx.compose.runtime.Composable

/** Chat AI sull'elenco esami filtrato; riusa [ExamAiChatViewModel] in modalità lista. */
@Composable
fun ExamsListAiChatScreen(
    subjectName: String,
    onBack: () -> Unit,
) {
    ExamAiChatScreen(
        examId = "",
        examName = "",
        subjectName = subjectName,
        onBack = onBack,
    )
}
