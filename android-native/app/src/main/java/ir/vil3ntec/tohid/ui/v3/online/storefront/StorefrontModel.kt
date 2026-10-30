package ir.vil3ntec.tohid.ui.v3.online.storefront

import ir.vil3ntec.tohid.core.i18n.OnlineFmt
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.data.repo.StorefrontConfigDto
import ir.vil3ntec.tohid.data.repo.StorefrontProductDto

/**
 *  حالتِ خالصِ صفحهٔ ویترین (آزمون: `StorefrontModelTest`): کدام برگه‌ها (`ارسال و زمان` فقط با پرچمِ
 *  جدای `zones` ِ سرور)، برگهٔ آغاز از پیوند، فیلترِ کالاها و متنِ قیمت — همان قاعده‌های صفحهٔ پیشین.
 */
object StorefrontModel {
  const val STATUS = "status"
  const val SETTINGS = "settings"
  const val PRODUCTS = "products"
  const val MARKET = "market"
  const val DELIVERY = "delivery"

  /** برگه‌هایی که بی ویترینِ ساخته‌شده معنا ندارند ⇒ «اول ویترین را بسازید» */
  val NEEDS_SETTINGS = setOf(STATUS, PRODUCTS, DELIVERY)

  fun tabs(config: StorefrontConfigDto?): List<String> =
    listOf(STATUS, SETTINGS, PRODUCTS, MARKET) + if (config?.zones == true) listOf(DELIVERY) else emptyList()

  fun initialTab(query: String?, config: StorefrontConfigDto?): String =
    query?.takeIf { it in tabs(config) || (it == DELIVERY && config == null) } ?: STATUS

  fun label(tab: String): String = when (tab) {
    STATUS -> StrOnline.sfTabStatus
    SETTINGS -> StrOnline.sfTabSettings
    PRODUCTS -> StrOnline.sfTabProducts
    MARKET -> StrOnline.sfTabMarket
    DELIVERY -> StrOnline.sfTabDelivery
    else -> tab
  }

  enum class Filter { ALL, ON, OFF }

  fun filter(list: List<StorefrontProductDto>, query: String, filter: Filter): List<StorefrontProductDto> {
    val q = query.trim()
    return list.filter {
      (q.isEmpty() || it.name.contains(q, ignoreCase = true)) &&
        when (filter) { Filter.ON -> it.published; Filter.OFF -> !it.published; Filter.ALL -> true }
    }
  }

  fun count(list: List<StorefrontProductDto>, filter: Filter): Int = filter(list, "", filter).size

  /** «۱۲۰ ؋ (آنلاین)» / «۱۲۰ ؋» / «بی‌قیمت — در ویترین دیده نمی‌شود» */
  fun priceText(p: StorefrontProductDto): String {
    val online = p.onlinePriceMinor
    val base = p.priceMinor
    return when {
      online != null -> StrOnline.onlinePrice(OnlineFmt.afn(online))
      base != null -> OnlineFmt.afn(base)
      else -> StrOnline.noPrice
    }
  }

  /** قیمتِ دفتر برای برگهٔ کالا (null = ندارد) */
  fun ledgerPrice(p: StorefrontProductDto): String? = p.priceMinor?.let { OnlineFmt.afn(it) }

  /** کالای غیرفعال یا بیرون از ویترین / در ویترین — برای قرصِ ردیف */
  fun stateLabel(p: StorefrontProductDto): String = when {
    !p.active -> StrOnline.sfInactive
    p.published -> StrOnline.sfInStore
    else -> StrOnline.sfOutStore
  }
}
