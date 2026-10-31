package it.vittorioscocca.kidbox.ui.screens.onboarding

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.vittorioscocca.kidbox.R
import java.util.Calendar
import kotlinx.coroutines.delay

/**
 * Ultima pagina del wizard «crea»: il primo contenuto vero, prima della Home.
 *
 * Perché esiste (31/10/2026, 96 famiglie nate dal 21/09): 59 non avevano mai
 * messo niente, e 48 di queste non riaprivano l'app dopo il primo giorno.
 * Nessuna era mai arrivata a un secondo membro. La checklist «Per iniziare» la
 * vedevano ma non la seguivano: tutto si decideva nella prima sessione, quindi
 * la prima cosa utile deve nascere qui. Scommessa 13 del registro.
 *
 * Tre gesti, ognuno chiuso in questa schermata: un impegno (titolo, giorno e
 * ora), la spesa (una riga, separata da virgole), un documento (che invece
 * apre Documenti, perché il caricamento ha già il suo flusso con la
 * cifratura). «Lo faccio dopo» resta sempre visibile: niente contenuti
 * d'esempio, deve essere una cosa dell'utente o niente. Stessa pagina di iOS
 * (OnboardingFirstContentCard.swift).
 */
internal enum class FirstContentChoice { EVENT, GROCERY, DOCUMENT }

private val PageBlack = Color(0xFF1A1A1A)
private val PageGray = Color(0xFF666666)
private val PageCaption = Color(0xFF888888)
private val PageBorder = Color(0xFFE0E0E0)
private val PageOrange = Color(0xFFFF6B00)
private val EventPurple = Color(0xFF8E5BD8)
private val GroceryGreen = Color(0xFF3DA668)
private val DocumentOrange = Color(0xFFFF9500)

private fun FirstContentChoice.tint(): Color = when (this) {
    FirstContentChoice.EVENT -> EventPurple
    FirstContentChoice.GROCERY -> GroceryGreen
    FirstContentChoice.DOCUMENT -> DocumentOrange
}

private fun FirstContentChoice.icon(): ImageVector = when (this) {
    FirstContentChoice.EVENT -> Icons.Filled.CalendarMonth
    FirstContentChoice.GROCERY -> Icons.Filled.ShoppingCart
    FirstContentChoice.DOCUMENT -> Icons.Filled.Description
}

/** Domani alle 17: un orario plausibile per la prima cosa della settimana. */
private fun defaultEventMillis(): Long = Calendar.getInstance().apply {
    add(Calendar.DAY_OF_YEAR, 1)
    set(Calendar.HOUR_OF_DAY, 17)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

@Composable
internal fun FirstContentPageContent(
    onSaveEvent: (title: String, startMillis: Long, onSaved: () -> Unit) -> Unit,
    onSaveGrocery: (names: List<String>, onSaved: () -> Unit) -> Unit,
    onOpenDocuments: () -> Unit,
    onSkip: () -> Unit,
    onFinished: () -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf<FirstContentChoice?>(null) }
    var saved by rememberSaveable { mutableStateOf<FirstContentChoice?>(null) }
    var eventTitle by rememberSaveable { mutableStateOf("") }
    var eventMillis by rememberSaveable { mutableLongStateOf(defaultEventMillis()) }
    var groceryText by rememberSaveable { mutableStateOf("") }
    val groceryNames = groceryText.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }

    // Il tempo di leggere dove l'ha messo, poi la Home.
    LaunchedEffect(saved) {
        if (saved != null) {
            delay(1_400)
            onFinished()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        val done = saved
        if (done != null) {
            Spacer(modifier = Modifier.height(48.dp))
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = done.tint(), modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(14.dp))
            Text(stringResource(R.string.onboarding_first_content_done), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = PageBlack)
            Text(
                stringResource(
                    if (done == FirstContentChoice.EVENT) R.string.onboarding_first_content_done_event
                    else R.string.onboarding_first_content_done_grocery,
                ),
                fontSize = 15.sp, color = PageGray, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            return@Column
        }

        // ── Header ──
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(PageOrange.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = PageOrange, modifier = Modifier.size(32.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            stringResource(R.string.onboarding_first_content_title),
            fontSize = 26.sp, fontWeight = FontWeight.Bold, color = PageBlack, textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.onboarding_first_content_subtitle),
            fontSize = 16.sp, color = PageGray, textAlign = TextAlign.Center, lineHeight = 23.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))

        for (choice in FirstContentChoice.entries) {
            ChoiceCard(
                choice = choice,
                isOpen = selected == choice,
                onToggle = { selected = if (selected == choice) null else choice },
            ) {
                when (choice) {
                    FirstContentChoice.EVENT -> EventEditor(
                        title = eventTitle,
                        onTitleChange = { eventTitle = it },
                        millis = eventMillis,
                        onMillisChange = { eventMillis = it },
                        onSave = { onSaveEvent(eventTitle, eventMillis) { saved = FirstContentChoice.EVENT } },
                    )
                    FirstContentChoice.GROCERY -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = groceryText,
                            onValueChange = { groceryText = it },
                            placeholder = { Text(stringResource(R.string.onboarding_first_content_grocery_hint)) },
                            maxLines = 3,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            colors = fieldColors(choice.tint()),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        PrimaryButton(
                            label = stringResource(R.string.onboarding_first_content_grocery_cta),
                            tint = choice.tint(),
                            enabled = groceryNames.isNotEmpty(),
                            onClick = { onSaveGrocery(groceryNames) { saved = FirstContentChoice.GROCERY } },
                        )
                    }
                    FirstContentChoice.DOCUMENT -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.onboarding_first_content_document_body), fontSize = 14.sp, color = PageGray, lineHeight = 20.sp)
                        PrimaryButton(
                            label = stringResource(R.string.onboarding_first_content_document_cta),
                            tint = choice.tint(),
                            enabled = true,
                            onClick = onOpenDocuments,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_first_content_skip), fontSize = 15.sp, color = PageCaption)
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun ChoiceCard(
    choice: FirstContentChoice,
    isOpen: Boolean,
    onToggle: () -> Unit,
    editor: @Composable () -> Unit,
) {
    val (titleRes, subRes) = when (choice) {
        FirstContentChoice.EVENT -> R.string.onboarding_first_content_event to R.string.onboarding_first_content_event_sub
        FirstContentChoice.GROCERY -> R.string.onboarding_first_content_grocery to R.string.onboarding_first_content_grocery_sub
        FirstContentChoice.DOCUMENT -> R.string.onboarding_first_content_document to R.string.onboarding_first_content_document_sub
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .border(
                width = 1.5.dp,
                color = if (isOpen) choice.tint().copy(alpha = 0.5f) else Color.Transparent,
                shape = RoundedCornerShape(20.dp),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(choice.tint()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(choice.icon(), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(titleRes), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = PageBlack)
                Text(stringResource(subRes), fontSize = 13.sp, color = PageGray)
            }
            Icon(
                if (isOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null, tint = PageCaption, modifier = Modifier.size(20.dp),
            )
        }
        AnimatedVisibility(visible = isOpen) {
            Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) { editor() }
        }
    }
}

@Composable
private fun EventEditor(
    title: String,
    onTitleChange: (String) -> Unit,
    millis: Long,
    onMillisChange: (Long) -> Unit,
    onSave: () -> Unit,
) {
    val context = LocalContext.current
    val tint = FirstContentChoice.EVENT.tint()
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    val dateLabel = DateFormat.getMediumDateFormat(context).format(cal.time)
    val timeLabel = DateFormat.getTimeFormat(context).format(cal.time)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            placeholder = { Text(stringResource(R.string.onboarding_first_content_event_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = fieldColors(tint),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.onboarding_first_content_when), fontSize = 15.sp, color = PageBlack, modifier = Modifier.weight(1f))
            PickerChip(dateLabel) {
                DatePickerDialog(
                    context,
                    { _, y, m, d ->
                        onMillisChange(Calendar.getInstance().apply {
                            timeInMillis = millis
                            set(y, m, d)
                        }.timeInMillis)
                    },
                    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH),
                ).show()
            }
            PickerChip(timeLabel) {
                TimePickerDialog(
                    context,
                    { _, h, min ->
                        onMillisChange(Calendar.getInstance().apply {
                            timeInMillis = millis
                            set(Calendar.HOUR_OF_DAY, h)
                            set(Calendar.MINUTE, min)
                        }.timeInMillis)
                    },
                    cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), DateFormat.is24HourFormat(context),
                ).show()
            }
        }
        PrimaryButton(
            label = stringResource(R.string.onboarding_first_content_event_cta),
            tint = tint,
            enabled = title.isNotBlank(),
            onClick = onSave,
        )
    }
}

@Composable
private fun PickerChip(label: String, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 15.sp,
        color = PageBlack,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(PageBorder.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun PrimaryButton(label: String, tint: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = if (enabled) 1f else 0.35f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

@Composable
private fun fieldColors(focused: Color) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = focused,
    unfocusedBorderColor = PageBorder,
    focusedTextColor = PageBlack,
    unfocusedTextColor = PageBlack,
    cursorColor = focused,
)
