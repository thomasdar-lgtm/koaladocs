package com.koala.docs

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Captures de contrôle des écrans (téléphone type S25), avec une bibliothèque d'exemple. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class ScreenshotTest {
    private fun seed(): Library {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        PdfOps.init(ctx)
        ctx.filesDir.deleteRecursively(); ctx.filesDir.mkdirs()
        val lib = Library(ctx)
        val f = lib.state.value.folders.associateBy { it.name }
        val tmp = File(ctx.cacheDir, "seed").apply { mkdirs() }
        listOf(
            Triple("Facture EDF novembre", "Maison", 2), Triple("Carte grise Golf 5", "Voiture", 1),
            Triple("Relevé de compte octobre", "Banque", 4), Triple("Ordonnance", "Santé", 1),
            Triple("Bordereau Mondial Relay", "Vinted", 1), Triple("Notice autoradio", null, 18),
        ).forEachIndexed { i, (n, folder, pages) ->
            val src = File(tmp, "$i.pdf")
            PDDocument().use { d -> repeat(pages) { d.addPage(PDPage(PDRectangle.A4)) }; d.save(src) }
            val doc = lib.add(src, n, folder?.let { f[it]?.id }, pages, if (i == 0) listOf("EDF", "électricité") else emptyList())
            if (i == 1) lib.update(doc.id) { it.copy(fav = true) }
        }
        return lib
    }

    private fun shoot(name: String, setup: (MainActivity) -> Unit = {}) {
        val sc = ActivityScenario.launch(MainActivity::class.java)
        sc.onActivity { setup(it) }
        repeat(10) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(150) }
        sc.onActivity { a ->
            val v = a.window.decorView
            val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(bmp))
            // Les fenêtres de dialogue sont dans leur propre fenêtre : on les dessine par-dessus
            val dir = File("build/shots").apply { mkdirs() }
            File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        sc.close()
    }

    @Test
    fun accueil() { seed(); shoot("1-accueil") }

    @Test
    fun accueilVide() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.filesDir.deleteRecursively(); ctx.filesDir.mkdirs()
        shoot("2-vide")
    }

    @Test
    fun dossier() { val lib = seed(); shoot("3-dossier") { it.app.filter = Filter.In(lib.state.value.folders.first { f -> f.name == "Maison" }.id); it.app.folderOpen = true } }

    @Test
    fun pages() { val lib = seed(); shoot("4-pages") { it.app.screen = Screen.Pages(lib.state.value.docs.last().id) } }

    @Test
    fun lecteur() { val lib = seed(); shoot("5-lecteur") { it.app.screen = Screen.Viewer(lib.state.value.docs.first().id) } }
}
