package com.example.ui.screens.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.auth.OfficialStudyPrograms
import com.example.ui.components.AzrilBrandLogo
import com.example.ui.components.AzrilCard
import com.example.ui.components.AzrilMintCard
import com.example.ui.components.AzrilStatusBadge
import com.example.ui.model.ToastType
import com.example.ui.theme.BorderMint
import com.example.ui.theme.DarkCardBg
import com.example.ui.theme.DarkCardElevated
import com.example.ui.theme.DarkObsidian
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSubtle
import com.example.ui.theme.InkText
import com.example.ui.theme.LightGreenSecondary
import com.example.ui.theme.LimeDeep
import com.example.ui.theme.LimeInk
import com.example.ui.theme.LimePrimary
import com.example.ui.theme.OnDarkMuted
import com.example.ui.theme.PureWhite
import com.example.ui.theme.Rose50
import com.example.ui.theme.Rose600
import com.example.ui.theme.Rose700
import com.example.ui.theme.SoftMintBg

@Composable
fun LoginScreen(
    rememberedIdentifier: String,
    rememberMeDefault: Boolean,
    authInProgress: Boolean,
    authErrorMessage: String?,
    isSupabaseConfigured: Boolean,
    onLogin: (identifier: String, password: String, rememberMe: Boolean) -> Unit,
    onRegisterStudent: (
        fullName: String,
        nim: String,
        emailOrUsername: String,
        password: String,
        confirmPassword: String,
        studyProgramCode: String,
        rememberMe: Boolean
    ) -> Unit,
    onClearAuthError: () -> Unit,
    onEnterAdminPreview: () -> Unit,
    onNotify: (String, ToastType) -> Unit
) {
    var isRegisterMode by rememberSaveable { mutableStateOf(false) }

    // Login state
    var loginIdentifier by rememberSaveable { mutableStateOf(rememberedIdentifier) }
    var loginPassword by rememberSaveable { mutableStateOf("") }
    var loginPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var rememberMe by rememberSaveable { mutableStateOf(rememberMeDefault) }
    var loginIdError by rememberSaveable { mutableStateOf<String?>(null) }
    var loginPwError by rememberSaveable { mutableStateOf<String?>(null) }

    // Registration state (first-time student signup)
    var regFullName by rememberSaveable { mutableStateOf("") }
    var regNim by rememberSaveable { mutableStateOf("") }
    var regEmailOrUsername by rememberSaveable { mutableStateOf("") }
    var regStudyProgram by rememberSaveable { mutableStateOf("") }
    var regPassword by rememberSaveable { mutableStateOf("") }
    var regConfirmPassword by rememberSaveable { mutableStateOf("") }
    var regPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var regConfirmVisible by rememberSaveable { mutableStateOf(false) }
    var regError by rememberSaveable { mutableStateOf<String?>(null) }

    var showForgotDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(rememberedIdentifier) {
        if (rememberedIdentifier.isNotBlank() && loginIdentifier.isBlank()) {
            loginIdentifier = rememberedIdentifier
        }
    }

    fun submitLogin() {
        onClearAuthError()
        val cleanId = loginIdentifier.trim()
        loginIdError = if (cleanId.isEmpty()) "Username atau email wajib diisi." else null
        loginPwError = if (loginPassword.isEmpty()) "Kata sandi wajib diisi." else null
        if (loginIdError == null && loginPwError == null) {
            onLogin(cleanId, loginPassword, rememberMe)
        }
    }

    fun submitRegistration() {
        onClearAuthError()
        regError = when {
            regFullName.trim().length < 3 -> "Nama lengkap wajib diisi (minimal 3 karakter)."
            regNim.trim().length < 5 -> "NIM wajib diisi (minimal 5 karakter)."
            regEmailOrUsername.trim().length < 3 -> "Email atau username wajib diisi (minimal 3 karakter)."
            regEmailOrUsername.trim().contains(" ") -> "Email atau username tidak boleh mengandung spasi."
            regStudyProgram.isBlank() -> "Pilih program studi terlebih dahulu (HES, PJKR, atau FARMASI)."
            regPassword.length < 8 -> "Kata sandi minimal 8 karakter."
            regPassword != regConfirmPassword -> "Konfirmasi kata sandi tidak cocok."
            else -> null
        }
        if (regError == null) {
            onRegisterStudent(
                regFullName.trim(),
                regNim.trim(),
                regEmailOrUsername.trim(),
                regPassword,
                regConfirmPassword,
                regStudyProgram,
                rememberMe
            )
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(SoftMintBg)
    ) {
        val isWide = maxWidth >= 840.dp

        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Editorial Dark Showcase Panel
                Surface(
                    modifier = Modifier
                        .weight(1.05f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(32.dp),
                    color = DarkCardBg,
                    border = BorderStroke(1.dp, LimePrimary.copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(36.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        AzrilBrandLogo(darkSurface = true, showTagline = true, iconSize = 46.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            Image(
                                painter = painterResource(id = R.drawable.img_lime_login_banner),
                                contentDescription = stringResource(id = R.string.cd_banner_illustration),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .clip(RoundedCornerShape(24.dp)),
                                contentScale = ContentScale.Crop
                            )
                            AzrilStatusBadge(
                                text = "HES • PJKR • FARMASI",
                                containerColor = LimePrimary,
                                contentColor = DarkCardBg
                            )
                            Text(
                                text = "Manajemen Perkuliahan, Absensi, Ranking, & AI dalam Satu Ruang Modern",
                                style = MaterialTheme.typography.headlineLarge,
                                color = PureWhite
                            )
                            Text(
                                text = "Daftar satu kali dengan Nama Lengkap, NIM, dan Program Studi. Login berikutnya cukup menggunakan username/email dan kata sandi.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OnDarkMuted
                            )
                        }
                        Surface(
                            color = DarkCardElevated,
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, LimePrimary.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(LimePrimary.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.Security, contentDescription = null, tint = LimePrimary, modifier = Modifier.size(18.dp))
                                }
                                Text(
                                    text = "Autentikasi aman tanpa menyimpan kata sandi teks biasa.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnDarkMuted
                                )
                            }
                        }
                    }
                }

                // Right Auth Column
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AuthCardStack(
                        isRegisterMode = isRegisterMode,
                        onToggleMode = {
                            isRegisterMode = it
                            onClearAuthError()
                            regError = null
                            loginIdError = null
                            loginPwError = null
                        },
                        isSupabaseConfigured = isSupabaseConfigured,
                        authInProgress = authInProgress,
                        authErrorMessage = authErrorMessage,
                        loginIdentifier = loginIdentifier,
                        onLoginIdentifierChange = { loginIdentifier = it; loginIdError = null; onClearAuthError() },
                        loginIdError = loginIdError,
                        loginPassword = loginPassword,
                        onLoginPasswordChange = { loginPassword = it; loginPwError = null; onClearAuthError() },
                        loginPwError = loginPwError,
                        loginPasswordVisible = loginPasswordVisible,
                        onToggleLoginPassword = { loginPasswordVisible = !loginPasswordVisible },
                        rememberMe = rememberMe,
                        onRememberMeChange = { rememberMe = it },
                        onSubmitLogin = ::submitLogin,
                        onForgotPassword = { showForgotDialog = true },
                        regFullName = regFullName,
                        onRegFullNameChange = { regFullName = it; regError = null; onClearAuthError() },
                        regNim = regNim,
                        onRegNimChange = { regNim = it; regError = null; onClearAuthError() },
                        regEmailOrUsername = regEmailOrUsername,
                        onRegEmailOrUsernameChange = { regEmailOrUsername = it; regError = null; onClearAuthError() },
                        regStudyProgram = regStudyProgram,
                        onRegStudyProgramChange = { regStudyProgram = it; regError = null; onClearAuthError() },
                        regPassword = regPassword,
                        onRegPasswordChange = { regPassword = it; regError = null; onClearAuthError() },
                        regConfirmPassword = regConfirmPassword,
                        onRegConfirmPasswordChange = { regConfirmPassword = it; regError = null; onClearAuthError() },
                        regPasswordVisible = regPasswordVisible,
                        onToggleRegPassword = { regPasswordVisible = !regPasswordVisible },
                        regConfirmVisible = regConfirmVisible,
                        onToggleRegConfirm = { regConfirmVisible = !regConfirmVisible },
                        regError = regError,
                        onSubmitRegistration = ::submitRegistration,
                        onEnterAdminPreview = onEnterAdminPreview,
                        modifier = Modifier.widthIn(max = 470.dp)
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = DarkCardBg,
                    border = BorderStroke(1.dp, LimePrimary.copy(alpha = 0.28f))
                ) {
                    Box(modifier = Modifier.fillMaxWidth().height(148.dp)) {
                        Image(
                            painter = painterResource(id = R.drawable.img_lime_login_banner),
                            contentDescription = stringResource(id = R.string.cd_banner_illustration),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(DarkObsidian.copy(alpha = 0.25f), DarkCardBg.copy(alpha = 0.92f))
                                    )
                                )
                        )
                        Box(modifier = Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                            AzrilBrandLogo(darkSurface = true, showTagline = true, iconSize = 42.dp)
                        }
                    }
                }

                AuthCardStack(
                    isRegisterMode = isRegisterMode,
                    onToggleMode = {
                        isRegisterMode = it
                        onClearAuthError()
                        regError = null
                        loginIdError = null
                        loginPwError = null
                    },
                    isSupabaseConfigured = isSupabaseConfigured,
                    authInProgress = authInProgress,
                    authErrorMessage = authErrorMessage,
                    loginIdentifier = loginIdentifier,
                    onLoginIdentifierChange = { loginIdentifier = it; loginIdError = null; onClearAuthError() },
                    loginIdError = loginIdError,
                    loginPassword = loginPassword,
                    onLoginPasswordChange = { loginPassword = it; loginPwError = null; onClearAuthError() },
                    loginPwError = loginPwError,
                    loginPasswordVisible = loginPasswordVisible,
                    onToggleLoginPassword = { loginPasswordVisible = !loginPasswordVisible },
                    rememberMe = rememberMe,
                    onRememberMeChange = { rememberMe = it },
                    onSubmitLogin = ::submitLogin,
                    onForgotPassword = { showForgotDialog = true },
                    regFullName = regFullName,
                    onRegFullNameChange = { regFullName = it; regError = null; onClearAuthError() },
                    regNim = regNim,
                    onRegNimChange = { regNim = it; regError = null; onClearAuthError() },
                    regEmailOrUsername = regEmailOrUsername,
                    onRegEmailOrUsernameChange = { regEmailOrUsername = it; regError = null; onClearAuthError() },
                    regStudyProgram = regStudyProgram,
                    onRegStudyProgramChange = { regStudyProgram = it; regError = null; onClearAuthError() },
                    regPassword = regPassword,
                    onRegPasswordChange = { regPassword = it; regError = null; onClearAuthError() },
                    regConfirmPassword = regConfirmPassword,
                    onRegConfirmPasswordChange = { regConfirmPassword = it; regError = null; onClearAuthError() },
                    regPasswordVisible = regPasswordVisible,
                    onToggleRegPassword = { regPasswordVisible = !regPasswordVisible },
                    regConfirmVisible = regConfirmVisible,
                    onToggleRegConfirm = { regConfirmVisible = !regConfirmVisible },
                    regError = regError,
                    onSubmitRegistration = ::submitRegistration,
                    onEnterAdminPreview = onEnterAdminPreview,
                    modifier = Modifier.widthIn(max = 520.dp)
                )
            }
        }
    }

    if (showForgotDialog) {
        ForgotPasswordDialog(
            onDismiss = { showForgotDialog = false },
            onSubmitEmail = { resetEmail ->
                showForgotDialog = false
                onNotify("Permintaan reset kata sandi untuk $resetEmail diterima.", ToastType.INFO)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthCardStack(
    isRegisterMode: Boolean,
    onToggleMode: (Boolean) -> Unit,
    isSupabaseConfigured: Boolean,
    authInProgress: Boolean,
    authErrorMessage: String?,
    loginIdentifier: String,
    onLoginIdentifierChange: (String) -> Unit,
    loginIdError: String?,
    loginPassword: String,
    onLoginPasswordChange: (String) -> Unit,
    loginPwError: String?,
    loginPasswordVisible: Boolean,
    onToggleLoginPassword: () -> Unit,
    rememberMe: Boolean,
    onRememberMeChange: (Boolean) -> Unit,
    onSubmitLogin: () -> Unit,
    onForgotPassword: () -> Unit,
    regFullName: String,
    onRegFullNameChange: (String) -> Unit,
    regNim: String,
    onRegNimChange: (String) -> Unit,
    regEmailOrUsername: String,
    onRegEmailOrUsernameChange: (String) -> Unit,
    regStudyProgram: String,
    onRegStudyProgramChange: (String) -> Unit,
    regPassword: String,
    onRegPasswordChange: (String) -> Unit,
    regConfirmPassword: String,
    onRegConfirmPasswordChange: (String) -> Unit,
    regPasswordVisible: Boolean,
    onToggleRegPassword: () -> Unit,
    regConfirmVisible: Boolean,
    onToggleRegConfirm: () -> Unit,
    regError: String?,
    onSubmitRegistration: () -> Unit,
    onEnterAdminPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    var prodiMenuExpanded by rememberSaveable { mutableStateOf(false) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = SoftMintBg.copy(alpha = 0.45f),
        unfocusedContainerColor = PureWhite,
        focusedBorderColor = DarkCardBg,
        unfocusedBorderColor = BorderMint,
        focusedLabelColor = DarkCardBg,
        cursorColor = DarkCardBg
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AzrilCard(contentPadding = PaddingValues(24.dp)) {
            // Pill Segmented Mode Switcher
            Surface(
                color = SoftMintBg,
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, BorderMint),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (!isRegisterMode) DarkCardBg else Color.Transparent)
                            .clickable { onToggleMode(false) }
                            .padding(vertical = 11.dp)
                            .testTag("go_to_login_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Masuk",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (!isRegisterMode) LimePrimary else InkMuted
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (isRegisterMode) DarkCardBg else Color.Transparent)
                            .clickable { onToggleMode(true) }
                            .padding(vertical = 11.dp)
                            .testTag("go_to_register_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Daftar Akun",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isRegisterMode) LimePrimary else InkMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isRegisterMode) "Buat Akun Mahasiswa" else "Selamat Datang",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = InkText
                )
                AzrilStatusBadge(
                    text = if (isSupabaseConfigured) "Supabase" else "Sesi Aman",
                    containerColor = LightGreenSecondary,
                    contentColor = LimeInk
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isRegisterMode) {
                    "Lengkapi identitas mahasiswa Anda satu kali. Role Mahasiswa ditetapkan otomatis."
                } else {
                    "Masuk dengan username atau email dan kata sandi. Identitas akademik dimuat otomatis."
                },
                style = MaterialTheme.typography.bodySmall,
                color = InkSubtle
            )

            // Error Banner
            val displayedError = authErrorMessage ?: if (isRegisterMode) regError else null
            if (displayedError != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    color = Rose50,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Rose600.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = displayedError,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = Rose700,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (!isRegisterMode) {
                // LOGIN FORM
                OutlinedTextField(
                    value = loginIdentifier,
                    onValueChange = onLoginIdentifierChange,
                    label = { Text("Username atau Email") },
                    placeholder = { Text("username atau nama@kampus.ac.id") },
                    leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null, tint = InkMuted) },
                    isError = loginIdError != null,
                    supportingText = loginIdError?.let { err -> { Text(err) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth().testTag("login_email_input")
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = loginPassword,
                    onValueChange = onLoginPasswordChange,
                    label = { Text("Password") },
                    placeholder = { Text("Masukkan kata sandi") },
                    leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null, tint = InkMuted) },
                    trailingIcon = {
                        IconButton(onClick = onToggleLoginPassword) {
                            Icon(
                                imageVector = if (loginPasswordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = stringResource(id = R.string.cd_toggle_password),
                                tint = InkMuted
                            )
                        }
                    },
                    visualTransformation = if (loginPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    isError = loginPwError != null,
                    supportingText = loginPwError?.let { err -> { Text(err) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth().testTag("login_password_input")
                )

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = rememberMe,
                            onCheckedChange = onRememberMeChange,
                            colors = CheckboxDefaults.colors(
                                checkedColor = DarkCardBg,
                                checkmarkColor = LimePrimary,
                                uncheckedColor = InkSubtle
                            ),
                            modifier = Modifier.testTag("remember_me_checkbox")
                        )
                        Text(
                            text = "Ingat Saya",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = InkText
                        )
                    }
                    TextButton(
                        onClick = onForgotPassword,
                        modifier = Modifier.testTag("forgot_password_button")
                    ) {
                        Text(
                            text = "Lupa kata sandi?",
                            style = MaterialTheme.typography.labelMedium,
                            color = LimeDeep
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onSubmitLogin,
                    enabled = !authInProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("login_submit_button"),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LimePrimary, contentColor = InkText)
                ) {
                    if (authInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = InkText,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Login ke Azril Clas", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }
            } else {
                // FIRST-TIME STUDENT REGISTRATION FORM
                OutlinedTextField(
                    value = regFullName,
                    onValueChange = onRegFullNameChange,
                    label = { Text("Nama Lengkap *") },
                    placeholder = { Text("Contoh: Azril Alafriza") },
                    leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null, tint = InkMuted) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth().testTag("reg_full_name_input")
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = regNim,
                    onValueChange = onRegNimChange,
                    label = { Text("NIM (Unik) *") },
                    placeholder = { Text("Contoh: 230101045") },
                    leadingIcon = { Icon(Icons.Rounded.Badge, contentDescription = null, tint = InkMuted) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth().testTag("reg_nim_input")
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = regEmailOrUsername,
                    onValueChange = onRegEmailOrUsernameChange,
                    label = { Text("Email atau Username *") },
                    placeholder = { Text("azril@kampus.ac.id atau azril23") },
                    leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null, tint = InkMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth().testTag("reg_identifier_input")
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Program Studi Dropdown — STRICTLY ONLY HES, PJKR, FARMASI
                ExposedDropdownMenuBox(
                    expanded = prodiMenuExpanded,
                    onExpandedChange = { prodiMenuExpanded = !prodiMenuExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = regStudyProgram,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Program Studi *") },
                        placeholder = { Text("Pilih Program Studi") },
                        leadingIcon = { Icon(Icons.Rounded.School, contentDescription = null, tint = InkMuted) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = prodiMenuExpanded) },
                        shape = RoundedCornerShape(16.dp),
                        colors = fieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .testTag("reg_prodi_dropdown")
                    )
                    ExposedDropdownMenu(
                        expanded = prodiMenuExpanded,
                        onDismissRequest = { prodiMenuExpanded = false },
                        containerColor = PureWhite
                    ) {
                        OfficialStudyPrograms.list.forEach { prodi ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = prodi.code,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = InkText
                                        )
                                        Text(
                                            text = prodi.faculty,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = InkSubtle
                                        )
                                    }
                                },
                                onClick = {
                                    onRegStudyProgramChange(prodi.code)
                                    prodiMenuExpanded = false
                                },
                                modifier = Modifier.testTag("prodi_option_${prodi.code.lowercase()}")
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = regPassword,
                    onValueChange = onRegPasswordChange,
                    label = { Text("Password (min. 8 karakter) *") },
                    leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null, tint = InkMuted) },
                    trailingIcon = {
                        IconButton(onClick = onToggleRegPassword) {
                            Icon(
                                imageVector = if (regPasswordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = stringResource(id = R.string.cd_toggle_password),
                                tint = InkMuted
                            )
                        }
                    },
                    visualTransformation = if (regPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth().testTag("reg_password_input")
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = regConfirmPassword,
                    onValueChange = onRegConfirmPasswordChange,
                    label = { Text("Konfirmasi Password *") },
                    leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null, tint = InkMuted) },
                    trailingIcon = {
                        IconButton(onClick = onToggleRegConfirm) {
                            Icon(
                                imageVector = if (regConfirmVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = stringResource(id = R.string.cd_toggle_password),
                                tint = InkMuted
                            )
                        }
                    },
                    visualTransformation = if (regConfirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth().testTag("reg_confirm_password_input")
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = rememberMe,
                        onCheckedChange = onRememberMeChange,
                        colors = CheckboxDefaults.colors(
                            checkedColor = DarkCardBg,
                            checkmarkColor = LimePrimary,
                            uncheckedColor = InkSubtle
                        )
                    )
                    Text(
                        text = "Ingat Saya setelah mendaftar",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkText
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onSubmitRegistration,
                    enabled = !authInProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("register_submit_button"),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LimePrimary, contentColor = InkText)
                ) {
                    if (authInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = InkText,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Daftar Akun Mahasiswa", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }
            }
        }

        // Admin Panel Access Card (Light Green Secondary Card)
        AzrilMintCard(contentPadding = PaddingValues(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Panel Administrator", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = InkText)
                    Text(
                        "Akun Admin ditetapkan melalui prosedur database, bukan pendaftaran publik.",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkMuted
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = onEnterAdminPreview,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkCardBg, contentColor = LimePrimary),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("preview_admin_button")
                ) {
                    Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Admin", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun ForgotPasswordDialog(
    onDismiss: () -> Unit,
    onSubmitEmail: (String) -> Unit
) {
    var resetEmail by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(26.dp),
        containerColor = PureWhite,
        title = { Text("Reset Kata Sandi", style = MaterialTheme.typography.titleLarge, color = InkText) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Masukkan email akademik terdaftar untuk menerima tautan pengaturan ulang kata sandi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted
                )
                OutlinedTextField(
                    value = resetEmail,
                    onValueChange = { resetEmail = it; error = null },
                    label = { Text("Email Akademik") },
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = resetEmail.trim()
                    if (!trimmed.contains("@") || !trimmed.contains(".")) {
                        error = "Masukkan format email akademik yang valid."
                    } else {
                        onSubmitEmail(trimmed)
                    }
                },
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LimePrimary, contentColor = InkText)
            ) {
                Text("Kirim Tautan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal", color = InkMuted) }
        }
    )
}
