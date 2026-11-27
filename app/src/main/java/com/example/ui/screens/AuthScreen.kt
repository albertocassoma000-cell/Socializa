package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.AuthModeTab
import com.example.data.model.OtpAuthStep
import com.example.ui.components.rememberClickHaptic
import com.example.ui.theme.CrimsonAngola
import com.example.ui.theme.LocalMeuPaisColors
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.OrangeVibrant
import java.util.Locale

@Composable
fun AuthScreen(
    isLoading: Boolean,
    authMode: AuthModeTab = AuthModeTab.SIGN_IN,
    otpStep: OtpAuthStep = OtpAuthStep.NONE,
    pendingAuthEmail: String = "",
    otpRemainingSeconds: Int = 0,
    otpResendCooldownSeconds: Int = 0,
    authErrorMessage: String? = null,
    onSelectAuthMode: (AuthModeTab) -> Unit = {},
    onGoogleSignIn: () -> Unit,
    onEmailSignIn: (email: String, password: String) -> Unit = { _, _ -> },
    onStartRegister: (fullName: String, email: String, password: String) -> Unit = { _, _, _ -> },
    onConfirmRegisterOtp: (otp: String) -> Unit = {},
    onResendRegisterOtp: () -> Unit = {},
    onStartPasswordReset: (email: String) -> Unit = {},
    onConfirmPasswordReset: (otp: String, newPassword: String) -> Unit = { _, _ -> },
    onResendResetOtp: () -> Unit = {},
    onCancelOtp: () -> Unit = {}
) {
    val colors = LocalMeuPaisColors.current
    val clickHaptic = rememberClickHaptic()

    var fullName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    var otpInput by rememberSaveable(otpStep) { mutableStateOf("") }
    var newResetPassword by rememberSaveable(otpStep) { mutableStateOf("") }
    var newPasswordVisible by rememberSaveable(otpStep) { mutableStateOf(false) }

    if (otpStep != OtpAuthStep.NONE) {
        BackHandler(enabled = !isLoading) {
            onCancelOtp()
        }
    }

    val googleInteraction = remember { MutableInteractionSource() }
    val googlePressed by googleInteraction.collectIsPressedAsState()
    val googleScale by animateFloatAsState(
        targetValue = if (googlePressed) 0.98f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "googleAuthScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Ícone circular nativo Socializa (sempre redondo)
            Image(
                painter = painterResource(id = R.drawable.ic_socializa_round_badge),
                contentDescription = "Socializa",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, OrangeVibrant.copy(alpha = 0.55f), CircleShape)
                    .testTag("socializa_auth_logo")
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Socializa",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (otpStep == OtpAuthStep.NONE) {
                    "Conecta-te com pessoas, vídeos e comunidades."
                } else {
                    "Confirma o código enviado para o teu e-mail."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            AnimatedContent(
                targetState = otpStep,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "authStepTransition"
            ) { currentOtpStep ->
                if (currentOtpStep == OtpAuthStep.NONE) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Barra de navegação nativa entre Entrar / Criar conta / Recuperar
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(0.8.dp, colors.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                AuthModeTab.entries.forEach { tab ->
                                    val selected = authMode == tab
                                    Surface(
                                        color = if (selected) OrangeVibrant else Color.Transparent,
                                        shape = RoundedCornerShape(9.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 44.dp)
                                            .clip(RoundedCornerShape(9.dp))
                                            .clickable(enabled = !isLoading) {
                                                clickHaptic()
                                                onSelectAuthMode(tab)
                                            }
                                            .testTag("auth_tab_${tab.name}")
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp)
                                        ) {
                                            Text(
                                                text = tab.label,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (!authErrorMessage.isNullOrBlank()) {
                            AuthErrorBanner(message = authErrorMessage)
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        if (authMode == AuthModeTab.REGISTER) {
                            OutlinedTextField(
                                value = fullName,
                                onValueChange = { fullName = it },
                                label = { Text("Nome completo") },
                                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                shape = RoundedCornerShape(12.dp),
                                colors = authFieldColors(colors.cardBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_fullname_input")
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("E-mail") },
                            leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = if (authMode == AuthModeTab.FORGOT_PASSWORD) ImeAction.Done else ImeAction.Next
                            ),
                            shape = RoundedCornerShape(12.dp),
                            colors = authFieldColors(colors.cardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_email_input")
                        )

                        if (authMode != AuthModeTab.FORGOT_PASSWORD) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Senha") },
                                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                            contentDescription = if (passwordVisible) "Ocultar senha" else "Mostrar senha"
                                        )
                                    }
                                },
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                shape = RoundedCornerShape(12.dp),
                                colors = authFieldColors(colors.cardBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_password_input")
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                clickHaptic()
                                when (authMode) {
                                    AuthModeTab.SIGN_IN -> onEmailSignIn(email, password)
                                    AuthModeTab.REGISTER -> onStartRegister(fullName, email, password)
                                    AuthModeTab.FORGOT_PASSWORD -> onStartPasswordReset(email)
                                }
                            },
                            enabled = !isLoading,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = OrangeVibrant,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("auth_submit_button")
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.4.dp,
                                    color = Color.White
                                )
                            } else {
                                Text(
                                    text = when (authMode) {
                                        AuthModeTab.SIGN_IN -> "Entrar"
                                        AuthModeTab.REGISTER -> "Receber código no e-mail"
                                        AuthModeTab.FORGOT_PASSWORD -> "Enviar código de recuperação"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (authMode == AuthModeTab.SIGN_IN) {
                            TextButton(
                                onClick = {
                                    clickHaptic()
                                    onSelectAuthMode(AuthModeTab.FORGOT_PASSWORD)
                                },
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .heightIn(min = 48.dp)
                            ) {
                                Text(
                                    text = "Esqueceste a senha? Recuperar por código",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OrangeVibrant,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = colors.divider, thickness = 0.8.dp)
                            Text(
                                text = "ou",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f), color = colors.divider, thickness = 0.8.dp)
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                clickHaptic()
                                onGoogleSignIn()
                            },
                            enabled = !isLoading,
                            interactionSource = googleInteraction,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .scale(googleScale)
                                .testTag("google_signin_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(0.8.dp, Color(0xFFE2E8F0)),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF1F1F1F)
                            )
                        ) {
                            GoogleColorEmblem(size = 20.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Continuar com Google",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    // Passo de confirmação de código OTP de 6 dígitos (Registo ou Recuperação sem links)
                    val isReset = currentOtpStep == OtpAuthStep.RESET_CODE
                    val isExpired = otpRemainingSeconds <= 0
                    val minutes = otpRemainingSeconds / 60
                    val secs = otpRemainingSeconds % 60
                    val timerFormatted = String.format(Locale.US, "%02d:%02d", minutes, secs)

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(0.8.dp, colors.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("otp_verification_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        clickHaptic()
                                        onCancelOtp()
                                    },
                                    enabled = !isLoading,
                                    modifier = Modifier.testTag("otp_cancel_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Voltar"
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isReset) "Recuperar senha" else "Confirmar e-mail",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = pendingAuthEmail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Surface(
                                color = if (isExpired) CrimsonAngola.copy(alpha = 0.14f) else OrangeVibrant.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("otp_timer_badge")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (isExpired) Icons.Filled.Schedule else Icons.Filled.MarkEmailRead,
                                        contentDescription = null,
                                        tint = if (isExpired) CrimsonAngola else OrangeVibrant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isExpired) {
                                            "Código expirado (5 minutos terminaram)"
                                        } else {
                                            "Código válido por $timerFormatted"
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isExpired) CrimsonAngola else OrangeVibrant,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            if (!authErrorMessage.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                AuthErrorBanner(message = authErrorMessage)
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            OutlinedTextField(
                                value = otpInput,
                                onValueChange = { raw ->
                                    otpInput = raw.filter { it.isDigit() }.take(6)
                                },
                                label = { Text("Código de 6 dígitos") },
                                placeholder = { Text("000000") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.NumberPassword,
                                    imeAction = if (isReset) ImeAction.Next else ImeAction.Done
                                ),
                                shape = RoundedCornerShape(12.dp),
                                colors = authFieldColors(colors.cardBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("otp_code_input")
                            )

                            if (isReset) {
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = newResetPassword,
                                    onValueChange = { newResetPassword = it },
                                    label = { Text("Nova senha (mín. 6 caracteres)") },
                                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                                    trailingIcon = {
                                        IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                                            Icon(
                                                imageVector = if (newPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                contentDescription = if (newPasswordVisible) "Ocultar senha" else "Mostrar senha"
                                            )
                                        }
                                    },
                                    visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Done
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = authFieldColors(colors.cardBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("otp_new_password_input")
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    clickHaptic()
                                    if (isReset) {
                                        onConfirmPasswordReset(otpInput, newResetPassword)
                                    } else {
                                        onConfirmRegisterOtp(otpInput)
                                    }
                                },
                                enabled = !isLoading && otpInput.length == 6 && !isExpired,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = OrangeVibrant,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("confirm_otp_button")
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.4.dp,
                                        color = Color.White
                                    )
                                } else {
                                    Text(
                                        text = if (isReset) "Alterar senha" else "Confirmar e entrar",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            val canResend = !isLoading && otpResendCooldownSeconds <= 0
                            OutlinedButton(
                                onClick = {
                                    clickHaptic()
                                    if (isReset) onResendResetOtp() else onResendRegisterOtp()
                                },
                                enabled = canResend,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(0.8.dp, colors.cardBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("resend_otp_button")
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (otpResendCooldownSeconds > 0) {
                                        "Reenviar código em ${otpResendCooldownSeconds}s"
                                    } else {
                                        "Reenviar código"
                                    },
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "Ao continuar, aceitas os Termos de Uso e a Política de Privacidade do Socializa.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun AuthErrorBanner(message: String) {
    Surface(
        color = CrimsonAngola.copy(alpha = 0.14f),
        border = BorderStroke(0.8.dp, CrimsonAngola.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("auth_error_banner")
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun authFieldColors(borderColor: Color) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = OrangeVibrant,
    unfocusedBorderColor = borderColor,
    focusedLabelColor = OrangeVibrant,
    cursorColor = OrangeVibrant
)

@Composable
private fun GoogleColorEmblem(size: Dp = 22.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val strokeWidth = w * 0.21f
        val inset = strokeWidth * 0.58f
        val arcSize = Size(w - inset * 2f, h - inset * 2f)
        val topLeft = Offset(inset, inset)
        val cx = w * 0.5f
        val cy = h * 0.5f

        drawArc(
            color = Color(0xFFEA4335),
            startAngle = -152f,
            sweepAngle = 112f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 148f,
            sweepAngle = 62f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 36f,
            sweepAngle = 114f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = -5f,
            sweepAngle = 44f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(cx, cy),
            end = Offset(w - inset * 0.35f, cy),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Butt
        )
    }
}
