package ir.vil3ntec.tohid.ui.v3.stock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrDeskWork
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrReports
import ir.vil3ntec.tohid.core.i18n.StrStock
import ir.vil3ntec.tohid.data.ReportCsv
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.displayDate
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.desk.Desk
import ir.vil3ntec.tohid.ui.desk.DeskButton
import ir.vil3ntec.tohid.ui.desk.DeskButtonKind
import ir.vil3ntec.tohid.ui.desk.DeskChip
import ir.vil3ntec.tohid.ui.desk.DeskChipRow
import ir.vil3ntec.tohid.ui.desk.DeskColumn
import ir.vil3ntec.tohid.ui.desk.DeskDivider
import ir.vil3ntec.tohid.ui.desk.DeskEmpty
import ir.vil3ntec.tohid.ui.desk.DeskHues
import ir.vil3ntec.tohid.ui.desk.DeskIconButton
import ir.vil3ntec.tohid.ui.desk.DeskSize
import ir.vil3ntec.tohid.ui.desk.DeskSpace
import ir.vil3ntec.tohid.ui.desk.DeskTableHead
import ir.vil3ntec.tohid.ui.desk.DeskType
import ir.vil3ntec.tohid.ui.desk.deskCard

/*
 *  «حرکات انبار» ِ یک کالا روی **کامپیوتر** (`stock/{id}/movements` با `desk`، `claude/e-desk-d3a`): سرخط با برگشت و خروجیِ
 *  CSV، تراشه‌های گونه (INV-02) و جدولِ آرام — تاریخ • ساعت، گونه، مقدار (+/−)، توضیح و تنها کارِ ردیف («باز کردن» ِ فاکتور یا
 *  تأمین‌کننده). همهٔ داده از `MovementsModel` (همان صفحهٔ گوشی)؛ گوشی دست نخورد.
 */

/** برچسبِ تراشهٔ گونه — همان `kindChip` ِ صفحهٔ گوشی */
internal fun deskKindLabel(k: MoveKind): String = if (k == MoveKind.OTHER) StrStock.moveOther else moveLabel(k, "")

/** «تاریخ • ساعت» (ساعت فقط وقتی مُهرِ زمانِ واقعی هست) */
internal fun deskMoveWhen(date: String, createdAt: Long, today: String, yesterday: String): String {
  val day = when (date) {
    today -> Str.today
    yesterday -> StrKit.yesterday
    else -> displayDate(date)
  }
  //  پیش از سالِ ۲۰۰۰ ⇒ شمارهٔ ترتیب است، نه ساعت
  val time = if (createdAt > 946_684_800_000L) ir.vil3ntec.tohid.ui.v3.sales.history.SaleTime.clock(createdAt) else ""
  return if (time.isBlank()) day else "$day • $time"
}

private val W_WHEN = 168.dp
private val W_KIND = 150.dp
private val W_QTY = 96.dp
private val W_OPEN = 104.dp

@Composable
internal fun DeskMovementsScreen(
  data: ShopData,
  productId: String,
  today: String,
  onOpen: (String) -> Unit,
  onBack: () -> Unit,
  onExport: ((ReportCsv.Sheet) -> Unit)? = null,
) {
  val c = Desk.colors
  val tone = DeskHues.tone("warehouse")
  val product = data.products.find { it.id == productId }
  var kindKey by rememberSaveable(productId) { mutableStateOf<String?>(null) }
  val kinds = remember(data, productId) { MovementsModel.kinds(data, productId) }
  val kind = kinds.firstOrNull { it.name == kindKey }
  val moves = remember(data, productId, kind) { MovementsModel.days(data, productId, kind).flatMap { it.moves } }
  val yesterday = remember(today) { MovementsModel.yesterday(today) }
  Column(Modifier.fillMaxSize().background(c.app).padding(horizontal = DeskSpace.page, vertical = DeskSpace.x4)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DeskSpace.x2)) {
      DeskIconButton(Icons.AutoMirrored.Filled.ArrowBack, StrKit.back, shortcut = "Esc", onClick = onBack)
      Text(StrStock.movementsTitle(product?.name.orEmpty()), Modifier.weight(1f), style = DeskType.sectionTitle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
      if (onExport != null && moves.isNotEmpty()) DeskButton(StrStock.exportCsv, { onExport(MovementsModel.csv(data, productId, kind, today)) }, icon = Icons.Outlined.FileDownload)
      DeskButton(StrStock.entryAction, { onOpen(StockPaths.entry(productId)) }, icon = Icons.Outlined.MoveToInbox, kind = DeskButtonKind.PRIMARY)
    }
    if (kinds.isNotEmpty()) {
      Spacer(Modifier.height(DeskSpace.x3))
      DeskChipRow {
        DeskChip(StrStock.filterAll, selected = kind == null, onClick = { kindKey = null }, tone = tone)
        kinds.forEach { k -> DeskChip(deskKindLabel(k), selected = k == kind, onClick = { kindKey = if (k == kind) null else k.name }, tone = tone) }
      }
    }
    Spacer(Modifier.height(DeskSpace.x3))
    Box(Modifier.weight(1f).fillMaxWidth().deskCard(c).padding(DeskSpace.x2)) {
      if (moves.isEmpty()) {
        DeskEmpty(Icons.Outlined.SwapVert, StrStock.noMovements, StrStock.movementsRow, tone, action = StrReports.stockEntry, onAction = { onOpen(StockPaths.entry(productId)) })
        return@Box
      }
      val cols = listOf(
        DeskColumn(StrDeskWork.colDate, width = W_WHEN),
        DeskColumn(StrDeskWork.colKind, width = W_KIND),
        DeskColumn(StrDeskWork.colAmount, width = W_QTY, end = true),
        DeskColumn(StrDeskWork.colNote, weight = 1f),
        DeskColumn("", width = W_OPEN),
      )
      Column(Modifier.fillMaxSize()) {
        DeskTableHead(cols)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = DeskSpace.x2)) {
          items(moves) { m ->
            val positive = m.move.qty >= 0
            Column {
              Row(
                Modifier.fillMaxWidth().heightIn(min = DeskSize.tableRow).padding(horizontal = DeskSpace.x3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DeskSpace.x3),
              ) {
                Text(deskMoveWhen(m.move.date, m.move.createdAt, today, yesterday), Modifier.width(W_WHEN), style = DeskType.table, color = c.inkSecondary, maxLines = 1)
                Text(moveLabel(m.kind, m.move.type), Modifier.width(W_KIND), style = DeskType.bodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("\u200E" + (if (positive) "+" else "−") + qty(kotlin.math.abs(m.move.qty)), Modifier.width(W_QTY), style = DeskType.number, color = c.ink, textAlign = TextAlign.End, maxLines = 1)
                Text(MovementsModel.detail(m.move) ?: "—", Modifier.weight(1f), style = DeskType.secondary, color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.width(W_OPEN), contentAlignment = Alignment.CenterEnd) {
                  m.link?.let { link -> DeskButton(StrDeskWork.openLink, { onOpen(link) }, kind = DeskButtonKind.GHOST) }
                }
              }
              DeskDivider(Modifier.padding(horizontal = DeskSpace.x3))
            }
          }
        }
      }
    }
  }
}
