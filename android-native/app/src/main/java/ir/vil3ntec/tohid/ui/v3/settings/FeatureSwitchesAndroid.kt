package ir.vil3ntec.tohid.ui.v3.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.data.AutoFeesPref
import ir.vil3ntec.tohid.data.GiftCardPref
import ir.vil3ntec.tohid.data.LayawayPref
import ir.vil3ntec.tohid.data.LoyaltyPref
import ir.vil3ntec.tohid.data.QuotePref
import ir.vil3ntec.tohid.data.SafeBookPref
import ir.vil3ntec.tohid.data.SpecialOrderPref
import ir.vil3ntec.tohid.data.TaxQuarterPref
import ir.vil3ntec.tohid.ui.components.pos.DeliverLaterPrefs
import ir.vil3ntec.tohid.ui.components.pos.PriceListPrefs
import ir.vil3ntec.tohid.ui.screens.SettingsLayout

/**
 *  حالِ «قابلیت‌ها» (`SettingsLayout.FEATURES`) — مرزِ سکو: هیچ ذخیرهٔ تازه‌ای نیست، هر کدام **همان** کلید و همان
 *  نوشتنِ صفحهٔ تنظیماتِ خودش را می‌خواند و می‌نویسد (`GiftCardPref.set`، `TaxQuarterPref.setOn`…؛ `SettingsPortTest`).
 *  پیش‌فرضِ همه خاموش می‌ماند (`SettingsTiersTest.B_PREFS`).
 *
 *  «هزینهٔ خودکار» کلیدِ جدا ندارد: روشن یعنی دست‌کم یک قاعده (`AutoFeesPref.rules`) و دکمه‌اش صفحهٔ قاعده‌ها را
 *  باز می‌کند؛ «حساب‌پیِ دکان» وصل کردنِ حساب است و فقط صفحه‌اش را دارد.
 */
internal class FeatureStates(
  /** قابلیت‌های روشن */
  val on: Set<String>,
  private val write: (String, Boolean) -> Boolean,
) {
  /** کلیدِ ساده‌ای دارد که کارت خودش روشن/خاموشش کند؟ */
  fun toggleable(key: String): Boolean = key in TOGGLES

  /** همان نوشتنِ صفحهٔ خودش؛ false ⇒ این قابلیت کلیدِ ساده ندارد (صفحه‌اش باز شود) */
  fun set(key: String, value: Boolean): Boolean = write(key, value)

  companion object {
    val TOGGLES: Set<String> = SettingsLayout.FEATURE_GATED - SettingsLayout.AUTO_FEES
  }
}

private fun featureOn(context: Context, key: String): Boolean = when (key) {
  SettingsLayout.GIFT_CARD -> GiftCardPref.on(context)
  SettingsLayout.LAYAWAY -> LayawayPref.on(context)
  SettingsLayout.LOYALTY -> LoyaltyPref.on(context)
  SettingsLayout.SAFE_BOOK -> SafeBookPref.on(context)
  SettingsLayout.SPECIAL_ORDER -> SpecialOrderPref.on(context)
  SettingsLayout.QUOTE -> QuotePref.on(context)
  SettingsLayout.PRICE_LIST -> PriceListPrefs.on(context)
  SettingsLayout.DELIVER_LATER -> { DeliverLaterPrefs.load(context); DeliverLaterPrefs.on.value }
  SettingsLayout.TAX_QUARTER -> TaxQuarterPref.on(context)
  SettingsLayout.AUTO_FEES -> AutoFeesPref.rules(context).isNotEmpty()
  else -> false
}

private fun writeFeature(context: Context, key: String, on: Boolean): Boolean {
  when (key) {
    SettingsLayout.GIFT_CARD -> GiftCardPref.set(context, on)
    SettingsLayout.LAYAWAY -> LayawayPref.set(context, on)
    SettingsLayout.LOYALTY -> LoyaltyPref.set(context, on)
    SettingsLayout.SAFE_BOOK -> SafeBookPref.set(context, on)
    SettingsLayout.SPECIAL_ORDER -> SpecialOrderPref.set(context, on)
    SettingsLayout.QUOTE -> QuotePref.set(context, on)
    SettingsLayout.PRICE_LIST -> PriceListPrefs.set(context, on)
    SettingsLayout.DELIVER_LATER -> DeliverLaterPrefs.set(context, on)
    SettingsLayout.TAX_QUARTER -> TaxQuarterPref.setOn(context, on)
    else -> return false
  }
  return true
}

/**
 *  حالِ همین لحظهٔ قابلیت‌ها؛ هر بار که صفحه کشیده می‌شود از همان کلیدها خوانده می‌شود (خواندنِ حافظه‌ایِ
 *  SharedPreferences) تا روشن/خاموشی که در صفحهٔ خودِ قابلیت زده شده هم بی‌درنگ دیده شود.
 */
@Composable
internal fun rememberFeatureStates(): FeatureStates {
  val context = LocalContext.current
  var version by remember { mutableIntStateOf(0) }
  //  خواندنِ `version` در ترکیب: پس از هر نوشتن از کارت، صفحه دوباره کشیده و حال دوباره خوانده می‌شود
  val tick = version
  val on = SettingsLayout.FEATURES.filter { tick >= 0 && featureOn(context, it) }.toSet()
  return FeatureStates(on) { key, value -> writeFeature(context, key, value).also { if (it) version++ } }
}
