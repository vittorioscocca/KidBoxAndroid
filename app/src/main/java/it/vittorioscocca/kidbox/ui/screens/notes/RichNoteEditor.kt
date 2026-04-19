package it.vittorioscocca.kidbox.ui.screens.notes

import android.graphics.Typeface
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.text.style.BulletSpan
import android.text.style.RelativeSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.widget.AppCompatEditText
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import it.vittorioscocca.kidbox.data.remote.notes.normalizeChecklistGlyphsToIos
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import kotlin.math.abs

/** Cerchi checklist ○/◉ leggermente più grandi del corpo testo (1.2f è usato per sottotitoli). */
private const val CHECKLIST_CIRCLE_RELATIVE_SIZE = 1.25f

/**
 * La tastiera software inserisce `\n` via [InputConnection.commitText], non come [KeyEvent.KEYCODE_ENTER]
 * su [setOnKeyListener]. Intercettiamo commitText e sendKeyEvent(ENTER) con debounce per non duplicare.
 */
private class ListEnterInputConnection(
    target: InputConnection,
    private val editText: EditText,
    private val onBodyChange: (String) -> Unit,
    private val onAfterEnter: () -> Unit,
) : InputConnectionWrapper(target, true) {
    private var lastEnterMs: Long = 0L

    private fun runListEnter(): Boolean {
        val now = android.os.SystemClock.uptimeMillis()
        if (abs(now - lastEnterMs) < 120L) return true
        lastEnterMs = now
        editText.handleEnterKey(onBodyChange)
        editText.post { onAfterEnter() }
        return true
    }

    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
        if (text != null && text.length == 1 && text[0] == '\n') {
            return runListEnter()
        }
        return super.commitText(text, newCursorPosition)
    }

    override fun sendKeyEvent(event: KeyEvent?): Boolean {
        if (event?.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER) {
            return runListEnter()
        }
        return super.sendKeyEvent(event)
    }
}

private enum class RichNoteCommand {
    Bold,
    Italic,
    Underline,
    Strike,
    Heading,
    Subheading,
    Body,
    Bullet,
    Number,
    Checklist,
    Quote,
    IndentMore,
    IndentLess,
}

private data class RichToolbarState(
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isStrike: Boolean = false,
    val isHeading: Boolean = false,
    val isSubheading: Boolean = false,
    val isBullet: Boolean = false,
    val isNumber: Boolean = false,
    val isChecklist: Boolean = false,
    val isQuote: Boolean = false,
)

@Composable
fun RichNoteEditor(
    title: String,
    onTitleChange: (String) -> Unit,
    body: String,
    onBodyChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current
    val editTextRef = remember { arrayOfNulls<EditText>(1) }
    var toolbarState by remember { mutableStateOf(RichToolbarState()) }
    var isAaExpanded by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            editTextRef[0] = null
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = kb.card),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(Modifier.fillMaxSize()) {
                androidx.compose.material3.TextField(
                    value = title,
                    onValueChange = onTitleChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    placeholder = {
                        Text(
                            "Titolo",
                            color = kb.subtitle,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 22.sp,
                        color = kb.title,
                    ),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = kb.title,
                        unfocusedTextColor = kb.title,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        errorContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        errorIndicatorColor = Color.Transparent,
                        cursorColor = kb.title,
                        focusedPlaceholderColor = kb.subtitle,
                        unfocusedPlaceholderColor = kb.subtitle,
                    ),
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    thickness = 1.dp,
                    color = kb.divider.copy(alpha = 0.35f),
                )

                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    factory = {
                        object : AppCompatEditText(
                            ContextThemeWrapper(context, androidx.appcompat.R.style.Theme_AppCompat),
                        ) {
                            var selectionChanged: (() -> Unit)? = null
                            override fun onSelectionChanged(selStart: Int, selEnd: Int) {
                                super.onSelectionChanged(selStart, selEnd)
                                selectionChanged?.invoke()
                            }

                            override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
                                val base = super.onCreateInputConnection(outAttrs) ?: return null
                                return ListEnterInputConnection(
                                    base,
                                    this,
                                    onBodyChange,
                                ) {
                                    toolbarState = buildToolbarState(this)
                                }
                            }
                        }.apply {
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            gravity = Gravity.TOP or Gravity.START
                            includeFontPadding = false
                            isVerticalScrollBarEnabled = true
                            setPadding(20, 12, 20, 20)
                            hint = "Testo"
                            setTextColor(resolveTextColor())
                            setHintTextColor(resolveHintColor())
                            textSize = 16f
                            inputType = InputType.TYPE_CLASS_TEXT or
                                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                                InputType.TYPE_TEXT_FLAG_MULTI_LINE
                            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
                            setSingleLine(false)
                            val initial = bodyHtmlToSpanned(body)
                            setText(initial)
                            setSelection(text?.length ?: 0)
                            addTextChangedListener(
                                object : TextWatcher {
                                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                                    override fun afterTextChanged(s: Editable?) {
                                        toolbarState = buildToolbarState(this@apply)
                                        onBodyChange(spannedToHtml(s ?: ""))
                                    }
                                },
                            )
                            setOnKeyListener { _, keyCode, event ->
                                if (event?.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                                if (keyCode == KeyEvent.KEYCODE_DEL) {
                                    val handled = handleBackspaceOnEmptyListLine(onBodyChange)
                                    if (handled) {
                                        toolbarState = buildToolbarState(this)
                                        true
                                    } else {
                                        false
                                    }
                                } else {
                                    false
                                }
                            }
                            setOnTouchListener { view, event ->
                                if (event?.action == MotionEvent.ACTION_UP) {
                                    val clicked = handleChecklistTap(event.x.toInt(), onBodyChange)
                                    if (clicked) {
                                        view.performClick()
                                        return@setOnTouchListener true
                                    }
                                    toolbarState = buildToolbarState(this)
                                }
                                false
                            }
                            selectionChanged = {
                                toolbarState = buildToolbarState(this)
                            }
                            editTextRef[0] = this
                        }
                    },
                    update = { editText ->
                        editTextRef[0] = editText
                        val currentBody = spannedToHtml(editText.text ?: "")
                        val targetBody = body
                        if (currentBody != targetBody) {
                            val sel = editText.selectionStart.coerceAtLeast(0)
                            editText.setText(bodyHtmlToSpanned(targetBody))
                            val textLength = editText.text?.length ?: 0
                            editText.setSelection(sel.coerceAtMost(textLength))
                        }
                        toolbarState = buildToolbarState(editText)
                    },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        RichToolbarBar(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding(),
            state = toolbarState,
            isAaExpanded = isAaExpanded,
            onAction = { command ->
                editTextRef[0]?.applyCommand(command, onBodyChange)
                editTextRef[0]?.let { toolbarState = buildToolbarState(it) }
            },
            onToggleAa = { isAaExpanded = !isAaExpanded },
        )
    }
}

@Composable
private fun RichToolbarBar(
    modifier: Modifier = Modifier,
    state: RichToolbarState,
    isAaExpanded: Boolean,
    onAction: (RichNoteCommand) -> Unit,
    onToggleAa: () -> Unit,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.kidBoxColors.card, RoundedCornerShape(18.dp))
            .wrapContentHeight()
            .padding(vertical = 10.dp),
    ) {
        HorizontalDivider(
            modifier = Modifier.padding(bottom = 10.dp),
            color = MaterialTheme.kidBoxColors.divider.copy(alpha = 0.5f),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ToolbarChip("Aa", active = isAaExpanded) { onToggleAa() }
            ToolbarChip("B", active = state.isBold) { onAction(RichNoteCommand.Bold) }
            ToolbarChip("I", active = state.isItalic) { onAction(RichNoteCommand.Italic) }
            ToolbarChip("U", active = state.isUnderline) { onAction(RichNoteCommand.Underline) }
            ToolbarChip("S", active = state.isStrike) { onAction(RichNoteCommand.Strike) }
            ToolbarChip("•", active = state.isBullet) { onAction(RichNoteCommand.Bullet) }
            ToolbarChip("1.", active = state.isNumber) { onAction(RichNoteCommand.Number) }
            ToolbarChip("Check", active = state.isChecklist) { onAction(RichNoteCommand.Checklist) }
        }
        if (isAaExpanded) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ToolbarChip("H1", active = state.isHeading) { onAction(RichNoteCommand.Heading) }
                ToolbarChip("H2", active = state.isSubheading) { onAction(RichNoteCommand.Subheading) }
                ToolbarChip("Body", active = !state.isHeading && !state.isSubheading) { onAction(RichNoteCommand.Body) }
                ToolbarChip("Quote", active = state.isQuote) { onAction(RichNoteCommand.Quote) }
                ToolbarChip("<") { onAction(RichNoteCommand.IndentLess) }
                ToolbarChip(">") { onAction(RichNoteCommand.IndentMore) }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ToolbarChip("H1", active = state.isHeading) { onAction(RichNoteCommand.Heading) }
                ToolbarChip("H2", active = state.isSubheading) { onAction(RichNoteCommand.Subheading) }
                ToolbarChip("Body", active = !state.isHeading && !state.isSubheading) { onAction(RichNoteCommand.Body) }
                ToolbarChip("Quote", active = state.isQuote) { onAction(RichNoteCommand.Quote) }
                ToolbarChip("<") { onAction(RichNoteCommand.IndentLess) }
                ToolbarChip(">") { onAction(RichNoteCommand.IndentMore) }
            }
        }
    }
}

@Composable
private fun ToolbarChip(
    label: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    androidx.compose.material3.AssistChip(
        onClick = onClick,
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (active) Color(0xFFFF6B00).copy(alpha = 0.16f) else MaterialTheme.kidBoxColors.card,
            labelColor = if (active) Color(0xFFFF6B00) else MaterialTheme.kidBoxColors.title,
        ),
        border = null,
    )
}

private fun EditText.applyCommand(
    command: RichNoteCommand,
    onBodyChange: (String) -> Unit,
) {
    val editable = text ?: return
    val start = selectionStart.coerceAtLeast(0)
    val end = selectionEnd.coerceAtLeast(start)
    when (command) {
        RichNoteCommand.Bold -> toggleSpan(editable, start, end, StyleSpan(Typeface.BOLD))
        RichNoteCommand.Italic -> toggleSpan(editable, start, end, StyleSpan(Typeface.ITALIC))
        RichNoteCommand.Underline -> toggleSpan(editable, start, end, UnderlineSpan())
        RichNoteCommand.Strike -> toggleSpan(editable, start, end, StrikethroughSpan())
        RichNoteCommand.Heading -> applyHeading(editable, start, end)
        RichNoteCommand.Subheading -> applySubheading(editable, start, end)
        RichNoteCommand.Body -> applyBody(editable, start, end)
        RichNoteCommand.Bullet -> applyBullet(editable, start, end)
        RichNoteCommand.Number -> applyNumberedList(editable, start, end)
        RichNoteCommand.Checklist -> applyChecklist(editable, start, end)
        RichNoteCommand.Quote -> applyQuote(editable, start, end)
        RichNoteCommand.IndentMore -> changeIndent(editable, start, end, 40)
        RichNoteCommand.IndentLess -> changeIndent(editable, start, end, -40)
    }
    onBodyChange(spannedToHtml(editable))
}

private fun toggleSpan(
    editable: Editable,
    start: Int,
    end: Int,
    span: Any,
) {
    if (start == end) return
    val existing = editable.getSpans(start, end, span::class.java)
    if (existing.isNotEmpty()) {
        existing.forEach { editable.removeSpan(it) }
    } else {
        editable.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}

private fun applyHeading(
    editable: Editable,
    start: Int,
    end: Int,
) {
    if (start == end) return
    clearBlockSizing(editable, start, end)
    editable.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    editable.setSpan(RelativeSizeSpan(1.4f), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
}

private fun applySubheading(
    editable: Editable,
    start: Int,
    end: Int,
) {
    if (start == end) return
    clearBlockSizing(editable, start, end)
    editable.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    editable.setSpan(RelativeSizeSpan(1.2f), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
}

private fun applyBody(
    editable: Editable,
    start: Int,
    end: Int,
) {
    clearBlockSizing(editable, start, end)
}

private fun applyBullet(
    editable: Editable,
    start: Int,
    end: Int,
) {
    val ranges = mutableListOf<Pair<Int, Int>>()
    forEachSelectedLine(editable, start, end) { lineStart, lineEnd ->
        ranges += lineStart to lineEnd
    }
    ranges.asReversed().forEach { (lineStart, _) ->
        val ns = editable.toString()
        val safeStart = lineStart.coerceAtMost(ns.length)
        val lineEnd = ns.indexOf('\n', safeStart).let { if (it == -1) ns.length else it }
        val line = editable.substring(safeStart, lineEnd)
        val (stripped, removedLen) = stripListPrefix(line)
        if (removedLen > 0) editable.replace(safeStart, safeStart + removedLen, "")
        if (!stripped.startsWith("• ") && !line.startsWith("• ")) {
            editable.insert(safeStart, "• ")
        }
    }
}

private fun applyNumberedList(
    editable: Editable,
    start: Int,
    end: Int,
) {
    val ranges = mutableListOf<Pair<Int, Int>>()
    forEachSelectedLine(editable, start, end) { lineStart, lineEnd ->
        ranges += lineStart to lineEnd
    }
    val ordered = ranges.sortedBy { it.first }
    val isAlreadyNumbered = ordered.all {
        val ns = editable.toString()
        val s = it.first.coerceAtMost(ns.length)
        val e = ns.indexOf('\n', s).let { idx -> if (idx == -1) ns.length else idx }
        Regex("^[0-9]+\\. ").containsMatchIn(editable.substring(s, e))
    }
    var counter = 1
    ordered.asReversed().forEach { (lineStart, _) ->
        val ns = editable.toString()
        val safeStart = lineStart.coerceAtMost(ns.length)
        val lineEnd = ns.indexOf('\n', safeStart).let { if (it == -1) ns.length else it }
        val line = editable.substring(safeStart, lineEnd)
        val (_, removedLen) = stripListPrefix(line)
        if (removedLen > 0) editable.replace(safeStart, safeStart + removedLen, "")
    }
    if (isAlreadyNumbered) return
    ordered.forEachIndexed { index, (lineStart, _) ->
        val ns = editable.toString()
        val safeStart = lineStart.coerceAtMost(ns.length)
        val prefix = "${index + 1}. "
        editable.insert(safeStart, prefix)
        counter = index + 2
    }
    @Suppress("UNUSED_VARIABLE") val _unused = counter
}

private fun stripListPrefix(line: String): Pair<String, Int> {
    if (line.startsWith("○ ") || line.startsWith("◉ ")) {
        return line.drop(2) to 2
    }
    if (line.startsWith("• ")) {
        return line.drop(2) to 2
    }
    val numMatch = Regex("^([0-9]+)\\. ").find(line)
    if (numMatch != null) {
        val len = numMatch.value.length
        return line.drop(len) to len
    }
    return line to 0
}

private fun applyChecklist(
    editable: Editable,
    start: Int,
    end: Int,
) {
    val ranges = mutableListOf<Pair<Int, Int>>()
    forEachSelectedLine(editable, start, end) { lineStart, lineEnd ->
        ranges += lineStart to lineEnd
    }
    ranges.asReversed().forEach { (lineStart, lineEnd) ->
        val line = editable.substring(lineStart, lineEnd)
        when {
            line.startsWith("○ ") -> {
                editable.replace(lineStart, lineStart + 1, "◉")
                styleChecklistCircle(editable, lineStart, lineEnd, checked = true)
            }
            line.startsWith("◉ ") -> {
                editable.replace(lineStart, lineStart + 1, "○")
                styleChecklistCircle(editable, lineStart, lineEnd, checked = false)
            }
            else -> {
                editable.insert(lineStart, "○ ")
                val ns = editable.toString()
                val newLineEnd = ns.indexOf('\n', lineStart).let { if (it == -1) ns.length else it }
                styleChecklistCircle(editable, lineStart, newLineEnd, checked = false)
            }
        }
    }
}

private fun applyQuote(
    editable: Editable,
    start: Int,
    end: Int,
) {
    val ranges = mutableListOf<Pair<Int, Int>>()
    forEachSelectedLine(editable, start, end) { lineStart, lineEnd ->
        ranges += lineStart to lineEnd
    }
    ranges.asReversed().forEach { (lineStart, lineEnd) ->
        val line = editable.substring(lineStart, lineEnd)
        if (line.startsWith("> ")) {
            editable.delete(lineStart, lineStart + 2)
        } else {
            editable.insert(lineStart, "> ")
        }
    }
}

private fun changeIndent(
    editable: Editable,
    start: Int,
    end: Int,
    delta: Int,
) {
    val ranges = mutableListOf<Pair<Int, Int>>()
    forEachSelectedLine(editable, start, end) { lineStart, lineEnd ->
        ranges += lineStart to lineEnd
    }
    ranges.asReversed().forEach { (lineStart, lineEnd) ->
        val line = editable.substring(lineStart, lineEnd)
        if (delta > 0) {
            editable.insert(lineStart, "    ")
        } else if (line.startsWith("    ")) {
            editable.delete(lineStart, lineStart + 4)
        }
    }
}

private fun buildToolbarState(
    editText: EditText,
): RichToolbarState {
    val editable = editText.text ?: return RichToolbarState()
    val length = editable.length
    // Lavoriamo SEMPRE sulla snapshot `ns` (String) per evitare che le sub-sequence
    // su `Editable` (SpannableBuilder di emoji2) vadano in StringIndexOutOfBoundsException
    // in stati transitori (es. subito dopo aver svuotato una riga con backspace).
    val ns = editable.toString()
    val start = editText.selectionStart.coerceAtLeast(0).coerceAtMost(length)
    val end = editText.selectionEnd.coerceAtLeast(start).coerceAtMost(length)
    val safeEnd = if (end == start) (start + 1).coerceAtMost(length) else end
    val lineStart = ns.lastIndexOf('\n', (start - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
    val lineEnd = ns.indexOf('\n', start.coerceAtMost(ns.length))
        .let { if (it == -1) ns.length else it }
        .coerceAtLeast(lineStart)
    val line = ns.substring(lineStart.coerceAtMost(ns.length), lineEnd.coerceAtMost(ns.length))
    val isChecklistLine = line.startsWith("○ ") || line.startsWith("◉ ")
    // Riga checklist: il cerchio usa RelativeSizeSpan (1.25f); non confonderlo con H2.
    return RichToolbarState(
        isBold = editable.getSpans(start, safeEnd, StyleSpan::class.java).any { it.style == Typeface.BOLD },
        isItalic = editable.getSpans(start, safeEnd, StyleSpan::class.java).any { it.style == Typeface.ITALIC },
        isUnderline = editable.getSpans(start, safeEnd, UnderlineSpan::class.java).isNotEmpty(),
        isStrike = editable.getSpans(start, safeEnd, StrikethroughSpan::class.java).isNotEmpty(),
        isHeading = !isChecklistLine && editable.getSpans(start, safeEnd, RelativeSizeSpan::class.java).any { it.sizeChange >= 1.35f },
        isSubheading = !isChecklistLine && editable.getSpans(start, safeEnd, RelativeSizeSpan::class.java).any { it.sizeChange in 1.15f..1.34f },
        isBullet = editable.getSpans(lineStart, lineEnd, BulletSpan::class.java).isNotEmpty() || line.startsWith("• "),
        isNumber = Regex("^[0-9]+\\. ").containsMatchIn(line),
        isChecklist = line.startsWith("○ ") || line.startsWith("◉ "),
        isQuote = line.startsWith("> "),
    )
}

private fun EditText.handleEnterKey(
    onBodyChange: (String) -> Unit,
) {
    val editable = text ?: return
    val cursor = selectionStart.coerceAtLeast(0)
    val ns = editable.toString()
    val lineStart = ns.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
    val lineEnd = ns.indexOf('\n', cursor).let { if (it == -1) ns.length else it }
    val line = ns.substring(lineStart, lineEnd)
    val trimmed = line.trim()
    // Come iOS: "1. testo" con spazio opzionale dopo il punto
    val numberMatch = Regex("^([0-9]+)\\.(?:\\s*)(.*)$").find(trimmed)
    val nextNumber = numberMatch?.groupValues?.get(1)?.toIntOrNull()

    val isChecklistEmpty = (trimmed == "○" || trimmed == "◉" || trimmed == "○ " || trimmed == "◉ ")
    val isBulletEmpty = (trimmed == "•" || trimmed == "• ")
    val isNumberEmpty = numberMatch != null && numberMatch.groupValues[2].isBlank()

    if (isChecklistEmpty || isBulletEmpty || isNumberEmpty) {
        editable.replace(lineStart, lineEnd, "")
        setSelection(lineStart.coerceAtMost(editable.length))
        onBodyChange(spannedToHtml(editable))
        return
    }

    val continuation = when {
        trimmed.startsWith("○ ") || trimmed.startsWith("◉ ") -> "\n○ "
        trimmed.startsWith("• ") -> "\n• "
        nextNumber != null -> "\n${nextNumber + 1}. "
        trimmed.startsWith("> ") -> "\n> "
        line.startsWith("    ") -> "\n    "
        else -> "\n"
    }
    editable.insert(cursor, continuation)
    setSelection((cursor + continuation.length).coerceAtMost(editable.length))
    onBodyChange(spannedToHtml(editable))
}

private fun EditText.handleBackspaceOnEmptyListLine(
    onBodyChange: (String) -> Unit,
): Boolean {
    val editable = text ?: return false
    val selStart = selectionStart
    val selEnd = selectionEnd
    if (selStart != selEnd) return false
    val cursor = selStart.coerceAtLeast(0)
    val ns = editable.toString()
    val lineStart = ns.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
    val lineEnd = ns.indexOf('\n', cursor).let { if (it == -1) ns.length else it }
    val line = ns.substring(lineStart, lineEnd)
    val numberMatch = Regex("^([0-9]+)\\. ").find(line)
    val prefixLen = when {
        line.startsWith("○ ") || line.startsWith("◉ ") -> 2
        line.startsWith("• ") -> 2
        numberMatch != null -> numberMatch.value.length
        else -> return false
    }
    if (line.length != prefixLen) return false
    if (cursor != lineStart + prefixLen) return false
    editable.replace(lineStart, lineEnd, "")
    setSelection(lineStart.coerceAtMost(editable.length))
    onBodyChange(spannedToHtml(editable))
    return true
}

private fun EditText.handleChecklistTap(
    tapX: Int,
    onBodyChange: (String) -> Unit,
) : Boolean {
    val maxTapX = (56f * resources.displayMetrics.density).toInt()
    if (tapX > maxTapX) return false
    val editable = text ?: return false
    val cursor = selectionStart.coerceAtLeast(0)
    val lineStart = editable.toString().lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
    val lineEnd = editable.toString().indexOf('\n', cursor).let { if (it == -1) editable.length else it }
    val line = editable.substring(lineStart, lineEnd)
    when {
        line.startsWith("○ ") -> {
            editable.replace(lineStart, lineStart + 1, "◉")
            styleChecklistCircle(editable, lineStart, lineEnd, checked = true)
            onBodyChange(spannedToHtml(editable))
            return true
        }
        line.startsWith("◉ ") -> {
            editable.replace(lineStart, lineStart + 1, "○")
            styleChecklistCircle(editable, lineStart, lineEnd, checked = false)
            onBodyChange(spannedToHtml(editable))
            return true
        }
    }
    return false
}

/** Come iOS: solo il cerchio cambia colore, niente barrato sul testo. */
private fun styleChecklistCircle(
    editable: Editable,
    lineStart: Int,
    lineEnd: Int,
    checked: Boolean,
) {
    val len = editable.length
    val circleEnd = (lineStart + 1).coerceAtMost(len)
    if (lineStart < circleEnd) {
        editable.getSpans(lineStart, circleEnd, ForegroundColorSpan::class.java).forEach { editable.removeSpan(it) }
        editable.getSpans(lineStart, circleEnd, RelativeSizeSpan::class.java).forEach { editable.removeSpan(it) }
        val color = if (checked) 0xFF34C759.toInt() else 0xFF8A8A8A.toInt()
        editable.setSpan(
            ForegroundColorSpan(color),
            lineStart,
            circleEnd,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        editable.setSpan(
            RelativeSizeSpan(CHECKLIST_CIRCLE_RELATIVE_SIZE),
            lineStart,
            circleEnd,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }
    val textStart = (lineStart + 2).coerceAtMost(len)
    val safeLineEnd = lineEnd.coerceAtMost(len)
    if (textStart < safeLineEnd) {
        editable.getSpans(textStart, safeLineEnd, StrikethroughSpan::class.java).forEach { editable.removeSpan(it) }
    }
}

private fun clearBlockSizing(
    editable: Editable,
    start: Int,
    end: Int,
) {
    editable.getSpans(start, end, RelativeSizeSpan::class.java).forEach { editable.removeSpan(it) }
}

private inline fun forEachSelectedLine(
    editable: Editable,
    start: Int,
    end: Int,
    block: (lineStart: Int, lineEnd: Int) -> Unit,
) {
    val safeStart = start.coerceAtLeast(0)
    val safeEnd = end.coerceAtLeast(safeStart)
    var lineStart = editable.toString().lastIndexOf('\n', (safeStart - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
    while (lineStart <= editable.length) {
        val lineEnd = editable.toString().indexOf('\n', lineStart).let { if (it == -1) editable.length else it }
        block(lineStart, lineEnd)
        if (lineEnd >= safeEnd || lineEnd >= editable.length) break
        lineStart = lineEnd + 1
    }
}

private fun bodyHtmlToSpanned(
    html: String,
): Spanned {
    val normalized = html.trim().normalizeChecklistGlyphsToIos()
    if (normalized.isEmpty()) {
        return SpannableStringBuilder("")
    }
    val builder = SpannableStringBuilder(
        HtmlCompat.fromHtml(normalized, HtmlCompat.FROM_HTML_MODE_LEGACY),
    )
    applyChecklistStylesToAllLines(builder)
    return builder
}

/** Applica colore e ingrandimento ai cerchi dopo il parse HTML da remoto. */
private fun applyChecklistStylesToAllLines(editable: Editable) {
    val s = editable.toString()
    var lineStart = 0
    while (lineStart < s.length) {
        val lineEnd = s.indexOf('\n', lineStart).let { if (it == -1) s.length else it }
        val line = s.substring(lineStart, lineEnd)
        if (line.startsWith("○ ") || line.startsWith("◉ ")) {
            styleChecklistCircle(editable, lineStart, lineEnd, checked = line.startsWith("◉ "))
        }
        lineStart = lineEnd + 1
    }
}

private fun spannedToHtml(
    text: CharSequence,
): String {
    if (text.isEmpty()) return ""
    val builder = SpannableStringBuilder(text)
    // Non serializzare l'ingrandimento del cerchio nell'HTML (parità iOS / storage pulito).
    builder.getSpans(0, builder.length, RelativeSizeSpan::class.java).forEach { span ->
        if (kotlin.math.abs(span.sizeChange - CHECKLIST_CIRCLE_RELATIVE_SIZE) < 0.02f) {
            builder.removeSpan(span)
        }
    }
    return HtmlCompat.toHtml(
        builder,
        HtmlCompat.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE,
    )
}

private fun EditText.resolveTextColor(): Int = android.graphics.Color.parseColor("#EAEAEA").takeIf {
    resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
} ?: android.graphics.Color.parseColor("#1A1A1A")

private fun EditText.resolveHintColor(): Int = android.graphics.Color.parseColor("#8A8A8A")
