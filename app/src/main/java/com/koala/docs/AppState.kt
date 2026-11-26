package com.koala.docs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

sealed interface Screen {
    data object Home : Screen
    data class Viewer(val docId: String, val page: Int = 0) : Screen
    /** PDF ouvert depuis une autre appli, pas encore dans la bibliothèque. */
    data class External(val file: File, val name: String) : Screen
    data class Pages(val docId: String) : Screen
}

class PwdAsk(val name: String, val wrong: Boolean, val answer: CompletableDeferred<String?>)

class CompressResult(val docId: String, val file: File, val before: Long, val pages: Int) {
    val after get() = file.length()
}

/** Tout ce que fait l'appli, indépendamment des écrans. */
class AppState(val ctx: Context, val scope: CoroutineScope) {
    val lib = Library(ctx)
    val upd = UpdateState()

    var screen by mutableStateOf<Screen>(Screen.Home)
    var filter by mutableStateOf<Filter>(Filter.All)
    var busy by mutableStateOf<String?>(null)
    var progress by mutableStateOf<Float?>(null)
    var pwd by mutableStateOf<PwdAsk?>(null)
    /** Document à classer juste après son ajout (nom, dossier, mots-clés). */
    var classify by mutableStateOf<String?>(null)
    var compressed by mutableStateOf<CompressResult?>(null)

    private val indexer = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    init {
        PdfOps.init(ctx)
        Uris.cleanTemp(ctx)
        // Indexation du texte des documents qui ne l'ont pas encore été (recherche dans le contenu)
        lib.state.value.docs.filterNot { lib.hasText(it.id) }.forEach { index(it.id) }
    }

    fun msg(s: String) = toast(ctx, s)

    fun index(id: String) {
        scope.launch(indexer) {
            val t = runCatching { PdfOps.extractText(lib.file(id)) }.getOrDefault("")
            if (lib.doc(id) != null) runCatching { lib.saveText(id, t) }
        }
    }

    /** Lance un travail long avec le voile d'attente ; affiche un message clair en cas d'échec. */
    fun work(label: String, block: suspend () -> Unit) {
        scope.launch {
            busy = label; progress = null
            try {
                block()
            } catch (e: Throwable) {
                msg("Échec : ${e.message ?: e.javaClass.simpleName}")
            } finally {
                busy = null; progress = null
            }
        }
    }

    private suspend fun askPassword(name: String, wrong: Boolean): String? {
        val d = CompletableDeferred<String?>()
        pwd = PwdAsk(name, wrong, d)
        return try { d.await() } finally { pwd = null }
    }

    /** Vérifie un PDF reçu, enlève sa protection si besoin (en demandant le mot de passe). Renvoie fichier prêt + pages. */
    private suspend fun prepare(tmp: File, name: String): Pair<File, Int>? {
        when (val r = withContext(Dispatchers.IO) { PdfOps.inspect(tmp) }) {
            Inspect.Invalid -> { msg("« $name » n'est pas un PDF lisible"); return null }
            is Inspect.Ok -> {
                if (!r.restricted) return tmp to r.pages
                val out = Uris.temp(ctx)
                val n = withContext(Dispatchers.IO) { runCatching { PdfOps.unlock(tmp, null, out) }.getOrNull() }
                return if (n != null) { tmp.delete(); out to n } else tmp to r.pages
            }
            Inspect.NeedPassword -> {
                var wrong = false
                val saved = busy; busy = null
                try {
                    while (true) {
                        val p = askPassword(name, wrong) ?: run { msg("« $name » non ajouté"); return null }
                        val out = Uris.temp(ctx)
                        val n = withContext(Dispatchers.IO) { runCatching { PdfOps.unlock(tmp, p, out) }.getOrNull() }
                        if (n != null) { tmp.delete(); return out to n }
                        out.delete(); wrong = true
                    }
                } finally { busy = saved }
            }
        }
    }

    private fun currentFolder() = (filter as? Filter.In)?.folderId

    private suspend fun addPrepared(tmp: File, name: String, folder: String?): Doc? {
        val (f, pages) = prepare(tmp, name) ?: return null
        val d = withContext(Dispatchers.IO) { lib.add(f, name, folder, pages) }
        index(d.id)
        return d
    }

    private fun afterAdd(added: List<Doc>) {
        when (added.size) {
            0 -> Unit
            1 -> classify = added[0].id
            else -> msg("${added.size} documents ajoutés")
        }
    }

    // ---------------- Entrées ----------------

    fun importPdfs(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val folder = currentFolder()
        work(if (uris.size > 1) "Import de ${uris.size} documents…" else "Import…") {
            val added = ArrayList<Doc>()
            for (u in uris) {
                val name = Uris.name(ctx, u) ?: "Document"
                val tmp = withContext(Dispatchers.IO) { Uris.copy(ctx, u) } ?: run { msg("Impossible de lire « $name »"); continue }
                addPrepared(tmp, name, folder)?.let { added += it }
            }
            afterAdd(added)
        }
    }

    fun importPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val folder = currentFolder()
        work("Création du PDF…") {
            val out = Uris.temp(ctx)
            val n = withContext(Dispatchers.IO) { Images.toPdf(ctx, uris, out) }
            if (n == 0) { msg("Aucune photo lisible"); return@work }
            val d = withContext(Dispatchers.IO) { lib.add(out, "Photos du ${fmtStamp()}", folder, n) }
            afterAdd(listOf(d))
        }
    }

    fun importScan(uri: Uri) {
        val folder = currentFolder()
        work("Enregistrement du scan…") {
            val tmp = withContext(Dispatchers.IO) { Uris.copy(ctx, uri) } ?: run { msg("Scan introuvable"); return@work }
            addPrepared(tmp, "Scan du ${fmtStamp()}", folder)?.let { afterAdd(listOf(it)) }
        }
    }

    @Suppress("DEPRECATION")
    fun handleIntent(i: Intent?) {
        i ?: return
        val type = i.type ?: ""
        when (i.action) {
            Intent.ACTION_VIEW -> {
                val u = i.data ?: return
                work("Ouverture…") {
                    val name = Library.cleanName(Uris.name(ctx, u) ?: "Document")
                    val tmp = withContext(Dispatchers.IO) { Uris.copy(ctx, u) } ?: run { msg("Fichier illisible"); return@work }
                    val (f, _) = prepare(tmp, name) ?: return@work
                    screen = Screen.External(f, name)
                }
            }
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> {
                val uris: List<Uri> = if (i.action == Intent.ACTION_SEND) {
                    listOfNotNull(if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else i.getParcelableExtra(Intent.EXTRA_STREAM))
                } else {
                    (if (Build.VERSION.SDK_INT >= 33) i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) else i.getParcelableArrayListExtra(Intent.EXTRA_STREAM)) ?: emptyList()
                }
                screen = Screen.Home
                if (type.startsWith("image/")) importPhotos(uris) else importPdfs(uris)
            }
        }
    }

    /** Ajoute à la bibliothèque le PDF ouvert depuis une autre appli. */
    fun keepExternal(e: Screen.External) {
        work("Ajout…") {
            val copy = Uris.temp(ctx)
            withContext(Dispatchers.IO) { e.file.copyTo(copy, overwrite = true) }
            val pages = withContext(Dispatchers.IO) { PdfOps.pageCount(copy) }
            val d = withContext(Dispatchers.IO) { lib.add(copy, e.name, null, pages) }
            index(d.id)
            screen = Screen.Viewer(d.id)
            classify = d.id
        }
    }

    // ---------------- Outils ----------------

    fun merge(ids: List<String>, name: String, folder: String?, deleteOriginals: Boolean, done: () -> Unit) {
        work("Fusion de ${ids.size} documents…") {
            val out = Uris.temp(ctx, "merge")
            withContext(Dispatchers.IO) { PdfOps.merge(ids.map { lib.file(it) }, out) }
            val pages = withContext(Dispatchers.IO) { PdfOps.pageCount(out) }
            val d = withContext(Dispatchers.IO) { lib.add(out, name, folder, pages, ids.flatMap { lib.doc(it)?.tags ?: emptyList() }.distinct()) }
            index(d.id)
            if (deleteOriginals) withContext(Dispatchers.IO) { lib.delete(ids) }
            done()
            msg("« ${d.name} » créé (${pagesLabel(pages)})")
        }
    }

    /** Applique l'organisation des pages : sur le document lui-même ou dans une copie. */
    fun rebuild(docId: String, ops: List<PageOp>, asCopy: Boolean, copyName: String? = null) {
        val d = lib.doc(docId) ?: return
        work("Enregistrement…") {
            val out = Uris.temp(ctx, "pages")
            withContext(Dispatchers.IO) { PdfOps.rebuild(lib.file(docId), ops, out) }
            if (asCopy) {
                val nd = withContext(Dispatchers.IO) { lib.add(out, copyName ?: "${d.name} (copie)", d.folder, ops.size, d.tags) }
                index(nd.id)
                msg("« ${nd.name} » créé")
                screen = Screen.Viewer(nd.id)
            } else {
                withContext(Dispatchers.IO) { lib.replace(docId, out, ops.size) }
                index(docId)
                msg("Document enregistré")
                screen = Screen.Viewer(docId)
            }
        }
    }

    fun split(docId: String, ranges: List<IntRange>) {
        val d = lib.doc(docId) ?: return
        work("Découpe…") {
            val dir = File(ctx.cacheDir, "work/split${System.currentTimeMillis()}")
            val files = withContext(Dispatchers.IO) { PdfOps.split(lib.file(docId), ranges, dir) }
            files.forEachIndexed { i, f ->
                val r = ranges[i]
                val label = if (r.first == r.last) "p. ${r.first + 1}" else "p. ${r.first + 1}-${r.last + 1}"
                val nd = withContext(Dispatchers.IO) { lib.add(f, "${d.name} ($label)", d.folder, r.count(), d.tags) }
                index(nd.id)
            }
            dir.deleteRecursively()
            msg("${files.size} documents créés")
            screen = Screen.Home
        }
    }

    fun compress(docId: String, level: Level) {
        val d = lib.doc(docId) ?: return
        work("Compression…") {
            val out = Uris.temp(ctx, "small")
            val pages = Compressor.run(lib.file(docId), out, level) { progress = it }
            compressed = CompressResult(docId, out, d.size, pages)
        }
    }

    fun applyCompression(r: CompressResult, replace: Boolean) {
        compressed = null
        val d = lib.doc(r.docId) ?: return
        work("Enregistrement…") {
            withContext(Dispatchers.IO) {
                if (replace) lib.replace(d.id, r.file, r.pages)
                else lib.add(r.file, "${d.name} (compressé)", d.folder, r.pages, d.tags)
            }
            if (replace) index(d.id)
            msg(if (replace) "Document remplacé par la version compressée" else "Copie compressée ajoutée")
        }
    }

    fun delete(ids: Collection<String>) {
        lib.delete(ids)
        msg(if (ids.size > 1) "${ids.size} documents supprimés" else "Document supprimé")
    }

    fun share(ids: List<String>) {
        runCatching { Share.send(ctx, ids.mapNotNull { id -> lib.doc(id)?.let { lib.file(id) to it.name } }) }
            .onFailure { msg("Partage impossible") }
    }
}
