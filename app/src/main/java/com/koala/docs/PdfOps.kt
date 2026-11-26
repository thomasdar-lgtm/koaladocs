package com.koala.docs

import android.content.Context
import android.graphics.Bitmap
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

/** Une page dans le résultat : son numéro d'origine (0 = première) et la rotation à ajouter. */
data class PageOp(val index: Int, val rotate: Int = 0)

sealed interface Inspect {
    data class Ok(val pages: Int, val restricted: Boolean) : Inspect
    data object NeedPassword : Inspect
    data object Invalid : Inspect
}

/** Opérations sur les PDF (PdfBox). Sans perte : les pages sont recopiées telles quelles. */
object PdfOps {
    @Volatile private var ready = false

    fun init(ctx: Context) {
        if (!ready) { PDFBoxResourceLoader.init(ctx.applicationContext); ready = true }
    }

    private fun mem() = MemoryUsageSetting.setupMixed(48L * 1024 * 1024)

    private fun load(f: File, password: String? = null): PDDocument =
        if (password == null) PDDocument.load(f, mem()) else PDDocument.load(f, password, mem())

    fun isPdf(f: File): Boolean = runCatching {
        f.inputStream().use { s -> val b = ByteArray(1024); val n = s.read(b); n > 4 && String(b, 0, n, Charsets.ISO_8859_1).contains("%PDF") }
    }.getOrDefault(false)

    fun inspect(f: File): Inspect {
        if (!isPdf(f)) return Inspect.Invalid
        return try {
            load(f).use { Inspect.Ok(it.numberOfPages, it.isEncrypted) }
        } catch (e: InvalidPasswordException) {
            Inspect.NeedPassword
        } catch (e: Throwable) {
            Inspect.Invalid
        }
    }

    /** Enregistre une copie sans protection (mot de passe ou restrictions). Renvoie le nombre de pages, null si mauvais mot de passe. */
    fun unlock(src: File, password: String?, dst: File): Int? = try {
        load(src, password).use { d ->
            d.isAllSecurityToBeRemoved = true
            d.save(dst)
            d.numberOfPages
        }
    } catch (e: InvalidPasswordException) {
        null
    }

    fun pageCount(f: File): Int = load(f).use { it.numberOfPages }

    fun merge(files: List<File>, dst: File) {
        val m = PDFMergerUtility()
        files.forEach { m.addSource(it) }
        m.destinationFileName = dst.absolutePath
        m.mergeDocuments(mem())
    }

    /** Nouveau PDF avec les pages choisies, dans l'ordre donné, avec rotation éventuelle. */
    fun rebuild(src: File, ops: List<PageOp>, dst: File) {
        require(ops.isNotEmpty())
        load(src).use { s ->
            PDDocument(mem()).use { out ->
                for (op in ops) {
                    val page = out.importPage(s.getPage(op.index))
                    if (op.rotate != 0) page.rotation = ((page.rotation + op.rotate) % 360 + 360) % 360
                }
                out.save(dst)
            }
        }
    }

    /** Lit « 1-3, 5, 8- » (numéros à partir de 1) en plages d'index (à partir de 0). Null si invalide. */
    fun parseRanges(text: String, pages: Int): List<IntRange>? {
        val parts = text.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        val out = ArrayList<IntRange>()
        for (p in parts) {
            val m = Regex("^(\\d*)\\s*[-–]\\s*(\\d*)$").find(p)
            val (a, b) = when {
                m != null -> {
                    val x = m.groupValues[1].toIntOrNull() ?: 1
                    val y = m.groupValues[2].toIntOrNull() ?: pages
                    x to y
                }
                else -> p.toIntOrNull()?.let { it to it } ?: return null
            }
            if (a < 1 || b > pages || a > b) return null
            out += (a - 1)..(b - 1)
        }
        return out
    }

    /** Découpe en plusieurs fichiers, un par plage. */
    fun split(src: File, ranges: List<IntRange>, outDir: File): List<File> {
        outDir.mkdirs()
        return ranges.mapIndexed { i, r ->
            File(outDir, "part$i.pdf").also { rebuild(src, r.map { PageOp(it) }, it) }
        }
    }

    fun extractText(f: File, maxPages: Int = 80): String = load(f).use { d ->
        PDFTextStripper().apply { startPage = 1; endPage = minOf(d.numberOfPages, maxPages) }.getText(d)
    }

    /** Ajoute une page contenant une image, centrée avec une marge (0 = pleine page). */
    fun addImagePage(doc: PDDocument, bmp: Bitmap, w: Float, h: Float, margin: Float, quality: Float) {
        val page = PDPage(PDRectangle(w, h))
        doc.addPage(page)
        val img = JPEGFactory.createFromImage(doc, bmp, quality)
        val aw = w - 2 * margin; val ah = h - 2 * margin
        val s = minOf(aw / bmp.width, ah / bmp.height)
        val iw = bmp.width * s; val ih = bmp.height * s
        PDPageContentStream(doc, page).use { it.drawImage(img, (w - iw) / 2, (h - ih) / 2, iw, ih) }
    }

    fun newDoc() = PDDocument(mem())
}
