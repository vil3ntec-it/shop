package af.tohid.shop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import af.tohid.shop.TohidApp
import af.tohid.shop.data.db.AuditEntity
import af.tohid.shop.ui.components.*
import af.tohid.shop.ui.theme.Radius
import af.tohid.shop.ui.theme.T
import af.tohid.shop.util.Format

@Composable
fun AuditLogScreen() {
    val app = TohidApp.instance

    var entries by remember { mutableStateOf<List<AuditEntity>>(emptyList()) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { entries = app.db.audit().recent(300) }

    val visible = remember(entries, query) {
        val q = query.trim()
        if (q.isEmpty()) entries
        else entries.filter { it.notes.contains(q, true) || it.type.contains(q, true) }
    }

    Column(Modifier.fillMaxSize().background(T.surface)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            PageToolbar("دفتر رویدادها", "هر تغییری که در دفتر ثبت شده است")
            SearchField(query, { query = it }, "جستجو در رویدادها…")
        }

        if (visible.isEmpty()) {
            TCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), padding = 0.dp) {
                EmptyState(
                    icon = Icons.Outlined.History,
                    title = if (entries.isEmpty()) "هنوز رویدادی ثبت نشده" else "نتیجه‌ای یافت نشد",
                    subtitle = "هر ثبت، ویرایش یا حذف در دفتر، اینجا با تاریخ نگهداری می‌شود.",
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visible, key = { it.id }) { e -> AuditRow(e) }
            }
        }
    }
}

@Composable
private fun AuditRow(e: AuditEntity) {
    val tone = when {
        e.type.endsWith("_delete") || e.type == "sale_cancel" -> Tone.Red
        e.type.endsWith("_edit") -> Tone.Orange
        e.type == "sale" || e.type.endsWith("_add") || e.type == "stock_in" -> Tone.Green
        else -> Tone.Blue
    }
    TCard(Modifier.fillMaxWidth(), padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(toneFg(tone))
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(e.notes.ifBlank { e.type }, fontSize = 12.5.sp, color = T.text, lineHeight = 22.sp)
                Spacer(Modifier.height(3.dp))
                Text(
                    "${Format.shortDate(e.date)} — ${Format.ago(e.createdAt)}",
                    fontSize = 10.5.sp,
                    color = T.muted2,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
