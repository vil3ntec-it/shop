package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.hw.EscPos
import ir.vil3ntec.tohid.core.hw.PrintQueue
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.core.pos.Quote
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.QuoteBook
import ir.vil3ntec.tohid.data.QuotePref
import ir.vil3ntec.tohid.data.QuoteStore
import ir.vil3ntec.tohid.data.Quotes
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.SavedQuote
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.displayDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.print.HwPages
import ir.vil3ntec.tohid.print.PrintSpooler
import ir.vil3ntec.tohid.print.PrinterRegistry
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.screens.SegmentedChoice
import ir.vil3ntec.tohid.ui.screens.SettingsSection
import ir.vil3ntec.tohid.ui.screens.newId
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 *  POS-25 — پیش‌فاکتور در صندوقِ گوشی و کامپیوتر (یک خط کنارِ `HeldInvoiceBar` در `Sale` و `DeskSale`). فقط با تنظیمِ
 *  همین دستگاه (`QuotePref`، پیش‌فرض خاموش ⇒ هیچ). «پیش‌فاکتور از این سبد» سبد را با قیمتِ همین لحظه و «اعتبار تا»
 *  ذخیره می‌کند و سبد دست نمی‌خورد؛ فهرست: چاپ (صفِ چاپ، نوعِ `REPORT`)، «به سبد» (فقط سبدِ خالی و پیش‌فاکتورِ معتبر؛
 *  ردیف‌ها با همان قیمت، فروش از راهِ همیشگیِ صندوق) و حذف. هیچ چیزی به دفترِ دکان نمی‌رود.
 */
@Composable
fun QuoteBar(d: ShopData, cart: CartStore, toast: (String) -> Unit, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val on = remember { QuotePref.on(context) }
  if (!on) return
  val me = remember { runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrDefault("") }
  val scope = rememberCoroutineScope()
  val lines by cart.lines.collectAsState()
  val debtorId by cart.debtorId.collectAsState()
  var book by remember { mutableStateOf(QuoteBook()) }
  LaunchedEffect(Unit) { book = withContext(Dispatchers.IO) { QuoteStore.read(context) } }
  var saving by remember { mutableStateOf(false) }
  var listing by remember { mutableStateOf(false) }

  fun problem(code: String): String = when (code) {
    "EMPTY" -> StrPos.quoteErrEmpty
    "TOO_MANY" -> StrPos.quoteErrTooMany
    "BAD_DAYS" -> StrPos.quoteErrDays
    "SOLD" -> StrPos.quoteErrSold
    "EXPIRED" -> StrPos.quoteErrExpired
    "CART_BUSY" -> StrPos.quoteErrCartBusy
    else -> StrPos.quoteErrNotSaved
  }

  Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    OutlinedButton(enabled = lines.isNotEmpty(), onClick = { saving = true }) { Text(StrPos.quoteFromCart) }
    TextButton(onClick = { listing = true }) { Text(StrPos.quoteList(book.items.size)) }
  }

  if (saving) {
    QuoteSaveDialog(onDismiss = { saving = false }) { days, note ->
      val today = ir.vil3ntec.tohid.todayIso()
      val now = System.currentTimeMillis()
      val id = newId()
      scope.launch {
        var refused: String? = null
        var made: SavedQuote? = null
        val next = withContext(Dispatchers.IO) {
          QuoteStore.update(context) { b ->
            when (val r = Quotes.create(b, d, cart.lines.value, debtorId, note, today, days, now, id, me)) {
              is Quotes.Result.Ok -> { made = r.quote; r.book }
              is Quotes.Result.Refused -> { refused = r.code; null }
            }
          }
        }
        if (next != null) { book = next; saving = false; toast(StrPos.quoteSaved(made?.number ?: 0)) }
        else toast(problem(refused ?: ""))
      }
    }
  }

  if (listing) {
    QuoteListDialog(
      d = d, book = book, cartKey = cart.snapshot().key, today = ir.vil3ntec.tohid.todayIso(),
      onPrint = { q -> scope.launch { toast(printQuote(context, d, q)) } },
      onConvert = { q ->
        val today = ir.vil3ntec.tohid.todayIso()
        when (val r = Quotes.convert(book, q.id, d, cart.snapshot().key, cart.lines.value.isEmpty(), today)) {
          is Quotes.Result.Refused -> toast(problem(r.code))
          is Quotes.Result.Ok -> {
            //  سبدِ وسطِ جابه‌جاییِ فاکتور ⇒ `set` هیچ نمی‌کند و پیش‌فاکتور «در سبد» نمی‌شود
            if (!cart.set(r.quote.lines.map(Quotes::cartLine))) { toast(StrPos.quoteErrNotSaved); return@QuoteListDialog }
            cart.setDebtor(r.quote.debtorId)
            val key = cart.snapshot().key
            scope.launch {
              withContext(Dispatchers.IO) { QuoteStore.update(context) { Quotes.markInCart(it, q.id, key) } }?.let { book = it }
            }
            listing = false
            toast(StrPos.quoteInCart(q.number))
          }
        }
      },
      onDelete = { q ->
        scope.launch {
          withContext(Dispatchers.IO) { QuoteStore.update(context) { Quotes.remove(it, q.id) } }?.let { book = it; toast(StrPos.quoteDeleted) }
        }
      },
      onDismiss = { listing = false },
    )
  }
}

@Composable
private fun QuoteSaveDialog(onDismiss: () -> Unit, onSave: (Int, String) -> Unit) {
  var days by remember { mutableStateOf(Quote.DEFAULT_DAYS.toString()) }
  var note by remember { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(StrPos.quoteSaveTitle) },
    text = {
      Column {
        Text(StrPos.quoteSaveBody, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
          value = days, onValueChange = { days = ir.vil3ntec.tohid.numericInput(it).filter(Char::isDigit).take(3) },
          label = { Text(StrPos.quoteDays) }, singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = note, onValueChange = { note = it.replace('\n', ' ').take(Quote.NOTE_MAX) },
          label = { Text(StrPos.quoteNote) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = { TextButton(onClick = { onSave(days.toIntOrNull() ?: 0, note) }) { Text(StrPos.quoteSave) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(StrPos.quoteCancel) } },
  )
}

private fun customer(d: ShopData, q: SavedQuote): String =
  q.debtorId?.let { id -> d.debtors.find { it.id == id }?.name?.ifBlank { null } } ?: StrPos.quoteWalkIn

private fun stateLabel(s: Quote.State): String = when (s) {
  Quote.State.VALID -> StrPos.quoteStateValid
  Quote.State.IN_CART -> StrPos.quoteStateInCart
  Quote.State.EXPIRED -> StrPos.quoteStateExpired
  Quote.State.SOLD -> StrPos.quoteStateSold
}

@Composable
private fun QuoteListDialog(
  d: ShopData, book: QuoteBook, cartKey: String?, today: String,
  onPrint: (SavedQuote) -> Unit, onConvert: (SavedQuote) -> Unit, onDelete: (SavedQuote) -> Unit, onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(StrPos.quoteListTitle) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        val list = Quotes.listed(book)
        if (list.isEmpty()) Text(StrPos.quoteEmpty, color = Shop.colors.muted)
        list.forEach { q ->
          val st = Quotes.state(q, d, cartKey, today)
          Text(StrPos.quoteRow(q.number, customer(d, q), money(PayFlow.major(q.totalMinor))), style = MaterialTheme.typography.bodyMedium)
          Text(
            StrPos.quoteValidTo(displayDate(q.validUntil)) + " · " + stateLabel(st) + (q.note?.let { " · $it" } ?: ""),
            style = MaterialTheme.typography.labelSmall,
            color = if (st == Quote.State.EXPIRED) Shop.colors.danger else Shop.colors.muted,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { onPrint(q) }) { Text(StrPos.quotePrint) }
            if (st == Quote.State.VALID || st == Quote.State.IN_CART) TextButton(onClick = { onConvert(q) }) { Text(StrPos.quoteToCart) }
            TextButton(onClick = { onDelete(q) }) { Text(StrPos.quoteDelete, color = Shop.colors.danger) }
          }
          HorizontalDivider(color = Shop.colors.fieldBorder.copy(alpha = 0.35f))
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text(StrPos.quoteClose) } },
  )
}

/** برگهٔ چاپی (روی چاپگرِ فاکتور، از صفِ چاپ): شماره، تاریخ، اعتبار، مشتری، ردیف‌ها با قیمتِ ثابت، جمع و «این فاکتور نیست» */
private suspend fun printQuote(context: android.content.Context, d: ShopData, q: SavedQuote): String {
  val printer = PrinterRegistry.defaultSaved(context) ?: return StrPos.quoteNoPrinter
  val title = StrPos.quoteSlipTitle(q.number)
  val bytes = withContext(Dispatchers.IO) {
    runCatching {
      val p = printer.profile()
      val rows = q.lines.map { l ->
        val name = l.label.ifBlank { d.products.find { it.id == l.productId }?.name.orEmpty() }
        val price = l.unitPrice ?: 0.0
        HwPages.reportLine(StrPos.quoteSlipLine(name, qty(l.quantity), money(price), money(SalesEngine.lineTotal(price, l.quantity))))
      }
      val lines = listOf(HwPages.Line(title, HwPages.Style.TITLE)) +
        HwPages.reportLine(StrPos.quoteSlipDate(displayDate(q.createdDay))) +
        HwPages.reportLine(StrPos.quoteValidTo(displayDate(q.validUntil)), strong = true) +
        HwPages.reportLine(StrPos.quoteSlipCustomer(customer(d, q))) + rows +
        HwPages.reportLine(StrPos.quoteSlipTotal(money(PayFlow.major(q.totalMinor))), strong = true) +
        listOfNotNull(q.note?.let { HwPages.reportLine(it) }) +
        HwPages.Line(StrPos.quoteSlipFooter, HwPages.Style.SMALL)
      EscPos.document(HwPages.text(context, p.paperDots, lines), p)
    }
  }.getOrElse { return StrPos.quoteNotPrinted(it.message ?: Str.unknownReason) }
  return PrintSpooler.submitAndWait(context, PrintQueue.Kind.REPORT, printer.link, title, bytes, done = StrPos.quotePrinted)
}

/** بخشِ تنظیمات (همین دستگاه، پیش‌فرض خاموش) — یک خط در `Settings` کنارِ تنظیم‌های صندوق */
@Composable
fun QuoteSettingsWired(context: android.content.Context) {
  var on by remember { mutableStateOf(QuotePref.on(context)) }
  SettingsSection(
    icon = Icons.Filled.Description,
    title = StrPos.quoteSettingsTitle,
    subtitle = if (on) StrShell.switchOn else StrShell.switchOff,
    tint = if (on) Shop.colors.primary else Shop.colors.muted,
  ) {
    Text(StrPos.quoteSettingsNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Text(StrShell.thisDeviceOnly, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Spacer(Modifier.height(8.dp))
    SegmentedChoice(
      options = listOf("off" to StrShell.switchOff, "on" to StrShell.switchOn),
      selected = if (on) "on" else "off",
      onSelect = { on = it == "on"; QuotePref.set(context, on) },
    )
  }
}
