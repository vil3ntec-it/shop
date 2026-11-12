package ir.vil3ntec.tohid.ui.v3.settings.sections

// i18n: سه‌زبانه

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrPromoHeld
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.promo.PromoHeld
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3ConfirmSheet
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.online.orders.OrdersModel
import ir.vil3ntec.tohid.ui.v3.sales.history.ReceiptModel
import ir.vil3ntec.tohid.ui.v3.settings.SettingsNote
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPageFrame
import kotlinx.coroutines.launch

/**
 *  PRM-02 — `settings/promo-held`: صاحب/مدیر کدِ سقف‌داری را آزاد می‌کند که لغوکنندهٔ فاکتور اجازه‌اش را نداشت
 *  (`release_not_allowed`). فهرست از `GET /api/shop/promos/redemptions`، آزاد کردن با همان `POST /redeem/release`.
 *  ⛔ دکمه برای فاکتوری که در دفترِ همین دستگاه لغوشده است (`PromoHeld.groups`)، و (۴-۲۱) برای فاکتورِ نارسیده فقط صاحب، ۷ روز
 *     پس از گرفتنِ کد و پس از برگهٔ هشدارِ «شاید هنوز برسد» (`PromoHeld.release`)؛ ردیفش در تنظیمات فقط با شمارشِ روشن
 *  و اجازهٔ لغوِ فاکتور (`promoHeldRow`)؛ نشانیِ مستقیم با پرچمِ خاموش یا سرورِ کهنه فقط «روشن نیست» می‌گوید.
 */
internal interface PromoHeldSource {
  suspend fun held(): ApiResult<PromoHeld.Page>
  suspend fun release(saleId: String): ApiResult<Int>
  /** پس از آزاد کردن: حالِ تخفیف‌ها (شمارِ مصرف) از نو */
  suspend fun changed()
}

/** حالِ صفحه — بی سرور و بی Compose ِ دیدنی آزموده می‌شود (`PromoHeldStateTest`) */
@Stable
internal class PromoHeldState(private val src: PromoHeldSource?) {
  var page by mutableStateOf<PromoHeld.Page?>(null)
    private set
  /** null ⇒ دسترسی هست؛ OFF/DENIED ⇒ پیامِ همان */
  var access by mutableStateOf<PromoHeld.Access?>(if (src == null) PromoHeld.Access.DENIED else null)
    private set
  var failed by mutableStateOf(false)
    private set
  /** فاکتوری که همین حالا آزاد می‌شود */
  var busy by mutableStateOf<String?>(null)
    private set

  suspend fun load() {
    val s = src ?: return
    failed = false
    when (val r = s.held()) {
      is ApiResult.Success -> { page = r.value; access = null }
      is ApiResult.Failure -> {
        val a = PromoHeld.access(r.error.status, r.error.code)
        if (a != null) { access = a; page = null } else failed = true
      }
    }
  }

  /** یک فاکتورِ لغوشده؛ دو بار زدن هم‌زمان ⇒ فقط یکی. @return null ⇒ هیچ کاری نشد */
  suspend fun release(saleId: String): PromoHeld.Done? {
    val s = src ?: return null
    if (busy != null) return null
    busy = saleId
    val done = try {
      when (val r = s.release(saleId)) {
        is ApiResult.Success -> PromoHeld.releaseOutcome(true, r.value, 200, "")
        is ApiResult.Failure -> PromoHeld.releaseOutcome(false, 0, r.error.status, r.error.code)
      }
    } finally {
      busy = null
    }
    when (done) {
      PromoHeld.Done.RELEASED, PromoHeld.Done.NOTHING -> { s.changed(); load() }
      PromoHeld.Done.OFF -> { access = PromoHeld.Access.OFF; page = null }
      PromoHeld.Done.DENIED, PromoHeld.Done.FAILED -> Unit
    }
    return done
  }
}

private fun doneText(d: PromoHeld.Done): String = when (d) {
  PromoHeld.Done.RELEASED -> StrPromoHeld.released
  PromoHeld.Done.NOTHING -> StrPromoHeld.nothing
  PromoHeld.Done.OFF -> StrPromoHeld.off
  PromoHeld.Done.DENIED -> StrPromoHeld.denied
  PromoHeld.Done.FAILED -> StrPromoHeld.releaseFailed
}

private fun doneTone(d: PromoHeld.Done): V3Tone = when (d) {
  PromoHeld.Done.RELEASED -> V3Tone.SUCCESS
  PromoHeld.Done.NOTHING -> V3Tone.INFO
  PromoHeld.Done.OFF, PromoHeld.Done.DENIED -> V3Tone.WARNING
  PromoHeld.Done.FAILED -> V3Tone.ERROR
}

@Composable
internal fun RouteScope.PromoHeldScreen(onBack: (() -> Unit)?) {
  val src = promoHeldSource()
  val s = remember(src) { PromoHeldState(src) }
  var tick by remember { mutableIntStateOf(0) }
  val scope = rememberCoroutineScope()
  val owner = promoHeldOwner()
  //  ۴-۲۱: فاکتورِ نارسیده‌ای که صاحب «آزاد کن» زد ⇒ برگهٔ هشدار پیش از آزاد کردن
  var warn by remember { mutableStateOf<String?>(null) }
  val go: (String) -> Unit = { id -> scope.launch { s.release(id)?.let { toasts.show(doneText(it), doneTone(it)) } } }
  LaunchedEffect(s, tick) { s.load() }
  val page = s.page
  val sales = data.sales
  val groups = remember(page, sales) {
    val rows = page?.rows.orEmpty()
    val ids = rows.map { it.saleId }.toSet()
    val mine = sales.filter { it.id in ids }.associateBy { it.id }
    PromoHeld.groups(rows) { id -> mine[id]?.status } to mine
  }
  SettingsPageFrame(title = StrPromoHeld.title, subtitle = StrPromoHeld.line, onBack = onBack) {
    val access = s.access
    when {
      access == PromoHeld.Access.OFF -> { item(key = "off") { SettingsNote(StrPromoHeld.off, Modifier.padding(top = 8.dp)) }; return@SettingsPageFrame }
      access == PromoHeld.Access.DENIED -> { item(key = "denied") { SettingsNote(StrPromoHeld.denied, Modifier.padding(top = 8.dp)) }; return@SettingsPageFrame }
      s.failed && page == null -> {
        item(key = "failed") {
          Column {
            SettingsNote(StrPromoHeld.failed, Modifier.padding(top = 8.dp))
            V3Button(StrPromoHeld.retry, { tick++ }, Modifier.padding(top = 8.dp), kind = V3ButtonKind.SECONDARY)
          }
        }
        return@SettingsPageFrame
      }
      page == null -> { item(key = "loading") { SettingsNote(StrPromoHeld.loading, Modifier.padding(top = 8.dp)) }; return@SettingsPageFrame }
    }
    val p = page ?: return@SettingsPageFrame
    val now = promoHeldNow()
    item(key = "note") { SettingsNote(StrPromoHeld.note, Modifier.padding(vertical = 8.dp)) }
    val (list, bySale) = groups
    if (list.isEmpty()) {
      item(key = "none") { Text(StrPromoHeld.none, style = V3Type.callout, color = PhoneV3.colors.inkMuted, modifier = Modifier.padding(16.dp)) }
      return@SettingsPageFrame
    }
    for (kind in PromoHeld.Sale.entries) {
      val part = list.filter { it.sale == kind }
      if (part.isEmpty()) continue
      item(key = "g-" + kind.name) {
        val (title, footer) = when (kind) {
          PromoHeld.Sale.CANCELLED -> StrPromoHeld.cancelled to StrPromoHeld.cancelledFooter
          PromoHeld.Sale.ACTIVE -> StrPromoHeld.active to StrPromoHeld.activeFooter
          PromoHeld.Sale.MISSING -> StrPromoHeld.missing to if (owner) StrPromoHeld.missingOwnerFooter else StrPromoHeld.missingFooter
        }
        V3Section(title, footer = footer) {
          for (g in part) {
            val sale = bySale[g.saleId]
            val how = PromoHeld.release(g, owner, now)
            V3ListRow(
              title = StrPromoHeld.invoice(sale?.let { ReceiptModel.number(it) } ?: g.saleId.takeLast(6)),
              subtitle = StrPromoHeld.codes(g.codes.joinToString("، "), OrdersModel.stamp(g.at)),
              chevron = false,
              trailing = if (how == PromoHeld.Release.NONE) null else {
                {
                  V3Button(
                    StrPromoHeld.release,
                    { if (how == PromoHeld.Release.WARN) warn = g.saleId else go(g.saleId) },
                    kind = V3ButtonKind.SECONDARY, enabled = s.busy == null, loading = s.busy == g.saleId, height = 36.dp,
                  )
                }
              },
            )
          }
        }
      }
    }
    if (p.more) item(key = "more") { SettingsNote(StrPromoHeld.more, Modifier.padding(vertical = 8.dp)) }
  }
  warn?.let { id ->
    V3ConfirmSheet(
      title = StrPromoHeld.warnTitle,
      message = StrPromoHeld.warnBody,
      confirm = StrPromoHeld.release,
      onConfirm = { warn = null; go(id) },
      onDismiss = { warn = null },
    )
  }
}
