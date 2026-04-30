package it.vittorioscocca.kidbox.ui.screens.health

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

@Composable
fun HealthSubjectSelectorScreen(
    familyId: String,
    onBack: () -> Unit,
    onSelect: (childId: String) -> Unit,
    viewModel: HealthSubjectSelectorViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(familyId) { viewModel.load(familyId) }

    // Auto-enter when only one subject is available.
    LaunchedEffect(state.subjects.size, state.isLoading) {
        if (!state.isLoading && state.subjects.size == 1) {
            onSelect(state.subjects.first().id)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(kb.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 18.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Indietro",
                        tint = kb.title,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Salute",
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                color = kb.title,
            )
            Spacer(Modifier.height(20.dp))

            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.subjects.isEmpty() -> EmptySubjects()
                else -> {
                    val children = state.subjects.filter { it.isChild }
                    val adults = state.subjects.filter { !it.isChild }

                    if (children.isNotEmpty()) {
                        SectionHeader("Bambini")
                        children.forEach { s ->
                            SubjectRow(name = s.name, isChild = true) { onSelect(s.id) }
                            Spacer(Modifier.height(10.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    if (adults.isNotEmpty()) {
                        SectionHeader("Adulti")
                        adults.forEach { s ->
                            SubjectRow(name = s.name, isChild = false) { onSelect(s.id) }
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    val kb = MaterialTheme.kidBoxColors
    Text(
        text,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        color = kb.subtitle,
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SubjectRow(name: String, isChild: Boolean, onClick: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = kb.card),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF6B00).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isChild) Icons.Default.ChildCare else Icons.Default.Person,
                    contentDescription = null,
                    tint = Color(0xFFFF6B00),
                )
            }
            Spacer(Modifier.width(14.dp))
            Text(
                name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = kb.title,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = kb.subtitle,
            )
        }
    }
}

@Composable
private fun EmptySubjects() {
    val kb = MaterialTheme.kidBoxColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Nessun profilo disponibile",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = kb.title,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Aggiungi figli o verifica i membri nelle impostazioni famiglia.",
            fontSize = 14.sp,
            color = kb.subtitle,
        )
    }
}
