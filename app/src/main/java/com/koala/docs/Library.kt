package com.koala.docs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.util.UUID

data class Folder(val id: String, val name: String, val color: Long)

data class Doc(
    val id: String,
    val name: String,
    val folder: String? = null,
    val tags: List<String> = emptyList(),
    val added: Long = System.currentTimeMillis(),
    val modified: Long = added,
    val pages: Int = 0,
    val size: Long = 0,
    val fav: Boolean = false,
)

data class LibState(val folders: List<Folder> = emptyList(), val docs: List<Doc> = emptyList())

sealed interface Filter {
    data object All : Filter
    data object Fav : Filter
    data object Unfiled : Filter
    data class In(val folderId: String) : Filter
}

enum class Sort(val label: String) { Recent("Plus récents"), Name("Nom A → Z"), Size("Plus lourds") }

fun norm(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase().trim()

/** Bibliothèque locale : les PDF sont dans le stockage privé de l'appli, la liste dans un JSON. */
class Library(private val ctx: Context, private val fileName: String = "library.json") {
    val docsDir = File(ctx.filesDir, "docs").apply { mkdirs() }
    private val textDir = File(ctx.filesDir, "text").apply { mkdirs() }
    private val jsonFile get() = File(ctx.filesDir, fileName)
    private val texts = HashMap<String, String>()

    private val _state = MutableStateFlow(load())
    val state: StateFlow<LibState> = _state

    fun file(id: String) = File(docsDir, "$id.pdf")
    fun doc(id: String) = _state.value.docs.firstOrNull { it.id == id }
    fun folder(id: String?) = id?.let { f -> _state.value.folders.firstOrNull { it.id == f } }

    // ---------- Documents ----------

    /** Range un PDF déjà valide (et non chiffré) dans la bibliothèque. Le fichier source est déplacé. */
    @Synchronized
    fun add(src: File, name: String, folder: String? = null, pages: Int, tags: List<String> = emptyList()): Doc {
        val id = UUID.randomUUID().toString().take(12)
        val dst = file(id)
        if (!src.renameTo(dst)) { src.copyTo(dst, overwrite = true); src.delete() }
        val d = Doc(id = id, name = cleanName(name), folder = folder, tags = tags, pages = pages, size = dst.length())
        save(_state.value.copy(docs = _state.value.docs + d))
        return d
    }

    /** Remplace le contenu d'un document (pages réorganisées, compression…). */
    @Synchronized
    fun replace(id: String, newFile: File, pages: Int) {
        val dst = file(id)
        val tmp = File(docsDir, "$id.old")
        dst.renameTo(tmp)
        if (!newFile.renameTo(dst)) { newFile.copyTo(dst, overwrite = true); newFile.delete() }
        tmp.delete()
        texts.remove(id); File(textDir, "$id.txt").delete()
        update(id) { it.copy(pages = pages, size = dst.length(), modified = System.currentTimeMillis()) }
    }

    @Synchronized
    fun update(id: String, f: (Doc) -> Doc) {
        save(_state.value.copy(docs = _state.value.docs.map { if (it.id == id) f(it) else it }))
    }

    @Synchronized
    fun delete(ids: Collection<String>) {
        ids.forEach { file(it).delete(); File(textDir, "$it.txt").delete(); texts.remove(it) }
        save(_state.value.copy(docs = _state.value.docs.filterNot { it.id in ids }))
    }

    // ---------- Texte (recherche dans le contenu) ----------

    fun hasText(id: String) = File(textDir, "$id.txt").exists()

    fun saveText(id: String, text: String) {
        val t = norm(text).replace(Regex("\\s+"), " ").take(300_000)
        File(textDir, "$id.txt").writeText(t)
        synchronized(texts) { texts[id] = t }
    }

    private fun text(id: String): String = synchronized(texts) {
        texts.getOrPut(id) { File(textDir, "$id.txt").takeIf { it.exists() }?.readText() ?: "" }
    }

    // ---------- Dossiers ----------

    @Synchronized
    fun addFolder(name: String, color: Long): Folder {
        val f = Folder(UUID.randomUUID().toString().take(8), name.trim(), color)
        save(_state.value.copy(folders = _state.value.folders + f))
        return f
    }

    @Synchronized
    fun editFolder(id: String, name: String, color: Long) {
        save(_state.value.copy(folders = _state.value.folders.map { if (it.id == id) it.copy(name = name.trim(), color = color) else it }))
    }

    /** Supprime le dossier ; ses documents restent, « non classés ». */
    @Synchronized
    fun deleteFolder(id: String) {
        val s = _state.value
        save(LibState(s.folders.filterNot { it.id == id }, s.docs.map { if (it.folder == id) it.copy(folder = null) else it }))
    }

    @Synchronized
    fun moveFolder(id: String, delta: Int) {
        val l = _state.value.folders.toMutableList()
        val i = l.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in l.indices) return
        l.add(j, l.removeAt(i))
        save(_state.value.copy(folders = l))
    }

    // ---------- Recherche ----------

    data class Hit(val doc: Doc, val inText: Boolean)

    fun query(filter: Filter, q: String, sort: Sort): List<Hit> {
        val s = _state.value
        val names = s.folders.associate { it.id to norm(it.name) }
        val words = norm(q).split(' ').filter { it.isNotBlank() }
        val base = s.docs.filter {
            when (filter) {
                Filter.All -> true
                Filter.Fav -> it.fav
                Filter.Unfiled -> it.folder == null || it.folder !in names
                is Filter.In -> it.folder == filter.folderId
            }
        }
        val hits = if (words.isEmpty()) base.map { Hit(it, false) } else base.mapNotNull { d ->
            val meta = norm(d.name) + " " + d.tags.joinToString(" ") { norm(it) } + " " + (names[d.folder] ?: "")
            if (words.all { it in meta }) Hit(d, false)
            else if (words.all { it in meta || it in text(d.id) }) Hit(d, true)
            else null
        }
        return when (sort) {
            Sort.Recent -> hits.sortedByDescending { it.doc.added }
            Sort.Name -> hits.sortedBy { norm(it.doc.name) }
            Sort.Size -> hits.sortedByDescending { it.doc.size }
        }.sortedBy { it.inText } // les correspondances dans le nom d'abord
    }

    fun allTags(): List<String> = _state.value.docs.flatMap { it.tags }.distinctBy { norm(it) }.sortedBy { norm(it) }

    // ---------- Persistance ----------

    private fun load(): LibState {
        for (f in listOf(jsonFile, File(ctx.filesDir, "$fileName.bak"))) {
            val s = runCatching { parse(JSONObject(f.readText())) }.getOrNull()
            if (s != null) return s.copy(docs = s.docs.filter { file(it.id).exists() })
        }
        // Premier lancement : quelques dossiers de départ
        val start = listOf(
            "Maison" to 0xFFFFD23FL, "Voiture" to 0xFF6FD3FFL, "Banque" to 0xFF4FE3C1L,
            "Santé" to 0xFFFF6FAEL, "Travail" to 0xFFFF8A3DL, "Vinted" to 0xFFB9A6FFL,
        ).mapIndexed { i, (n, c) -> Folder("f$i", n, c) }
        return LibState(start, emptyList()).also { write(it) }
    }

    private fun save(s: LibState) {
        _state.value = s
        write(s)
    }

    private fun write(s: LibState) {
        val txt = toJson(s).toString()
        val tmp = File(ctx.filesDir, "$fileName.tmp")
        tmp.writeText(txt)
        if (jsonFile.exists()) jsonFile.copyTo(File(ctx.filesDir, "$fileName.bak"), overwrite = true)
        if (!tmp.renameTo(jsonFile)) { jsonFile.writeText(txt); tmp.delete() }
    }

    /**
     * Restauration : ajoute les dossiers et documents de la sauvegarde qui ne sont pas déjà là
     * (rien n'est écrasé ni supprimé). `files` = fichiers PDF extraits, par identifiant. Renvoie le nombre ajouté.
     */
    @Synchronized
    fun merge(backup: LibState, files: Map<String, File>): Int {
        val s = _state.value
        val haveF = s.folders.map { it.id }.toSet()
        val haveD = s.docs.map { it.id }.toSet()
        val newDocs = backup.docs.filter { it.id !in haveD && files[it.id]?.exists() == true }
        newDocs.forEach { d -> val src = files.getValue(d.id); if (!src.renameTo(file(d.id))) src.copyTo(file(d.id), overwrite = true) }
        save(LibState(s.folders + backup.folders.filter { it.id !in haveF }, s.docs + newDocs))
        return newDocs.size
    }

    companion object {
        fun cleanName(n: String): String =
            n.trim().removeSuffix(".pdf").removeSuffix(".PDF").replace(Regex("[\\\\/:*?\"<>|\\n]"), " ").trim().ifBlank { "Document" }.take(120)

        fun toJson(s: LibState) = JSONObject().apply {
            put("v", 1)
            put("folders", JSONArray().apply {
                s.folders.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("color", it.color)) }
            })
            put("docs", JSONArray().apply {
                s.docs.forEach {
                    put(JSONObject().put("id", it.id).put("name", it.name).put("folder", it.folder ?: JSONObject.NULL)
                        .put("tags", JSONArray(it.tags)).put("added", it.added).put("modified", it.modified)
                        .put("pages", it.pages).put("size", it.size).put("fav", it.fav))
                }
            })
        }

        fun parse(o: JSONObject): LibState {
            val fs = o.optJSONArray("folders") ?: JSONArray()
            val ds = o.optJSONArray("docs") ?: JSONArray()
            return LibState(
                (0 until fs.length()).map { fs.getJSONObject(it) }.map { Folder(it.getString("id"), it.getString("name"), it.getLong("color")) },
                (0 until ds.length()).map { ds.getJSONObject(it) }.map { d ->
                    val t = d.optJSONArray("tags") ?: JSONArray()
                    Doc(
                        id = d.getString("id"), name = d.getString("name"),
                        folder = if (d.isNull("folder")) null else d.getString("folder"),
                        tags = (0 until t.length()).map { t.getString(it) },
                        added = d.optLong("added"), modified = d.optLong("modified", d.optLong("added")),
                        pages = d.optInt("pages"), size = d.optLong("size"), fav = d.optBoolean("fav"),
                    )
                },
            )
        }
    }
}
