@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.ui.screens.settings.support

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import it.vittorioscocca.kidbox.ui.screens.ai.common.AIChatCopyableMessageContainer
import it.vittorioscocca.kidbox.ui.screens.ai.common.AIChatListScrollEffect
import it.vittorioscocca.kidbox.ui.screens.ai.common.TypewriterClaudeMarkdownText
import it.vittorioscocca.kidbox.ui.screens.ai.planning.AIChatTypingIndicator
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R

private const val ROLE_USER = "user"
private const val ROLE_ASSISTANT = "assistant"
private const val TYPE_BUG = "bug"
private const val MAX_IMAGES = 5
/** Arancione bolle messaggi utente (allineato a Chat KidBox). */
private val KidBoxUserBubbleOrange = Color(0xFFFF6B00)

@Composable
fun SupportChatScreen(
    onBack: () -> Unit,
    viewModel: SupportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val kb = MaterialTheme.kidBoxColors
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val snackHost = remember { SnackbarHostState() }
    var showInfoSheet by remember { mutableStateOf(false) }
    val infoSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES),
    ) { uris ->
        val remaining = MAX_IMAGES - state.attachedImages.size
        uris.take(remaining.coerceAtLeast(0)).forEach { viewModel.addImage(it) }
    }

    AIChatListScrollEffect(
        listState = listState,
        messageCount = state.messages.size + if (state.isLoading) 1 else 0,
        isLoading = state.isLoading,
        streamingMessageId = null,
        streamScrollTick = 0,
        reverseLayout = false,
    )

    val ticketSentMessage = stringResource(R.string.settings_support_sent)
    LaunchedEffect(state.ticketSent) {
        if (state.ticketSent) {
            snackHost.showSnackbar(ticketSentMessage)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = kb.card,
                    titleContentColor = kb.title,
                    navigationIconContentColor = kb.title,
                    actionIconContentColor = kb.title,
                ),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("🤖", fontSize = 22.sp)
                        Text(
                            stringResource(R.string.settings_support_assistant),
                            color = kb.title,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_common_back),
                            tint = kb.title,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showInfoSheet = true }) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = stringResource(R.string.settings_support_info),
                            tint = kb.title,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackHost) },
        containerColor = kb.background,
        bottomBar = {
            SupportInputBar(
                inputText = state.inputText,
                attachedImages = state.attachedImages,
                isLoading = state.isLoading,
                ticketSent = state.ticketSent,
                onInputChange = viewModel::updateInputText,
                onAttachClick = {
                    val slots = MAX_IMAGES - state.attachedImages.size
                    if (slots > 0) {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    }
                },
                onRemoveImage = viewModel::removeImage,
                onSend = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    viewModel.sendMessage(
                        text = state.inputText,
                        images = state.attachedImages,
                    )
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }
                    },
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = 8.dp,
                    bottom = 8.dp,
                ),
            ) {
                if (state.errorMessage != null) {
                    item(key = "error") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    state.errorMessage.orEmpty(),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = viewModel::dismissError) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.settings_common_close))
                                }
                            }
                        }
                    }
                }

                if (state.messages.isEmpty() && !state.isLoading) {
                    item(key = "hint") {
                        Text(
                            text = stringResource(R.string.settings_support_describe_full),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            fontSize = 14.sp,
                            color = kb.subtitle,
                        )
                    }
                }

                items(state.messages, key = { it.id }) { message ->
                    SupportChatBubble(message = message)
                }
                if (state.isLoading) {
                    item(key = "typing") {
                        AIChatTypingIndicator()
                    }
                }
            }
        }
    }

    if (showInfoSheet) {
        SupportInfoBottomSheet(
            sheetState = infoSheetState,
            onDismiss = { showInfoSheet = false },
        )
    }

    if (state.showSubmitConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissSubmitConfirm,
            title = { Text(stringResource(R.string.settings_support_send_q)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.settings_support_send_full),
                    )
                    if (state.detectedType == TYPE_BUG) {
                        Text(
                            stringResource(R.string.settings_support_logs_full),
                            fontSize = 14.sp,
                            color = kb.subtitle,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmSubmit() },
                    enabled = !state.isLoading,
                ) {
                    Text(stringResource(R.string.settings_support_send))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissSubmitConfirm) {
                    Text(stringResource(R.string.settings_common_cancel))
                }
            },
        )
    }
}

@Composable
private fun SupportInfoBottomSheet(
    sheetState: androidx.compose.material3.SheetState,
    onDismiss: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = kb.card,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.settings_support_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = kb.title,
            )
            Text(
                stringResource(R.string.settings_support_intro_full),
                fontSize = 15.sp,
                color = kb.subtitle,
                lineHeight = 22.sp,
            )
            InfoBullet(
                title = stringResource(R.string.settings_support_questions),
                body = stringResource(R.string.settings_support_questions_body),
            )
            InfoBullet(
                title = stringResource(R.string.settings_support_bugs),
                body = stringResource(R.string.settings_support_bugs_full),
            )
            InfoBullet(
                title = stringResource(R.string.settings_support_suggestions),
                body = stringResource(R.string.settings_support_suggestions_body),
            )
            InfoBullet(
                title = stringResource(R.string.settings_support_screenshots),
                body = stringResource(R.string.settings_support_screenshots_body),
            )
            InfoBullet(
                title = stringResource(R.string.settings_support_sending),
                body = stringResource(R.string.settings_support_sending_full),
            )
            Text(
                stringResource(R.string.settings_support_quota_note),
                fontSize = 13.sp,
                color = kb.subtitle,
                lineHeight = 18.sp,
            )
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.settings_support_got_it), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun InfoBullet(title: String, body: String) {
    val kb = MaterialTheme.kidBoxColors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = kb.title)
        Text(body, fontSize = 14.sp, color = kb.subtitle, lineHeight = 20.sp)
    }
}

@Composable
private fun SupportChatBubble(message: SupportMessage) {
    val isUser = message.role == ROLE_USER
    val maxUserWidth = LocalConfiguration.current.screenWidthDp.dp * 0.75f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = if (isUser) {
                Modifier.widthIn(max = maxUserWidth)
            } else {
                Modifier.fillMaxWidth()
            },
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        ) {
            if (isUser) {
                AIChatCopyableMessageContainer(
                    copyText = message.text,
                    modifier = Modifier.widthIn(max = maxUserWidth),
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = KidBoxUserBubbleOrange,
                        ),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (message.text.isNotBlank()) {
                                Text(
                                    message.text,
                                    color = Color.White,
                                )
                            }
                            if (message.imageUris.isNotEmpty()) {
                                if (message.text.isNotBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                }
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(message.imageUris, key = { it.toString() }) { uri ->
                                        AsyncImage(
                                            model = uri,
                                            contentDescription = stringResource(R.string.settings_support_image_attached),
                                            modifier = Modifier
                                                .size(72.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                AIChatCopyableMessageContainer(
                    copyText = message.text,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TypewriterClaudeMarkdownText(
                        text = message.text,
                        streamReveal = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SupportAttachmentPreviewTray(
    images: List<android.net.Uri>,
    backgroundColor: Color,
    onRemove: (android.net.Uri) -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(images, key = { it.toString() }) { uri ->
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(kb.rowBackground),
            ) {
                AsyncImage(
                    model = uri,
                    contentDescription = stringResource(R.string.settings_support_attachment_preview),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable { onRemove(uri) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.settings_family_remove),
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SupportInputBar(
    inputText: String,
    attachedImages: List<android.net.Uri>,
    isLoading: Boolean,
    ticketSent: Boolean,
    onInputChange: (String) -> Unit,
    onAttachClick: () -> Unit,
    onRemoveImage: (android.net.Uri) -> Unit,
    onSend: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val barBackground = kb.card
    val fieldBackground = kb.incomingBubble
    val fieldBorder = kb.divider
    val canSend = !isLoading && !ticketSent &&
        (inputText.isNotBlank() || attachedImages.isNotEmpty())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(barBackground)
            .windowInsetsPadding(WindowInsets.ime)
            .navigationBarsPadding(),
    ) {
        if (attachedImages.isNotEmpty()) {
            SupportAttachmentPreviewTray(
                images = attachedImages,
                backgroundColor = barBackground,
                onRemove = onRemoveImage,
            )
            HorizontalDivider(color = kb.divider)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconButton(
                onClick = onAttachClick,
                enabled = !isLoading && !ticketSent && attachedImages.size < MAX_IMAGES,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Icons.Default.AddCircle,
                    contentDescription = stringResource(R.string.settings_support_attach_photo),
                    tint = KidBoxUserBubbleOrange,
                    modifier = Modifier.size(32.dp),
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(fieldBackground)
                    .border(1.dp, fieldBorder, RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                BasicTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !ticketSent,
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        color = kb.title,
                    ),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = { if (canSend) onSend() },
                    ),
                    decorationBox = { innerTextField ->
                        Box {
                            if (inputText.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.settings_support_message_placeholder),
                                    color = kb.subtitle,
                                    fontSize = 16.sp,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            }

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(32.dp),
                    strokeWidth = 2.dp,
                    color = KidBoxUserBubbleOrange,
                )
            } else {
                IconButton(
                    onClick = onSend,
                    enabled = canSend,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        Icons.Default.ArrowCircleUp,
                        contentDescription = stringResource(R.string.settings_support_send),
                        tint = if (canSend) {
                            KidBoxUserBubbleOrange
                        } else {
                            kb.subtitle.copy(alpha = 0.45f)
                        },
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }
}
