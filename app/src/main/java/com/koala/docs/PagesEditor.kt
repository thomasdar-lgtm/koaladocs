package com.koala.docs

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Une vignette de l'éditeur : page d'origine + rotation ; `key` reste stable quand on déplace. */
private data class Tile(val key: Int, val index: Int, val rotate: Int = 0)

/** Déplace d'un cran les éléments sélectionnés, en gardant leur ordre (logique pure, testée). */
fun <T> moveSelected(list: List<T>, selected: (T) -> Boolean, delta: Int): List<T> {
    val l = list.toMutableList()
    val idx = if (delta < 0) l.indices.toList() else l.indices.reversed()
    for (i in idx) {
        val j = i + delta
        if (selected(l[i]) && j in l.indices && !selected(l[j])) { val t = l[i]; l[i] = l[j]; l[j] = t }
    }
    return l
}

@Composable
fun PagesScreen(app: AppState, docId: String) {
    val d = app.lib.doc(docId)
    if (d == null) { LaunchedEffect(Unit) { app.screen = Screen.Home }; return }
    val file = app.lib.file(d.id)
    val view by produceState<PdfView?>(null, docId) {
        val v = withContext(Dispatchers.IO) { PdfView.open(file) }
        value = v
        awaitDispose { if (v != null) CoroutineScope(Dispatchers.IO).launch { v.closeSafely() } }
    }
    val tiles = remember { mutableStateListOf<Tile>() }
    val sel = remember { mutableStateListOf<Int>() } // clés sélectionnées
    var quit by remember { mutableStateOf(false) }
    var extract by remember { mutableStateOf(false) }
    LaunchedEffect(view) { view?.let { v -> if (tiles.isEmpty()) tiles.addAll((0 until v.count).map { Tile(it, it) }) } }

    val changed = tiles.size != (view?.count ?: 0) || tiles.withIndex().any { (i, t) -> t.index != i || t.rotate % 360 != 0 }
    fun leave() { if (changed) quit = true else app.screen = Screen.Viewer(docId) }
    BackHandler { leave() }

    Column(Modifier.fillMaxSize()) {
        TopBar("Organiser les pages", d.name, onBack = { leave() }) {
            val all = tiles.isNotEmpty() && sel.size == tiles.size
            Text(
                if (all) "Aucune" else "Toutes", color = Yellow, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable {
                    if (all) sel.clear() else { sel.clear(); sel.addAll(tiles.map { it.key }) }
                }.padding(12.dp),
            )
        }
        val v = view
        if (v == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Yellow) }
        } else {
            LazyVerticalGrid(
                GridCells.Adaptive(106.dp), Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(tiles, key = { _, t -> t.key }) { pos, t ->
                    PageTile(v, t, pos, t.key in sel) { if (t.key in sel) sel.remove(t.key) else sel.add(t.key) }
                }
            }
        }

        // ---------- Outils ----------
        Column(Modifier.fillMaxWidth().background(Card).navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            val any = sel.isNotEmpty()
            Text(
                if (!any) "Touche des pages pour les sélectionner" else "${sel.size} page${if (sel.size > 1) "s" else ""} sélectionnée${if (sel.size > 1) "s" else ""}",
                color = Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                fun rot(dg: Int) { for (i in tiles.indices) if (tiles[i].key in sel) tiles[i] = tiles[i].copy(rotate = (tiles[i].rotate + dg + 360) % 360) }
                fun move(dl: Int) { val n = moveSelected(tiles.toList(), { it.key in sel }, dl); tiles.clear(); tiles.addAll(n) }
                Tool(G.RotL, "Gauche", any) { rot(-90) }
                Tool(G.RotR, "Droite", any) { rot(90) }
                Tool(G.Left, "Avant", any) { move(-1) }
                Tool(G.Right, "Après", any) { move(1) }
                Tool(G.Split, "Extraire", any) { extract = true }
                Tool(G.Trash, "Retirer", any && sel.size < tiles.size, danger = true) { tiles.removeAll { it.key in sel }; sel.clear() }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("Copie", Modifier.weight(1f), glyph = G.Plus, enabled = changed) {
                    app.rebuild(docId, tiles.map { PageOp(it.index, it.rotate) }, asCopy = true, copyName = "${d.name} (modifié)")
                }
                Pill("Enregistrer", Modifier.weight(1f), primary = true, enabled = changed) {
                    app.rebuild(docId, tiles.map { PageOp(it.index, it.rotate) }, asCopy = false)
                }
            }
        }
    }

    if (quit) ConfirmDialog("Abandonner les modifications ?", "Les changements faits sur les pages ne seront pas enregistrés.", "Abandonner", onDismiss = { quit = false }) {
        quit = false; app.screen = Screen.Viewer(docId)
    }
    if (extract) {
        val chosen = tiles.filter { it.key in sel }
        val nums = chosen.map { it.index + 1 }
        ConfirmDialog(
            "Extraire ${chosen.size} page${if (chosen.size > 1) "s" else ""} ?",
            "Un nouveau document sera créé avec ces pages (dans l'ordre affiché). « ${d.name} » ne change pas.",
            "Extraire", danger = false, onDismiss = { extract = false },
        ) {
            extract = false
            val label = if (nums.size == 1) "p. ${nums[0]}" else "${nums.size} pages"
            app.rebuild(docId, chosen.map { PageOp(it.index, it.rotate) }, asCopy = true, copyName = "${d.name} ($label)")
        }
    }
}

@Composable
private fun PageTile(v: PdfView, t: Tile, pos: Int, selected: Boolean, onClick: () -> Unit) {
    val raw by produceState<Bitmap?>(null, v, t.index) { value = v.render(t.index, 260) }
    val bmp = remember(raw, t.rotate) {
        raw?.let { b -> if (t.rotate == 0) b else Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(t.rotate.toFloat()) }, true) }
    }
    val (pw, ph) = v.sizes[t.index]
    val ratio = if (t.rotate % 180 == 0) pw.toFloat() / ph else ph.toFloat() / pw
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(12.dp))
                .background(if (selected) CardHi else Navy2)
                .border(if (selected) 2.5.dp else 1.dp, if (selected) Yellow else Line, RoundedCornerShape(12.dp)).padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.aspectRatio(ratio.coerceIn(0.2f, 5f)).background(Color.White)) {
                bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
            }
            if (selected) Box(
                Modifier.align(Alignment.TopEnd).size(24.dp).clip(CircleShape).background(Yellow),
                contentAlignment = Alignment.Center,
            ) { Glyph(G.Check, Navy, 15.dp) }
        }
        Text(
            "${pos + 1}" + if (pos != t.index) "  (p. ${t.index + 1})" else "",
            color = if (selected) Yellow else Soft, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
        )
    }
}

@Composable
private fun Tool(g: G, label: String, enabled: Boolean, danger: Boolean = false, onClick: () -> Unit) {
    val c = when { !enabled -> Muted.copy(alpha = 0.35f); danger -> Red; else -> Yellow }
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Glyph(g, c, 22.dp)
        Spacer(Modifier.height(3.dp))
        Text(label, color = if (enabled) Color.White else Muted.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
