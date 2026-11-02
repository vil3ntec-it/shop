package ir.vil3ntec.tohid.ui.screens

// i18n: سه‌زبانه

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrLateApology
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.orders.LateApology
import ir.vil3ntec.tohid.data.repo.LateApologyRepository
import ir.vil3ntec.tohid.data.repo.LateApologySource
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/*
 *  NTF-10 — بخشِ «پوزش و کوپنِ خودکارِ تأخیر» (`GET/PUT /api/shop/orders/late-apology`). حالت و ذخیره یک‌جا
 *  (`LateApologyForm`، `LateApologyLive`)؛ رابطِ امروز (همین پرونده) و نسخهٔ ۳ (`ui/v3/settings/sections/LateApologyScreen.kt`)
 *  فقط می‌کشند. پرچمِ خاموش ⇒ سرور `{enabled:false}` ⇒ نه ردیف، نه بخش.
 */

/** درِ سرور و آخرین «دیده شود؟» برای فهرستِ تنظیمات (کامپیوترِ رابطِ امروز و ردیفِ نسخهٔ ۳) */
object LateApologyLive {
  var visible by mutableStateOf(false)
    private set

  suspend fun load(src: LateApologySource): ApiResult<LateApology.View> = src.load().also { r ->
    r.onSuccess { visible = it.enabled }.onFailure { f -> if (serverRefused(f)) visible = false }
  }

  /** صاحب و مدیر (سرور می‌سنجد: `settings.write`)؛ نسخهٔ کهنه ⇒ ۴۰۹ `version_conflict`؛ پرچمِ خاموش ⇒ ۴۰۳ */
  suspend fun set(src: LateApologySource, body: JsonObject): ApiResult<LateApology.Policy> = src.save(body).also { r ->
    r.onFailure { f -> if (f.code == LateApology.OFF || f.code == "orders_disabled") visible = false }
  }
}

/** ردیفِ فهرستِ تنظیمات: فقط با پرچمِ روشنِ سرور (`api == null` ⇒ نه) */
@Composable
fun lateApologyRow(api: ApiClient?): Boolean {
  LaunchedEffect(api) { if (api != null) LateApologyLive.load(LateApologyRepository(api)) }
  return api != null && LateApologyLive.visible
}

/** حالِ مشترکِ بخش: نما، فرم، خطای کادرها و ذخیره */
@Stable
class LateApologyForm(private val src: LateApologySource) {
  var view by mutableStateOf<LateApology.View?>(null)
    private set
  var form by mutableStateOf(LateApology.Form())
  var errors by mutableStateOf<Set<LateApology.Bad>>(emptySet())
    private set
  var busy by mutableStateOf(false)
    private set

  val visible: Boolean get() = view?.enabled == true
  val editable: Boolean get() = view?.canEdit == true
  val changed: Boolean get() = view?.let { LateApology.changed(form, it.policy) } == true

  suspend fun load() {
    LateApologyLive.load(src)
      .onSuccess { v -> view = v; form = LateApology.formOf(v.policy); errors = emptySet() }
      .onFailure { f -> if (serverRefused(f)) view = null }
  }

  fun edit(next: LateApology.Form) {
    if (editable && !busy) form = next.copy(minutes = next.minutes.take(6), amount = next.amount.take(12), percent = next.percent.take(6), days = next.days.take(3))
  }

  /**
   *  «ذخیره»: سنجشِ فرم با بازه‌های سرور، سپس [write] (همان `LateApologyLive.set` در هر دو رابط). ⇒ پیامِ نتیجه،
   *  یا null وقتی چیزی فرستاده نشد (در حالِ ذخیره، یا بی اجازه).
   */
  suspend fun save(write: suspend (JsonObject) -> ApiResult<LateApology.Policy>): String? {
    val v = view ?: return null
    if (busy || !v.canEdit) return null
    val c = LateApology.check(form, v.policy.version)
    errors = c.errors
    val body = c.body ?: return StrLateApology.fixFields
    busy = true
    try {
      return when (val r = write(body)) {
        is ApiResult.Success -> {
          view = v.copy(policy = r.value)
          form = LateApology.formOf(r.value)
          StrLateApology.saved
        }
        is ApiResult.Failure -> when (r.error.code) {
          "version_conflict" -> { load(); StrLateApology.conflict }
          LateApology.OFF, "orders_disabled" -> { view = null; r.error.userMessage }
          else -> r.error.userMessage
        }
      }
    } finally {
      busy = false
    }
  }

  fun subtitle(): String = view?.policy?.takeIf { it.enabled }?.let { StrLateApology.on(it.lateMinutes.toString()) } ?: StrLateApology.off

  /** خطای یک کادر، یا null */
  fun error(b: LateApology.Bad): String? = if (b !in errors) null else when (b) {
    LateApology.Bad.MINUTES -> StrLateApology.badMinutes
    LateApology.Bad.AMOUNT -> StrLateApology.badAmount
    LateApology.Bad.PERCENT -> StrLateApology.badPercent
    LateApology.Bad.DAYS -> StrLateApology.badDays
  }

  /** خط‌های «پوزش‌های اخیر» (تا ده تا) */
  fun recentLines(stamp: (Long) -> String): List<String> = view?.recent.orEmpty().take(10).map { r ->
    val tail = buildList {
      if (r.outcome == "skipped_customer") add(StrLateApology.skipped)
      r.couponLast4?.let { add(StrLateApology.couponLast(it)) }
      if (r.redeemed) add(StrLateApology.redeemed)
      if (r.queued) add(StrLateApology.queued)
    }
    (listOf(StrLateApology.recentLine(r.orderNo.toString(), r.lateMinutes.toString(), stamp(r.at))) + tail).joinToString(" · ")
  }

  /** خلاصهٔ سیاست برای کسی که نمی‌تواند عوضش کند */
  fun summary(): List<String> {
    val p = view?.policy ?: return emptyList()
    val c = p.coupon
    return listOfNotNull(
      subtitle(),
      when {
        c == null -> StrLateApology.kindNone
        c.kind == LateApology.AMOUNT -> StrLateApology.kindAmount + ": " + evidenceMoney(c.amountMinor)
        else -> StrLateApology.kindPercent + ": " + LateApology.bpText(c.bp) + "%"
      },
      c?.let { StrLateApology.days + ": " + it.days },
    )
  }
}

/** کلیدِ «بی کوپن / مبلغ / درصد» برای دو رابط */
internal val lateApologyKinds: List<Pair<String, String>>
  get() = listOf(
    LateApology.NONE to StrLateApology.kindNone,
    LateApology.AMOUNT to StrLateApology.kindAmount,
    LateApology.PERCENT to StrLateApology.kindPercent,
  )

private val NUM = KeyboardOptions(keyboardType = KeyboardType.Number)
private val DEC = KeyboardOptions(keyboardType = KeyboardType.Decimal)

/** زمانِ کوتاه برای «پوزش‌های اخیر» (همان مهرِ صفِ سفارش) */
internal fun lateApologyStamp(ms: Long): String = ir.vil3ntec.tohid.ui.v3.online.orders.OrdersModel.stamp(ms)

/**
 *  بخشِ «پوزش و کوپنِ خودکارِ تأخیر» در تنظیماتِ رابطِ امروز. [api] null (بیرون از حساب) ⇒ هیچ؛ پرچمِ خاموش ⇒ هیچ؛
 *  نقشِ بی `settings.write` فقط خلاصه را می‌بیند.
 */
@Composable
fun LateApologySettings(api: ApiClient?, toast: (String) -> Unit) {
  if (api == null) return
  val src = remember(api) { LateApologyRepository(api) }
  val s = remember(src) { LateApologyForm(src) }
  val scope = rememberCoroutineScope()
  LaunchedEffect(src) { s.load() }
  val v = s.view ?: return
  if (!v.enabled) return
  val f = s.form
  SettingsSection(
    icon = Icons.Filled.Schedule,
    title = StrLateApology.title,
    subtitle = s.subtitle(),
    tint = if (v.policy.enabled) Shop.colors.success else Shop.colors.muted,
  ) {
    Text(StrLateApology.note, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Spacer(Modifier.height(8.dp))
    if (!s.editable) {
      for (l in s.summary()) Text(l, color = Shop.colors.text)
      Text(StrLateApology.readOnly, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    } else {
      SegmentedChoice(
        options = listOf("off" to StrLateApology.off, "on" to StrLateApology.switchOn),
        selected = if (f.enabled) "on" else "off",
        onSelect = { s.edit(f.copy(enabled = it == "on")) },
      )
      Spacer(Modifier.height(8.dp))
      TohidTextField(f.minutes, { s.edit(f.copy(minutes = it)) }, StrLateApology.minutes, error = s.error(LateApology.Bad.MINUTES), keyboardOptions = NUM)
      Text(StrLateApology.coupon, style = MaterialTheme.typography.labelLarge, color = Shop.colors.muted)
      SegmentedChoice(options = lateApologyKinds, selected = f.kind, onSelect = { s.edit(f.copy(kind = it)) })
      if (f.kind == LateApology.AMOUNT) {
        TohidTextField(f.amount, { s.edit(f.copy(amount = it)) }, StrLateApology.amount, error = s.error(LateApology.Bad.AMOUNT), keyboardOptions = DEC)
      }
      if (f.kind == LateApology.PERCENT) {
        TohidTextField(f.percent, { s.edit(f.copy(percent = it)) }, StrLateApology.percent, error = s.error(LateApology.Bad.PERCENT), keyboardOptions = DEC)
      }
      if (f.kind != LateApology.NONE) {
        TohidTextField(f.days, { s.edit(f.copy(days = it)) }, StrLateApology.days, error = s.error(LateApology.Bad.DAYS), keyboardOptions = NUM)
      }
      TextButton(
        enabled = !s.busy && s.changed,
        onClick = { scope.launch { s.save { LateApologyLive.set(src, it) }?.let(toast) } },
      ) { Text(StrLateApology.save) }
    }
    val recent = s.recentLines(::lateApologyStamp)
    if (recent.isNotEmpty()) {
      Text(StrLateApology.recent, style = MaterialTheme.typography.labelLarge, color = Shop.colors.muted)
      for (l in recent) Text(l, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    }
  }
}
