package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.keymap.KeyMap
import ir.vil3ntec.tohid.core.keymap.KeyName
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.HeldExtras
import ir.vil3ntec.tohid.data.HeldInvoice
import ir.vil3ntec.tohid.data.HeldInvoiceStore
import ir.vil3ntec.tohid.data.HeldInvoices
import ir.vil3ntec.tohid.data.HeldRules
import ir.vil3ntec.tohid.data.SaleDraft
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.ui.screens.hint
import ir.vil3ntec.tohid.ui.screens.matches
import ir.vil3ntec.tohid.ui.theme.Shape
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrNav

/**
 *  برگه‌های فاکتورهای باز — POS-12 (چند فاکتورِ هم‌زمان، نگه‌داشتن با نام یا شماره).
 *
 *  یک تکهٔ خودبسنده تا در طراحی‌های تازهٔ صندوق (گوشی و کامپیوتر) هم همین را
 *  جابه‌جا کنند: صفحه فقط سه کار می‌کند —
 *   ۱) `val held = rememberHeldInvoices(cartStore, d, snackbar, loaded, extras, onSwitched)`
 *   ۲) `HeldInvoiceBar(held)` جایی بالای سبد
 *   ۳) کامپیوتر: `if (held.onKey(e)) return true` اولِ `onPreviewKeyEvent`ِ صفحه
 *      (پیش از هر شرطی: وسطِ جابه‌جایی همهٔ کلیدها را می‌خورد)
 *
 *  پشتِ تنظیمِ «چند فاکتورِ هم‌زمان» که پیش‌فرض **خاموش** است. خاموش و بی
 *  نگه‌داشته ⇒ نه نواری کشیده می‌شود نه کلیدی خورده می‌شود. خاموش ولی با
 *  نگه‌داشته‌ای که مانده ⇒ نوار می‌ماند تا هیچ فاکتوری پشتِ تنظیم گیر نکند.
 */
object HeldInvoicePrefs {
  val on = mutableStateOf(false)
  /** POS-12 — پاک‌سازیِ خودکارِ کهنه‌ها (`HeldInvoiceStore.autoDropStale`)؛ پیش‌فرض **خاموش**، فقط با [on] اثر دارد */
  val autoClean = mutableStateOf(false)
  private var loaded = false
  private const val KEY = "held_invoices"
  private const val AUTO_KEY = "held_auto_clean"

  fun load(context: android.content.Context) {
    if (loaded) return
    loaded = true
    val p = context.getSharedPreferences("tohid-ui", 0)
    on.value = p.getBoolean(KEY, false)
    autoClean.value = p.getBoolean(AUTO_KEY, false)
  }

  fun set(context: android.content.Context, value: Boolean) {
    on.value = value
    context.getSharedPreferences("tohid-ui", 0).edit().putBoolean(KEY, value).apply()
  }

  fun setAutoClean(context: android.content.Context, value: Boolean) {
    autoClean.value = value
    context.getSharedPreferences("tohid-ui", 0).edit().putBoolean(AUTO_KEY, value).apply()
  }

  val SECTION: String get() = StrPos.heldSection
}

internal sealed interface HeldDialog {
  data class Hold(val label: String, val note: String) : HeldDialog
  data object List : HeldDialog
  data class Discard(val invoice: HeldInvoice) : HeldDialog
  data object DropStale : HeldDialog
}

/** وضعیت و کارهای برگه‌ها برای یک صفحهٔ صندوق */
@Stable
class HeldInvoicesUi internal constructor(
  internal val store: HeldInvoiceStore,
  internal val cart: CartStore,
  private val scope: CoroutineScope,
  private val snackbar: SnackbarHostState,
  /** پاک کردنِ گروهیِ کهنه‌ها — مجوزِ «لغوِ فاکتور» (S09: «با مجوز») */
  internal val canDropStale: Boolean,
) {
  internal var extras: () -> HeldExtras = { HeldExtras() }
  internal var onSwitched: (HeldExtras) -> Unit = {}
  internal var onFocusBack: () -> Unit = {}
  internal var data: ShopData = ShopData()
  /** دادهٔ دکان بار شده — بی آن `fit` همهٔ ردیف‌های کالا را «ناموجود» می‌دید */
  internal var loaded: Boolean = true
  internal var dialog by mutableStateOf<HeldDialog?>(null)

  private var pending by mutableStateOf(0)
  private var warned = false
  /** جابه‌جاییِ فاکتور در جریان است: سبد قفل است و صفحه ورودی نمی‌گیرد */
  val switching: Boolean get() = pending > 0

  /** نوار دیده می‌شود: تنظیم روشن است یا نگه‌داشته‌ای مانده */
  val visible: Boolean get() = HeldInvoicePrefs.on.value || store.book.value.items.isNotEmpty()

  private fun toast(text: String) { scope.launch { snackbar.showSnackbar(text) } }

  /** `switches` ⇒ سبد عوض می‌شود: از همین لحظه تا پایان ورودیِ صفحه خورده می‌شود */
  private fun run(switches: Boolean = false, op: suspend () -> HeldInvoiceStore.Outcome) {
    if (switches) pending++
    scope.launch {
      val message = try {
        when (val o = op()) {
          is HeldInvoiceStore.Outcome.Done -> { o.extras?.let(onSwitched); o.message }
          is HeldInvoiceStore.Outcome.Refused -> o.message
        }
      } finally {
        if (switches && --pending == 0) warned = false
      }
      //  پیش از پیام: `showSnackbar` تا بسته شدنِ پیام صبر می‌کند
      onFocusBack()
      snackbar.showSnackbar(message)
    }
  }

  fun close() { dialog = null; onFocusBack() }

  /** F6 — پنجرهٔ نام؛ سبدِ خالی ⇒ همان پیامِ «چیزی برای نگه‌داشتن نیست» */
  fun askHold() {
    val active = cart.snapshot()
    if (active.lines.isEmpty()) { toast(HeldRules.EMPTY); return }
    val tag = HeldRules.activeTag(store.book.value, active)
    val customer = active.debtorId?.let { id -> data.debtors.firstOrNull { it.id == id }?.name }.orEmpty()
    dialog = HeldDialog.Hold(tag?.label?.ifBlank { null } ?: customer, tag?.note.orEmpty())
  }

  fun hold(label: String?, note: String?) { dialog = null; run(switches = true) { store.hold(cart, extras(), label, note) } }
  /** «+ تازه» — نگه‌داشتن بی پرسیدنِ نام و صندوقِ خالی برای مشتریِ بعدی */
  fun newInvoice() = hold(null, null)
  fun resume(id: String) { dialog = null; if (ready()) run(switches = true) { store.resume(cart, id, extras(), data) } }
  fun step(dir: Int) { if (ready()) run(switches = true) { store.step(cart, dir, extras(), data) } }

  private fun ready(): Boolean {
    if (!loaded) toast(NOT_LOADED)
    return loaded
  }
  fun discard(h: HeldInvoice) { dialog = null; run { store.discard(h.id) } }
  fun dropStale() { dialog = null; run { store.dropStale() } }

  /** POS-12 — پاک‌سازیِ خودکار؛ پیام فقط وقتی چیزی پاک شد (بی کهنه یا خاموش ⇒ بی‌صدا) */
  internal suspend fun autoDropStale(on: Boolean) {
    val n = store.autoDropStale(on)
    if (n > 0) snackbar.showSnackbar(StrPos.heldAutoCleared(plain(n), plain(HeldRules.STALE_HOURS)))
  }

  /**
   *  کلیدهای صندوق (F6، Shift+F6، Ctrl+PgDn، Ctrl+PgUp) — `true` یعنی خورده شد.
   *  پنجره‌ای باز است ⇒ کلید مالِ همان پنجره است.
   */
  fun onKey(e: KeyEvent): Boolean {
    //  وسطِ جابه‌جایی: اسکن، F9/F12 و ویرایش به سبدی که دارد عوض می‌شود نرسند
    if (switching) {
      if (!warned && e.type == KeyEventType.KeyDown) { warned = true; toast(SWITCHING) }
      return true
    }
    if (e.type != KeyEventType.KeyDown || dialog != null) return false
    val hit = listOf(KeyMap.SALE_HOLD, KeyMap.SALE_HELD_LIST, KeyMap.SALE_NEXT_OPEN, KeyMap.SALE_PREV_OPEN).firstOrNull { e.matches(it) } ?: return false
    //  تنظیم خاموش و نگه‌داشته‌ای نیست ⇒ کلید مالِ صفحه است، همان رفتارِ پیش
    if (!visible) return false
    when (hit) {
      KeyMap.SALE_HOLD -> askHold()
      KeyMap.SALE_HELD_LIST -> dialog = HeldDialog.List
      KeyMap.SALE_NEXT_OPEN -> step(+1)
      else -> step(-1)
    }
    return true
  }

  internal companion object {
    val SWITCHING: String get() = StrPos.heldSwitching
    val NOT_LOADED: String get() = StrPos.heldNotLoaded
  }
}

/**
 *  @param loaded دادهٔ دکان بار شده (`ShopStore.loaded`) — تا آن، بازگرداندن نه
 *  @param extras حالتی از صفحه که با فاکتور می‌رود و در `CartStore` نیست (تخفیفِ نوشته‌شده)
 *  @param onSwitched فاکتورِ صندوق عوض شد ⇒ صفحه همین‌ها را بگذارد (و انتخاب/پرداختِ نیمه‌کاره را خالی کند)
 *  @param onFocusBack پس از هر کار — کامپیوتر فوکوس را به کادرِ اسکن برمی‌گرداند
 */
@Composable
fun rememberHeldInvoices(
  cartStore: CartStore,
  d: ShopData,
  snackbar: SnackbarHostState,
  loaded: Boolean,
  extras: () -> HeldExtras = { HeldExtras() },
  onSwitched: (HeldExtras) -> Unit = {},
  onFocusBack: () -> Unit = {},
): HeldInvoicesUi {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  HeldInvoicePrefs.load(context)
  val ui = remember(cartStore) {
    HeldInvoicesUi(HeldInvoices.of(context), cartStore, scope, snackbar, ir.vil3ntec.tohid.data.ShopRole.canCancelSale(context))
  }
  ui.extras = extras
  ui.onSwitched = onSwitched
  ui.onFocusBack = onFocusBack
  ui.data = d
  ui.loaded = loaded
  //  بالا آمدن پس از قطعِ برق وسطِ جابه‌جایی (شرحِ `HeldRules.reconcile`)؛ پرسشِ
  //  بازیابیِ پیش‌نویس بی‌پاسخ ⇒ پس از پاسخ، تا «دور انداختن» نگه‌داشته را نبرد
  val recovering by cartStore.recovered.collectAsState()
  LaunchedEffect(ui, recovering == null) { ui.store.reconcile(cartStore) }
  //  فاکتورِ فروش‌شده از فهرست می‌رود
  LaunchedEffect(ui, d.sales.size) { ui.store.prune(d) }
  //  POS-12: پاک‌سازیِ خودکارِ کهنه‌ها — با بازشدنِ صندوق و سپس هر ساعت تا صفحه باز است؛ خاموش ⇒ هیچ
  val autoClean = HeldInvoicePrefs.on.value && HeldInvoicePrefs.autoClean.value
  LaunchedEffect(ui, autoClean) {
    while (autoClean) { ui.autoDropStale(true); kotlinx.coroutines.delay(AUTO_CLEAN_EVERY_MS) }
  }
  return ui
}

/** نوارِ برگه‌ها: فاکتورِ جاری، نگه‌داشته‌ها، «+ تازه» و «همه». خاموش و خالی ⇒ هیچ. */
@Composable
fun HeldInvoiceBar(ui: HeldInvoicesUi, modifier: Modifier = Modifier) {
  val book by ui.store.book.collectAsState()
  val lines by ui.cart.lines.collectAsState()
  val debtorId by ui.cart.debtorId.collectAsState()
  HeldDialogs(ui, book.items)
  if (!HeldInvoicePrefs.on.value && book.items.isEmpty()) return

  val d = ui.data
  //  `lines`/`debtorId` بالا فقط برای دوباره کشیدن‌اند؛ خواندن بی ساختنِ کلید
  val active = ui.cart.snapshot()
  val activeNo = HeldRules.activeNumber(book, active)
  val tag = HeldRules.activeTag(book, active)
  //  POS-31: کامپیوترِ لمسی هم ۴۸ (`PosTouchPrefs`؛ خاموش ⇒ همان `!DESKTOP`)
  val target = PosLayout.minTarget(touch = PosTouchPrefs.touch()).dp
  val now = System.currentTimeMillis()
  val stale = HeldRules.stale(book, now).mapTo(HashSet()) { it.id }

  Row(
    modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    //  جاری — همیشه اول و برجسته
    HeldChip(
      number = activeNo,
      title = tag?.label?.ifBlank { null } ?: customerName(d, debtorId) ?: if (lines.isEmpty()) StrPos.newInvoice else StrPos.currentInvoice,
      detail = if (lines.isEmpty()) StrPos.empty else summary(d, active),
      selected = true,
      warn = false,
      target = target,
      onClick = {},
    )
    book.items.forEach { h ->
      HeldChip(
        number = h.number,
        title = h.label.ifBlank { null } ?: customerName(d, h.debtorId) ?: StrPos.invoice,
        detail = summary(d, h.draft()) + if (h.id in stale) " · " + StrPos.stale else "",
        selected = false,
        warn = h.id in stale,
        target = target,
        onClick = { ui.resume(h.id) },
      )
    }
    TextButton(onClick = { ui.newInvoice() }, enabled = lines.isNotEmpty(), modifier = Modifier.heightIn(min = target)) {
      Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(4.dp))
      Text(StrPos.newInvoice + keyHint(KeyMap.SALE_HOLD), maxLines = 1)
    }
    if (book.items.isNotEmpty()) {
      TextButton(onClick = { ui.dialog = HeldDialog.List }, modifier = Modifier.heightIn(min = target)) {
        Icon(Icons.Filled.Layers, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(Str.all + keyHint(KeyMap.SALE_HELD_LIST), maxLines = 1)
      }
    }
  }
}

@Composable
private fun HeldChip(number: Int?, title: String, detail: String, selected: Boolean, warn: Boolean, target: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
  val colors = Shop.colors
  val spoken = listOfNotNull(number?.let { StrPos.invoiceN(plain(it)) }, title, detail, if (selected) StrPos.atRegister else StrPos.held).joinToString(Str.listSep + " ")
  Surface(
    onClick = onClick,
    enabled = !selected,
    shape = Shape.chip,
    color = when { selected -> colors.primaryTint; warn -> colors.warningTint; else -> colors.surface2 },
    modifier = Modifier.heightIn(min = target).semantics { contentDescription = spoken; this.selected = selected },
  ) {
    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
      if (number != null) {
        Box(
          Modifier.size(22.dp).background(if (selected) colors.primary else colors.muted2, Shape.badge),
          contentAlignment = Alignment.Center,
        ) { Text(plain(number), style = MaterialTheme.typography.labelSmall, color = colors.surface, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(8.dp))
      }
      Column {
        Text(title, style = MaterialTheme.typography.labelLarge, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
        Text(detail, style = MaterialTheme.typography.labelSmall, color = if (warn) colors.warning else colors.muted, maxLines = 1)
      }
    }
  }
}

/** برچسبِ کلید فقط روی کامپیوتر — گوشی کیبورد ندارد */
private fun keyHint(a: ir.vil3ntec.tohid.core.keymap.KeyAction): String =
  if (ir.vil3ntec.tohid.ui.Platform.DESKTOP) " · ${a.hint}" else ""

private fun customerName(d: ShopData, debtorId: String?): String? = debtorId?.let { id -> d.debtors.firstOrNull { it.id == id }?.name?.ifBlank { null } }

private fun summary(d: ShopData, draft: SaleDraft): String {
  val cart = draft.cart()
  val total = cart.sumOf { SalesEngine.lineTotal(SalesEngine.linePrice(d, it), it.quantity) }
  return StrPos.linesAndTotal(plain(cart.size), money(total))
}

private fun ago(now: Long, at: Long): String {
  val min = ((now - at).coerceAtLeast(0) / 60_000L).toInt()
  return when {
    min < 1 -> StrNav.justNow
    min < 60 -> StrNav.minutesAgo(plain(min))
    min < 48 * 60 -> StrNav.hoursAgo(plain(min / 60))
    else -> StrNav.daysAgo(plain(min / (24 * 60)))
  }
}

@Composable
private fun HeldDialogs(ui: HeldInvoicesUi, items: List<HeldInvoice>) {
  val colors = Shop.colors
  when (val dlg = ui.dialog) {
    null -> Unit
    is HeldDialog.Hold -> {
      var label by remember(dlg) { mutableStateOf(dlg.label) }
      var note by remember(dlg) { mutableStateOf(dlg.note) }
      val focus = remember { FocusRequester() }
      LaunchedEffect(dlg) { runCatching { focus.requestFocus() } }
      AlertDialog(
        onDismissRequest = { ui.close() },
        shape = Shape.dialog,
        title = { Text(StrPos.holdInvoice) },
        text = {
          Column {
            Text(StrPos.holdNote, style = MaterialTheme.typography.bodySmall, color = colors.muted)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
              value = label,
              onValueChange = { label = it.take(HeldRules.LABEL_MAX) },
              singleLine = true,
              label = { Text(StrPos.nameOrNumberOptional) },
              keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
              keyboardActions = KeyboardActions(onDone = { ui.hold(label, note) }),
              modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { e ->
                //  کیبورد: F6 → نام → Enter
                if (e.type == KeyEventType.KeyDown && KeyName.Enter.matchesKey(e)) { ui.hold(label, note); true } else false
              },
            )
            OutlinedTextField(
              value = note,
              onValueChange = { note = it.take(HeldRules.NOTE_MAX) },
              label = { Text(StrPos.noteOptional) },
              modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
          }
        },
        confirmButton = { Button(onClick = { ui.hold(label, note) }) { Text(StrPos.holdIt) } },
        dismissButton = { TextButton(onClick = { ui.close() }) { Text(StrPos.nevermind) } },
      )
    }
    HeldDialog.List -> {
      val now = System.currentTimeMillis()
      val stale = HeldRules.stale(ir.vil3ntec.tohid.data.HeldBook(items = items), now)
      //  فوکوس روی نخستین «باز کن» تا رقم، Tab و Enter همان‌جا کار کنند
      val first = remember { FocusRequester() }
      LaunchedEffect(items.size) { runCatching { first.requestFocus() } }
      AlertDialog(
        onDismissRequest = { ui.close() },
        shape = Shape.dialog,
        title = { Text(StrPos.heldInvoices) },
        text = {
          if (items.isEmpty()) Text(StrPos.noHeldInvoices, color = colors.muted)
          //  حداکثر ۸ ردیف (`MAX_OPEN` − ۱) — ستونِ ساده با پیمایش، بی `LazyColumn` در پنجره
          else Column(
            Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).onPreviewKeyEvent { e ->
              //  رقمِ ۱ تا ۹ ⇒ همان شماره باز می‌شود
              if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
              val n = DIGITS.indexOfFirst { it.matchesKey(e) } + 1
              val h = items.firstOrNull { it.number == n } ?: return@onPreviewKeyEvent false
              ui.resume(h.id); true
            },
          ) {
            items.forEachIndexed { i, h ->
              Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                  Text("#${plain(h.number)} · ${h.label.ifBlank { null } ?: customerName(ui.data, h.debtorId) ?: StrPos.invoice}", style = MaterialTheme.typography.titleSmall, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  Text(
                    summary(ui.data, h.draft()) + " · " + ago(now, h.heldAt) + if (h in stale) " · " + StrPos.stale else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (h in stale) colors.warning else colors.muted,
                  )
                  if (h.note.isNotBlank()) Text(h.note, style = MaterialTheme.typography.labelSmall, color = colors.muted2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = { ui.dialog = HeldDialog.Discard(h) }) { Text(StrPos.discardIt, color = colors.danger) }
                Button(onClick = { ui.resume(h.id) }, modifier = if (i == 0) Modifier.focusRequester(first) else Modifier) { Text(StrPos.openIt) }
              }
            }
          }
        },
        confirmButton = { TextButton(onClick = { ui.close() }) { Text(Str.close) } },
        dismissButton = if (ui.canDropStale && stale.isNotEmpty()) {
          { TextButton(onClick = { ui.dialog = HeldDialog.DropStale }) { Text(StrPos.clearStale(plain(stale.size)), color = colors.danger) } }
        } else null,
      )
    }
    is HeldDialog.Discard -> AlertDialog(
      onDismissRequest = { ui.dialog = HeldDialog.List },
      shape = Shape.dialog,
      title = { Text(StrPos.discardQ(plain(dlg.invoice.number))) },
      text = { Text(StrPos.discardWarn(dlg.invoice.label.ifBlank { StrPos.unnamed }, summary(ui.data, dlg.invoice.draft()))) },
      confirmButton = {
        Button(onClick = { ui.discard(dlg.invoice) }, colors = ButtonDefaults.buttonColors(containerColor = colors.danger)) { Text(StrPos.discardIt) }
      },
      dismissButton = { TextButton(onClick = { ui.dialog = HeldDialog.List }) { Text(StrPos.no) } },
    )
    HeldDialog.DropStale -> AlertDialog(
      onDismissRequest = { ui.dialog = HeldDialog.List },
      shape = Shape.dialog,
      title = { Text(StrPos.clearStaleQ) },
      text = { Text(StrPos.clearStaleWarn(plain(HeldRules.STALE_HOURS))) },
      confirmButton = {
        Button(onClick = { ui.dropStale() }, colors = ButtonDefaults.buttonColors(containerColor = colors.danger)) { Text(StrPos.clearIt) }
      },
      dismissButton = { TextButton(onClick = { ui.dialog = HeldDialog.List }) { Text(StrPos.no) } },
    )
  }
}

/** بخشِ تنظیمات — روشن/خاموشِ همین دستگاه (پیش‌فرض خاموش) */
@Composable
fun HeldInvoiceSettings() {
  val context = LocalContext.current
  HeldInvoicePrefs.load(context)
  val on = HeldInvoicePrefs.on.value
  ir.vil3ntec.tohid.ui.screens.SettingsSection(
    icon = Icons.Filled.Layers,
    title = HeldInvoicePrefs.SECTION,
    subtitle = if (on) StrShell.switchOn + keyHint(KeyMap.SALE_HOLD) else StrShell.switchOff,
    tint = if (on) Shop.colors.success else Shop.colors.muted,
  ) {
    Text(
      StrPos.heldNote(plain(HeldRules.MAX_OPEN)),
      style = MaterialTheme.typography.labelSmall,
      color = Shop.colors.muted2,
    )
    Spacer(Modifier.height(8.dp))
    ir.vil3ntec.tohid.ui.screens.SegmentedChoice(
      options = listOf("off" to StrShell.switchOff, "on" to StrShell.switchOn),
      selected = if (on) "on" else "off",
      onSelect = { HeldInvoicePrefs.set(context, it == "on") },
    )
    //  POS-12 «پاک‌سازیِ خودکارِ کهنه‌ها با مجوز»: عوض کردنش همان مجوزِ پاک کردنِ دستیِ کهنه‌ها (لغوِ فاکتور)
    if (on) {
      val canCancel = remember { ir.vil3ntec.tohid.data.ShopRole.canCancelSale(context) }
      val auto = HeldInvoicePrefs.autoClean.value
      Spacer(Modifier.height(12.dp))
      Text(StrPos.heldAutoClean(plain(HeldRules.STALE_HOURS)), style = MaterialTheme.typography.labelMedium, color = Shop.colors.text)
      Spacer(Modifier.height(4.dp))
      if (canCancel) {
        ir.vil3ntec.tohid.ui.screens.SegmentedChoice(
          options = listOf("off" to StrShell.switchOff, "on" to StrShell.switchOn),
          selected = if (auto) "on" else "off",
          onSelect = { HeldInvoicePrefs.setAutoClean(context, it == "on") },
        )
      } else {
        Text(
          (if (auto) StrShell.switchOn else StrShell.switchOff) + " — " + StrPos.heldAutoCleanLocked,
          style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted,
        )
      }
      Text(StrPos.heldAutoCleanNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    }
  }
}

/** POS-12 — هر چند وقت کهنه‌ها دوباره سنجیده شوند تا صندوقِ همیشه‌باز هم پاک بماند */
private const val AUTO_CLEAN_EVERY_MS = 60L * 60 * 1000

private val DIGITS = listOf(KeyName.D1, KeyName.D2, KeyName.D3, KeyName.D4, KeyName.D5, KeyName.D6, KeyName.D7, KeyName.D8, KeyName.D9)

/** کلیدِ فیزیکی بی پیرایه — همان `KeyName.matches(Key)`ِ `KeyBinding.kt` */
private fun KeyName.matchesKey(e: KeyEvent): Boolean = e.matches(ir.vil3ntec.tohid.core.keymap.Chord(this))
