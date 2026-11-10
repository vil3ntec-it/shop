package ir.vil3ntec.tohid.customer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.market.PublicOrder
import ir.vil3ntec.tohid.core.shopper.CustomerDeliveryPrefs
import ir.vil3ntec.tohid.core.shopper.PrefsForm
import ir.vil3ntec.tohid.core.shopper.PrefsRules
import ir.vil3ntec.tohid.core.shopper.ShopperApi
import ir.vil3ntec.tohid.core.shopper.ShopperResult
import ir.vil3ntec.tohid.core.shopper.ShopperText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 *  کارتِ «دستورِ تحویل» ِ پیگیری (CHK-24؛ همزادِ `renderPrefs` ِ `server/public/store/order.js`، قاعده‌ها [PrefsRules]):
 *  «زنگ بزنید»، «تحویل به…»، «نشانهٔ تازه» و «با کدام اسکناس…» تا وقتی سرور `editable` می‌گوید.
 *
 *  ⛔ تکرارِ امن: در هر لحظه یک درخواست ([busy]؛ دو بار زدن ⇒ یکی)، بدنه همان لحظهٔ زدن ساخته می‌شود و همیشه نسخهٔ
 *     دیده‌شده را دارد — پس پاسخِ گم‌شده و زدنِ دوباره یا «همان بود» است یا ۴۰۹، هرگز دو ویرایشِ گوناگون روی یک نسخه.
 *     هیچ تکرارِ خودکاری نیست. ۴xx ⇒ پیگیری از نو ([reload])؛ نمای تازه همان فرستاده ⇒ «ثبت شد» ([PrefsRules.landed]).
 *  کادرها فقط با نسخهٔ تازهٔ سرور از نو پُر می‌شوند (پیگیریِ ۳۰ ثانیه‌ای نوشتهٔ مشتری را پاک نمی‌کند، مثلِ `data-v` ِ وب).
 */
class PrefsModel(
  private val api: ShopperApi,
  private val scope: CoroutineScope,
  private val io: CoroutineDispatcher,
  private val reload: () -> Unit,
) {
  var prefs: CustomerDeliveryPrefs? by mutableStateOf(null)
    private set
  var form: PrefsForm by mutableStateOf(PrefsForm())
  var busy: Boolean by mutableStateOf(false)
    private set
  var note: String? by mutableStateOf(null)
    private set
  /** [note] «ثبت شد» است (رنگ)، نه خطا */
  var ok: Boolean by mutableStateOf(false)
    private set
  private var order: PublicOrder? = null
  private var token = ""
  private var shownVersion = -1L
  /** بدنهٔ فرستاده‌ای که پاسخش خطا بود/نرسید — نخستین نمای بعدی می‌سنجد که نشست یا نه */
  private var check: PrefsForm? = null

  /** پیگیری رسید (هر بار، با کلیدِ `deliveryPrefs` ِ همان پاسخ؛ نبود ⇒ پرچمِ خاموش ⇒ کارت نیست) */
  fun bind(o: PublicOrder, p: CustomerDeliveryPrefs?) {
    if (o.token != token) {
      token = o.token
      shownVersion = -1L
      check = null
      note = null
      ok = false
    }
    order = o
    prefs = p
    if (p == null) return
    if (p.version != shownVersion) {
      form = PrefsRules.formOf(o, p)
      shownVersion = p.version
    }
    check?.let { sent ->
      check = null
      if (PrefsRules.landed(sent, o, p)) { note = ShopperText.prefsSaved; ok = true }
    }
    if (!p.editable) { note = ShopperText.prefsLocked; ok = false }
  }

  /** کارت دیده می‌شود؟ */
  fun shown(o: PublicOrder): Boolean = o.token == token && PrefsRules.shown(o, prefs)

  fun tenders(o: PublicOrder): List<Long> = PrefsRules.tenders(o, prefs)

  fun save(slug: String) {
    val o = order ?: return
    val p = prefs ?: return
    if (busy || !p.editable || !PrefsRules.shown(o, p)) return
    val sent = PrefsRules.sent(o, p, form)
    val body = PrefsRules.body(o, p, sent)
    val t = token
    busy = true
    note = null
    ok = false
    check = null
    scope.launch {
      val r = withContext(io) { api.editDelivery(slug, t, body) }
      busy = false
      if (t != token) return@launch
      when (r) {
        is ShopperResult.Ok -> {
          note = ShopperText.prefsSaved
          ok = true
          //  نسخهٔ تازه ⇒ کادرها همان که سرور نگه داشت
          r.value.prefs?.let { bind(o, it) }
        }
        is ShopperResult.Fail -> {
          note = ShopperText.error(r.error)
          check = sent
          //  نسخهٔ کهنه، قفل، پرچمِ خاموش یا اسکناسِ ردشده (جمع عوض شد) ⇒ نمای امروز؛ شبکه/۴۲۹/۵xx ⇒ همین کادرها می‌مانند
          if (r.error.status in 400..499 && r.error.status != 429) reload()
        }
      }
    }
  }
}
