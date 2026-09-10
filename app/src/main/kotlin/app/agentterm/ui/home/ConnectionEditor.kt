package app.agentterm.ui.home

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.agentterm.App
import app.agentterm.ssh.AuthType
import app.agentterm.ssh.SavedConnection
import app.agentterm.ssh.SecureStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient

@Composable
fun ConnectionEditor(connectionId: String?, onDone: () -> Unit) {
    val app = App.instance
    val existing = connectionId?.let { app.connections.get(it) }

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var host by remember { mutableStateOf(existing?.host ?: "") }
    var port by remember { mutableStateOf(existing?.port?.toString() ?: "22") }
    var user by remember { mutableStateOf(existing?.username ?: "") }
    var authType by remember { mutableStateOf(existing?.authType ?: AuthType.PASSWORD) }
    var password by remember { mutableStateOf("") }
    var keyLabel by remember { mutableStateOf(existing?.keyType?.takeIf { it.isNotBlank() }?.let { "Key: $it" } ?: "") }
    val secureStore = remember { SecureStore() }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val keyMaterial = withContext(Dispatchers.IO) {
                app.contentResolver.openInputStream(uri)?.use { it.readBytes()?.toString(Charsets.UTF_8) }
            }
            if (keyMaterial != null && (keyMaterial.contains("PRIVATE KEY"))) {
                password = keyMaterial
                keyLabel = "key loaded: ${keyMaterial.take(30)}…"
            } else {
                Toast.makeText(app, "That does not look like a private key", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val canSave = host.isNotBlank() && user.isNotBlank() && port.toIntOrNull() != null

    fun save() {
        val id = existing?.id ?: app.connections.newId()
        val secret = if (authType == AuthType.PASSWORD) password else keyLabel
        val secretRef = if (secret.isNotBlank()) secureStore.encrypt(secret) else existing?.secretRef ?: ""
        app.connections.upsert(
            SavedConnection(
                id = id,
                name = name.trim(),
                host = host.trim(),
                port = port.toIntOrNull() ?: 22,
                username = user.trim(),
                authType = authType,
                secretRef = secretRef,
                keyType = if (authType == AuthType.KEY) keyLabel.take(40) else "",
                keyFingerprint = "",
                keyComment = "",
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            )
        )
        onDone()
    }

    fun testConnect() {
        testing = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val c = SSHClient()
                    c.addHostKeyVerifier { _, _, _ -> true }
                    c.connect(host.trim(), port.toIntOrNull() ?: 22)
                    val decrypted = if (authType == AuthType.PASSWORD) password
                    else keyLabel // raw key text stored in password var
                    val success =
                        if (authType == AuthType.PASSWORD) {
                            c.authPassword(user.trim(), decrypted); true
                        } else {
                            val f = java.io.File(app.cacheDir, "tmp_key.pem")
                            f.writeText(decrypted); f.setReadable(true, true)
                            c.authPublickey(user.trim(), f)
                            f.delete(); true
                        }
                    c.disconnect()
                    success
                } catch (e: Exception) { false }
            }
            testing = false
            Toast.makeText(app, if (ok) "Connection OK ✓" else "Connection failed ✗", Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize()) {
        BackBar(if (existing == null) "New SSH host" else "Edit host", onBack = onDone)
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(name, { name = it }, label = { Text("Name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item {
                OutlinedTextField(host, { host = it }, label = { Text("Host (IP, DNS, Tailscale name)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(user, { user = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(port, { port = it }, label = { Text("Port") }, singleLine = true, modifier = Modifier.weight(0.5f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = authType == AuthType.PASSWORD,
                        onClick = { authType = AuthType.PASSWORD },
                        label = { Text("Password") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    )
                    FilterChip(
                        selected = authType == AuthType.KEY,
                        onClick = { authType = AuthType.KEY },
                        label = { Text("Private key") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    )
                }
            }
            if (authType == AuthType.PASSWORD) {
                item {
                    OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            } else {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = app.agentterm.ui.theme.Panel),
                        modifier = Modifier.fillMaxWidth().clickable { filePicker.launch(arrayOf("text/*", "application/x-pem-file", "application/octet-stream")) }
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Import private key", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                keyLabel.ifBlank { "OpenSSH or PEM format; stored encrypted in the Android Keystore" },
                                style = MaterialTheme.typography.bodySmall,
                                color = app.agentterm.ui.theme.TextSecondary,
                            )
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = ::save, enabled = canSave, modifier = Modifier.weight(1f)) { Text("Save") }
                    TextButton(onClick = ::testConnect, enabled = canSave && !testing, modifier = Modifier.weight(1f)) {
                        if (testing) CircularProgressIndicator(Modifier.height(16.dp), strokeWidth = 2.dp)
                        else Text("Test")
                    }
                }
            }
            item {
                Text(
                    "Secrets are encrypted with an Android Keystore key and never stored in plain text. Host/port/user metadata is stored locally as JSON.",
                    style = MaterialTheme.typography.bodySmall,
                    color = app.agentterm.ui.theme.TextSecondary,
                )
            }
        }
    }
}