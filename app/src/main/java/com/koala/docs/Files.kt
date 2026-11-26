package com.koala.docs

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object Share {
    private fun uri(ctx: Context, f: File) = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)

    /** Copie avec le vrai nom du document, pour que le destinataire reçoive « Facture EDF.pdf » et pas un identifiant. */
    private fun named(ctx: Context, items: List<Pair<File, String>>): List<File> {
        val root = File(ctx.cacheDir, "share").apply { mkdirs() }
        root.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 3_600_000 }?.forEach { it.deleteRecursively() }
        val dir = File(root, System.currentTimeMillis().toString()).apply { mkdirs() }
        val used = HashSet<String>()
        return items.map { (f, name) ->
            var n = Library.cleanName(name); var i = 2
            while (!used.add(n.lowercase())) n = "${Library.cleanName(name)} ($i)".also { i++ }
            File(dir, "$n.pdf").also { f.copyTo(it, overwrite = true) }
        }
    }

    fun send(ctx: Context, items: List<Pair<File, String>>) {
        if (items.isEmpty()) return
        val files = named(ctx, items)
        val uris = files.map { uri(ctx, it) }
        val i = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0]).putExtra(Intent.EXTRA_SUBJECT, files[0].nameWithoutExtension)
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        i.type = "application/pdf"
        i.clipData = ClipData.newUri(ctx.contentResolver, "pdf", uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, if (uris.size == 1) "Partager le document" else "Partager ${uris.size} documents"))
    }

    fun openWith(ctx: Context, f: File, name: String) {
        val u = uri(ctx, named(ctx, listOf(f to name))[0])
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(u, "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, "Ouvrir avec"))
    }

    fun print(ctx: Context, f: File, name: String) {
        val pm = ctx.getSystemService(Context.PRINT_SERVICE) as PrintManager
        pm.print(name, object : PrintDocumentAdapter() {
            override fun onLayout(old: PrintAttributes?, new: PrintAttributes?, cancel: CancellationSignal?, cb: LayoutResultCallback, extras: Bundle?) {
                if (cancel?.isCanceled == true) { cb.onLayoutCancelled(); return }
                cb.onLayoutFinished(PrintDocumentInfo.Builder("$name.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(), true)
            }

            override fun onWrite(pages: Array<out PageRange>?, dest: ParcelFileDescriptor, cancel: CancellationSignal?, cb: WriteResultCallback) {
                try {
                    f.inputStream().use { i -> FileOutputStream(dest.fileDescriptor).use { o -> i.copyTo(o) } }
                    cb.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    cb.onWriteFailed(e.message)
                }
            }
        }, null)
    }
}

object Uris {
    fun name(ctx: Context, u: Uri): String? = runCatching {
        ctx.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: u.lastPathSegment?.substringAfterLast('/')

    /** Copie le contenu d'un fichier reçu dans un fichier temporaire de l'appli. */
    fun copy(ctx: Context, u: Uri): File? = runCatching {
        val dir = File(ctx.cacheDir, "incoming").apply { mkdirs() }
        val f = File.createTempFile("in", ".pdf", dir)
        ctx.contentResolver.openInputStream(u)!!.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        f
    }.getOrNull()

    fun temp(ctx: Context, prefix: String = "tmp"): File {
        val dir = File(ctx.cacheDir, "work").apply { mkdirs() }
        return File.createTempFile(prefix, ".pdf", dir)
    }

    fun cleanTemp(ctx: Context) {
        listOf("incoming", "work").forEach { d ->
            File(ctx.cacheDir, d).listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }?.forEach { it.deleteRecursively() }
        }
    }
}

/** Sauvegarde complète (liste + PDF) dans un seul fichier .zip, et restauration sans rien écraser. */
object Backup {
    fun export(ctx: Context, lib: Library, out: Uri) {
        val s = lib.state.value
        ctx.contentResolver.openOutputStream(out)!!.use { os ->
            ZipOutputStream(os.buffered()).use { z ->
                z.putNextEntry(ZipEntry("koaladocs.json")); z.write(Library.toJson(s).toString(1).toByteArray()); z.closeEntry()
                s.docs.forEach { d ->
                    val f = lib.file(d.id)
                    if (f.exists()) { z.putNextEntry(ZipEntry("docs/${d.id}.pdf")); f.inputStream().use { it.copyTo(z) }; z.closeEntry() }
                }
            }
        }
    }

    /** Renvoie le nombre de documents ajoutés, ou null si le fichier n'est pas une sauvegarde Koaladocs. */
    fun restore(ctx: Context, lib: Library, src: Uri): Int? {
        val dir = File(ctx.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        var state: LibState? = null
        val files = HashMap<String, File>()
        try {
            ctx.contentResolver.openInputStream(src)!!.use { ins ->
                ZipInputStream(ins.buffered()).use { z ->
                    while (true) {
                        val e = z.nextEntry ?: break
                        val n = e.name
                        when {
                            n == "koaladocs.json" -> state = runCatching { Library.parse(JSONObject(z.readBytes().toString(Charsets.UTF_8))) }.getOrNull()
                            n.startsWith("docs/") && n.endsWith(".pdf") && !n.contains("..") -> {
                                val id = n.removePrefix("docs/").removeSuffix(".pdf")
                                if (id.matches(Regex("[A-Za-z0-9-]{1,40}"))) {
                                    val f = File(dir, "$id.pdf"); f.outputStream().use { z.copyTo(it) }; files[id] = f
                                }
                            }
                        }
                    }
                }
            }
            val s = state ?: return null
            return lib.merge(s, files)
        } catch (e: Exception) {
            return null
        } finally {
            dir.deleteRecursively()
        }
    }
}
