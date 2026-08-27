@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.account

import com.nuvio.tv.ui.theme.NuvioTheme

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.AuthState
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

private val AuthGold = Color(0xFFD4A574)
private val AuthGoldBright = Color(0xFFE8C794)
private val AuthGoldDeep = Color(0xFFB8884E)
private val AuthTextPrimary = Color(0xFFF5F1E8)
private val AuthTextSecondary = Color(0xFF8A8580)
private val AuthPaneBackground = Color(0xFF131211).copy(alpha = 0.92f)
private val AuthPaneBorder = AuthGold.copy(alpha = 0.22f)
private val AuthSecondaryButtonBackground = Color.White.copy(alpha = 0.05f)
private val AuthSecondaryButtonBorder = AuthGold.copy(alpha = 0.28f)

private enum class AuthPanelMode {
    EMAIL,
    QR
}

@Composable
fun AuthQrSignInScreen(
    onBackPress: () -> Unit = {},
    onContinue: (() -> Unit)? = null,
    viewModel: AccountViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val fullAccount = uiState.authState as? AuthState.FullAccount
    val isSignedIn = fullAccount != null
    val isOnboardingMode = onContinue != null
    var authPanelMode by rememberSaveable { mutableStateOf(AuthPanelMode.EMAIL) }
    val showEmailPanel = viewModel.usesEmailPasswordLogin && authPanelMode == AuthPanelMode.EMAIL
    val showQrPanel = viewModel.usesQrLogin && authPanelMode == AuthPanelMode.QR
    val isApproved = remember(uiState.qrLoginStatus) {
        uiState.qrLoginStatus?.contains("approved", ignoreCase = true) == true
    }
    var onboardingTransitionHandled by remember(isOnboardingMode) { mutableStateOf(false) }
    var exitRequested by remember { mutableStateOf(false) }
    var showSignOutConfirmation by remember { mutableStateOf(false) }
    val loginFocusRequester = remember { FocusRequester() }

    fun leaveAuthScreen() {
        exitRequested = true
        viewModel.clearQrLoginSession()
        onBackPress()
    }

    fun continueFromAuthScreen() {
        if (onContinue != null && !isSignedIn) return
        exitRequested = true
        viewModel.clearQrLoginSession()
        if (onContinue != null) {
            onContinue()
        } else {
            onBackPress()
        }
    }

    BackHandler {
        leaveAuthScreen()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearQrLoginSession()
        }
    }

    LaunchedEffect(uiState.authState, isSignedIn, uiState.qrLoginCode, uiState.isLoading, uiState.error, exitRequested, showQrPanel) {
        if (
            showQrPanel &&
            !exitRequested &&
            uiState.authState !is AuthState.Loading &&
            !isSignedIn &&
            uiState.qrLoginCode.isNullOrBlank() &&
            uiState.error.isNullOrBlank() &&
            !uiState.isLoading
        ) {
            viewModel.startQrLogin()
        }
    }

    LaunchedEffect(isSignedIn, showQrPanel) {
        if (showQrPanel && isSignedIn && !uiState.qrLoginCode.isNullOrBlank()) {
            viewModel.clearQrLoginSession()
        }
    }

    LaunchedEffect(isApproved, uiState.isLoading, showQrPanel) {
        if (showQrPanel && isApproved && !uiState.isLoading) {
            viewModel.exchangeQrLogin()
        }
    }

    LaunchedEffect(isOnboardingMode, isSignedIn) {
        if (!isOnboardingMode || onboardingTransitionHandled) return@LaunchedEffect
        if (isSignedIn) {
            onboardingTransitionHandled = true
            exitRequested = true
            viewModel.clearQrLoginSession()
            onContinue.invoke()
        }
    }

    val nowMillis by produceState(initialValue = System.currentTimeMillis(), key1 = uiState.qrLoginCode) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val remainingMillis = uiState.qrLoginExpiresAtMillis?.let { (it - nowMillis).coerceAtLeast(0L) } ?: 0L

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .authGradientBackground()
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            AuthQrBrandPanel(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 56.dp, end = 56.dp),
                isSignedIn = isSignedIn,
                fullAccount = fullAccount,
                showEmailPanel = showEmailPanel
            )

            AuthQrLoginPane(
                modifier = Modifier
                    .width(460.dp)
                    .fillMaxHeight()
                    .background(AuthPaneBackground)
                    .drawBehind {
                        drawLine(
                            color = AuthPaneBorder,
                            start = Offset(0f, 0f),
                            end = Offset(0f, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    },
                uiState = uiState,
                isSignedIn = isSignedIn,
                isOnboardingMode = isOnboardingMode,
                showEmailPanel = showEmailPanel,
                showQrPanel = showQrPanel,
                canSwitchToQr = viewModel.usesQrLogin && !isSignedIn,
                canSwitchToEmail = viewModel.usesEmailPasswordLogin && !isSignedIn,
                remainingMillis = remainingMillis,
                onSignIn = viewModel::signIn,
                onSignUp = viewModel::signUp,
                onSwitchToQr = {
                    viewModel.clearQrLoginSession()
                    authPanelMode = AuthPanelMode.QR
                },
                onSwitchToEmail = {
                    viewModel.clearQrLoginSession()
                    authPanelMode = AuthPanelMode.EMAIL
                },
                onRefreshOrSignOut = {
                    if (isSignedIn) {
                        showSignOutConfirmation = true
                    } else {
                        viewModel.startQrLogin()
                    }
                },
                onBackOrContinue = {
                    if (isOnboardingMode) {
                        continueFromAuthScreen()
                    } else {
                        leaveAuthScreen()
                    }
                },
                initialFocusRequester = loginFocusRequester
            )
        }

    }

    LaunchedEffect(Unit) {
        loginFocusRequester.requestFocusAfterFrames(frames = 3)
    }

    if (showSignOutConfirmation) {
        AccountSignOutConfirmationDialog(
            onConfirm = {
                viewModel.signOut()
                showSignOutConfirmation = false
            },
            onDismiss = { showSignOutConfirmation = false }
        )
    }
}

@Composable
private fun AuthQrBrandPanel(
    modifier: Modifier,
    isSignedIn: Boolean,
    fullAccount: AuthState.FullAccount?,
    showEmailPanel: Boolean
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.app_logo_wordmark),
            contentDescription = stringResource(R.string.cd_nuvio),
            modifier = Modifier.height(60.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.auth_qr_tagline),
            modifier = Modifier.widthIn(max = 440.dp),
            style = MaterialTheme.typography.displayLarge.copy(
                color = AuthTextPrimary,
                fontSize = 40.sp,
                lineHeight = 45.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = if (isSignedIn) {
                stringResource(R.string.auth_qr_connected)
            } else if (showEmailPanel) {
                stringResource(R.string.auth_email_hint_tv)
            } else {
                stringResource(R.string.auth_qr_phone_hint)
            },
            modifier = Modifier.widthIn(max = 400.dp),
            style = MaterialTheme.typography.bodyLarge.copy(
                color = AuthTextSecondary,
                fontSize = 17.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Normal
            )
        )
        if (isSignedIn && fullAccount != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = fullAccount.email,
                style = MaterialTheme.typography.titleMedium,
                color = AuthGoldBright
            )
        }
    }
}

@Composable
private fun AuthQrLoginPane(
    modifier: Modifier,
    uiState: AccountUiState,
    isSignedIn: Boolean,
    isOnboardingMode: Boolean,
    showEmailPanel: Boolean,
    showQrPanel: Boolean,
    canSwitchToQr: Boolean,
    canSwitchToEmail: Boolean,
    remainingMillis: Long,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String) -> Unit,
    onSwitchToQr: () -> Unit,
    onSwitchToEmail: () -> Unit,
    onRefreshOrSignOut: () -> Unit,
    onBackOrContinue: () -> Unit,
    initialFocusRequester: FocusRequester
) {
    val focusEmail = showEmailPanel && !isSignedIn
    val focusMainAction = !focusEmail && !uiState.isLoading
    Column(
        modifier = modifier
            .padding(start = 48.dp, end = 48.dp, top = 48.dp, bottom = 36.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = stringResource(R.string.auth_qr_account_login),
            style = MaterialTheme.typography.headlineLarge.copy(
                color = AuthTextPrimary,
                fontSize = 30.sp,
                lineHeight = 33.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = if (isSignedIn) {
                stringResource(R.string.auth_qr_synced_data)
            } else if (showEmailPanel) {
                stringResource(R.string.auth_email_instruction)
            } else {
                stringResource(R.string.auth_qr_scan_instruction)
            },
            style = MaterialTheme.typography.bodyLarge.copy(
                color = AuthTextSecondary,
                fontSize = 15.sp,
                lineHeight = 21.sp
            ),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))

        if (isSignedIn && !isOnboardingMode) {
            AccountConnectedStatsStrip(
                stats = uiState.connectedStats,
                isLoading = uiState.isStatsLoading
            )
        } else if (isSignedIn && isOnboardingMode) {
            StatusPill(
                text = stringResource(R.string.auth_qr_finishing),
                containerColor = AuthSecondaryButtonBackground,
                contentColor = AuthTextSecondary
            )
        } else if (showEmailPanel) {
            AuthEmailLoginForm(
                uiState = uiState,
                onSignIn = onSignIn,
                onSignUp = onSignUp,
                initialFocusRequester = initialFocusRequester
            )
        } else if (showQrPanel) {
            AuthQrCodeBlock(uiState = uiState, remainingMillis = remainingMillis)
        }

        if (!isSignedIn && showEmailPanel && canSwitchToQr) {
            Spacer(modifier = Modifier.height(14.dp))
            AuthLinkButton(
                text = stringResource(R.string.auth_use_qr_code),
                onClick = onSwitchToQr
            )
        }
        if (!isSignedIn && showQrPanel && canSwitchToEmail) {
            Spacer(modifier = Modifier.height(14.dp))
            AuthLinkButton(
                text = stringResource(R.string.auth_use_email_password),
                onClick = onSwitchToEmail
            )
        }

        val showSecondaryAction = !isOnboardingMode || isSignedIn
        if (isSignedIn || showQrPanel || showSecondaryAction) {
            Spacer(modifier = Modifier.height(28.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSignedIn || showQrPanel) {
                    Button(
                        onClick = onRefreshOrSignOut,
                        enabled = !uiState.isLoading,
                        modifier = (if (focusMainAction) Modifier.focusRequester(initialFocusRequester) else Modifier)
                            .defaultMinSize(minHeight = 48.dp),
                        colors = authButtonColors(focusedContainer = AuthGoldBright),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        border = authButtonBorder()
                    ) {
                        Text(
                            when {
                                isSignedIn -> stringResource(R.string.account_sign_out)
                                uiState.isLoading -> stringResource(R.string.auth_qr_please_wait)
                                else -> stringResource(R.string.auth_qr_refresh)
                            }
                        )
                    }
                }
                if (showSecondaryAction) {
                    Button(
                        onClick = onBackOrContinue,
                        modifier = (if (!focusEmail && !focusMainAction) {
                            Modifier.focusRequester(initialFocusRequester)
                        } else {
                            Modifier
                        }).defaultMinSize(minHeight = 48.dp),
                        colors = authButtonColors(focusedContainer = AuthGoldBright),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        border = authButtonBorder()
                    ) {
                        Text(
                            when {
                                isOnboardingMode && isSignedIn -> stringResource(R.string.auth_qr_continue)
                                isOnboardingMode -> stringResource(R.string.auth_qr_back)
                                else -> stringResource(R.string.auth_qr_back)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthEmailLoginForm(
    uiState: AccountUiState,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String) -> Unit,
    initialFocusRequester: FocusRequester
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var isRegisterMode by rememberSaveable { mutableStateOf(false) }
  var localError by rememberSaveable { mutableStateOf<String?>(null) }

    val passwordMismatch = isRegisterMode &&
        confirmPassword.isNotBlank() &&
        password != confirmPassword
    val passwordTooShort = isRegisterMode && password.isNotBlank() && password.length < 8
    val canSubmit = email.isNotBlank() &&
        password.isNotBlank() &&
        !uiState.isLoading &&
        (!isRegisterMode || (confirmPassword.isNotBlank() && !passwordMismatch && !passwordTooShort))

    val submit: () -> Unit = {
        if (canSubmit) {
            localError = null
            if (isRegisterMode) {
                onSignUp(email.trim(), password)
            } else {
                onSignIn(email.trim(), password)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
    ) {
        if (isRegisterMode) {
            Text(
                text = stringResource(R.string.auth_email_register_instruction),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = AuthTextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        InputField(
            value = email,
            onValueChange = { email = it },
            placeholder = stringResource(R.string.auth_email_placeholder),
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
            modifier = Modifier.focusRequester(initialFocusRequester)
        )
        InputField(
            value = password,
            onValueChange = { password = it },
            placeholder = stringResource(R.string.auth_password_placeholder),
            keyboardType = KeyboardType.Password,
            isPassword = true,
            imeAction = if (isRegisterMode) ImeAction.Next else ImeAction.Done,
            onImeAction = { if (!isRegisterMode) submit() }
        )
        if (isRegisterMode) {
            InputField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                placeholder = stringResource(R.string.auth_password_confirm_placeholder),
                keyboardType = KeyboardType.Password,
                isPassword = true,
                imeAction = ImeAction.Done,
                onImeAction = submit
            )
        }
        Button(
            onClick = submit,
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.colors(
                containerColor = AuthGold,
                focusedContainerColor = AuthGoldBright,
                contentColor = Color(0xFF0A0A0A),
                focusedContentColor = Color(0xFF0A0A0A),
                disabledContainerColor = AuthGold.copy(alpha = 0.35f),
                disabledContentColor = AuthTextPrimary.copy(alpha = 0.58f)
            ),
            shape = ButtonDefaults.shape(RoundedCornerShape(16.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        uiState.isLoading && isRegisterMode -> stringResource(R.string.auth_email_signing_up)
                        uiState.isLoading -> stringResource(R.string.auth_email_signing_in)
                        isRegisterMode -> stringResource(R.string.auth_email_create_account)
                        else -> stringResource(R.string.auth_email_sign_in)
                    },
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        }
        AuthLinkButton(
            text = if (isRegisterMode) {
                stringResource(R.string.auth_switch_to_sign_in)
            } else {
                stringResource(R.string.auth_switch_to_register)
            },
            onClick = {
                isRegisterMode = !isRegisterMode
                confirmPassword = ""
                localError = null
            },
            enabled = !uiState.isLoading
        )
        AuthTermsAcknowledgement()
        when {
            passwordMismatch -> {
                StatusPill(
                    text = stringResource(R.string.auth_password_mismatch),
                    containerColor = Color(0x33C62828),
                    contentColor = Color(0xFFFF6E6E)
                )
            }
            passwordTooShort -> {
                StatusPill(
                    text = stringResource(R.string.account_error_password_too_short),
                    containerColor = Color(0x33C62828),
                    contentColor = Color(0xFFFF6E6E)
                )
            }
            localError != null -> {
                StatusPill(
                    text = localError!!,
                    containerColor = Color(0x33C62828),
                    contentColor = Color(0xFFFF6E6E)
                )
            }
            uiState.error?.takeIf { it.isNotBlank() } != null -> {
                StatusPill(
                    text = uiState.error!!,
                    containerColor = Color(0x33C62828),
                    contentColor = Color(0xFFFF6E6E)
                )
            }
        }
    }
}

@Composable
private fun AuthQrCodeBlock(
    uiState: AccountUiState,
    remainingMillis: Long
) {
    val qrBitmap = uiState.qrLoginBitmap
    if (qrBitmap != null) {
        Image(
            bitmap = qrBitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.cd_qr_login),
            modifier = Modifier
                .size(206.dp)
                .background(Color.White, RoundedCornerShape(8.dp))
                .padding(8.dp),
            contentScale = ContentScale.Fit
        )
    } else {
        Box(
            modifier = Modifier
                .size(206.dp)
                .background(AuthSecondaryButtonBackground, RoundedCornerShape(8.dp))
                .border(1.dp, AuthSecondaryButtonBorder, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (uiState.isLoading) stringResource(R.string.auth_qr_generating) else stringResource(R.string.auth_qr_unavailable),
                color = AuthTextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }

    Spacer(modifier = Modifier.height(18.dp))
    AuthTermsAcknowledgement()

    val qrLoginCode = uiState.qrLoginCode
    if (!qrLoginCode.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.auth_qr_code_display, qrLoginCode),
            style = MaterialTheme.typography.bodyMedium,
            color = AuthTextPrimary,
            fontWeight = FontWeight.Medium
        )
    }
    if (uiState.qrLoginExpiresAtMillis != null) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.auth_qr_expires, formatDuration(remainingMillis)),
            style = MaterialTheme.typography.bodySmall,
            color = AuthTextSecondary
        )
    }

    val statusText = uiState.error ?: uiState.qrLoginStatus
    if (!statusText.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(14.dp))
        if (uiState.error != null) {
            StatusPill(
                text = statusText,
                containerColor = Color(0x33C62828),
                contentColor = Color(0xFFFF6E6E)
            )
        } else {
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = AuthTextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AuthTermsAcknowledgement() {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.auth_qr_terms_prefix),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = AuthTextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.auth_qr_terms_link),
            modifier = Modifier.clickable {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://nuvio.tv/terms")))
            },
            style = MaterialTheme.typography.bodyMedium.copy(
                color = AuthTextPrimary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

@Composable
private fun StatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(NuvioTheme.spacing.hairline, NuvioTheme.colors.Border.copy(alpha = 0.35f), RoundedCornerShape(NuvioTheme.radii.md))
            .background(containerColor, RoundedCornerShape(NuvioTheme.radii.md))
            .padding(horizontal = NuvioTheme.spacing.md, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = contentColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.wrapContentHeight()
        )
    }
}

@Composable
private fun AccountConnectedStatsStrip(
    stats: AccountConnectedStats?,
    isLoading: Boolean
) {
    val values = if (isLoading) {
        listOf("...", "...")
    } else {
        listOf(
            (stats?.library ?: 0).toString(),
            (stats?.watchProgress ?: 0).toString()
        )
    }
    val labels = listOf(
        stringResource(R.string.account_stat_library),
        stringResource(R.string.account_stat_progress)
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(NuvioTheme.spacing.hairline)
                .background(NuvioTheme.colors.Border.copy(alpha = 0.8f))
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(values.size) { index ->
                AccountStatItem(
                    value = values[index],
                    label = labels[index],
                    modifier = Modifier.weight(1f)
                )
                if (index != values.lastIndex) {
                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .width(NuvioTheme.spacing.hairline)
                            .background(NuvioTheme.colors.Border.copy(alpha = 0.75f))
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(NuvioTheme.spacing.hairline)
                .background(NuvioTheme.colors.Border.copy(alpha = 0.8f))
        )
    }
}

@Composable
private fun AccountStatItem(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = NuvioTheme.colors.TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(NuvioTheme.spacing.xxs))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = NuvioTheme.colors.TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AuthLinkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp),
        colors = authButtonColors(focusedContainer = AuthGoldBright),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        border = authButtonBorder(focusedBorderColor = AuthGold),
        shape = ButtonDefaults.shape(RoundedCornerShape(12.dp))
    ) {
        Text(
            text = text,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun authButtonColors(focusedContainer: Color) = ButtonDefaults.colors(
    containerColor = AuthSecondaryButtonBackground,
    focusedContainerColor = focusedContainer,
    contentColor = AuthTextPrimary,
    focusedContentColor = Color(0xFF0A0A0A),
    disabledContainerColor = AuthSecondaryButtonBackground.copy(alpha = 0.45f),
    disabledContentColor = AuthTextPrimary.copy(alpha = 0.58f)
)

@Composable
private fun authButtonBorder(focusedBorderColor: Color = AuthGoldBright) = ButtonDefaults.border(
    border = androidx.tv.material3.Border(
        border = androidx.compose.foundation.BorderStroke(1.dp, AuthSecondaryButtonBorder),
        shape = RoundedCornerShape(16.dp)
    ),
    focusedBorder = androidx.tv.material3.Border(
        border = androidx.compose.foundation.BorderStroke(2.dp, focusedBorderColor),
        shape = RoundedCornerShape(16.dp)
    )
)

private fun Modifier.authGradientBackground(): Modifier = drawWithCache {
    val angleRadians = 122.0 * PI / 180.0
    val directionX = sin(angleRadians).toFloat()
    val directionY = (-cos(angleRadians)).toFloat()
    val halfLength = (abs(size.width * directionX) + abs(size.height * directionY)) / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    val start = Offset(
        x = center.x - directionX * halfLength,
        y = center.y - directionY * halfLength
    )
    val end = Offset(
        x = center.x + directionX * halfLength,
        y = center.y + directionY * halfLength
    )
    val brush = Brush.linearGradient(
        colorStops = arrayOf(
            0f to Color(0xFF181714),
            0.18f to Color(0xFF131211),
            0.34f to Color(0xFF0F0E0C),
            0.50f to Color(0xFF0A0A0A),
            0.68f to Color(0xFF070605),
            0.84f to Color(0xFF040302),
            1f to Color.Black
        ),
        start = start,
        end = end
    )
    onDrawBehind {
        drawRect(brush = brush)
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
