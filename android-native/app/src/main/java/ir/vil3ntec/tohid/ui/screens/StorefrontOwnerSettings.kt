package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.LocaleChoice
import ir.vil3ntec.tohid.LocaleText
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.storefront.EtaSettingsDto
import ir.vil3ntec.tohid.core.storefront.EtaViewDto
import ir.vil3ntec.tohid.core.storefront.PromoGuardApi
import ir.vil3ntec.tohid.core.storefront.PromoGuardSettingsDto
import ir.vil3ntec.tohid.core.storefront.PromoGuardViewDto
import ir.vil3ntec.tohid.core.storefront.StorefrontEtaApi
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/** عنوانِ بخش‌ها — همان کلیدِ فهرستِ تنظیماتِ کامپیوتر (`AppRootLegacy`، `DeskSettingsCoverageTest`) */
const val STOREFRONT_ETA_TITLE: String = "زمانِ تقریبیِ تحویلِ ویترین"
const val PROMO_GUARD_TITLE: String = "تخفیفِ اولین خرید و سدِ سوءاستفاده"

/**
 *  درِ سرورِ زمانِ تقریبیِ تحویل (WEB-04) و آخرین «دیده شود؟» برای فهرستِ کامپیوتر.
 *  تا پاسخِ نخست، با پرچمِ خاموشِ `shop.storefront.eta` یا نقشِ بی `settings.write` ⇒ `visible = false`.
 */
object StorefrontEtaLive {
  var visible by mutableStateOf(false)
    private set

  suspend fun load(api: ApiClient): ApiResult<EtaViewDto> =
    StorefrontEtaApi.load(api).also { r -> visible = StorefrontEtaApi.visible(r.valueOrNull()) }

  /** صاحب و مدیر (سرور می‌سنجد)؛ نسخهٔ کهنه ⇒ ۴۰۹ `version_conflict`؛ پرچمِ خاموش ⇒ ۴۰۳ */
  suspend fun set(api: ApiClient, body: JsonObject): ApiResult<EtaSettingsDto> = StorefrontEtaApi.save(api, body)
}

/**
 *  درِ سرورِ اولین خرید و سدِ تخفیف (MKT-02، RSK-09). پرچمِ خاموشِ `shop.storefront.promo_guard` یا نقشِ دیگر ⇒
 *  `GET` ۴۰۳ ⇒ `visible = false`.
 */
object PromoGuardLive {
  var visible by mutableStateOf(false)
    private set

  suspend fun load(api: ApiClient): ApiResult<PromoGuardViewDto> =
    PromoGuardApi.load(api).also { r -> visible = r.isSuccess }

  suspend fun set(api: ApiClient, next: PromoGuardSettingsDto): ApiResult<PromoGuardSettingsDto> = PromoGuardApi.save(api, next)
}

/** ردیفِ فهرستِ کامپیوتر: فقط با پرچمِ روشنِ سرور و دسترسیِ تنظیم (`api == null` ⇒ نه) */
@Composable
fun storefrontEtaRow(api: ApiClient?): Boolean {
  LaunchedEffect(api) { if (api != null) StorefrontEtaLive.load(api) }
  return api != null && StorefrontEtaLive.visible
}

@Composable
fun promoGuardRow(api: ApiClient?): Boolean {
  LaunchedEffect(api) { if (api != null) PromoGuardLive.load(api) }
  return api != null && PromoGuardLive.visible
}

private val NUM = KeyboardOptions(keyboardType = KeyboardType.Number)
private val DEC = KeyboardOptions(keyboardType = KeyboardType.Decimal)

private fun money(minor: Long): String = LocaleText.afghani(minor / 100.0, LocaleChoice.digits, LocaleChoice.symbol)

@Composable
private fun Note(text: String) = Text(text, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)

/**
 *  بخشِ «زمانِ تقریبیِ تحویلِ ویترین»: نمایش روشن/خاموش، دقیقهٔ آماده‌سازی و راه، سفارشِ هم‌زمان و دقیقهٔ راهِ
 *  هر ناحیه، با نشانه‌های همین لحظه و پیش‌نمایشِ همان بازه‌ای که مشتری می‌بیند. [api] null ⇒ هیچ.
 */
@Composable
fun StorefrontEtaSettings(api: ApiClient?, toast: (String) -> Unit) {
  if (api == null) return
  var view by remember { mutableStateOf<EtaViewDto?>(null) }
  var form by remember { mutableStateOf(StorefrontEtaApi.Form()) }
  var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
  var busy by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  LaunchedEffect(api) { StorefrontEtaLive.load(api).onSuccess { v -> view = v; form = StorefrontEtaApi.formOf(v) } }
  val v = view ?: return
  if (!StorefrontEtaApi.visible(v)) return

  SettingsSection(
    icon = Icons.Filled.Schedule,
    title = STOREFRONT_ETA_TITLE,
    subtitle = if (v.settings.show) "روشن — مشتری بازهٔ تقریبی می‌بیند" else "خاموش",
    tint = if (v.settings.show) Shop.colors.success else Shop.colors.muted,
  ) {
    Note("مشتری پیش از ثبت و در پیگیری یک بازه می‌بیند (نه یک وعده): صفِ سفارش‌ها، آماده‌سازی، شلوغی، پیک و راه. " +
      "با سه سفارشِ آماده‌شدهٔ اخیر، آماده‌سازی از خودِ سفارش‌ها حساب می‌شود.")
    Spacer(Modifier.height(8.dp))
    SegmentedChoice(
      options = listOf("off" to "نشان داده نشود", "on" to "نشان داده شود"),
      selected = if (form.show) "on" else "off",
      onSelect = { form = form.copy(show = it == "on") },
    )
    Spacer(Modifier.height(8.dp))
    TohidTextField(form.prep, { form = form.copy(prep = it) }, "دقیقهٔ آماده‌سازیِ هر سفارش (۱ تا ۲۴۰)",
      error = errors["prep"], keyboardOptions = NUM)
    TohidTextField(form.travel, { form = form.copy(travel = it) }, "دقیقهٔ راهِ پیک (۰ تا ۲۴۰)",
      error = errors["travel"], keyboardOptions = NUM)
    TohidTextField(form.parallel, { form = form.copy(parallel = it) }, "سفارشِ هم‌زمانی که دکان آماده می‌کند (۱ تا ۲۰)",
      error = errors["parallel"], keyboardOptions = NUM)
    if (v.zones.isNotEmpty()) {
      Note("دقیقهٔ راهِ هر ناحیه (۰ تا ۲۴۰)؛ خالی = همان دقیقهٔ راهِ بالا")
      for (z in v.zones) {
        TohidTextField(form.zones[z.id].orEmpty(), { form = form.copy(zones = form.zones + (z.id to it)) },
          if (z.active) z.name else "${z.name} (غیرفعال)", error = errors["zone:${z.id}"], keyboardOptions = NUM)
      }
    }
    errors["zones"]?.let { Text(it, color = Shop.colors.warning) }
    TextButton(
      enabled = !busy,
      onClick = {
        val c = StorefrontEtaApi.check(form, v.zones.map { it.id }, v.settings.version)
        errors = c.errors
        val body = c.body
        if (body == null) { toast("چند کادر درست نیست؛ پیامِ زیرِ هر کادر را ببینید"); return@TextButton }
        busy = true
        scope.launch {
          StorefrontEtaLive.set(api, body)
            .onSuccess {
              toast("ذخیره شد")
              StorefrontEtaLive.load(api).onSuccess { n -> view = n; form = StorefrontEtaApi.formOf(n) }
            }
            .onFailure { f -> toast(StorefrontEtaApi.errorText(f.code, f.userMessage)) }
          busy = false
        }
      },
    ) { Text("ذخیره") }
    Note(StorefrontEtaApi.signalLine(v.signals))
    for (line in StorefrontEtaApi.previewLines(v.preview, v.zones)) Note(line)
  }
}

/**
 *  بخشِ «تخفیفِ اولین خرید و سدِ سوءاستفاده»: درصد یا مبلغ، سقف، کفِ سبد، ترکیب/انحصاری؛ سقفِ کد برای هر
 *  شماره و دستگاه و سقفِ تخفیفِ هر سفارش؛ و فهرستِ «مشکوک» (یک دستگاه با چند شماره، یک شماره با چند دستگاه).
 */
@Composable
fun PromoGuardSettings(api: ApiClient?, toast: (String) -> Unit) {
  if (api == null) return
  var view by remember { mutableStateOf<PromoGuardViewDto?>(null) }
  var form by remember { mutableStateOf(PromoGuardApi.Form()) }
  var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
  var busy by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  LaunchedEffect(api) { PromoGuardLive.load(api).onSuccess { v -> view = v; form = PromoGuardApi.formOf(v.settings) } }
  val v = view ?: return
  val f0 = v.settings.firstOrder
  val suspects = PromoGuardApi.suspectRows(v.suspicious)

  SettingsSection(
    icon = Icons.Filled.Redeem,
    title = PROMO_GUARD_TITLE,
    subtitle = buildString {
      append(
        when {
          !f0.enabled -> "اولین خرید خاموش"
          f0.kind == PromoGuardApi.AMOUNT -> "اولین خرید: ${money(f0.amountMinor)}"
          else -> "اولین خرید: ${PromoGuardApi.percentText(f0.bp)}٪"
        },
      )
      if (suspects.isNotEmpty()) append(" · ${ir.vil3ntec.tohid.core.storefront.StorefrontOwner.toFa(suspects.size.toString())} موردِ مشکوک")
    },
    tint = if (suspects.isNotEmpty()) Shop.colors.warning else if (f0.enabled) Shop.colors.success else Shop.colors.muted,
  ) {
    Note("اولین خرید فقط یک بار برای هر شمارهٔ تأییدشده و هر دستگاه، وقتی آن شماره سفارشِ پیشینی در این دکان ندارد.")
    Spacer(Modifier.height(8.dp))
    SegmentedChoice(
      options = listOf("off" to "اولین خرید خاموش", "on" to "اولین خرید روشن"),
      selected = if (form.enabled) "on" else "off",
      onSelect = { form = form.copy(enabled = it == "on") },
    )
    if (form.enabled) {
      Spacer(Modifier.height(8.dp))
      SegmentedChoice(
        options = listOf(PromoGuardApi.PERCENT to "درصد", PromoGuardApi.AMOUNT to "مبلغ"),
        selected = form.kind,
        onSelect = { form = form.copy(kind = it) },
      )
      if (form.kind == PromoGuardApi.PERCENT) {
        TohidTextField(form.percent, { form = form.copy(percent = it) }, "درصدِ تخفیف (تا ۱۰۰)", error = errors["percent"], keyboardOptions = DEC)
        TohidTextField(form.max, { form = form.copy(max = it) }, "سقفِ تخفیف به افغانی (خالی = بی سقف)", error = errors["max"], keyboardOptions = NUM)
      } else {
        TohidTextField(form.amount, { form = form.copy(amount = it) }, "مبلغِ تخفیف به افغانی", error = errors["amount"], keyboardOptions = NUM)
      }
      TohidTextField(form.minSubtotal, { form = form.copy(minSubtotal = it) }, "کفِ سبد به افغانی (خالی = بی کف)", error = errors["minSubtotal"], keyboardOptions = NUM)
      Spacer(Modifier.height(8.dp))
      SegmentedChoice(
        options = listOf(PromoGuardApi.COMBINE to "با تخفیف‌های دیگر", PromoGuardApi.EXCLUSIVE to "انحصاری"),
        selected = form.stack,
        onSelect = { form = form.copy(stack = it) },
      )
    }
    Spacer(Modifier.height(8.dp))
    Note("سدِ سوءاستفاده — برای همهٔ کدهای ویترین؛ خالی یا ۰ = بی سقف")
    TohidTextField(form.codePerPhone, { form = form.copy(codePerPhone = it) }, "هر کد برای هر شماره چند بار (۰ تا ۱۰۰۰)", error = errors["codePerPhone"], keyboardOptions = NUM)
    TohidTextField(form.codePerDevice, { form = form.copy(codePerDevice = it) }, "هر کد برای هر دستگاه چند بار (۰ تا ۱۰۰۰)", error = errors["codePerDevice"], keyboardOptions = NUM)
    TohidTextField(form.maxDiscount, { form = form.copy(maxDiscount = it) }, "سقفِ تخفیفِ هر سفارش به افغانی", error = errors["maxDiscount"], keyboardOptions = NUM)
    TextButton(
      enabled = !busy,
      onClick = {
        val c = PromoGuardApi.check(form)
        errors = c.errors
        val next = c.settings
        if (next == null) { toast("چند کادر درست نیست؛ پیامِ زیرِ هر کادر را ببینید"); return@TextButton }
        busy = true
        scope.launch {
          PromoGuardLive.set(api, next)
            .onSuccess { s -> view = v.copy(settings = s); form = PromoGuardApi.formOf(s); toast("ذخیره شد") }
            .onFailure { f -> toast(PromoGuardApi.errorText(f.code, f.userMessage)) }
          busy = false
        }
      },
    ) { Text("ذخیره") }
    Text(PromoGuardApi.suspectSummary(v.suspicious), color = if (suspects.isEmpty()) Shop.colors.muted else Shop.colors.warning)
    for (r in suspects) {
      Text(r.title, color = Shop.colors.text)
      Note(r.detail)
    }
    if (v.recent.isNotEmpty()) {
      Spacer(Modifier.height(8.dp))
      Note("تخفیف‌های نشستهٔ اخیر")
      for (c in v.recent.take(5)) {
        val code = if (c.code.isNotBlank()) " ${c.code}" else ""
        Note("${PromoGuardApi.kindLabel(c.kind)}$code · ${c.phone} · ${money(c.discountMinor)}")
      }
    }
  }
}
