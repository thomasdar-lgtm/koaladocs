package com.koala.docs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, danger: Boolean = true, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    KDialog(title, onDismiss, buttons = {
        Pill("Annuler", onClick = onDismiss)
        Pill(confirm, primary = true, danger = danger, onClick = onConfirm)
    }) { Text(text, color = Soft, fontSize = 15.sp, lineHeight = 21.sp) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FolderDialog(initial: Folder?, onDismiss: () -> Unit, onDelete: (() -> Unit)? = null, onSave: (String, Long) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var color by remember { mutableStateOf(initial?.color ?: FolderColors[0]) }
    var confirmDelete by remember { mutableStateOf(false) }
    KDialog(if (initial == null) "Nouveau dossier" else "Modifier le dossier", onDismiss, buttons = {
        if (onDelete != null) { IconBtn(G.Trash, Red) { confirmDelete = true }; Spacer(Modifier.weight(1f)) }
        Pill("Annuler", onClick = onDismiss)
        Pill("Enregistrer", primary = true, enabled = name.isNotBlank()) { onSave(name, color) }
    }) {
        Field(name, { name = it.take(30) }, "Nom du dossier")
        Spacer(Modifier.height(16.dp))
        Text("Couleur", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FolderColors.forEach { c ->
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(Color(c))
                        .border(3.dp, if (c == color) Color.White else Color.Transparent, CircleShape).clickable { color = c },
                    contentAlignment = Alignment.Center,
                ) { if (c == color) Glyph(G.Check, Navy, 20.dp) }
            }
        }
    }
    if (confirmDelete) ConfirmDialog(
        "Supprimer « ${initial?.name} » ?", "Les documents du dossier ne sont pas effacés : ils passent dans « Non classés ».", "Supprimer",
        onDismiss = { confirmDelete = false },
    ) { confirmDelete = false; onDelete?.invoke() }
}

@Composable
fun FoldersDialog(app: AppState, onEdit: (Folder) -> Unit, onNew: () -> Unit, onDismiss: () -> Unit) {
    val st by app.lib.state.collectAsState()
    KDialog("Dossiers", onDismiss, buttons = {
        Pill("Nouveau", glyph = G.Plus, onClick = onNew)
        Pill("Fermer", primary = true, onClick = onDismiss)
    }) {
        st.folders.forEachIndexed { i, f ->
            val n = st.docs.count { it.folder == f.id }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onEdit(f) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(Color(f.color)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(if (n <= 1) "$n document" else "$n documents", color = Muted, fontSize = 12.sp)
                }
                IconBtn(G.Left, Muted, 38, enabled = i > 0, modifier = Modifier.rotate90()) { app.lib.moveFolder(f.id, -1) }
                IconBtn(G.Right, Muted, 38, enabled = i < st.folders.lastIndex, modifier = Modifier.rotate90()) { app.lib.moveFolder(f.id, 1) }
                IconBtn(G.Edit, Yellow, 38) { onEdit(f) }
            }
        }
        if (st.folders.isEmpty()) Text("Aucun dossier.", color = Muted)
    }
}

/** Choix d'un dossier (ou « Non classé » = null). */
@Composable
fun FolderPicker(folders: List<Folder>, current: String?, title: String, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    KDialog(title, onDismiss, buttons = { Pill("Annuler", onClick = onDismiss) }) {
        (folders.map { it.id to it } + (null to null)).forEach { (id, f) ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (id == current) CardHi else Color.Transparent)
                    .clickable { onPick(id) }.padding(horizontal = 10.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (f != null) Box(Modifier.size(14.dp).clip(CircleShape).background(Color(f.color)))
                else Box(Modifier.size(14.dp).clip(CircleShape).border(2.dp, Muted, CircleShape))
                Spacer(Modifier.width(14.dp))
                Text(f?.name ?: "Non classé", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                if (id == current) Glyph(G.Check, Yellow, 20.dp)
            }
        }
    }
}

/** Nom, dossier, mots-clés, favori. Sert aussi juste après un ajout pour classer le document. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InfoDialog(app: AppState, docId: String, title: String = "Classer le document", onDismiss: () -> Unit) {
    val d = app.lib.doc(docId) ?: return
    val st by app.lib.state.collectAsState()
    var name by remember { mutableStateOf(d.name) }
    var folder by remember { mutableStateOf(d.folder) }
    val tags = remember { mutableStateListOf<String>().apply { addAll(d.tags) } }
    var tag by remember { mutableStateOf("") }
    fun addTag(t: String) {
        val v = t.trim().trimStart('#')
        if (v.isNotEmpty() && tags.none { norm(it) == norm(v) }) tags.add(v)
        tag = ""
    }
    KDialog(title, onDismiss, buttons = {
        Pill("Annuler", onClick = onDismiss)
        Pill("Enregistrer", primary = true, enabled = name.isNotBlank()) {
            if (tag.isNotBlank()) addTag(tag)
            app.lib.update(docId) { it.copy(name = Library.cleanName(name), folder = folder, tags = tags.toList()) }
            onDismiss()
        }
    }) {
        Field(name, { name = it }, "Nom")
        Spacer(Modifier.height(16.dp))
        Text("Dossier", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            st.folders.forEach { f -> Chip(f.name, folder == f.id, Color(f.color)) { folder = if (folder == f.id) null else f.id } }
            Chip("Non classé", folder == null) { folder = null }
        }
        Spacer(Modifier.height(16.dp))
        Text("Mots-clés", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { t ->
                    Row(
                        Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(CardHi).clickable { tags.remove(t) }.padding(start = 12.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("#$t", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.width(6.dp))
                        Glyph(G.Close, Muted, 14.dp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Field(tag, { v -> if (v.endsWith(",")) addTag(v.dropLast(1)) else tag = v }, "Ajouter un mot-clé", Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            IconBtn(G.Plus, Navy, 48, bg = Yellow, enabled = tag.isNotBlank()) { addTag(tag) }
        }
        val known = app.lib.allTags().filter { k -> tags.none { norm(it) == norm(k) } && (tag.isBlank() || norm(k).contains(norm(tag))) }.take(8)
        if (known.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                known.forEach { k -> Chip("#$k", false) { addTag(k) } }
            }
        }
    }
}

@Composable
fun PasswordDialog(ask: PwdAsk) {
    var p by remember { mutableStateOf("") }
    KDialog("Document protégé", { ask.answer.complete(null) }, buttons = {
        Pill("Annuler") { ask.answer.complete(null) }
        Pill("Déverrouiller", primary = true, enabled = p.isNotEmpty()) { ask.answer.complete(p) }
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph(G.Lock, Yellow, 22.dp)
            Spacer(Modifier.width(10.dp))
            Text("« ${ask.name} » demande un mot de passe.", color = Soft, fontSize = 15.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text("Il sera enregistré sans mot de passe pour s'ouvrir directement ensuite.", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        Field(p, { p = it }, "Mot de passe", password = true)
        if (ask.wrong) {
            Spacer(Modifier.height(8.dp))
            Text("Mot de passe incorrect", color = Red, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun MergeDialog(app: AppState, ids: List<String>, onDismiss: () -> Unit, onDone: () -> Unit) {
    val order = remember { mutableStateListOf<String>().apply { addAll(ids) } }
    val first = app.lib.doc(ids.first())
    var name by remember { mutableStateOf("Fusion du ${fmtStamp().substringBefore(" à")}") }
    var delete by remember { mutableStateOf(false) }
    KDialog("Fusionner ${ids.size} documents", onDismiss, buttons = {
        Pill("Annuler", onClick = onDismiss)
        Pill("Fusionner", primary = true, enabled = name.isNotBlank()) {
            app.merge(order.toList(), name, first?.folder, delete) { onDone() }
        }
    }) {
        Text("Ordre des documents", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        order.forEachIndexed { i, id ->
            val d = app.lib.doc(id) ?: return@forEachIndexed
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp)).background(Navy2).padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${i + 1}", color = Yellow, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.width(22.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(pagesLabel(d.pages), color = Muted, fontSize = 12.sp)
                }
                IconBtn(G.Left, Soft, 40, enabled = i > 0, modifier = Modifier.rotate90()) { order.add(i - 1, order.removeAt(i)) }
                IconBtn(G.Right, Soft, 40, enabled = i < order.lastIndex, modifier = Modifier.rotate90()) { order.add(i + 1, order.removeAt(i)) }
            }
        }
        Spacer(Modifier.height(14.dp))
        Field(name, { name = it }, "Nom du nouveau document")
        Spacer(Modifier.height(10.dp))
        CheckRow("Supprimer les originaux après la fusion", delete) { delete = it }
    }
}

@Composable
fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(if (checked) Yellow else Color.Transparent)
                .border(2.dp, if (checked) Yellow else Muted, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) { if (checked) Glyph(G.Check, Navy, 16.dp) }
        Spacer(Modifier.width(12.dp))
        Text(label, color = Soft, fontSize = 15.sp)
    }
}

@Composable
fun SplitDialog(app: AppState, d: Doc, onDismiss: () -> Unit) {
    var each by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    val ranges = if (each) (0 until d.pages).map { it..it } else PdfOps.parseRanges(text, d.pages)
    KDialog("Découper", onDismiss, buttons = {
        Pill("Annuler", onClick = onDismiss)
        Pill("Découper", primary = true, enabled = !ranges.isNullOrEmpty() && d.pages > 0) { onDismiss(); app.split(d.id, ranges!!) }
    }) {
        Text("« ${d.name} » · ${pagesLabel(d.pages)}", color = Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(12.dp))
        OptionRow("Par plages de pages", "Un document par plage indiquée", !each) { each = false }
        OptionRow("Une page par document", "${pagesLabel(d.pages)} → ${d.pages} documents", each) { each = true }
        if (!each) {
            Spacer(Modifier.height(12.dp))
            Field(text, { text = it }, "Plages", placeholder = "ex. 1-3, 4, 5-${maxOf(d.pages, 5)}")
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    text.isBlank() -> "Sépare les plages par des virgules."
                    ranges == null -> "Plages invalides (pages 1 à ${d.pages})"
                    else -> "${ranges.size} document${if (ranges.size > 1) "s" else ""} seront créés"
                },
                color = if (text.isNotBlank() && ranges == null) Red else Muted, fontSize = 13.sp,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text("Le document d'origine est conservé.", color = Muted, fontSize = 13.sp)
    }
}

@Composable
fun OptionRow(title: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp))
            .background(if (selected) CardHi else Navy2).border(1.5.dp, if (selected) Yellow else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(20.dp).clip(CircleShape).border(2.dp, if (selected) Yellow else Muted, CircleShape), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(Yellow))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(sub, color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
fun CompressDialog(app: AppState, d: Doc, onDismiss: () -> Unit) {
    var level by remember { mutableStateOf(Level.Medium) }
    KDialog("Compresser", onDismiss, buttons = {
        Pill("Annuler", onClick = onDismiss)
        Pill("Compresser", primary = true) { onDismiss(); app.compress(d.id, level) }
    }) {
        Text("Taille actuelle : ${fmtSize(d.size)}", color = Soft, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Level.entries.forEach { l -> OptionRow(l.label, l.detail, level == l) { level = l } }
        Spacer(Modifier.height(8.dp))
        Text("Idéal pour les scans et photos. Les pages deviennent des images : le texte ne sera plus sélectionnable.", color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
fun CompressResultDialog(app: AppState, r: CompressResult) {
    val gain = if (r.before > 0) ((1 - r.after.toDouble() / r.before) * 100).toInt() else 0
    if (gain < 5) {
        KDialog("Pas de gain", { r.file.delete(); app.compressed = null }, buttons = {
            Pill("OK", primary = true) { r.file.delete(); app.compressed = null }
        }) { Text("Ce document est déjà bien optimisé (${fmtSize(r.before)} → ${fmtSize(r.after)}). L'original est conservé tel quel.", color = Soft, fontSize = 15.sp, lineHeight = 21.sp) }
        return
    }
    KDialog("Compression terminée", { r.file.delete(); app.compressed = null }, buttons = {
        Pill("Annuler") { r.file.delete(); app.compressed = null }
        Pill("Garder les 2") { app.applyCompression(r, replace = false) }
        Pill("Remplacer", primary = true) { app.applyCompression(r, replace = true) }
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(fmtSize(r.before), color = Muted, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            Glyph(G.Right, Muted, 18.dp)
            Spacer(Modifier.width(10.dp))
            Text(fmtSize(r.after), color = Yellow, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(12.dp))
            Text("−$gain %", color = Green, fontSize = 16.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(10.dp))
        Text("Remplacer l'original par la version compressée, ou garder les deux ?", color = Soft, fontSize = 15.sp)
    }
}

/** Flèches gauche/droite réutilisées en haut/bas. */
fun Modifier.rotate90() = this.rotate(90f)
