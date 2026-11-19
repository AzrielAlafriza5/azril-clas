package com.example.ui.screens.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.ui.components.AzrilBrandLogo
import com.example.ui.components.AzrilCard
import com.example.ui.components.AzrilMintCard
import com.example.ui.components.AzrilStatusBadge
import com.example.ui.theme.BorderMint
import com.example.ui.theme.DarkCardBg
import com.example.ui.theme.DarkCardElevated
import com.example.ui.theme.DarkObsidian
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSubtle
import com.example.ui.theme.InkText
import com.example.ui.theme.LightGreenSecondary
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
    onClearAuthError: () -> Unit,
    onOpenAdminLogin: () -> Unit
) {
    var loginIdentifier by rememberSaveable { mutableStateOf(rememberedIdentifier) }
    var loginPassword by rememberSaveable { mutableStateOf("") }
    var loginPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var rememberMe by rememberSaveable { mutableStateOf(rememberMeDefault) }
    var loginIdError by rememberSaveable { mutableStateOf<String?>(null) }
    var loginPwError by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(rememberedIdentifier) {
        if (rememberedIdentifier.isNotBlank() && loginIdentifier.isBlank()) {
            loginIdentifier = rememberedIdentifier
        }
    }

    fun submitLogin() {
        onClearAuthError()
        val cleanId = loginIdentifier.trim()
        loginIdError = if (cleanId.isEmpty()) "NIM wajib diisi." else null
        loginPwError = if (loginPassword.isEmpty()) "Kata sandi wajib diisi." else null
        if (loginIdError == null && loginPwError == null) {
            onLogin(cleanId, loginPassword, rememberMe)
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
                                text = "Manajemen Perkuliahan, Penugasan, Absensi, & Peringkat Terpusat",
                                style = MaterialTheme.typography.headlineLarge,
                                color = PureWhite
                            )
                            Text(
                                text = "Mahasiswa masuk menggunakan NIM dan kata sandi yang telah ditetapkan oleh Admin. Data identitas akademik dimuat otomatis dari database.",
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
                                    text = "Akses terverifikasi berbasis NIM dengan penyimpanan kata sandi terenkripsi.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnDarkMuted
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LoginCardStack(
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
                        onOpenAdminLogin = onOpenAdminLogin,
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

                LoginCardStack(
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
                    onOpenAdminLogin = onOpenAdminLogin,
                    modifier = Modifier.widthIn(max = 520.dp)
                )
            }
        }
    }
}

@Composable
private fun LoginCardStack(
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
    onOpenAdminLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Masuk Mahasiswa",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = InkText
                )
                AzrilStatusBadge(
                    text = if (isSupabaseConfigured) "Supabase" else "Login NIM",
                    containerColor = LightGreenSecondary,
                    contentColor = LimeInk
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Gunakan NIM sebagai username dan kata sandi yang telah ditetapkan oleh Admin.",
                style = MaterialTheme.typography.bodySmall,
                color = InkSubtle
            )

            if (authErrorMessage != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    color = Rose50,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Rose600.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = authErrorMessage,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = Rose700,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            OutlinedTextField(
                value = loginIdentifier,
                onValueChange = onLoginIdentifierChange,
                label = { Text("NIM (Username)") },
                placeholder = { Text("Contoh: 230101045") },
                leadingIcon = { Icon(Icons.Rounded.Badge, contentDescription = null, tint = InkMuted) },
                isError = loginIdError != null,
                supportingText = loginIdError?.let { err -> { Text(err) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                shape = RoundedCornerShape(16.dp),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth().testTag("login_nim_input")
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = loginPassword,
                onValueChange = onLoginPasswordChange,
                label = { Text("Kata Sandi") },
                placeholder = { Text("Masukkan kata sandi dari Admin") },
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
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                    text = "Ingat Saya (simpan sesi login dengan aman)",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = InkText
                )
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
                Text("Login dengan NIM", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }

            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                color = SoftMintBg,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, BorderMint),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = LimeInk, modifier = Modifier.size(18.dp))
                    Text(
                        text = "Pendaftaran akun mahasiswa dan reset kata sandi hanya dilakukan oleh Admin. Hubungi Admin jika Anda belum memiliki akun atau lupa kata sandi.",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkMuted
                    )
                }
            }
        }

        AzrilMintCard(contentPadding = PaddingValues(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Login Administrator", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = InkText)
                    Text(
                        "Masuk khusus Administrator dengan verifikasi username dan password resmi.",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkMuted
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = onOpenAdminLogin,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkCardBg, contentColor = LimePrimary),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("open_admin_login_button")
                ) {
                    Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Login Admin", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
