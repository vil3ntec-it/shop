package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.hw.NciScale
import ir.vil3ntec.tohid.core.hw.Scales
import ir.vil3ntec.tohid.core.hw.WeightBarcode
import ir.vil3ntec.tohid.core.pos.ScaleSale
import ir.vil3ntec.tohid.hw.HwPlatform
import ir.vil3ntec.tohid.hw.ScaleReader
import ir.vil3ntec.tohid.print.HwTools
import ir.vil3ntec.tohid.ui.components.common.SegmentTab
import ir.vil3ntec.tohid.ui.components.common.SegmentedTabs
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 *  POS-29 — وزن از ترازو در کادرِ ردیفِ صندوق و نوعِ برچسبِ ترازو (وزنی/قیمتی)؛ فایلِ مرزیِ پلتفرم.
 *
 *  «وزن از ترازو» تنظیمِ **همین دستگاه** است (`tohid-ui:scale_at_pos`)، پیش‌فرض **خاموش**: خاموش،
 *  گوشی (بی درگاهِ سریال) یا ترازوی انتخاب‌نشده ⇒ کادرِ ردیف همان امروز و دکمه‌ای نیست. حساب فقط
 *  `ScaleSale.fromScale` ِ هسته (وزنِ ایستا، مثبت و بی‌خطا؛ کالای کیلویی یا گرمی)؛ درگاه و پروتکل
 *  همانِ «دستگاه‌ها ⇐ ترازو» (`scale_port`، `scale_protocol` با «ابزارهای تازهٔ دستگاه»، وگرنه NCI).
 */
object ScaleAtPos {
  val on = mutableStateOf(false)
  private var loaded = false
  private const val KEY = "scale_at_pos"

  fun load(context: android.content.Context) {
    if (loaded) return
    loaded = true
    on.value = runCatching { context.getSharedPreferences("tohid-ui", 0).getBoolean(KEY, false) }.getOrDefault(false)
  }

  fun set(context: android.content.Context, value: Boolean) {
    on.value = value
    runCatching { context.getSharedPreferences("tohid-ui", 0).edit().putBoolean(KEY, value).apply() }
  }

  fun readWhy(why: ScaleSale.ReadWhy): String = when (why) {
    ScaleSale.ReadWhy.NOT_WEIGHED -> "این کالا کیلویی یا گرمی نیست"
    ScaleSale.ReadWhy.ERROR -> "ترازو خطا می‌دهد"
    ScaleSale.ReadWhy.OVER -> "بیش از ظرفیتِ ترازو"
    ScaleSale.ReadWhy.UNDER -> "زیرِ ظرفیتِ ترازو — ترازو را صفر کنید"
    ScaleSale.ReadWhy.MOTION -> "ترازو هنوز ایستا نیست — صبر کنید و دوباره بزنید"
    ScaleSale.ReadWhy.NO_WEIGHT -> "وزنی روی ترازو نیست"
  }
}

/** دکمهٔ «وزن از ترازو» زیرِ کادرِ مقدارِ ردیف؛ [onWeight] مقدار به واحدِ کالا ([unit]) */
@Composable
fun ScaleWeighButton(unit: String, onWeight: (Double) -> Unit) {
  val context = LocalContext.current
  ScaleAtPos.load(context)
  HwTools.load(context)
  if (!ScaleAtPos.on.value || HwPlatform.serial == null || ScaleSale.weighs(unit) == ScaleSale.Weighs.NONE) return
  val prefs = remember { context.getSharedPreferences("tohid", 0) }
  val port = remember { prefs.getString("scale_port", "").orEmpty() }
  if (port.isBlank()) return
  val proto = remember {
    if (HwTools.on.value) Scales.of(prefs.getString("scale_protocol", NciScale.id) ?: NciScale.id) ?: NciScale else NciScale
  }
  val scope = rememberCoroutineScope()
  var busy by remember { mutableStateOf(false) }
  var note by remember { mutableStateOf<String?>(null) }
  OutlinedButton(enabled = !busy, onClick = {
    busy = true
    scope.launch {
      val r = withContext(Dispatchers.IO) { ScaleReader.read(port, proto) }
      busy = false
      note = when (r) {
        is ScaleReader.Result.Failed -> r.message
        is ScaleReader.Result.Ok -> when (val w = ScaleSale.fromScale(r.reading, unit)) {
          is ScaleSale.Weigh.Ok -> { onWeight(w.quantity); null }
          is ScaleSale.Weigh.Rejected -> ScaleAtPos.readWhy(w.why)
        }
      }
    }
  }) { Text(if (busy) "در حالِ خواندن…" else "وزن از ترازو") }
  note?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Shop.colors.warning) }
}

/**
 *  «دستگاه‌ها ⇐ ترازو»: نوعِ برچسب (وزنی/قیمتی) وقتی برچسبِ ترازو روشن است، و کلیدِ «وزن از ترازو
 *  در صندوق» وقتی این دستگاه درگاهِ سریال دارد. [onRules] قاعده‌های تازه را ذخیره می‌کند.
 */
@Composable
fun ScaleAtPosSettings(rules: List<WeightBarcode.Rule>, onRules: (List<WeightBarcode.Rule>) -> Unit) {
  val context = LocalContext.current
  ScaleAtPos.load(context)
  if (rules.isNotEmpty()) {
    //  POS-29: نوع، طولِ کدِ کالا و رقمِ اعشار — همیشه قاعدهٔ EAN-13 ِ درست از `ScaleSale.rules`
    val first = rules.first()
    val kind = first.kind
    SegmentedTabs(
      listOf(SegmentTab(WeightBarcode.Kind.WEIGHT, "برچسبِ وزنی"), SegmentTab(WeightBarcode.Kind.PRICE, "برچسبِ قیمتی")),
      kind,
      { k -> onRules(ScaleSale.rules(k, first.itemDigits, ScaleSale.decimalsFor(k).first())) },
    )
    SegmentedTabs(
      ScaleSale.ITEM_DIGITS.map { SegmentTab(it, "کدِ کالا $it رقم") },
      first.itemDigits,
      { n -> onRules(ScaleSale.rules(kind, n, first.decimals)) },
    )
    SegmentedTabs(
      ScaleSale.decimalsFor(kind).map { SegmentTab(it, decimalsLabel(kind, it)) },
      first.decimals,
      { dec -> onRules(ScaleSale.rules(kind, first.itemDigits, dec)) },
    )
    val sample = ScaleSale.sample(rules)
    Text(
      (if (kind == WeightBarcode.Kind.PRICE) "مقدار = مبلغ ÷ قیمتِ کالا، و برچسبی که مبلغِ ردیف را دقیقاً همان نکند رد می‌شود. " else "") +
        "${first.valueDigits} رقمِ مقدار پس از ${first.itemDigits} رقمِ کد" + (sample?.let { "؛ نمونه: $it" } ?: ""),
      style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted,
    )
  }
  if (HwPlatform.serial == null) return
  Row(Modifier.fillMaxWidth().padding(vertical = Space.xxs), verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) {
      Text("وزن از ترازو در صندوق", style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
      Text(
        "دکمهٔ «وزن از ترازو» در کادرِ ردیفِ کالای کیلویی و گرمی؛ فقط وزنِ ایستا. انتخابِ همین دستگاه است.",
        style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted,
      )
    }
    Switch(checked = ScaleAtPos.on.value, onCheckedChange = { ScaleAtPos.set(context, it) })
  }
}

private fun decimalsLabel(kind: WeightBarcode.Kind, decimals: Int): String = when {
  kind == WeightBarcode.Kind.PRICE && decimals == 0 -> "مبلغ به افغانی"
  kind == WeightBarcode.Kind.PRICE -> "مبلغ با دو رقمِ پول"
  decimals == 3 -> "وزن به گرم"
  else -> "وزن به ده گرم"
}
