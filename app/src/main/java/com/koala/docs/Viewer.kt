package com.koala.docs

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** État de chargement du moteur d'affichage d'un PDF. */
private sealed interface Loaded {
    data object Loading : Loaded
    data object Failed : Loaded
    class Ok(val v: PdfView) : Loaded
}

@Composable
private fun rememberPdf(file: File, key: Any): Loaded {
    val s by produceState<Loaded>(Loaded.Loading, key) {
        val v = withContext(Dispatchers.IO) { PdfView.open(file) }
        value = if (v == null || v.count == 0) Loaded.Failed else Loaded.Ok(v)
        awaitDispose { if (v != null) CoroutineScope(Dispatchers.IO).launch { v.closeSafely() } }
    }
    return s
}

@Composable
fun ViewerScreen(app: AppState, docId: String, startPage: Int) {
    val st by app.lib.state.collectAsState()
    val d = st.docs.firstOrNull { it.id == docId }
    if (d == null) { LaunchedEffect(Unit) { app.screen = Screen.Home }; return }
    val ctx = LocalContext.current
    var sheet by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    var split by remember { mutableStateOf(false) }
    var compress by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    val file = app.lib.file(d.id)

    BackHandler { app.screen = Screen.Home }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = d.name,
            sub = listOfNotNull(app.lib.folder(d.folder)?.name, pagesLabel(d.pages), fmtSize(d.size)).joinToString(" · "),
            onBack = { app.screen = Screen.Home },
        ) {
            IconBtn(if (d.fav) G.StarFill else G.Star, if (d.fav) Yellow else Soft) { app.lib.update(d.id) { it.copy(fav = !it.fav) } }
            IconBtn(G.Share) { app.share(listOf(d.id)) }
            IconBtn(G.More) { sheet = true }
        }
        PdfPages(file, "${d.id}_${d.modified}", startPage, Modifier.weight(1f)) {
            Pill("Ouvrir avec une autre appli", glyph = G.FileIn) { Share.openWith(ctx, file, d.name) }
        }
    }

    if (sheet) ActionSheet(d.name, listOf(
        SheetItem(G.Edit, "Nom, dossier et mots-clés") { info = true },
        SheetItem(G.Pages, "Organiser les pages") { app.screen = Screen.Pages(d.id) },
        SheetItem(G.Split, "Découper") { split = true },
        SheetItem(G.Compress, "Compresser") { compress = true },
        SheetItem(G.Print, "Imprimer") { runCatching { Share.print(ctx, file, d.name) }.onFailure { app.msg("Impression indisponible") } },
        SheetItem(G.FileIn, "Ouvrir avec une autre appli") { runCatching { Share.openWith(ctx, file, d.name) } },
        SheetItem(G.Trash, "Supprimer", danger = true) { delete = true },
    )) { sheet = false }
    if (info) InfoDialog(app, d.id, "Infos du document") { info = false }
    if (split) SplitDialog(app, d) { split = false }
    if (compress) CompressDialog(app, d) { compress = false }
    if (delete) ConfirmDialog("Supprimer ce document ?", "« ${d.name} » sera effacé du téléphone. Cette action est définitive.", "Supprimer", onDismiss = { delete = false }) {
        delete = false; app.screen = Screen.Home; app.delete(listOf(d.id))
    }
}

/** PDF reçu d'une autre appli : on le lit, et on peut l'ajouter à la bibliothèque. */
@Composable
fun ExternalScreen(app: AppState, e: Screen.External) {
    val ctx = LocalContext.current
    var sheet by remember { mutableStateOf(false) }
    BackHandler { app.screen = Screen.Home }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(e.name, "Pas encore dans Koaladocs", onBack = { app.screen = Screen.Home }) {
                IconBtn(G.Share) { runCatching { Share.send(ctx, listOf(e.file to e.name)) } }
                IconBtn(G.More) { sheet = true }
            }
            PdfPages(e.file, e.file.path, 0, Modifier.weight(1f)) {}
        }
        Pill(
            "Ajouter à Koaladocs", Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(20.dp),
            primary = true, glyph = G.Plus,
        ) { app.keepExternal(e) }
    }
    if (sheet) ActionSheet(e.name, listOf(
        SheetItem(G.Plus, "Ajouter à Koaladocs") { app.keepExternal(e) },
        SheetItem(G.Print, "Imprimer") { runCatching { Share.print(ctx, e.file, e.name) } },
        SheetItem(G.FileIn, "Ouvrir avec une autre appli") { runCatching { Share.openWith(ctx, e.file, e.name) } },
    )) { sheet = false }
}

@Composable
fun TopBar(title: String, sub: String?, onBack: () -> Unit, actions: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBtn(G.Back, Color.White, onClick = onBack)
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(title, fontWeight = FontWeight.Black, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

/** Lecture page par page ; pincer ou toucher deux fois une page pour zoomer. */
@Composable
fun PdfPages(file: File, key: Any, startPage: Int, modifier: Modifier, onFail: @Composable () -> Unit) {
    when (val l = rememberPdf(file, key)) {
        Loaded.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Yellow) }
        Loaded.Failed -> Box(modifier.fillMaxSize()) {
            Empty("Affichage impossible", "Ce PDF n'a pas pu être affiché par le téléphone.") { onFail() }
        }
        is Loaded.Ok -> {
            val v = l.v
            var zoom by remember(key) { mutableStateOf<Int?>(null) }
            val list = rememberLazyListState(startPage.coerceIn(0, v.count - 1))
            val current by remember { derivedStateOf {
                val info = list.layoutInfo
                // la page qui occupe le plus le centre de l'écran
                val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
                (info.visibleItemsInfo.firstOrNull { it.offset <= mid && it.offset + it.size >= mid } ?: info.visibleItemsInfo.firstOrNull())?.index ?: 0
            } }
            BoxWithConstraints(modifier.fillMaxSize().background(Navy2)) {
                val w = constraints.maxWidth
                LazyColumn(
                    state = list, modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(v.count, key = { it }) { i -> Page(v, i, minOf(w, 1440)) { zoom = i } }
                }
                Box(
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 84.dp)
                        .clip(RoundedCornerShape(16.dp)).background(Navy.copy(alpha = 0.85f)).padding(horizontal = 14.dp, vertical = 6.dp)
                ) { Text("${current + 1} / ${v.count}", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            }
            zoom?.let { z ->
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                ZoomView(v, z) { last ->
                    zoom = null
                    scope.launch { list.scrollToItem(last) }
                }
            }
        }
    }
}

@Composable
private fun Page(v: PdfView, i: Int, widthPx: Int, onZoom: () -> Unit) {
    val (pw, ph) = v.sizes[i]
    val bmp by produceState<Bitmap?>(null, v, i) { value = v.render(i, widthPx) }
    Box(
        Modifier.fillMaxWidth().aspectRatio(pw.toFloat() / ph.coerceAtLeast(1))
            .shadow(6.dp, RoundedCornerShape(4.dp)).clip(RoundedCornerShape(4.dp)).background(Color.White)
            .pointerInput(i) { detectTapGestures(onTap = { onZoom() }) }
            .pointerInput(i) {
                // Pincer à deux doigts ouvre le zoom ; un seul doigt laisse défiler la liste
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var total = 1f
                    while (true) {
                        val ev = awaitPointerEvent()
                        if (ev.changes.none { it.pressed }) break
                        if (ev.changes.count { it.pressed } >= 2) {
                            total *= ev.calculateZoom()
                            if (total > 1.12f) { ev.changes.forEach { it.consume() }; onZoom(); break }
                        }
                    }
                }
            },
    ) {
        bmp?.let { Image(it.asImageBitmap(), "Page ${i + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
            ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Line, strokeWidth = 3.dp) }
    }
}

/** Plein écran : glisser à gauche/droite pour changer de page, pincer ou toucher deux fois pour zoomer. */
@Composable
private fun ZoomView(v: PdfView, start: Int, onClose: (Int) -> Unit) {
    val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = start) { v.count }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    BackHandler { onClose(pager.currentPage) }
    Box(Modifier.fillMaxSize().background(Navy)) {
        androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.fillMaxSize(), pageSpacing = 16.dp) { index ->
            ZoomPage(v, index)
        }
        IconBtn(G.Close, Color.White, bg = Navy.copy(alpha = 0.8f), modifier = Modifier.align(Alignment.TopStart).padding(10.dp)) { onClose(pager.currentPage) }
        if (v.count > 1) Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 20.dp)
                .clip(RoundedCornerShape(26.dp)).background(Card).border(1.dp, Line, RoundedCornerShape(26.dp)).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val p = pager.currentPage
            IconBtn(G.Left, Yellow, enabled = p > 0) { scope.launch { pager.animateScrollToPage(p - 1) } }
            Text("${p + 1} / ${v.count}", fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp), textAlign = TextAlign.Center)
            IconBtn(G.Right, Yellow, enabled = p < v.count - 1) { scope.launch { pager.animateScrollToPage(p + 1) } }
        }
    }
}

@Composable
private fun ZoomPage(v: PdfView, index: Int) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val bw = constraints.maxWidth.toFloat()
        val bh = constraints.maxHeight.toFloat()
        val (pw, ph) = v.sizes[index]
        val aspect = pw.toFloat() / ph.coerceAtLeast(1)
        // Taille affichée à l'échelle 1 : la page entière tient dans l'écran
        val fitW = minOf(bw, bh * aspect)
        val fitH = fitW / aspect
        var scale by remember { mutableFloatStateOf(1f) }
        var off by remember { mutableStateOf(Offset.Zero) }
        fun clamp(o: Offset, s: Float): Offset {
            val mx = maxOf(0f, (fitW * s - bw) / 2); val my = maxOf(0f, (fitH * s - bh) / 2)
            return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
        }
        val bmp by produceState<Bitmap?>(null, v, index) { value = v.render(index, (fitW * 2.2f).toInt().coerceAtMost(2600)) }
        Box(
            Modifier.fillMaxSize()
                .pointerInput(index) {
                    detectTapGestures(onDoubleTap = { p ->
                        if (scale > 1.05f) { scale = 1f; off = Offset.Zero }
                        else { scale = 2.5f; off = clamp(Offset((bw / 2 - p.x) * 1.5f, (bh / 2 - p.y) * 1.5f), 2.5f) }
                    })
                }
                .pointerInput(index) {
                    // Zoom/déplacement seulement à deux doigts ou si la page est déjà agrandie ;
                    // sinon le geste est laissé au défilement des pages (swipe).
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        while (true) {
                            val ev = awaitPointerEvent()
                            if (ev.changes.none { it.pressed }) break
                            if (ev.changes.count { it.pressed } >= 2 || scale > 1.01f) {
                                val ns = (scale * ev.calculateZoom()).coerceIn(1f, 6f)
                                scale = ns
                                off = if (ns <= 1.01f) Offset.Zero else clamp(off + ev.calculatePan(), ns)
                                ev.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                        if (scale <= 1.01f) { scale = 1f; off = Offset.Zero }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.width(with(androidx.compose.ui.platform.LocalDensity.current) { fitW.toDp() }).aspectRatio(aspect)
                    .graphicsLayer { scaleX = scale; scaleY = scale; translationX = off.x; translationY = off.y }
                    .background(Color.White)
            ) {
                bmp?.let { Image(it.asImageBitmap(), "Page ${index + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
                    ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Line) }
            }
        }
    }
}
