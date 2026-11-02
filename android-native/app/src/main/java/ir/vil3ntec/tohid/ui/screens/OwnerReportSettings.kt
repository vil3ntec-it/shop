package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.report.OwnerReportPlan
import ir.vil3ntec.tohid.data.repo.result
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/** عنوانِ بخش — همان کلیدِ فهرستِ تنظیماتِ کامپیوتر (`AppRootLegacy`) */
const val OWNER_REPORT_TITLE: String = "گزارشِ خودکار برای صاحب"

/**
 *  درِ سرورِ گزارشِ خودکار (`/api/shop/owner-report`، PAY-20/RPT-12) و آخرین «دیده شود؟» برای فهرستِ کامپیوتر.
 *  تا پاسخِ نخست و با پرچمِ خاموش یا نقشِ دیگر `visible = false` ⇒ نه ردیفی، نه بخشی (همان رابطِ امروز).
 */
object OwnerReportLive {
  const val ROOT = "/shop/owner-report"

  var visible by mutableStateOf(false)
    private set

  suspend fun load(api: ApiClient): ApiResult<OwnerReportPlan.View> = result {
    OwnerReportPlan.parse(api.get(ROOT)).also { visible = it.enabled && it.owner }
  }

  /** فقط صاحبِ دکان (سرور می‌سنجد: ۴۰۳ `owner_only`)؛ نسخهٔ کهنه ⇒ ۴۰۹ `version_conflict` */
  suspend fun set(api: ApiClient, next: OwnerReportPlan.Settings, expectedVersion: Long): ApiResult<OwnerReportPlan.Settings> = result {
    OwnerReportPlan.parseSettings(api.put(ROOT, OwnerReportPlan.body(next, expectedVersion))["settings"] as? JsonObject)
  }
}

/** ردیفِ فهرستِ تنظیماتِ کامپیوتر: فقط برای صاحبِ واردشده و پرچمِ روشنِ سرور (`api == null` ⇒ نه). */
@Composable
fun ownerReportRow(api: ApiClient?): Boolean {
  LaunchedEffect(api) { if (api != null) OwnerReportLive.load(api) }
  return api != null && OwnerReportLive.visible
}

private val STATUS_LABELS = mapOf("sent" to "فرستاده شد", "error" to "نرسید", "claimed" to "در راه")

/**
 *  بخشِ «گزارشِ خودکار برای صاحب» در تنظیمات: روشن/خاموش، روزانه یا هفتگی، ساعتِ محلیِ دکان و کانال‌ها
 *  (داخلِ برنامه، پوش، ایمیل). [api] فقط برای صاحبِ واردشده؛ وگرنه null و هیچ چیز رسم نمی‌شود.
 */
@Composable
fun OwnerReportSettings(api: ApiClient?, toast: (String) -> Unit) {
  if (api == null) return
  var view by remember { mutableStateOf<OwnerReportPlan.View?>(null) }
  var draft by remember { mutableStateOf(OwnerReportPlan.Settings()) }
  var busy by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  LaunchedEffect(api) { OwnerReportLive.load(api).onSuccess { v -> view = v; draft = v.settings } }
  val v = view ?: return
  if (!v.enabled || !v.owner) return

  SettingsSection(
    icon = Icons.Filled.TableChart,
    title = OWNER_REPORT_TITLE,
    subtitle = if (v.settings.active) OwnerReportPlan.coverage(v.settings) else "خاموش",
    tint = if (v.settings.active) Shop.colors.success else Shop.colors.muted,
  ) {
    Text(
      "خلاصهٔ فروش، برگشتی، خالص، روشِ پرداخت و پرفروش‌ها از ردیف‌های همگام‌شدهٔ همین دکان، فقط برای صاحبِ دکان. " +
        "ساعت به وقتِ دکان${if (v.timezone.isNotBlank()) " (${v.timezone})" else ""}؛ هر روز حداکثر یک گزارش. واتس‌اپ هنوز وصل نیست.",
      style = MaterialTheme.typography.labelSmall,
      color = Shop.colors.muted2,
    )
    Spacer(Modifier.height(8.dp))
    SegmentedChoice(
      options = listOf("off" to "خاموش", "on" to "روشن"),
      selected = if (draft.active) "on" else "off",
      onSelect = { draft = draft.copy(active = it == "on") },
    )
    if (draft.active) {
      Spacer(Modifier.height(8.dp))
      SegmentedChoice(
        options = listOf(OwnerReportPlan.DAILY to "هر روز", OwnerReportPlan.WEEKLY to "هر هفته"),
        selected = draft.frequency,
        onSelect = { draft = draft.copy(frequency = it) },
      )
      if (draft.frequency == OwnerReportPlan.WEEKLY) {
        val order = OwnerReportPlan.WEEK_ORDER
        Stepper("روزِ هفته", OwnerReportPlan.weekdayName(draft.weekday),
          onMinus = { draft = draft.copy(weekday = order[(order.indexOf(draft.weekday) + 6) % 7]) },
          onPlus = { draft = draft.copy(weekday = order[(order.indexOf(draft.weekday) + 1) % 7]) })
      }
      Stepper("ساعت", OwnerReportPlan.hourLabel(draft.hour),
        onMinus = { draft = draft.copy(hour = (draft.hour + 23) % 24) },
        onPlus = { draft = draft.copy(hour = (draft.hour + 1) % 24) })
      Text(OwnerReportPlan.coverage(draft), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
      Spacer(Modifier.height(8.dp))
      for ((ch, label) in listOf(OwnerReportPlan.INAPP to "داخلِ برنامه", OwnerReportPlan.PUSH to "پوش", OwnerReportPlan.EMAIL to "ایمیل")) {
        val usable = ch != OwnerReportPlan.EMAIL || v.emailAvailable
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(
            checked = ch in draft.channels,
            enabled = usable,
            onCheckedChange = { draft = draft.copy(channels = OwnerReportPlan.toggleChannel(draft.channels, ch)) },
          )
          Text(if (usable) label else "$label (حساب ایمیل ندارد)", color = Shop.colors.text)
        }
      }
    }
    TextButton(
      enabled = !busy && draft != v.settings,
      onClick = {
        busy = true
        scope.launch {
          OwnerReportLive.set(api, draft, v.settings.version)
            .onSuccess { s -> view = v.copy(settings = s); draft = s; toast("ذخیره شد") }
            .onFailure { f -> toast(if (f.code == "version_conflict") "تنظیم در این میان عوض شده؛ دوباره باز کنید" else f.userMessage) }
          busy = false
        }
      },
    ) { Text("ذخیره") }
    for (r in v.reports.take(5)) {
      val net = r.netSalesMinor?.let { " — خالص ${money(it / 100.0)}" }.orEmpty()
      Text("${r.reportDate} · ${STATUS_LABELS[r.status] ?: r.status}$net", style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    }
  }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(label, color = Shop.colors.muted)
    TextButton(onClick = onMinus) { Text("−") }
    Text(value, color = Shop.colors.text)
    TextButton(onClick = onPlus) { Text("+") }
  }
}
