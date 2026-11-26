package com.koala.docs

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

fun fmtSize(b: Long): String = when {
    b < 1024 -> "$b o"
    b < 1024 * 1024 -> "${b / 1024} Ko"
    else -> String.format(Locale.FRENCH, "%.1f Mo", b / 1048576.0)
}

fun fmtDate(t: Long): String = SimpleDateFormat("d MMM yyyy", Locale.FRENCH).format(Date(t))
fun fmtStamp(t: Long = System.currentTimeMillis()): String = SimpleDateFormat("dd/MM/yyyy 'à' HH'h'mm", Locale.FRENCH).format(Date(t))
fun pagesLabel(n: Int) = if (n <= 1) "$n page" else "$n pages"

@Composable
fun Logo(size: Int = 28) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(R.drawable.logo), contentDescription = null,
            modifier = Modifier.size((size + 12).dp).clip(RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Text("Koala", color = Yellow, fontSize = size.sp, fontWeight = FontWeight.Black)
        Text("docs", color = Color.White, fontSize = size.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun IconBtn(g: G, tint: Color = Soft, size: Int = 44, bg: Color = Color.Transparent, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(size.dp).clip(CircleShape).background(bg).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Glyph(g, if (enabled) tint else tint.copy(alpha = 0.3f), (size * 0.5f).dp) }
}

/** Bouton pilule. `primary` = jaune plein, sinon contour discret. */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier, primary: Boolean = false, danger: Boolean = false, glyph: G? = null, enabled: Boolean = true, onClick: () -> Unit) {
    val bg = when { !enabled -> CardHi; danger && primary -> Red; primary -> Yellow; else -> Color.Transparent }
    val fg = when { !enabled -> Muted; primary -> Navy; danger -> Red; else -> Soft }
    Row(
        modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(bg)
            .then(if (!primary && enabled) Modifier.border(1.5.dp, if (danger) Red.copy(alpha = 0.6f) else Line, RoundedCornerShape(22.dp)) else Modifier)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (glyph != null) { Glyph(glyph, fg, 18.dp); Spacer(Modifier.width(8.dp)) }
        Text(text, color = fg, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
    }
}

/** Fenêtre de dialogue maison, cohérente avec le reste de l'appli. */
@Composable
fun KDialog(
    title: String,
    onDismiss: () -> Unit,
    dismissible: Boolean = true,
    buttons: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = { if (dismissible) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = dismissible, dismissOnBackPress = dismissible),
    ) {
        Column(
            Modifier.padding(20.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Card)
                .border(1.dp, Line, RoundedCornerShape(24.dp)).padding(20.dp)
        ) {
            Text(title, fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color.White)
            Spacer(Modifier.height(14.dp))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) { content() }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) { buttons() }
        }
    }
}

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    password: Boolean = false,
    singleLine: Boolean = true,
    placeholder: String? = null,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = modifier, singleLine = singleLine,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = Muted) } },
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = if (password) KeyboardOptions(keyboardType = KeyboardType.Password)
        else KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Yellow, unfocusedBorderColor = Line, focusedLabelColor = Yellow, unfocusedLabelColor = Muted,
            cursorColor = Yellow, focusedTextColor = Color.White, unfocusedTextColor = Color.White,
        ),
    )
}

@Composable
fun Chip(text: String, selected: Boolean, color: Color? = null, glyph: G? = null, onClick: () -> Unit) {
    Row(
        Modifier.height(36.dp).clip(RoundedCornerShape(18.dp))
            .background(if (selected) (color ?: Yellow) else Card)
            .border(1.dp, if (selected) Color.Transparent else Line, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) { Glyph(glyph, if (selected) Navy else (color ?: Soft), 16.dp); Spacer(Modifier.width(6.dp)) }
        else if (color != null && !selected) { Box(Modifier.size(9.dp).clip(CircleShape).background(color)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = if (selected) Navy else Soft, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
    }
}

data class SheetItem(val g: G, val label: String, val danger: Boolean = false, val onClick: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSheet(title: String?, items: List<SheetItem>, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Card,
        contentColor = Color.White,
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            if (title != null) Text(
                title, Modifier.padding(horizontal = 24.dp, vertical = 6.dp), color = Muted, fontSize = 14.sp,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            items.forEach { it ->
                Row(
                    Modifier.fillMaxWidth().clickable { onDismiss(); it.onClick() }.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(it.g, if (it.danger) Red else Yellow, 22.dp)
                    Spacer(Modifier.width(18.dp))
                    Text(it.label, color = if (it.danger) Red else Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Voile « travail en cours » par-dessus tout l'écran. */
@Composable
fun BusyOverlay(label: String, progress: Float?) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Column(
            Modifier.clip(RoundedCornerShape(22.dp)).background(Card).border(1.dp, Line, RoundedCornerShape(22.dp)).padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (progress == null) CircularProgressIndicator(color = Yellow, strokeWidth = 4.dp)
            else {
                Text("${(progress * 100).toInt()} %", color = Yellow, fontWeight = FontWeight.Black, fontSize = 26.sp)
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.width(200.dp), color = Yellow, trackColor = Line)
            }
            Spacer(Modifier.height(16.dp))
            Text(label, color = Soft, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun Empty(title: String, sub: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit = {}) {
    Column(modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(86.dp).clip(CircleShape).background(Card), contentAlignment = Alignment.Center) { Glyph(G.Doc, Yellow, 42.dp) }
        Spacer(Modifier.height(18.dp))
        Text(title, fontWeight = FontWeight.Black, fontSize = 20.sp)
        Spacer(Modifier.height(6.dp))
        Text(sub, color = Muted, fontSize = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        content()
    }
}
