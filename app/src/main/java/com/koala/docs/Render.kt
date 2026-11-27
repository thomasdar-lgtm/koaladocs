package com.koala.docs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

/** Affichage des pages avec le moteur PDF d'Android (rapide, fidèle). Un seul rendu à la fois par document. */
class PdfView private constructor(private val pfd: ParcelFileDescriptor, private val r: PdfRenderer) : Closeable {
    val count = r.pageCount
    /** Taille de chaque page en points (1/72 de pouce). */
    val sizes: List<Pair<Int, Int>> = (0 until count).map { i -> r.openPage(i).use { it.width to it.height } }
    private val lock = Mutex()
    @Volatile private var closed = false

    /** Pages déjà dessinées (partagées entre la liste et le plein écran), ~64 Mo max. */
    private val cache = object : LruCache<String, Bitmap>(64 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun cached(index: Int, widthPx: Int): Bitmap? = cache.get("${index}_${widthPx.coerceIn(16, 4096)}")

    suspend fun render(index: Int, widthPx: Int, keep: Boolean = true): Bitmap? = withContext(Dispatchers.IO) {
        val key = "${index}_${widthPx.coerceIn(16, 4096)}"
        cache.get(key)?.let { return@withContext it }
        lock.withLock {
            cache.get(key)?.let { return@withLock it }
            if (closed || index !in 0 until count) return@withLock null
            runCatching {
                r.openPage(index).use { p ->
                    val w = widthPx.coerceIn(16, 4096)
                    val h = (w.toLong() * p.height / p.width).toInt().coerceIn(16, 6000)
                    val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    b.eraseColor(Color.WHITE)
                    p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    b.prepareToDraw() // envoi à la carte graphique hors de l'écran, pour éviter les à-coups
                    if (keep) cache.put(key, b)
                    b
                }
            }.getOrNull()
        }
    }

    override fun close() {
        closed = true
        runCatching { r.close() }; runCatching { pfd.close() }
    }

    suspend fun closeSafely() = lock.withLock { close() }

    companion object {
        fun open(f: File): PdfView? = runCatching {
            val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
            try { PdfView(pfd, PdfRenderer(pfd)) } catch (e: Throwable) { pfd.close(); throw e }
        }.getOrNull()
    }
}

/** Miniatures de la première page : mémoire + disque (cache). */
object Thumbs {
    private val memSized = object : LruCache<String, Bitmap>(40 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val lock = Mutex()

    private fun key(d: Doc) = "${d.id}_${d.modified}"

    fun cached(d: Doc): Bitmap? = memSized.get(key(d))

    suspend fun get(ctx: Context, file: File, d: Doc, width: Int = 360): Bitmap? = withContext(Dispatchers.IO) {
        memSized.get(key(d))?.let { return@withContext it }
        lock.withLock {
            memSized.get(key(d))?.let { return@withLock it }
            val dir = File(ctx.cacheDir, "thumbs").apply { mkdirs() }
            val f = File(dir, key(d) + ".jpg")
            var b = if (f.exists()) BitmapFactory.decodeFile(f.path) else null
            if (b == null) {
                val v = PdfView.open(file) ?: return@withLock null
                b = try { v.render(0, width) } finally { v.close() }
                if (b != null) {
                    dir.listFiles()?.filter { it.name.startsWith(d.id + "_") }?.forEach { it.delete() }
                    runCatching { f.outputStream().use { o -> b.compress(Bitmap.CompressFormat.JPEG, 82, o) } }
                }
            }
            b?.also { memSized.put(key(d), it) }
        }
    }
}

object Images {
    /** Ouvre une photo dans le bon sens, réduite à `maxSide` pixels sur le plus grand côté. */
    fun decode(ctx: Context, uri: Uri, maxSide: Int = 2400): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { dec, info, _ ->
                val s = info.size
                val big = maxOf(s.width, s.height)
                if (big > maxSide) dec.setTargetSize(s.width * maxSide / big, s.height * maxSide / big)
                dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            var sample = 1
            while (maxOf(o.outWidth, o.outHeight) / (sample * 2) >= maxSide) sample *= 2
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        }
    }.getOrNull()

    /** Photos → PDF : une page A4 par photo (portrait ou paysage selon la photo). Renvoie le nombre de pages. */
    fun toPdf(ctx: Context, uris: List<Uri>, dst: File): Int {
        var n = 0
        PdfOps.newDoc().use { doc ->
            for (u in uris) {
                val b = decode(ctx, u) ?: continue
                val land = b.width > b.height
                val (w, h) = if (land) 842f to 595f else 595f to 842f
                PdfOps.addImagePage(doc, b, w, h, 0f, 0.88f)
                b.recycle()
                n++
            }
            if (n > 0) doc.save(dst)
        }
        return n
    }
}

enum class Level(val label: String, val detail: String, val dpi: Int, val quality: Float) {
    Light("Légère", "Très bonne qualité", 150, 0.85f),
    Medium("Moyenne", "Bon compromis", 110, 0.72f),
    Strong("Forte", "Fichier le plus petit", 80, 0.55f),
}

object Compressor {
    /**
     * Recrée chaque page en image JPEG à la résolution choisie. Efficace sur les scans et photos ;
     * le texte n'est plus sélectionnable ensuite. Renvoie le nombre de pages.
     */
    suspend fun run(src: File, dst: File, level: Level, onProgress: (Float) -> Unit): Int = withContext(Dispatchers.IO) {
        val v = PdfView.open(src) ?: error("illisible")
        try {
            PdfOps.newDoc().use { doc ->
                for (i in 0 until v.count) {
                    val (pw, ph) = v.sizes[i]
                    val px = (pw * level.dpi / 72f).toInt().coerceIn(200, 4000)
                    val b = v.render(i, px) ?: error("page $i")
                    PdfOps.addImagePage(doc, b, pw.toFloat(), ph.toFloat(), 0f, level.quality)
                    b.recycle()
                    onProgress((i + 1f) / v.count)
                }
                doc.save(dst)
            }
            v.count
        } finally {
            v.close()
        }
    }
}
