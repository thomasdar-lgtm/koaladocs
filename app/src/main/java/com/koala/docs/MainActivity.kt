package com.koala.docs

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val app by lazy { AppState(applicationContext, lifecycleScope) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent { KoaladocsTheme { App(app) } }
        if (savedInstanceState == null) app.handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        app.handleIntent(intent)
    }
}

@Composable
fun App(app: AppState) {
    val ctx = LocalContext.current
    val activity = ctx as Activity
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { app.upd.info = runCatching { Updater.check() }.getOrNull() }

    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { app.importPdfs(it) }
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { app.importPhotos(it) }
    val scan = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            GmsDocumentScanningResult.fromActivityResultIntent(r.data)?.pdf?.uri?.let { app.importScan(it) }
        }
    }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { u ->
        if (u != null) app.work("Sauvegarde…") {
            withContext(Dispatchers.IO) { Backup.export(ctx, app.lib, u) }
            app.msg("Sauvegarde enregistrée (${app.lib.state.value.docs.size} documents)")
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) app.work("Restauration…") {
            val n = withContext(Dispatchers.IO) { Backup.restore(ctx, app.lib, u) }
            if (n == null) app.msg("Ce fichier n'est pas une sauvegarde Koaladocs")
            else {
                app.lib.state.value.docs.filterNot { app.lib.hasText(it.id) }.forEach { app.index(it.id) }
                app.msg(if (n == 0) "Rien de nouveau : tout est déjà là" else "$n document${if (n > 1) "s" else ""} restauré${if (n > 1) "s" else ""}")
            }
        }
    }

    val actions = HomeActions(
        scan = {
            val opts = GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build()
            GmsDocumentScanning.getClient(opts).getStartScanIntent(activity)
                .addOnSuccessListener { scan.launch(IntentSenderRequest.Builder(it).build()) }
                .addOnFailureListener { app.msg("Scanner indisponible : ${it.message ?: "réessaie dans un instant"}") }
        },
        pickPdf = { pickPdf.launch(arrayOf("application/pdf")) },
        pickPhotos = { pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        backup = {
            val day = SimpleDateFormat("yyyy-MM-dd", Locale.FRENCH).format(Date())
            backup.launch("Koaladocs-sauvegarde-$day.zip")
        },
        restore = { restore.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
        checkUpdate = {
            scope.launch {
                val r = runCatching { Updater.check() }
                r.onSuccess { app.upd.info = it; if (it == null) app.msg("Koaladocs ${BuildConfig.VERSION_NAME} est à jour") }
                r.onFailure { app.msg("Pas de connexion pour vérifier les mises à jour") }
            }
        },
    )

    Box(Modifier.fillMaxSize().background(BgBrush)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            UpdateBanner(app.upd)
            AnimatedContent(
                targetState = app.screen,
                transitionSpec = {
                    if (targetState != Screen.Home) (slideInHorizontally { it / 4 } + fadeIn()) togetherWith fadeOut()
                    else fadeIn() togetherWith (slideOutHorizontally { it / 4 } + fadeOut())
                },
                contentKey = { s -> when (s) { is Screen.Viewer -> "v" + s.docId; is Screen.Pages -> "p" + s.docId; is Screen.External -> "e"; Screen.Home -> "h" } },
                label = "screen",
            ) { s ->
                when (s) {
                    Screen.Home -> HomeScreen(app, actions)
                    is Screen.Viewer -> ViewerScreen(app, s.docId, s.page)
                    is Screen.External -> ExternalScreen(app, s)
                    is Screen.Pages -> PagesScreen(app, s.docId)
                }
            }
        }
    }

    app.pwd?.let { PasswordDialog(it) }
    app.classify?.let { id -> InfoDialog(app, id, "Classer le document") { app.classify = null } }
    app.compressed?.let { CompressResultDialog(app, it) }
    app.busy?.let { BusyOverlay(it, app.progress) }
}
