package com.koala.docs

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HomeActions(
    val scan: () -> Unit,
    val pickPdf: () -> Unit,
    val pickPhotos: () -> Unit,
    val backup: () -> Unit,
    val restore: () -> Unit,
    val checkUpdate: () -> Unit,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(app: AppState, act: HomeActions) {
    val st by app.lib.state.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(Sort.Recent) }
    val sel = remember { mutableStateListOf<String>() }
    var fab by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var editFolder by remember { mutableStateOf<Folder?>(null) }
    var newFolder by remember { mutableStateOf(false) }
    var manageFolders by remember { mutableStateOf(false) }
    var moveSel by remember { mutableStateOf(false) }
    var deleteSel by remember { mutableStateOf(false) }
    var mergeSel by remember { mutableStateOf<List<String>?>(null) }
    val filter = app.filter
    val focus = LocalFocusManager.current

    // Si le dossier affiché a été supprimé, on revient sur « Tous »
    LaunchedEffect(st) {
        val f = app.filter
        if (f is Filter.In && st.folders.none { it.id == f.folderId }) app.filter = Filter.All
        // On retire de la sélection ce qui n'existe plus
        sel.retainAll(st.docs.map { it.id }.toSet())
    }

    val initial = remember { app.lib.query(filter, "", sort) }
    val hits by produceState(initialValue = initial, st, filter, query, sort) {
        value = withContext(Dispatchers.Default) { app.lib.query(filter, query, sort) }
    }
    val selecting = sel.isNotEmpty()

    BackHandler(enabled = selecting || fab || query.isNotEmpty()) {
        when {
            fab -> fab = false
            selecting -> sel.clear()
            else -> query = ""
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // ---------- En-tête ----------
            if (!selecting) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Logo()
                    Spacer(Modifier.weight(1f))
                    Box {
                        IconBtn(G.Sort) { sortMenu = true }
                        DropdownMenu(sortMenu, { sortMenu = false }, Modifier.background(Card)) {
                            Sort.entries.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s.label, color = if (s == sort) Yellow else Color.White, fontWeight = FontWeight.Bold) },
                                    leadingIcon = { Glyph(G.Check, if (s == sort) Yellow else Color.Transparent, 18.dp) },
                                    onClick = { sort = s; sortMenu = false },
                                )
                            }
                        }
                    }
                    Box {
                        IconBtn(G.More) { menu = true }
                        DropdownMenu(menu, { menu = false }, Modifier.background(Card)) {
                            MenuItem(G.Folder, "Gérer les dossiers") { menu = false; manageFolders = true }
                            MenuItem(G.Backup, "Sauvegarder tout") { menu = false; act.backup() }
                            MenuItem(G.Restore, "Restaurer une sauvegarde") { menu = false; act.restore() }
                            MenuItem(G.Info, "Version ${BuildConfig.VERSION_NAME}") { menu = false; act.checkUpdate() }
                        }
                    }
                }
                SearchBar(query, { query = it }) { focus.clearFocus() }
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBtn(G.Close, Color.White) { sel.clear() }
                    Text(if (sel.size > 1) "${sel.size} sélectionnés" else "1 sélectionné", fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Spacer(Modifier.weight(1f))
                    val all = hits.size == sel.size
                    Text(
                        if (all) "Aucun" else "Tout", color = Yellow, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable {
                            if (all) sel.clear() else { sel.clear(); sel.addAll(hits.map { it.doc.id }) }
                        }.padding(12.dp),
                    )
                }
            }

            // ---------- Dossiers ----------
            val counts = st.docs.groupingBy { it.folder }.eachCount()
            val unfiled = st.docs.count { d -> d.folder == null || st.folders.none { it.id == d.folder } }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Chip("Tous · ${st.docs.size}", filter == Filter.All) { app.filter = Filter.All } }
                item { Chip("Favoris", filter == Filter.Fav, glyph = G.StarFill) { app.filter = Filter.Fav } }
                items(st.folders, key = { it.id }) { f ->
                    val n = counts[f.id] ?: 0
                    Chip(if (n > 0) "${f.name} · $n" else f.name, filter == Filter.In(f.id), Color(f.color)) { app.filter = Filter.In(f.id) }
                }
                if (unfiled > 0) item { Chip("Non classés · $unfiled", filter == Filter.Unfiled) { app.filter = Filter.Unfiled } }
                item { Chip("Dossier", false, glyph = G.Plus) { newFolder = true } }
            }

            // ---------- Documents ----------
            if (hits.isEmpty()) {
                when {
                    st.docs.isEmpty() -> Empty("Aucun document", "Scanne un papier ou importe tes PDF pour commencer.") {
                        Pill("Scanner un document", primary = true, glyph = G.Scan, onClick = act.scan)
                        Spacer(Modifier.height(10.dp))
                        Pill("Importer des PDF", glyph = G.FileIn, onClick = act.pickPdf)
                    }
                    query.isNotBlank() -> Empty("Rien trouvé", "Aucun document ne correspond à « $query ».")
                    else -> Empty("Dossier vide", "Appuie sur + pour ajouter un document ici.")
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(hits, key = { it.doc.id }) { h ->
                        DocCard(
                            app, h, app.lib.folder(h.doc.folder), selected = h.doc.id in sel, selecting = selecting,
                            onClick = {
                                if (selecting) { if (h.doc.id in sel) sel.remove(h.doc.id) else sel.add(h.doc.id) }
                                else app.screen = Screen.Viewer(h.doc.id)
                            },
                            onLong = { if (h.doc.id !in sel) sel.add(h.doc.id) },
                        )
                    }
                }
            }
        }

        // ---------- Barre d'actions de la sélection ----------
        AnimatedVisibility(selecting, Modifier.align(Alignment.BottomCenter), enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            Row(
                Modifier.fillMaxWidth().background(Card).navigationBarsPadding().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                BarAction(G.Share, "Partager") { app.share(sel.toList()) }
                BarAction(G.Merge, "Fusionner", enabled = sel.size >= 2) { mergeSel = hits.map { it.doc.id }.filter { it in sel } }
                BarAction(G.Folder, "Classer") { moveSel = true }
                BarAction(G.Trash, "Supprimer", danger = true) { deleteSel = true }
            }
        }

        // ---------- Bouton + ----------
        if (!selecting) {
            if (fab) Box(Modifier.fillMaxSize().background(Navy.copy(alpha = 0.75f)).clickable(remember { MutableInteractionSource() }, null) { fab = false })
            Column(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp), horizontalAlignment = Alignment.End) {
                AnimatedVisibility(fab, enter = fadeIn() + slideInVertically { it / 4 }, exit = fadeOut() + slideOutVertically { it / 4 }) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        FabItem(G.Scan, "Scanner un document") { fab = false; act.scan() }
                        FabItem(G.FileIn, "Importer des PDF") { fab = false; act.pickPdf() }
                        FabItem(G.Photo, "Photos → PDF") { fab = false; act.pickPhotos() }
                        Spacer(Modifier.height(4.dp))
                    }
                }
                val rot by animateFloatAsState(if (fab) 45f else 0f, label = "fab")
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(Yellow).clickable { fab = !fab },
                    contentAlignment = Alignment.Center,
                ) { Glyph(G.Plus, Navy, 30.dp, Modifier.rotate(rot)) }
            }
        }
    }

    // ---------- Fenêtres ----------
    if (newFolder) FolderDialog(null, onDismiss = { newFolder = false }) { n, c -> app.filter = Filter.In(app.lib.addFolder(n, c).id); newFolder = false }
    editFolder?.let { f ->
        FolderDialog(f, onDismiss = { editFolder = null }, onDelete = { app.lib.deleteFolder(f.id); editFolder = null }) { n, c -> app.lib.editFolder(f.id, n, c); editFolder = null }
    }
    if (manageFolders) FoldersDialog(app, onEdit = { editFolder = it }, onNew = { newFolder = true }) { manageFolders = false }
    if (moveSel) FolderPicker(st.folders, null, title = "Classer dans…", onDismiss = { moveSel = false }) { fid ->
        sel.toList().forEach { id -> app.lib.update(id) { it.copy(folder = fid) } }
        app.msg(if (sel.size > 1) "${sel.size} documents classés" else "Document classé")
        sel.clear(); moveSel = false
    }
    if (deleteSel) ConfirmDialog(
        "Supprimer ${if (sel.size > 1) "${sel.size} documents" else "ce document"} ?",
        "Ils seront effacés du téléphone. Cette action est définitive.", "Supprimer",
        onDismiss = { deleteSel = false },
    ) { app.delete(sel.toList()); sel.clear(); deleteSel = false }
    mergeSel?.let { ids -> MergeDialog(app, ids, onDismiss = { mergeSel = null }) { mergeSel = null; sel.clear() } }
}

@Composable
private fun MenuItem(g: G, label: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label, color = Color.White, fontWeight = FontWeight.Bold) }, leadingIcon = { Glyph(g, Yellow, 20.dp) }, onClick = onClick)
}

@Composable
private fun SearchBar(q: String, onChange: (String) -> Unit, onDone: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().height(50.dp).clip(RoundedCornerShape(25.dp))
            .background(Card).border(1.dp, Line, RoundedCornerShape(25.dp)).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(G.Search, Muted, 20.dp)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (q.isEmpty()) Text("Nom, mot-clé, contenu…", color = Muted, fontSize = 16.sp)
            BasicTextField(
                q, onChange, singleLine = true, modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.copy(fontSize = 16.sp, color = Color.White),
                cursorBrush = SolidColor(Yellow),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onDone() }),
            )
        }
        if (q.isNotEmpty()) IconBtn(G.Close, Muted, 36) { onChange("") }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocCard(app: AppState, h: Library.Hit, folder: Folder?, selected: Boolean, selecting: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    val d = h.doc
    val ctx = LocalContext.current
    val thumb by produceState(Thumbs.cached(d), d.id, d.modified) { value = Thumbs.get(ctx, app.lib.file(d.id), d) }
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).background(if (selected) CardHi else Card)
            .border(if (selected) 2.dp else 1.dp, if (selected) Yellow else Line, RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLong)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.85f).padding(start = 6.dp, end = 6.dp, top = 6.dp).clip(RoundedCornerShape(8.dp)).background(Paper)) {
            thumb?.let {
                Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
            } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Glyph(G.Doc, Muted, 26.dp) }
            if (folder != null) Box(
                Modifier.align(Alignment.BottomStart).padding(4.dp).clip(RoundedCornerShape(6.dp)).background(Navy.copy(alpha = 0.85f)).padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Color(folder.color)))
                    Spacer(Modifier.width(4.dp))
                    Text(folder.name, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                }
            }
            if (selecting) Box(
                Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).clip(CircleShape)
                    .background(if (selected) Yellow else Navy.copy(alpha = 0.6f)).border(2.dp, if (selected) Yellow else Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) { if (selected) Glyph(G.Check, Navy, 13.dp) }
            else if (d.fav) Box(
                Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).clip(CircleShape).background(Navy.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center,
            ) { Glyph(G.StarFill, Yellow, 13.dp) }
        }
        Column(Modifier.padding(horizontal = 8.dp, vertical = 7.dp)) {
            Text(d.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
            Spacer(Modifier.height(2.dp))
            Text("${d.pages} p. · ${fmtSize(d.size)}", color = Muted, fontSize = 11.sp, maxLines = 1)
            Text(fmtDate(d.added), color = Muted, fontSize = 11.sp, maxLines = 1)
            if (h.inText) Text("Dans le texte", color = Yellow, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BarAction(g: G, label: String, enabled: Boolean = true, danger: Boolean = false, onClick: () -> Unit) {
    val c = when { !enabled -> Muted.copy(alpha = 0.4f); danger -> Red; else -> Color.White }
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Glyph(g, if (enabled && !danger) Yellow else c, 24.dp)
        Spacer(Modifier.height(4.dp))
        Text(label, color = c, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FabItem(g: G, label: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(26.dp)).clickable(onClick = onClick)) {
        Text(
            label, fontWeight = FontWeight.Bold, fontSize = 15.sp,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Card).border(1.dp, Line, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 9.dp),
        )
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(52.dp).clip(CircleShape).background(CardHi).border(1.dp, Line, CircleShape), contentAlignment = Alignment.Center) { Glyph(g, Yellow, 24.dp) }
        Spacer(Modifier.width(6.dp))
    }
}
