package org.fdroid.tellicoviewer.ui.screens.sync

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.fdroid.tellicoviewer.R
import org.fdroid.tellicoviewer.data.repository.TellicoRepository
import java.io.*
import java.net.*
import javax.inject.Inject

// ---------------------------------------------------------------------------
// ViewModel de synchronisation
// ---------------------------------------------------------------------------

/**
 * ViewModel de synchronisation Wi-Fi.
 *
 * PRINCIPE :
 * L'appareil Android démarre un serveur HTTP minimal sur le port 8765.
 * Le PC accède à http://<IP_ANDROID>:8765/ depuis un navigateur web
 * pour uploader des fichiers .tc via un formulaire HTML.
 *
 * C'est intentionnellement simple : pas de TLS, réseau local uniquement.
 * Pour une vraie synchro bidirectionnelle, on pourrait utiliser Syncthing.
 *
 * ALTERNATIVE (pour utilisateurs avancés) : rsync via SSH + Termux.
 * Cette approche HTTP est plus accessible pour des non-techniciens.
 */
@HiltViewModel
class SyncViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: TellicoRepository
) : ViewModel() {

    companion object {
        private const val TAG = "SyncViewModel"
        const val SYNC_PORT = 8765
    }

    sealed class SyncState {
        object Stopped : SyncState()
        data class Running(val ip: String, val port: Int) : SyncState()
        data class Error(val message: String) : SyncState()
    }

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Stopped)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _importedFiles = MutableStateFlow<List<String>>(emptyList())
    val importedFiles: StateFlow<List<String>> = _importedFiles.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private var serverJob: Job? = null
    private var serverSocket: ServerSocket? = null

    /** Démarre le serveur HTTP sur le port SYNC_PORT */
    fun startServer() {
        if (_syncState.value is SyncState.Running) return

        serverJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val ip = getWifiIpAddress()
                serverSocket = ServerSocket(SYNC_PORT)
                _syncState.value = SyncState.Running(ip, SYNC_PORT)
                addLog("Serveur démarré sur $ip:$SYNC_PORT")
                addLog("Ouvrez http://$ip:$SYNC_PORT sur votre PC")

                while (isActive) {
                    try {
                        val client = serverSocket!!.accept()
                        launch { handleClient(client) }
                    } catch (e: SocketException) {
                        if (isActive) addLog("Erreur socket : ${e.message}")
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erreur serveur", e)
                _syncState.value = SyncState.Error(e.message ?: "Erreur inconnue")
                addLog("Erreur : ${e.message}")
            }
        }
    }

    fun stopServer() {
        serverJob?.cancel()
        serverSocket?.close()
        serverSocket = null
        _syncState.value = SyncState.Stopped
        addLog("Serveur arrêté")
    }

    /**
     * Gère une connexion HTTP entrante.
     * Implémentation HTTP/1.0 minimale : GET (interface web) et POST (upload fichier).
     */
    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        try {
            socket.use { s ->
                val inputStream  = s.getInputStream()
                val outputStream = s.getOutputStream()
                val reader = BufferedReader(InputStreamReader(inputStream))

                // Lire la première ligne HTTP (ex: "GET / HTTP/1.1")
                val requestLine = reader.readLine() ?: return@withContext
                val parts = requestLine.split(" ")
                val method = parts.getOrElse(0) { "GET" }
                val path   = parts.getOrElse(1) { "/" }

                addLog("$method $path")

                when {
                    method == "GET" && path == "/" -> {
                        // Renvoyer l'interface HTML d'upload
                        val html = buildUploadHtml()
                        sendHttpResponse(outputStream, 200, "text/html; charset=utf-8", html.toByteArray())
                    }
                    method == "POST" && path == "/upload" -> {
                        // Traiter l'upload du fichier .tc
                        val headers = mutableMapOf<String, String>()
                        var line = reader.readLine()
                        while (!line.isNullOrEmpty()) {
                            val (k, v) = line.split(": ", limit = 2).let {
                                it[0] to (it.getOrElse(1) { "" })
                            }
                            headers[k.lowercase()] = v
                            line = reader.readLine()
                        }

                        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                        val contentType = headers["content-type"] ?: ""

                        if (contentLength > 0) {
                            // Lire le body du POST (données multipart)
                            val bodyBytes = readBody(s.getInputStream(), contentLength)
                            val filename = extractFilename(contentType, bodyBytes)
                            val fileData = extractFileData(contentType, bodyBytes)

                            if (fileData != null && filename.endsWith(".tc")) {
                                // Sauvegarder temporairement et importer
                                val tempFile = File(context.cacheDir, filename)
                                tempFile.writeBytes(fileData)

                                withContext(Dispatchers.Main) {
                                    repository.importFromUri(
                                        android.net.Uri.fromFile(tempFile)
                                    ) { progress, msg ->
                                        addLog("Import $progress% : $msg")
                                    }.onSuccess {
                                        _importedFiles.value = _importedFiles.value + filename
                                        addLog("✓ $filename importé avec succès")
                                    }.onFailure { e ->
                                        addLog("✗ Erreur : ${e.message}")
                                    }
                                }
                                tempFile.delete()

                                val response = """{"status":"ok","file":"$filename"}"""
                                sendHttpResponse(outputStream, 200, "application/json", response.toByteArray())
                            } else {
                                sendHttpResponse(outputStream, 400, "text/plain", "Fichier .tc requis".toByteArray())
                            }
                        } else {
                            sendHttpResponse(outputStream, 400, "text/plain", "Body vide".toByteArray())
                        }
                    }
                    else -> sendHttpResponse(outputStream, 404, "text/plain", "Not found".toByteArray())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur client", e)
        }
    }

    private fun buildUploadHtml(): String = """
        <!DOCTYPE html>
        <html lang="fr">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>TellicoViewer – Synchronisation</title>
          <style>
            body { font-family: system-ui, sans-serif; max-width: 600px; margin: 40px auto; padding: 0 20px; }
            h1 { color: #1a6b5c; }
            .upload-zone { border: 2px dashed #ccc; border-radius: 8px; padding: 40px; text-align: center; margin: 20px 0; }
            button { background: #1a6b5c; color: white; border: none; padding: 12px 24px; border-radius: 6px; cursor: pointer; font-size: 16px; }
            button:hover { background: #145a4d; }
            #status { margin-top: 16px; padding: 12px; border-radius: 6px; display: none; }
            .success { background: #d1fae5; color: #065f46; }
            .error   { background: #fee2e2; color: #991b1b; }
          </style>
        </head>
        <body>
          <h1>📚 TellicoViewer Sync</h1>
          <p>Envoyez vos fichiers Tellico (.tc) depuis votre PC vers l'application Android.</p>
          <div class="upload-zone">
            <p>Glissez-déposez un fichier .tc ici, ou :</p>
            <form id="uploadForm" enctype="multipart/form-data">
              <input type="file" name="file" id="fileInput" accept=".tc,.zip" style="margin-bottom:12px;display:block;margin:0 auto 16px;">
              <button type="submit">📤 Envoyer vers Android</button>
            </form>
          </div>
          <div id="status"></div>
          <script>
            document.getElementById('uploadForm').addEventListener('submit', async (e) => {
              e.preventDefault();
              const status = document.getElementById('status');
              const file = document.getElementById('fileInput').files[0];
              if (!file) { status.textContent = 'Sélectionnez un fichier'; status.className=''; status.style.display='block'; return; }
              const fd = new FormData();
              fd.append('file', file);
              status.textContent = 'Envoi en cours...';
              status.className = '';
              status.style.display = 'block';
              try {
                const r = await fetch('/upload', { method: 'POST', body: fd });
                const j = await r.json();
                status.textContent = '✓ ' + j.file + ' importé avec succès !';
                status.className = 'success';
              } catch(err) {
                status.textContent = '✗ Erreur : ' + err.message;
                status.className = 'error';
              }
            });
          </script>
        </body>
        </html>
    """.trimIndent()

    private fun sendHttpResponse(out: java.io.OutputStream, code: Int, contentType: String, body: ByteArray) {
        val status = when (code) {
            200 -> "200 OK"; 400 -> "400 Bad Request"; 404 -> "404 Not Found"; else -> "$code"
        }
        // Écrire headers + body en une seule fois sur l'OutputStream brut
        val headers = "HTTP/1.0 $status\r\nContent-Type: $contentType\r\n" +
                      "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n"
        out.write(headers.toByteArray(Charsets.US_ASCII))
        out.write(body)
        out.flush()
    }

    private fun readBody(input: InputStream, length: Int): ByteArray {
        val buffer = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(buffer, read, length - read)
            if (n < 0) break
            read += n
        }
        return buffer
    }

    private fun extractFilename(contentType: String, body: ByteArray): String {
        val bodyStr = String(body.take(512).toByteArray())
        val regex = """filename="([^"]+)"""".toRegex()
        return regex.find(bodyStr)?.groupValues?.getOrNull(1) ?: "upload.tc"
    }

    private fun extractFileData(contentType: String, body: ByteArray): ByteArray? {
        // Parser multipart basique : trouver la frontière et extraire les données
        val boundary = contentType.substringAfter("boundary=").trim()
        if (boundary.isEmpty()) return null
        val sep = "\r\n\r\n".toByteArray()
        val startIdx = findBytes(body, sep)
        if (startIdx < 0) return null
        val dataStart = startIdx + sep.size
        val endMarker = ("\r\n--$boundary--").toByteArray()
        val endIdx = findBytes(body, endMarker, dataStart)
        return if (endIdx > dataStart) body.sliceArray(dataStart until endIdx) else null
    }

    private fun findBytes(haystack: ByteArray, needle: ByteArray, start: Int = 0): Int {
        outer@ for (i in start..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private fun getWifiIpAddress(): String {
        val wifiMgr = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ip = wifiMgr.connectionInfo.ipAddress
        return String.format("%d.%d.%d.%d",
            ip and 0xff, ip shr 8 and 0xff, ip shr 16 and 0xff, ip shr 24 and 0xff)
    }

    private fun addLog(msg: String) {
        _log.value = (_log.value + msg).takeLast(50)  // garder les 50 dernières lignes
    }

    override fun onCleared() {
        super.onCleared()
        stopServer()
    }
}

// ---------------------------------------------------------------------------
// Écran de synchronisation
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    onBack: () -> Unit,
    viewModel: SyncViewModel = hiltViewModel()
) {
    val syncState     by viewModel.syncState.collectAsState()
    val importedFiles by viewModel.importedFiles.collectAsState()
    val log           by viewModel.log.collectAsState()
    val context       = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sync_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.stopServer()
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---- Carte de statut ----
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                when (syncState) {
                                    is SyncViewModel.SyncState.Running -> Icons.Default.Wifi
                                    is SyncViewModel.SyncState.Error   -> Icons.Default.WifiOff
                                    else                               -> Icons.Default.WifiOff
                                },
                                contentDescription = null,
                                tint = when (syncState) {
                                    is SyncViewModel.SyncState.Running -> MaterialTheme.colorScheme.primary
                                    is SyncViewModel.SyncState.Error   -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = when (syncState) {
                                        is SyncViewModel.SyncState.Running ->
                                            stringResource(R.string.sync_running)
                                        is SyncViewModel.SyncState.Error ->
                                            stringResource(R.string.sync_error)
                                        else -> stringResource(R.string.sync_stopped)
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (syncState is SyncViewModel.SyncState.Running) {
                                    val running = syncState as SyncViewModel.SyncState.Running
                                    Text(
                                        "http://${running.ip}:${running.port}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (syncState is SyncViewModel.SyncState.Running) {
                                OutlinedButton(
                                    onClick = { viewModel.stopServer() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Stop, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.stop_server))
                                }
                            } else {
                                Button(
                                    onClick = { viewModel.startServer() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.start_server))
                                }
                            }
                        }
                    }
                }
            }

            // ---- Instructions ----
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.sync_instructions_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        listOf(
                            stringResource(R.string.sync_step1),
                            stringResource(R.string.sync_step2),
                            stringResource(R.string.sync_step3),
                            stringResource(R.string.sync_step4)
                        ).forEachIndexed { i, step ->
                            Row(Modifier.padding(vertical = 2.dp)) {
                                Text(
                                    "${i + 1}.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(24.dp)
                                )
                                Text(step, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            // ---- Fichiers importés ----
            if (importedFiles.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.imported_files),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                items(importedFiles) { filename ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(filename, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // ---- Log ----
            if (log.isNotEmpty()) {
                item {
                    Text(
                        "Journal",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        color  = MaterialTheme.colorScheme.surfaceVariant,
                        shape  = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            log.reversed().take(20).forEach { entry ->
                                Text(
                                    text  = entry,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
