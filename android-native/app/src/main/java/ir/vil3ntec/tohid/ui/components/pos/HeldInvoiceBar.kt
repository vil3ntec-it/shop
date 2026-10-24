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

/**
 *  برگه‌های فاکتورهای باز — POS-12 (چند فاکتورِ هم‌زمان، نگه‌داشتن با نام یا شماره).
 *
 *  یک تکهٔ خودبسنده تا در طراحی‌های تازهٔ صندوق (گوشی و کامپیوتر) هم همین را
 *  جابه‌جا کنند: صفحه فقط سه کار می‌کند —
 *   ۱) `val held = rememberHeldInvoices(cartStore, d, snackbar, extras, onSwitched)`
 *   ۲) `HeldInvoiceBar(held)` جایی بالای سبد
 *   ۳) کامپیوتر: `if (held.onKey(e)) return true` اولِ `onPreviewKeyEvent`ِ صفحه
 *
 *  پشتِ تنظیمِ «چند فاکتورِ هم‌زمان» که پیش‌فرض **خاموش** است. خاموش و بی
 *  نگه‌داشته ⇒ نه نواری کشیده می‌شود نه کلیدی خورده می‌شود (F6 فقط می‌گوید کجا
 *  روشنش کنند). خاموش ولی با نگه‌داشته‌ای که مانده ⇒ نوار می‌ماند تا هیچ
 *  فاکتوری پشتِ تنظیم گیر نکند.
 */
object HeldInvoicePrefs {
  val on = mutableStateOf(false)
  private var loaded = false
  private const val KEY = "held_invoices"

  fun load(context: android.content.Context) {
    if (loaded) return
    loaded = true
    on.value = context.getSharedPreferences("tohid-ui", 0).getBoolean(KEY, false)
  }

  fun set(context: android.content.Context, value: Boolean) {
    on.value = value
    context.getSharedPreferences("tohid-ui", 0).edit().putBoolean(KEY, value).apply()
  }

  const val SECTION = "چند فاکتورِ هم‌زمان"
  const val OFF_HINT = "نگه‌داشتنِ فاکتور خاموش است — تنظیمات ← «$SECTION»"
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
  internal var dialog by mutableStateOf<HeldDialog?>(null)

  /** نوار دیده می‌شود: تنظیم روشن است یا نگه‌داشته‌ای مانده */
  val visible: Boolean get() = HeldInvoicePrefs.on.value || store.book.value.items.isNotEmpty()

  private fun toast(text: String) { scope.launch { snackbar.showSnackbar(text) } }

  private fun run(op: suspend () -> HeldInvoiceStore.Outcome) {
    scope.launch {
      val message = when (val o = op()) {
        is HeldInvoiceStore.Outcome.Done -> { o.extras?.let(onSwitched); o.message }
        is HeldInvoiceStore.Outcome.Refused -> o.message
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

  fun hold(label: String?, note: String?) { dialog = null; run { store.hold(cart, extras(), label, note) } }
  /** «+ تازه» — نگه‌داشتن بی پرسیدنِ نام و صندوقِ خالی برای مشتریِ بعدی */
  fun newInvoice() = hold(null, null)
  fun resume(id: String) { dialog = null; run { store.resume(cart, id, extras(), data) } }
  fun step(dir: Int) = run { store.step(cart, dir, extras(), data) }
  fun discard(h: HeldInvoice) { dialog = null; run { store.discard(h.id) } }
  fun dropStale() { dialog = null; run { store.dropStale() } }

  /**
   *  کلیدهای صندوق (F6، Shift+F6، Ctrl+PgDn، Ctrl+PgUp) — `true` یعنی خورده شد.
   *  پنجره‌ای باز است ⇒ کلید مالِ همان پنجره است.
   */
  fun onKey(e: KeyEvent): Boolean {
    if (e.type != KeyEventType.KeyDown || dialog != null) return false
    val hit = listOf(KeyMap.SALE_HOLD, KeyMap.SALE_HELD_LIST, KeyMap.SALE_NEXT_OPEN, KeyMap.SALE_PREV_OPEN).firstOrNull { e.matches(it) } ?: return false
    if (!visible) { toast(HeldInvoicePrefs.OFF_HINT); return true }
    when (hit) {
      KeyMap.SALE_HOLD -> askHold()
      KeyMap.SALE_HELD_LIST -> dialog = HeldDialog.List
      KeyMap.SALE_NEXT_OPEN -> step(+1)
      else -> step(-1)
    }
    return true
  }
}

/**
 *  @param extras حالتی از صفحه که با فاکتور می‌رود و در `CartStore` نیست (تخفیفِ نوشته‌شده)
 *  @param onSwitched فاکتورِ صندوق عوض شد ⇒ صفحه همین‌ها را بگذارد (و انتخاب/پرداختِ نیمه‌کاره را خالی کند)
 *  @param onFocusBack پس از هر کار — کامپیوتر فوکوس را به کادرِ اسکن برمی‌گرداند
 */
@Composable
fun rememberHeldInvoices(
  cartStore: CartStore,
  d: ShopData,
  snackbar: SnackbarHostState,
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
  //  بالا آمدن پس از قطعِ برق وسطِ جابه‌جایی (شرحِ `HeldRules.reconcile`)
  LaunchedEffect(ui) { ui.store.reconcile(cartStore) }
  //  فاکتورِ فروش‌شده از فهرست می‌رود
  LaunchedEffect(ui, d.sales.size) { ui.store.prune(d) }
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
  val active = SaleDraft(key = if (lines.isEmpty()) null else ui.cart.saleKey(), lines = lines.map { SaleDraft.Line(it.productId, it.quantity, it.unitPrice, it.label) }, debtorId = debtorId)
  val activeNo = HeldRules.activeNumber(book, active)
  val tag = HeldRules.activeTag(book, active)
  val target = PosLayout.minTarget(touch = !ir.vil3ntec.tohid.ui.Platform.DESKTOP).dp
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
      title = tag?.label?.ifBlank { null } ?: customerName(d, debtorId) ?: if (lines.isEmpty()) "فاکتورِ تازه" else "فاکتورِ جاری",
      detail = if (lines.isEmpty()) "خالی" else summary(d, active),
      selected = true,
      warn = false,
      target = target,
      onClick = {},
    )
    book.items.forEach { h ->
      HeldChip(
        number = h.number,
        title = h.label.ifBlank { null } ?: customerName(d, h.debtorId) ?: "فاکتور",
        detail = summary(d, h.draft()) + if (h.id in stale) " · کهنه" else "",
        selected = false,
        warn = h.id in stale,
        target = target,
        onClick = { ui.resume(h.id) },
      )
    }
    TextButton(onClick = { ui.newInvoice() }, enabled = lines.isNotEmpty(), modifier = Modifier.heightIn(min = target)) {
      Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(4.dp))
      Text("فاکتورِ تازه" + keyHint(KeyMap.SALE_HOLD), maxLines = 1)
    }
    if (book.items.isNotEmpty()) {
      TextButton(onClick = { ui.dialog = HeldDialog.List }, modifier = Modifier.heightIn(min = target)) {
        Icon(Icons.Filled.Layers, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text("همه" + keyHint(KeyMap.SALE_HELD_LIST), maxLines = 1)
      }
    }
  }
}

@Composable
private fun HeldChip(number: Int?, title: String, detail: String, selected: Boolean, warn: Boolean, target: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
  val colors = Shop.colors
  val spoken = listOfNotNull(number?.let { "فاکتورِ ${plain(it)}" }, title, detail, if (selected) "در صندوق" else "نگه‌داشته").joinToString("، ")
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
  return "${plain(cart.size)} قلم · ${money(total)} ؋"
}

private fun ago(now: Long, at: Long): String {
  val min = ((now - at).coerceAtLeast(0) / 60_000L).toInt()
  return when {
    min < 1 -> "همین حالا"
    min < 60 -> "${plain(min)} دقیقه پیش"
    min < 48 * 60 -> "${plain(min / 60)} ساعت پیش"
    else -> "${plain(min / (24 * 60))} روز پیش"
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
        title = { Text("نگه‌داشتنِ فاکتور") },
        text = {
          Column {
            Text("مشتری منتظر است؟ نام یا شماره‌ای بدهید تا بعد پیدایش کنید. صندوق برای نفرِ بعد خالی می‌شود.", style = MaterialTheme.typography.bodySmall, color = colors.muted)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
              value = label,
              onValueChange = { label = it.take(HeldRules.LABEL_MAX) },
              singleLine = true,
              label = { Text("نام یا شماره (اختیاری)") },
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
              label = { Text("یادداشت (اختیاری)") },
              modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
          }
        },
        confirmButton = { Button(onClick = { ui.hold(label, note) }) { Text("نگه دار") } },
        dismissButton = { TextButton(onClick = { ui.close() }) { Text("بی‌خیال") } },
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
        title = { Text("فاکتورهای نگه‌داشته") },
        text = {
          if (items.isEmpty()) Text("فاکتورِ نگه‌داشته‌ای نیست.", color = colors.muted)
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
                  Text("#${plain(h.number)} · ${h.label.ifBlank { null } ?: customerName(ui.data, h.debtorId) ?: "فاکتور"}", style = MaterialTheme.typography.titleSmall, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  Text(
                    summary(ui.data, h.draft()) + " · " + ago(now, h.heldAt) + if (h in stale) " · کهنه" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (h in stale) colors.warning else colors.muted,
                  )
                  if (h.note.isNotBlank()) Text(h.note, style = MaterialTheme.typography.labelSmall, color = colors.muted2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = { ui.dialog = HeldDialog.Discard(h) }) { Text("دور بینداز", color = colors.danger) }
                Button(onClick = { ui.resume(h.id) }, modifier = if (i == 0) Modifier.focusRequester(first) else Modifier) { Text("باز کن") }
              }
            }
          }
        },
        confirmButton = { TextButton(onClick = { ui.close() }) { Text("بستن") } },
        dismissButton = if (ui.canDropStale && stale.isNotEmpty()) {
          { TextButton(onClick = { ui.dialog = HeldDialog.DropStale }) { Text("پاک کردنِ ${plain(stale.size)} کهنه", color = colors.danger) } }
        } else null,
      )
    }
    is HeldDialog.Discard -> AlertDialog(
      onDismissRequest = { ui.dialog = HeldDialog.List },
      shape = Shape.dialog,
      title = { Text("دور انداختنِ فاکتورِ #${plain(dlg.invoice.number)}؟") },
      text = { Text("«${dlg.invoice.label.ifBlank { "بی‌نام" }}» با ${summary(ui.data, dlg.invoice.draft())} دور انداخته می‌شود. این کار برنمی‌گردد؛ موجودی و دفتر دست نمی‌خورند.") },
      confirmButton = {
        Button(onClick = { ui.discard(dlg.invoice) }, colors = ButtonDefaults.buttonColors(containerColor = colors.danger)) { Text("دور بینداز") }
      },
      dismissButton = { TextButton(onClick = { ui.dialog = HeldDialog.List }) { Text("نه") } },
    )
    HeldDialog.DropStale -> AlertDialog(
      onDismissRequest = { ui.dialog = HeldDialog.List },
      shape = Shape.dialog,
      title = { Text("پاک کردنِ فاکتورهای کهنه؟") },
      text = { Text("فاکتورهای نگه‌داشتهٔ کهنه‌تر از ${plain(HeldRules.STALE_HOURS)} ساعت دور انداخته می‌شوند. این کار برنمی‌گردد.") },
      confirmButton = {
        Button(onClick = { ui.dropStale() }, colors = ButtonDefaults.buttonColors(containerColor = colors.danger)) { Text("پاک کن") }
      },
      dismissButton = { TextButton(onClick = { ui.dialog = HeldDialog.List }) { Text("نه") } },
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
    subtitle = if (on) "روشن" + keyHint(KeyMap.SALE_HOLD) else "خاموش",
    tint = if (on) Shop.colors.success else Shop.colors.muted,
  ) {
    Text(
      "مشتری‌ای منتظر است و دیگری پول می‌دهد: فاکتورِ اولی با نام یا شماره نگه داشته می‌شود و صندوق خالی. تا ${plain(HeldRules.MAX_OPEN)} فاکتورِ باز با هم، هر کدام با سبد، مشتری، تخفیف و یادداشتِ خودش. فقط روی همین دستگاه می‌ماند و با بسته شدنِ برنامه یا قطعِ برق از دست نمی‌رود.",
      style = MaterialTheme.typography.labelSmall,
      color = Shop.colors.muted2,
    )
    Spacer(Modifier.height(8.dp))
    ir.vil3ntec.tohid.ui.screens.SegmentedChoice(
      options = listOf("off" to "خاموش", "on" to "روشن"),
      selected = if (on) "on" else "off",
      onSelect = { HeldInvoicePrefs.set(context, it == "on") },
    )
  }
}

private val DIGITS = listOf(KeyName.D1, KeyName.D2, KeyName.D3, KeyName.D4, KeyName.D5, KeyName.D6, KeyName.D7, KeyName.D8, KeyName.D9)

/** کلیدِ فیزیکی بی پیرایه — همان `KeyName.matches(Key)`ِ `KeyBinding.kt` */
private fun KeyName.matchesKey(e: KeyEvent): Boolean = e.matches(ir.vil3ntec.tohid.core.keymap.Chord(this))
