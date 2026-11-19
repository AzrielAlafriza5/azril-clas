package com.example.ui.screens.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.AzrilBrandLogo
import com.example.ui.components.AzrilCard
import com.example.ui.components.AzrilStatusBadge
import com.example.ui.theme.BorderMint
import com.example.ui.theme.DarkCardBg
import com.example.ui.theme.DarkCardElevated
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSubtle
import com.example.ui.theme.InkText
import com.example.ui.theme.LimePrimary
import com.example.ui.theme.OnDarkMuted
import com.example.ui.theme.PureWhite
import com.example.ui.theme.Rose50
import com.example.ui.theme.Rose600
import com.example.ui.theme.Rose700
import com.example.ui.theme.SoftMintBg

@Composable
fun AdminLoginScreen(
    authInProgress: Boolean,
    authErrorMessage: String?,
    isSupabaseConfigured: Boolean,
    onSubmitAdminLogin: (username: String, password: String, rememberMe: Boolean) -> Unit,
    onClearAuthError: () -> Unit,
    onBackToStudentLogin: () -> Unit
) {
    BackHandler(enabled = !authInProgress) {
        onBackToStudentLogin()
    }

    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var rememberSession by rememberSaveable { mutableStateOf(false) }
    var usernameError by rememberSaveable { mutableStateOf<String?>(null) }
    var passwordError by rememberSaveable { mutableStateOf<String?>(null) }

    fun submit() {
        onClearAuthError()
        val cleanUser = username.trim()
        usernameError = if (cleanUser.isEmpty()) "Username administrator wajib diisi." else null
        passwordError = if (password.isEmpty()) "Password administrator wajib diisi." else null
        if (usernameError == null && passwordError == null) {
            onSubmitAdminLogin(cleanUser, password, rememberSession)
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
                        .weight(1.02f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(32.dp),
                    color = DarkCardBg,
                    border = BorderStroke(1.dp, LimePrimary.copy(alpha = 0.28f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(36.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        AzrilBrandLogo(darkSurface = true, showTagline = true, iconSize = 46.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            AzrilStatusBadge(
                                text = "PORTAL ADMINISTRATOR TERPROTEKSI",
                                containerColor = LimePrimary,
                                contentColor = DarkCardBg
                            )
                            Text(
                                text = "Autentikasi Khusus Administrator Azril Clas",
                                style = MaterialTheme.typography.headlineLarge,
                                color = PureWhite
                            )
                            Text(
                                text = "Akses pengelolaan mahasiswa, import Excel, penugasan, absensi, dan sistem peringkat dilindungi oleh verifikasi role dan kebijakan keamanan berlapis.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OnDarkMuted
                            )
                        }
                        Surface(
                            color = DarkCardElevated,
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, LimePrimary.copy(alpha = 0.22f))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(LimePrimary.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.VerifiedUser,
                                        contentDescription = null,
                                        tint = LimePrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Text(
                                    text = "Sesi administrator terpisah dari mahasiswa dengan batas waktu otomatis & verifikasi Supabase Auth.",
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
                    AdminLoginCard(
                        username = username,
                        onUsernameChange = { username = it; usernameError = null; onClearAuthError() },
                        usernameError = usernameError,
                        password = password,
                        onPasswordChange = { password = it; passwordError = null; onClearAuthError() },
                        passwordError = passwordError,
                        passwordVisible = passwordVisible,
                        onTogglePassword = { passwordVisible = !passwordVisible },
                        rememberSession = rememberSession,
                        onRememberSessionChange = { rememberSession = it },
                        authInProgress = authInProgress,
                        authErrorMessage = authErrorMessage,
                        isSupabaseConfigured = isSupabaseConfigured,
                        onSubmit = ::submit,
                        onBackToStudentLogin = onBackToStudentLogin,
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
                    Column(
                        modifier = Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AzrilBrandLogo(darkSurface = true, showTagline = false, iconSize = 38.dp)
                            AzrilStatusBadge(
                                text = "Admin Only",
                                containerColor = LimePrimary,
                                contentColor = DarkCardBg
                            )
                        }
                        Text(
                            text = "Portal Login Administrator",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = PureWhite
                        )
                        Text(
                            text = "Masuk menggunakan username dan password administrator yang telah diverifikasi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnDarkMuted
                        )
                    }
                }

                AdminLoginCard(
                    username = username,
                    onUsernameChange = { username = it; usernameError = null; onClearAuthError() },
                    usernameError = usernameError,
                    password = password,
                    onPasswordChange = { password = it; passwordError = null; onClearAuthError() },
                    passwordError = passwordError,
                    passwordVisible = passwordVisible,
                    onTogglePassword = { passwordVisible = !passwordVisible },
                    rememberSession = rememberSession,
                    onRememberSessionChange = { rememberSession = it },
                    authInProgress = authInProgress,
                    authErrorMessage = authErrorMessage,
                    isSupabaseConfigured = isSupabaseConfigured,
                    onSubmit = ::submit,
                    onBackToStudentLogin = onBackToStudentLogin,
                    modifier = Modifier.widthIn(max = 520.dp)
                )
            }
        }
    }
}

@Composable
private fun AdminLoginCard(
    username: String,
    onUsernameChange: (String) -> Unit,
    usernameError: String?,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordError: String?,
    passwordVisible: Boolean,
    onTogglePassword: () -> Unit,
    rememberSession: Boolean,
    onRememberSessionChange: (Boolean) -> Unit,
    authInProgress: Boolean,
    authErrorMessage: String?,
    isSupabaseConfigured: Boolean,
    onSubmit: () -> Unit,
    onBackToStudentLogin: () -> Unit,
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
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AzrilCard(contentPadding = PaddingValues(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(DarkCardBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.AdminPanelSettings,
                            contentDescription = null,
                            tint = LimePrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Login Administrator",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = InkText
                        )
                        Text(
                            text = if (isSupabaseConfigured) "Supabase Auth • Verifikasi Role" else "Autentikasi Terenkripsi",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkSubtle
                        )
                    }
                }
            }

            if (authErrorMessage != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    color = Rose50,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Rose600.copy(alpha = 0.28f)),
                    modifier = Modifier.fillMaxWidth().testTag("admin_login_error_banner")
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
                value = username,
                onValueChange = onUsernameChange,
                label = { Text("Username Administrator") },
                placeholder = { Text("Masukkan username admin") },
                leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null, tint = InkMuted) },
                isError = usernameError != null,
                supportingText = usernameError?.let { err -> { Text(err) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                shape = RoundedCornerShape(16.dp),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth().testTag("admin_login_username_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("Password Administrator") },
                placeholder = { Text("Masukkan password admin") },
                leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null, tint = InkMuted) },
                trailingIcon = {
                    IconButton(onClick = onTogglePassword) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = stringResource(id = R.string.cd_toggle_password),
                            tint = InkMuted
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                isError = passwordError != null,
                supportingText = passwordError?.let { err -> { Text(err) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(16.dp),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth().testTag("admin_login_password_input")
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = rememberSession,
                    onCheckedChange = onRememberSessionChange,
                    colors = CheckboxDefaults.colors(
                        checkedColor = DarkCardBg,
                        checkmarkColor = LimePrimary,
                        uncheckedColor = InkSubtle
                    ),
                    modifier = Modifier.testTag("admin_remember_me_checkbox")
                )
                Text(
                    text = "Pertahankan sesi admin pada perangkat ini (maks. 8 jam)",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = InkText
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onSubmit,
                enabled = !authInProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("admin_login_submit_button"),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DarkCardBg, contentColor = LimePrimary)
            ) {
                if (authInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = LimePrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Icon(Icons.Rounded.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Masuk Dashboard Admin",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onBackToStudentLogin,
                enabled = !authInProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("admin_login_back_button"),
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, BorderMint)
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Kembali ke Login Mahasiswa",
                    tint = InkText,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Kembali ke Login Mahasiswa",
                    style = MaterialTheme.typography.labelLarge,
                    color = InkText
                )
            }
        }
    }
}
