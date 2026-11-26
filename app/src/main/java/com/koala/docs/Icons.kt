package com.koala.docs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class G {
    Plus, Back, Search, Close, More, Scan, FileIn, Photo, Share, Trash, Edit, Folder, Merge, Split,
    Pages, RotL, RotR, Compress, Star, StarFill, Tag, Check, Print, Left, Right, Sort, Doc, Lock, Backup, Restore, Info,
}

/** Icônes dessinées (grille 24×24), pour ne dépendre d'aucune bibliothèque d'icônes. */
@Composable
fun Glyph(g: G, color: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 24f
        val st = Stroke(width = 2f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * k, y * k)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, p(x1, y1), p(x2, y2), strokeWidth = st.width, cap = StrokeCap.Round)
        fun Path.m(x: Float, y: Float) = moveTo(x * k, y * k)
        fun Path.l(x: Float, y: Float) = lineTo(x * k, y * k)
        fun Path.q(x1: Float, y1: Float, x: Float, y: Float) = quadraticTo(x1 * k, y1 * k, x * k, y * k)
        fun path(fill: Boolean = false, block: Path.() -> Unit) =
            drawPath(Path().apply(block), color, style = if (fill) Fill else st)
        fun rect(x: Float, y: Float, w: Float, h: Float, r: Float = 2f) =
            drawRoundRect(color, p(x, y), Size(w * k, h * k), CornerRadius(r * k), style = st)
        fun doc() = path { m(6f, 3f); l(14f, 3f); l(19f, 8f); l(19f, 21f); l(6f, 21f); close(); m(14f, 3f); l(14f, 8f); l(19f, 8f) }
        fun arc(cx: Float, cy: Float, r: Float, start: Float, sweep: Float) =
            drawArc(color, start, sweep, false, p(cx - r, cy - r), Size(2 * r * k, 2 * r * k), style = st)

        when (g) {
            G.Plus -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
            G.Back -> path { m(15f, 5f); l(8f, 12f); l(15f, 19f) }
            G.Left -> path { m(15f, 5f); l(8f, 12f); l(15f, 19f) }
            G.Right -> path { m(9f, 5f); l(16f, 12f); l(9f, 19f) }
            G.Search -> { drawCircle(color, 6.5f * k, p(10.5f, 10.5f), style = st); line(15.5f, 15.5f, 20f, 20f) }
            G.Close -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
            G.More -> for (y in listOf(5f, 12f, 19f)) drawCircle(color, 1.8f * k, p(12f, y))
            G.Scan -> {
                path { m(3f, 8f); l(3f, 5f); q(3f, 3f, 5f, 3f); l(8f, 3f) }
                path { m(16f, 3f); l(19f, 3f); q(21f, 3f, 21f, 5f); l(21f, 8f) }
                path { m(21f, 16f); l(21f, 19f); q(21f, 21f, 19f, 21f); l(16f, 21f) }
                path { m(8f, 21f); l(5f, 21f); q(3f, 21f, 3f, 19f); l(3f, 16f) }
                line(7f, 12f, 17f, 12f)
            }
            G.FileIn -> { doc(); line(12.5f, 11f, 12.5f, 18f); path { m(9.5f, 15f); l(12.5f, 18f); l(15.5f, 15f) } }
            G.Photo -> {
                rect(3f, 5f, 18f, 14f)
                drawCircle(color, 1.8f * k, p(8.5f, 9.5f))
                path { m(4f, 18f); l(10f, 12.5f); l(14f, 16f); l(16.5f, 13.5f); l(20f, 17f) }
            }
            G.Share -> {
                drawCircle(color, 2.6f * k, p(17.5f, 5.5f), style = st)
                drawCircle(color, 2.6f * k, p(6.5f, 12f), style = st)
                drawCircle(color, 2.6f * k, p(17.5f, 18.5f), style = st)
                line(8.8f, 10.7f, 15.2f, 6.8f); line(8.8f, 13.3f, 15.2f, 17.2f)
            }
            G.Trash -> {
                line(4f, 6.5f, 20f, 6.5f)
                path { m(9f, 6.5f); l(9f, 4f); l(15f, 4f); l(15f, 6.5f) }
                path { m(6f, 6.5f); l(7f, 20f); l(17f, 20f); l(18f, 6.5f) }
                line(10f, 10f, 10f, 16.5f); line(14f, 10f, 14f, 16.5f)
            }
            G.Edit -> {
                path { m(4f, 20f); l(4.8f, 15.8f); l(15.5f, 5f); q(17f, 3.6f, 18.5f, 5f); l(19f, 5.5f); q(20.4f, 7f, 19f, 8.5f); l(8.2f, 19.2f); close() }
                line(13.5f, 7f, 17f, 10.5f)
            }
            G.Folder -> path { m(3f, 7f); q(3f, 5f, 5f, 5f); l(9.5f, 5f); l(11.5f, 7.5f); l(19f, 7.5f); q(21f, 7.5f, 21f, 9.5f); l(21f, 17f); q(21f, 19f, 19f, 19f); l(5f, 19f); q(3f, 19f, 3f, 17f); close() }
            G.Merge -> {
                rect(3f, 4f, 7f, 9f, 1.5f); rect(14f, 4f, 7f, 9f, 1.5f)
                path { m(6.5f, 13f); l(6.5f, 15f); q(6.5f, 16.5f, 8.5f, 16.5f); l(15.5f, 16.5f); q(17.5f, 16.5f, 17.5f, 15f); l(17.5f, 13f) }
                line(12f, 16.5f, 12f, 21f)
            }
            G.Split -> {
                rect(7.5f, 2.5f, 9f, 8f, 1.5f)
                line(12f, 10.5f, 12f, 13f)
                path { m(5f, 21f); l(5f, 15.5f); q(5f, 13f, 7.5f, 13f); l(16.5f, 13f); q(19f, 13f, 19f, 15.5f); l(19f, 21f) }
                path { m(2.5f, 18.5f); l(5f, 21f); l(7.5f, 18.5f) }
                path { m(16.5f, 18.5f); l(19f, 21f); l(21.5f, 18.5f) }
            }
            G.Pages -> { rect(3.5f, 3.5f, 7f, 7f, 1.5f); rect(13.5f, 3.5f, 7f, 7f, 1.5f); rect(3.5f, 13.5f, 7f, 7f, 1.5f); rect(13.5f, 13.5f, 7f, 7f, 1.5f) }
            G.RotL -> { arc(12f, 13f, 7f, 200f, 250f); path { m(3.5f, 6.5f); l(5.6f, 10.6f); l(9.8f, 8.8f) } }
            G.RotR -> { arc(12f, 13f, 7f, 90f, 250f); path { m(20.5f, 6.5f); l(18.4f, 10.6f); l(14.2f, 8.8f) } }
            G.Compress -> {
                path { m(8f, 3f); l(12f, 7f); l(16f, 3f) }; line(12f, 7f, 12f, 1.5f)
                path { m(8f, 21f); l(12f, 17f); l(16f, 21f) }; line(12f, 17f, 12f, 22.5f)
                line(4f, 12f, 20f, 12f)
            }
            G.Star, G.StarFill -> {
                val pts = (0 until 10).map {
                    val r = if (it % 2 == 0) 9f else 4f
                    val a = Math.toRadians(-90.0 + it * 36.0)
                    Offset(12f + r * Math.cos(a).toFloat(), 12.8f + r * Math.sin(a).toFloat())
                }
                path(fill = g == G.StarFill) { m(pts[0].x, pts[0].y); pts.drop(1).forEach { l(it.x, it.y) }; close() }
            }
            G.Tag -> {
                path { m(3f, 4.5f); q(3f, 3f, 4.5f, 3f); l(11f, 3f); l(21f, 13f); l(13f, 21f); l(3f, 11f); close() }
                drawCircle(color, 1.6f * k, p(7.8f, 7.8f))
            }
            G.Check -> path { m(5f, 12.5f); l(10f, 17.5f); l(19f, 7f) }
            G.Print -> {
                path { m(7f, 9f); l(7f, 3.5f); l(17f, 3.5f); l(17f, 9f) }
                path { m(7f, 17.5f); l(4.5f, 17.5f); q(3f, 17.5f, 3f, 16f); l(3f, 10.5f); q(3f, 9f, 4.5f, 9f); l(19.5f, 9f); q(21f, 9f, 21f, 10.5f); l(21f, 16f); q(21f, 17.5f, 19.5f, 17.5f); l(17f, 17.5f) }
                rect(7f, 14f, 10f, 6.5f, 1f)
            }
            G.Sort -> {
                line(7f, 4f, 7f, 20f); path { m(3.5f, 16.5f); l(7f, 20f); l(10.5f, 16.5f) }
                line(13f, 6f, 21f, 6f); line(13f, 11f, 19f, 11f); line(13f, 16f, 17f, 16f)
            }
            G.Doc -> { doc(); line(9f, 13f, 16f, 13f); line(9f, 17f, 14f, 17f) }
            G.Lock -> { rect(5f, 10.5f, 14f, 10f, 2f); arc(12f, 9f, 4f, 180f, 180f); line(8f, 9f, 8f, 10.5f); line(16f, 9f, 16f, 10.5f) }
            G.Backup -> {
                path { m(7f, 18f); q(2.5f, 18f, 2.5f, 13.5f); q(2.5f, 9.5f, 6.5f, 9f); q(8f, 4.5f, 12.5f, 4.5f); q(17.5f, 4.5f, 18f, 9.5f); q(21.5f, 10f, 21.5f, 14f); q(21.5f, 18f, 17f, 18f) }
                line(12f, 11f, 12f, 20f); path { m(9f, 14f); l(12f, 11f); l(15f, 14f) }
            }
            G.Restore -> {
                path { m(7f, 18f); q(2.5f, 18f, 2.5f, 13.5f); q(2.5f, 9.5f, 6.5f, 9f); q(8f, 4.5f, 12.5f, 4.5f); q(17.5f, 4.5f, 18f, 9.5f); q(21.5f, 10f, 21.5f, 14f); q(21.5f, 18f, 17f, 18f) }
                line(12f, 11f, 12f, 20f); path { m(9f, 17f); l(12f, 20f); l(15f, 17f) }
            }
            G.Info -> { drawCircle(color, 9f * k, p(12f, 12f), style = st); line(12f, 11f, 12f, 16.5f); drawCircle(color, 1.3f * k, p(12f, 7.8f)) }
        }
    }
}
