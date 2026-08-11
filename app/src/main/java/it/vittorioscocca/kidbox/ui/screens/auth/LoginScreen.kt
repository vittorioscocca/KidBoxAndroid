package it.vittorioscocca.kidbox.ui.screens.auth

import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

private val BackgroundColor = Color(0xFFF2F0EB)
private val BlackButton = Color(0xFF1A1A1A)
private val OrangeAccent = Color(0xFFFF6B00)

private enum class SocialProvider { APPLE, GOOGLE, FACEBOOK }

/** Layout login adattato a larghezza schermo e dimensione testo di sistema. */
private data class LoginScreenMetrics(
    val horizontalPadding: Dp,
    val buttonHeight: Dp,
    val buttonContentPaddingHorizontal: Dp,
    val labelFontSize: TextUnit,
    val iconSize: Dp,
    val iconGap: Dp,
    val compactLabels: Boolean,
)

@Composable
private fun rememberLoginScreenMetrics(): LoginScreenMetrics {
    val widthDp = LocalConfiguration.current.screenWidthDp
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 2f)
    return remember(widthDp, fontScale) {
        // stress > 1 → schermo stretto e/o testo di sistema ingrandito (tipico su A12).
        val stress = fontScale * (360f / widthDp.coerceAtLeast(320).toFloat())
        val compactLabels = stress >= 1.05f || widthDp < 360
        val tight = stress >= 1.22f || widthDp < 340
        val labelSp = when {
            tight -> 12.sp
            compactLabels -> 13.sp
            widthDp < 400 -> 14.sp
            else -> 16.sp
        }
        LoginScreenMetrics(
            horizontalPadding = when {
                widthDp < 340 -> 16.dp
                widthDp < 360 -> 20.dp
                else -> 28.dp
            },
            buttonHeight = if (tight) 58.dp else 56.dp,
            buttonContentPaddingHorizontal = when {
                tight -> 10.dp
                compactLabels -> 12.dp
                else -> 16.dp
            },
            labelFontSize = labelSp,
            iconSize = if (tight) 20.dp else 22.dp,
            iconGap = if (tight) 8.dp else 10.dp,
            compactLabels = compactLabels,
        )
    }
}

// In forma compatta resta il solo nome del provider: è un nome proprio, uguale
// in tutte le lingue, e su schermi stretti la frase intera non ci sta.
@Composable
private fun socialLoginLabel(provider: SocialProvider, compact: Boolean): String = when (provider) {
    SocialProvider.APPLE -> if (compact) "Apple" else stringResource(R.string.login_continue_apple)
    SocialProvider.GOOGLE -> if (compact) "Google" else stringResource(R.string.login_continue_google)
    SocialProvider.FACEBOOK -> if (compact) "Facebook" else stringResource(R.string.login_continue_facebook)
}

@Composable
private fun emailLoginLabel(compact: Boolean): String =
    if (compact) stringResource(R.string.login_field_email) else stringResource(R.string.login_continue_email)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: LoginViewModel = hiltViewModel(),
    onLoginSuccess: (hasFamily: Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val activity = context as ComponentActivity

    val authCheckState by viewModel.authCheckState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val resetSent by viewModel.resetPasswordSent.collectAsStateWithLifecycle()
    val registrationPending by viewModel.registrationPendingVerification.collectAsStateWithLifecycle()

    var showEmailSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val uriHandler = LocalUriHandler.current
    val metrics = rememberLoginScreenMetrics()

    LaunchedEffect(authCheckState) {
        when (val state = authCheckState) {
            is LoginViewModel.AuthCheckState.Authenticated -> {
                delay(500)
                onLoginSuccess(state.hasFamily)
            }
            else -> Unit
        }
    }

    when (authCheckState) {
        is LoginViewModel.AuthCheckState.NotAuthenticated -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BackgroundColor),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = metrics.horizontalPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(96.dp))

            // Logo
            Image(
                painter = painterResource(id = R.drawable.kidbox_symbol_orange),
                contentDescription = stringResource(R.string.login_logo_desc),
                modifier = Modifier.size(80.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Titolo
            Text(
                text = "KidBox",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = BlackButton,
                letterSpacing = 0.sp,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Sottotitolo
            Text(
                text = stringResource(R.string.onboarding_slide1_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = BlackButton,
                textAlign = TextAlign.Center,
                lineHeight = 28.sp,
            )

            Spacer(modifier = Modifier.height(52.dp))

            // Bottoni neri (icone allineate a LoginView iOS)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SocialLoginButton(
                    label = socialLoginLabel(SocialProvider.APPLE, metrics.compactLabels),
                    enabled = !isBusy,
                    metrics = metrics,
                    onClick = { viewModel.signInApple(activity) },
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_apple),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(metrics.iconSize - 4.dp),
                    )
                }
                SocialLoginButton(
                    label = socialLoginLabel(SocialProvider.GOOGLE, metrics.compactLabels),
                    enabled = !isBusy,
                    metrics = metrics,
                    onClick = { viewModel.signInGoogle(activity) },
                ) {
                    GoogleLoginIcon(iconSize = metrics.iconSize)
                }
                SocialLoginButton(
                    label = socialLoginLabel(SocialProvider.FACEBOOK, metrics.compactLabels),
                    enabled = !isBusy,
                    metrics = metrics,
                    onClick = { viewModel.signInFacebook(activity) },
                ) {
                    FacebookLoginIcon(iconSize = metrics.iconSize)
                }
            }

            // Separatore con "o"
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                HorizontalDivider(color = Color(0xFFCCCCCC))
                Box(
                    modifier = Modifier
                        .background(BackgroundColor)
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        "o",
                        color = Color(0xFFAAAAAA),
                        fontSize = 14.sp,
                    )
                }
            }

            // Email outline
            Button(
                onClick = {
                    viewModel.clearError()
                    showEmailSheet = true
                },
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(metrics.buttonHeight)
                    .border(
                        width = 1.dp,
                        color = Color(0xFFCCCCCC),
                        shape = RoundedCornerShape(28.dp),
                    ),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BackgroundColor,
                    contentColor = BlackButton,
                    disabledContainerColor = BackgroundColor,
                    disabledContentColor = BlackButton.copy(alpha = 0.5f),
                ),
            ) {
                Text(
                    text = emailLoginLabel(metrics.compactLabels),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = metrics.labelFontSize,
                    maxLines = 1,
                )
            }

            errorMessage?.takeIf { it.isNotBlank() }?.let { err ->
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            if (registrationPending) {
                Text(
                    text = stringResource(R.string.login_registration_pending),
                    color = Color(0xFF666666),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            LoginTermsFooter(
                onTermsClick = { uriHandler.openUri("https://kidbox-landing.web.app/terms.html") },
                onPrivacyClick = { uriHandler.openUri("https://kidbox-landing.web.app/privacy.html") },
            )

            Spacer(modifier = Modifier.height(40.dp))
                }

                // Loading overlay
                if (isBusy) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = OrangeAccent)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.login_in_progress),
                                color = BlackButton,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            if (showEmailSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showEmailSheet = false },
                    sheetState = sheetState,
                ) {
                    EmailAuthSheetContent(
                        onDismiss = { showEmailSheet = false },
                        onSignIn = { email, pwd -> viewModel.signInEmail(email, pwd) },
                        onRegister = { email, pwd -> viewModel.registerEmail(email, pwd) },
                        onResetPassword = { email -> viewModel.resetPassword(email) },
                        resetSent = resetSent,
                        isBusy = isBusy,
                        errorMessage = errorMessage,
                    )
                }
            }
        }

        is LoginViewModel.AuthCheckState.Checking,
        is LoginViewModel.AuthCheckState.Authenticated,
        -> {
            val kb = MaterialTheme.kidBoxColors
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(kb.background),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = OrangeAccent,
                    trackColor = kb.divider,
                )
            }
        }
    }
}

@Composable
private fun SocialLoginButton(
    label: String,
    enabled: Boolean,
    metrics: LoginScreenMetrics,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.buttonHeight),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = BlackButton,
            contentColor = Color.White,
            disabledContainerColor = BlackButton.copy(alpha = 0.5f),
            disabledContentColor = Color.White.copy(alpha = 0.5f),
        ),
        contentPadding = PaddingValues(
            horizontal = metrics.buttonContentPaddingHorizontal,
            vertical = 10.dp,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier.size(metrics.iconSize),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
            Spacer(modifier = Modifier.size(metrics.iconGap))
            Text(
                text = label,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.labelFontSize,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** Cerchio bianco + G blu — come `GoogleIcon` su iOS. */
@Composable
private fun GoogleLoginIcon(iconSize: Dp) {
    val letterSp = (iconSize.value * 0.64f).sp
    Box(
        modifier = Modifier
            .size(iconSize)
            .background(Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "G",
            color = Color(0xFF4285F4),
            fontSize = letterSp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Cerchio blu + f bianca — come `FacebookIcon` su iOS (tema chiaro). */
@Composable
private fun FacebookLoginIcon(iconSize: Dp) {
    val letterSp = (iconSize.value * 0.68f).sp
    Box(
        modifier = Modifier
            .size(iconSize)
            .background(Color(0xFF3B5A99), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "f",
            color = Color.White,
            fontSize = letterSp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private const val TERMS_TAG = "terms"
private const val PRIVACY_TAG = "privacy"

@Composable
private fun LoginTermsFooter(
    onTermsClick: () -> Unit,
    onPrivacyClick: () -> Unit,
) {
    // La frase è una sola risorsa con due segnaposto, non cinque frammenti
    // concatenati: l'ordine delle parole cambia da lingua a lingua, e concatenare
    // pezzi tradotti separatamente produce frasi sgrammaticate. I due link si
    // ritrovano per posizione dentro la frase già composta.
    val termsLabel = stringResource(R.string.login_terms_link)
    val privacyLabel = stringResource(R.string.login_privacy_link)
    val sentence = stringResource(R.string.login_terms_footer, termsLabel, privacyLabel)

    val footerText = buildAnnotatedString {
        append(sentence)
        listOf(TERMS_TAG to termsLabel, PRIVACY_TAG to privacyLabel).forEach { (tag, label) ->
            val start = sentence.indexOf(label)
            if (start < 0) return@forEach
            val end = start + label.length
            addStyle(SpanStyle(color = OrangeAccent), start, end)
            addStringAnnotation(tag = tag, annotation = tag, start = start, end = end)
        }
    }
    ClickableText(
        text = footerText,
        style = MaterialTheme.typography.bodySmall.copy(
            fontSize = 12.sp,
            color = Color(0xFF888888),
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        onClick = { offset ->
            footerText.getStringAnnotations(TERMS_TAG, offset, offset).firstOrNull()?.let {
                onTermsClick()
                return@ClickableText
            }
            footerText.getStringAnnotations(PRIVACY_TAG, offset, offset).firstOrNull()?.let {
                onPrivacyClick()
            }
        },
    )
}

@Composable
private fun EmailAuthSheetContent(
    onDismiss: () -> Unit,
    onSignIn: (String, String) -> Unit,
    onRegister: (String, String) -> Unit,
    onResetPassword: (String) -> Unit,
    resetSent: Boolean,
    isBusy: Boolean,
    errorMessage: String?,
) {
    var isRegistering by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPwd by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val emailOk = email.contains('@') && email.contains('.')
    val formOk = emailOk && password.length >= 6 &&
            (!isRegistering || password == confirmPwd)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp)
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            stringResource(if (isRegistering) R.string.login_sheet_title_register else R.string.login_sheet_title_signin),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = BlackButton,
        )
        Text(
            stringResource(
                if (isRegistering) R.string.login_sheet_subtitle_register
                else R.string.login_sheet_subtitle_signin
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF888888),
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.login_field_email)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = OrangeAccent,
                focusedLabelColor = OrangeAccent,
                cursorColor = OrangeAccent,
            ),
        )
        Spacer(modifier = Modifier.height(14.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.login_field_password)) },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = null,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = OrangeAccent,
                focusedLabelColor = OrangeAccent,
                cursorColor = OrangeAccent,
            ),
        )
        if (isRegistering) {
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedTextField(
                value = confirmPwd,
                onValueChange = { confirmPwd = it },
                label = { Text(stringResource(R.string.login_field_confirm_password)) },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OrangeAccent,
                    focusedLabelColor = OrangeAccent,
                    cursorColor = OrangeAccent,
                ),
            )
            if (confirmPwd.isNotEmpty() && password != confirmPwd) {
                Text(
                    stringResource(R.string.login_passwords_mismatch),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        errorMessage?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (resetSent) {
            Text(
                stringResource(R.string.login_reset_email_sent),
                color = OrangeAccent,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = {
                if (isRegistering) onRegister(email, password) else onSignIn(email, password)
            },
            enabled = formOk && !isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BlackButton,
                contentColor = Color.White,
            ),
        ) {
            Text(
                stringResource(if (isRegistering) R.string.login_cta_register else R.string.login_cta_signin),
                fontWeight = FontWeight.SemiBold,
            )
        }

        TextButton(
            onClick = { isRegistering = !isRegistering },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(if (isRegistering) R.string.login_toggle_to_signin else R.string.login_toggle_to_register),
                color = OrangeAccent,
            )
        }
        TextButton(
            onClick = { if (emailOk) onResetPassword(email) },
            enabled = emailOk && !isBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.login_forgot_password), color = OrangeAccent)
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.login_close), color = Color(0xFF888888))
        }
    }
}