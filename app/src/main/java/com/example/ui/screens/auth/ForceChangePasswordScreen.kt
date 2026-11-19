package com.example.ui.screens.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ui.components.AzrilBrandLogo
import com.example.ui.components.AzrilCard
import com.example.ui.components.AzrilDarkCard
import com.example.ui.components.AzrilStatusBadge
import com.example.ui.model.UserProfile
import com.example.ui.theme.BorderMint
import com.example.ui.theme.DarkCardBg
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
fun ForceChangePasswordScreen(
    user: UserProfile,
    authInProgress: Boolean,
    authErrorMessage: String?,
    onClearError: () -> Unit,
    onSubmitNewPassword: (newPassword: String, confirmPassword: String) -> Unit,
    onLogout: () -> Unit
) {
    // Prevent hardware back press from bypassing mandatory password change
    BackHandler(enabled = true) { onLogout() }

    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var showNew by rememberSaveable { mutableStateOf(false) }
    var showConfirm by rememberSaveable { mutableStateOf(false) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = SoftMintBg.copy(alpha = 0.45f),
        unfocusedContainerColor = PureWhite,
        focusedBorderColor = DarkCardBg,
        unfocusedBorderColor = BorderMint,
        focusedLabelColor = DarkCardBg,
        cursorColor = DarkCardBg
    )

    fun handleSubmit() {
        onClearError()
        val cleanNew = newPassword.trim()
        val cleanConfirm = confirmPassword.trim()
        localError = when {
            cleanNew.length < 6 -> "Kata sandi baru minimal 6 karakter."
            user.nim != null && cleanNew.equals(user.nim.trim(), ignoreCase = true) ->
                "Kata sandi baru tidak boleh sama dengan NIM Anda."
            cleanNew != cleanConfirm -> "Konfirmasi kata sandi tidak cocok."
            else -> null
        }
        if (localError == null) {
            onSubmitNewPassword(cleanNew, cleanConfirm)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SoftMintBg)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AzrilDarkCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AzrilBrandLogo(darkSurface = true, showTagline = false, iconSize = 36.dp)
                    AzrilStatusBadge("LOGIN PERTAMA", LimePrimary, DarkCardBg)
                }
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(LimePrimary.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.LockReset, contentDescription = null, tint = LimePrimary, modifier = Modifier.size(24.dp))
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Wajib Ganti Kata Sandi",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = PureWhite
                        )
                        Text(
                            text = "${user.fullName} • NIM ${user.nim ?: "-"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = LimePrimary
                        )
                        Text(
                            text = "${user.studyProgramName ?: "-"} • ${user.className ?: "Belum terdaftar di kelas"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnDarkMuted
                        )
                    }
                }
            }

            AzrilCard(contentPadding = PaddingValues(22.dp)) {
                Text(
                    text = "Buat Kata Sandi Baru Anda",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = InkText
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Akun Anda menggunakan NIM sebagai password awal. Demi keamanan akun dan data akademik, buat kata sandi baru minimal 6 karakter yang berbeda dari NIM sebelum masuk ke dashboard.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSubtle
                )

                val displayError = localError ?: authErrorMessage
                if (displayError != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = Rose50,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Rose600.copy(alpha = 0.28f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = displayError,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = Rose700,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it; localError = null; onClearError() },
                    label = { Text("Kata Sandi Baru") },
                    placeholder = { Text("Minimal 6 karakter (berbeda dari NIM)") },
                    leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null, tint = InkMuted) },
                    trailingIcon = {
                        IconButton(onClick = { showNew = !showNew }) {
                            Icon(
                                imageVector = if (showNew) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = "Tampilkan atau sembunyikan kata sandi baru",
                                tint = InkMuted
                            )
                        }
                    },
                    visualTransformation = if (showNew) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth().testTag("force_new_password_input")
                )

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; localError = null; onClearError() },
                    label = { Text("Konfirmasi Kata Sandi Baru") },
                    placeholder = { Text("Ulangi kata sandi baru Anda") },
                    leadingIcon = { Icon(Icons.Rounded.Security, contentDescription = null, tint = InkMuted) },
                    trailingIcon = {
                        IconButton(onClick = { showConfirm = !showConfirm }) {
                            Icon(
                                imageVector = if (showConfirm) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = "Tampilkan atau sembunyikan konfirmasi kata sandi",
                                tint = InkMuted
                            )
                        }
                    },
                    visualTransformation = if (showConfirm) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth().testTag("force_confirm_password_input")
                )

                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = ::handleSubmit,
                    enabled = !authInProgress,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LimePrimary, contentColor = InkText),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("submit_force_change_password_button")
                ) {
                    if (authInProgress) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = InkText, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        "Simpan Password Baru & Masuk",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onLogout,
                    enabled = !authInProgress,
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.fillMaxWidth().testTag("force_change_logout_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Keluar ke Halaman Login")
                }

                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = LightGreenSecondary.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Lupa kata sandi di kemudian hari? Hubungi Admin untuk melakukan reset kata sandi secara aman.",
                        style = MaterialTheme.typography.labelSmall,
                        color = LimeInk,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}
