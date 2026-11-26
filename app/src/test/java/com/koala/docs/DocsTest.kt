package com.koala.docs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DocsTest {
    private lateinit var ctx: Context
    private lateinit var dir: File

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        PdfOps.init(ctx)
        ctx.filesDir.deleteRecursively(); ctx.filesDir.mkdirs()
        dir = File(ctx.cacheDir, "t").apply { deleteRecursively(); mkdirs() }
    }

    /** PDF de test : chaque page a une largeur différente (200, 201…) pour reconnaître son origine. */
    private fun pdf(name: String, pages: Int, base: Int = 200, text: String? = null): File {
        val f = File(dir, "$name.pdf")
        PDDocument().use { d ->
            repeat(pages) { i ->
                val p = PDPage(PDRectangle((base + i).toFloat(), 400f))
                d.addPage(p)
                if (text != null) PDPageContentStream(d, p).use { s ->
                    s.beginText(); s.setFont(PDType1Font.HELVETICA, 12f); s.newLineAtOffset(20f, 200f); s.showText("$text $i"); s.endText()
                }
            }
            d.save(f)
        }
        return f
    }

    private fun widths(f: File) = PDDocument.load(f).use { d -> (0 until d.numberOfPages).map { d.getPage(it).mediaBox.width.toInt() } }
    private fun rotations(f: File) = PDDocument.load(f).use { d -> (0 until d.numberOfPages).map { d.getPage(it).rotation } }

    @Test
    fun fusionGardeLOrdre() {
        val out = File(dir, "m.pdf")
        PdfOps.merge(listOf(pdf("a", 2, 200), pdf("b", 3, 300)), out)
        assertEquals(listOf(200, 201, 300, 301, 302), widths(out))
    }

    @Test
    fun reorganiserTournerRetirer() {
        val src = pdf("a", 4)
        val out = File(dir, "r.pdf")
        PdfOps.rebuild(src, listOf(PageOp(3), PageOp(0, 90), PageOp(2, -90)), out)
        assertEquals(listOf(203, 200, 202), widths(out))
        assertEquals(listOf(0, 90, 270), rotations(out))
    }

    @Test
    fun plagesDePages() {
        assertEquals(listOf(0..2, 4..4, 6..9), PdfOps.parseRanges("1-3, 5, 7-", 10))
        assertEquals(listOf(0..1), PdfOps.parseRanges("-2", 10))
        assertNull(PdfOps.parseRanges("3-1", 10))
        assertNull(PdfOps.parseRanges("12", 10))
        assertNull(PdfOps.parseRanges("abc", 10))
        assertNull(PdfOps.parseRanges("", 10))
    }

    @Test
    fun decoupe() {
        val parts = PdfOps.split(pdf("a", 5), listOf(0..1, 2..2, 3..4), File(dir, "parts"))
        assertEquals(listOf(listOf(200, 201), listOf(202), listOf(203, 204)), parts.map { widths(it) })
    }

    @Test
    fun motDePasse() {
        val f = File(dir, "lock.pdf")
        PDDocument().use { d ->
            d.addPage(PDPage(PDRectangle(210f, 300f))); d.addPage(PDPage(PDRectangle(211f, 300f)))
            d.protect(StandardProtectionPolicy("proprio", "1234", AccessPermission()).apply { encryptionKeyLength = 128 })
            d.save(f)
        }
        assertEquals(Inspect.NeedPassword, PdfOps.inspect(f))
        assertNull(PdfOps.unlock(f, "faux", File(dir, "x.pdf")))
        val ok = File(dir, "ok.pdf")
        assertEquals(2, PdfOps.unlock(f, "1234", ok))
        val r = PdfOps.inspect(ok)
        assertTrue(r is Inspect.Ok && !r.restricted && r.pages == 2)
    }

    @Test
    fun pasUnPdf() {
        val f = File(dir, "faux.pdf").apply { writeText("bonjour") }
        assertEquals(Inspect.Invalid, PdfOps.inspect(f))
    }

    @Test
    fun texteExtrait() {
        val t = PdfOps.extractText(pdf("t", 2, text = "Facture EDF"))
        assertTrue(t, t.contains("Facture EDF 0") && t.contains("Facture EDF 1"))
    }

    @Test
    fun bibliothequeRechercheEtPersistance() {
        val lib = Library(ctx, "lib.json")
        assertEquals(6, lib.state.value.folders.size)
        val maison = lib.state.value.folders.first { it.name == "Maison" }
        val a = lib.add(pdf("a", 2), "Facture électricité.pdf", maison.id, 2, listOf("EDF"))
        val b = lib.add(pdf("b", 1), "Carte grise", null, 1)
        lib.saveText(b.id, "Certificat d'immatriculation GOLF")
        assertEquals("Facture électricité", a.name)
        assertTrue(lib.file(a.id).exists())

        // nom sans accent, mot-clé, nom du dossier, contenu
        assertEquals(listOf(a.id), lib.query(Filter.All, "electricite", Sort.Recent).map { it.doc.id })
        assertEquals(listOf(a.id), lib.query(Filter.All, "edf", Sort.Recent).map { it.doc.id })
        assertEquals(listOf(a.id), lib.query(Filter.All, "maison", Sort.Recent).map { it.doc.id })
        val hit = lib.query(Filter.All, "golf", Sort.Recent).single()
        assertTrue(hit.doc.id == b.id && hit.inText)
        assertEquals(listOf(b.id), lib.query(Filter.Unfiled, "", Sort.Recent).map { it.doc.id })
        assertEquals(listOf(b.id, a.id), lib.query(Filter.All, "", Sort.Name).map { it.doc.id })

        lib.update(b.id) { it.copy(fav = true) }
        lib.deleteFolder(maison.id)
        val again = Library(ctx, "lib.json")
        assertEquals(5, again.state.value.folders.size)
        assertEquals(null, again.doc(a.id)?.folder)
        assertTrue(again.doc(b.id)!!.fav)

        again.delete(listOf(a.id))
        assertTrue(!again.file(a.id).exists())
        assertEquals(1, Library(ctx, "lib.json").state.value.docs.size)
    }

    @Test
    fun restaurationSansDoublon() {
        val lib = Library(ctx, "lib.json")
        val a = lib.add(pdf("a", 1), "A", null, 1)
        val backup = lib.state.value.copy(docs = lib.state.value.docs + Doc(id = "zz", name = "B", pages = 1))
        val files = mapOf(a.id to pdf("a2", 1), "zz" to pdf("b", 1))
        assertEquals(1, lib.merge(backup, files))
        assertEquals(setOf("A", "B"), lib.state.value.docs.map { it.name }.toSet())
        assertEquals(0, lib.merge(backup, mapOf("zz" to pdf("b", 1))))
    }

    @Test
    fun deplacerLaSelection() {
        val l = listOf(1, 2, 3, 4, 5)
        assertEquals(listOf(2, 1, 4, 3, 5), moveSelected(l, { it == 2 || it == 4 }, -1))
        assertEquals(listOf(1, 3, 2, 5, 4), moveSelected(l, { it == 2 || it == 4 }, 1))
        assertEquals(listOf(1, 2, 3, 4, 5), moveSelected(l, { it == 1 || it == 2 }, -1))
        assertEquals(listOf(1, 2, 3, 4, 5), moveSelected(l, { it == 5 }, 1))
    }
}
