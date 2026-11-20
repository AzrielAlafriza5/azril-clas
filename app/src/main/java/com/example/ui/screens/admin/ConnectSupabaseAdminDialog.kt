package com.example.ui.screens.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ui.theme.BorderMint
import com.example.ui.theme.DarkCardBg
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkText
import com.example.ui.theme.LimePrimary
import com.example.ui.theme.PureWhite
import com.example.ui.theme.Rose50
import com.example.ui.theme.Rose600
import com.example.ui.theme.Rose700
import com.example.ui.theme.SoftMintBg

/**
 * Reusable actionable warning banner displayed on Admin Dashboard & Admin Data Mahasiswa
 * whenever Supabase is configured but the Admin session is not yet authenticated with a live
 * Supabase Auth JWT (or when a Supabase sync error occurs).
 */
@Composable
fun AdminSupabaseSyncStatusBanner(
    errorMessage: String,
    hasLiveSession: Boolean,
    isSyncing: Boolean,
    onRetrySync: () -> Unit,
    onOpenConnectDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("students_sync_error_banner"),
        shape = RoundedCornerShape(20.dp),
        color = Rose50,
        border = BorderStroke(1.dp, Rose600.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Rounded.Warning,
                    contentDescription = "Kendala sinkronisasi Supabase",
                    tint = Rose700,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = if (!hasLiveSession) {
                            "Sesi Admin Belum Terhubung ke Supabase Auth"
                        } else {
                            "Sinkronisasi Database Supabase"
                        },
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = Rose700
                    )
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = Rose700
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!hasLiveSession) {
                    Button(
                        onClick = onOpenConnectDialog,
                        enabled = !isSyncing,
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkCardBg,
                            contentColor = LimePrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("open_connect_supabase_admin_button")
                    ) {
                        Icon(Icons.Rounded.CloudDone, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Hubungkan Supabase", fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
                OutlinedButton(
                    onClick = onRetrySync,
                    enabled = !isSyncing,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, Rose600.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .weight(if (hasLiveSession) 1f else 0.7f)
                        .testTag("retry_supabase_sync_button")
                ) {
                    Text(
                        text = if (isSyncing) "Memuat..." else "Coba Lagi",
                        color = Rose700,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * In-place modal allowing an already logged-in Admin to authenticate directly against Supabase Auth
 * (using their Supabase Admin email/username & password with `profiles.role = 'admin'`) without
 * having to log out first. Immediately attaches a live JWT & refreshes all students/classes/XP.
 */
@Composable
fun ConnectSupabaseAdminDialog(
    isConnecting: Boolean,
    errorMessage: String?,
    onConnect: (identifier: String, password: String) -> Unit,
    onClearError: () -> Unit,
    onDismiss: () -> Unit
) {
    var identifier by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isConnecting) onDismiss() },
        containerColor = PureWhite,
        shape = RoundedCornerShape(26.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.CloudDone, contentDescription = null, tint = DarkCardBg)
                Text(
                    text = "Hubungkan Sesi Admin Supabase",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = InkText
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = SoftMintBg,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, BorderMint),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Masukkan Email atau Username dan Password akun Admin yang terdaftar di Supabase Authentication (dengan role='admin' di tabel profiles) agar seluruh data mahasiswa, kelas, dan ranking langsung tersinkronisasi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                if (!errorMessage.isNullOrBlank()) {
                    Surface(
                        color = Rose50,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Rose600.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = Rose700,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = identifier,
                    onValueChange = { identifier = it; onClearError() },
                    label = { Text("Email atau Username Admin Supabase") },
                    placeholder = { Text("Contoh: admin@domain.com atau Azril12v") },
                    leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("connect_supabase_admin_email_input")
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; onClearError() },
                    label = { Text("Password Admin Supabase") },
                    leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (showPassword) "Sembunyikan password" else "Tampilkan password"
                            )
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("connect_supabase_admin_password_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConnect(identifier.trim(), password) },
                enabled = !isConnecting && identifier.isNotBlank() && password.isNotBlank(),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkCardBg,
                    contentColor = LimePrimary
                ),
                modifier = Modifier.testTag("connect_supabase_admin_submit_button")
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = LimePrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text("Hubungkan Sekarang", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isConnecting
            ) {
                Text("Batal")
            }
        }
    )
}
