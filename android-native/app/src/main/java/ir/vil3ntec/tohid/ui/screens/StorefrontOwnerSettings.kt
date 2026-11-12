package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
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
import kotlinx.serialization.json.JsonObject

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
