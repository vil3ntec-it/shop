package ir.vil3ntec.tohid.customer.ui

import ir.vil3ntec.tohid.core.shopper.RepeatPlan
import ir.vil3ntec.tohid.core.shopper.RepeatPlans
import ir.vil3ntec.tohid.core.shopper.ShopperLink
import ir.vil3ntec.tohid.core.shopper.ShopperLinks
import ir.vil3ntec.tohid.core.shopper.ShopperMemory
import ir.vil3ntec.tohid.core.shopper.ShopperResult
import ir.vil3ntec.tohid.core.shopper.ShopperText

/*
 *  برنامهٔ خرید، مرحلهٔ ۲ — سفارشِ تکرارشونده (MKT-08) و پیوندِ عمیق، کنارِ [CustomerModel] (همان حال و همان پشته؛
 *  فایلِ جدا فقط تا مدل از سقفِ اندازه نگذرد). قاعده‌ها در core: `RepeatPlans`، `ShopperLinks`.
 */

// ── سفارشِ تکرارشونده (MKT-08، روی همین دستگاه) ──

fun CustomerModel.repeatOf(token: String): RepeatPlan? = RepeatPlans.of(saved.plans, token)

/** [days] null ⇒ بی تکرار؛ فقط سفارشی که از همین دستگاه ثبت شده */
fun CustomerModel.setRepeat(token: String, days: Int?) {
  val o = saved.orders.firstOrNull { it.token == token } ?: return
  persist(saved.copy(plans = if (days == null) RepeatPlans.remove(saved.plans, token) else RepeatPlans.set(saved.plans, o, days, clock())))
}

fun CustomerModel.duePlans(): List<RepeatPlan> = RepeatPlans.due(saved.plans, clock())

/** سبد از «خریدِ دوباره» ِ سرور با قیمت و موجودیِ امروز؛ ثبت همان برگهٔ ثبت با تأییدِ خودِ مشتری */
fun CustomerModel.fillFromPlan(p: RepeatPlan) {
  request({ api.reorder(p.slug, p.token) }) { r ->
    when (r) {
      is ShopperResult.Ok -> {
        val (c, gone) = RepeatPlans.cartFrom(cartOf(p.slug), r.value, clock())
        persist(ShopperMemory.withCart(saved, c).let { it.copy(plans = RepeatPlans.advance(it.plans, p.token, clock())) })
        go(Route.CartPage(p.slug))
        if (gone > 0) notice = ShopperText.repeatGone(gone)
      }
      is ShopperResult.Fail -> linkNote = ShopperText.error(r.error)
    }
  }
}

fun CustomerModel.snoozePlan(p: RepeatPlan) = persist(saved.copy(plans = RepeatPlans.snooze(saved.plans, p.token, clock())))

fun CustomerModel.skipPlan(p: RepeatPlan) = persist(saved.copy(plans = RepeatPlans.advance(saved.plans, p.token, clock())))

// ── پیوندِ عمیق (دکان، کالا، سفارش، QR ِ کمپین) ──

/** پیوندی که سیستم به برنامه داد؛ نادرست ⇒ false و پیامِ روشن روی خانه */
fun CustomerModel.openLink(url: String): Boolean {
  linkNote = null
  when (val l = ShopperLinks.parse(url)) {
    null -> { linkNote = ShopperText.linkBad; return false }
    is ShopperLink.Shop -> {
      l.campaign?.let { campaigns[l.slug] = it }
      stack = listOf(Route.Home, Route.Shop(l.slug))
      enter(Route.Shop(l.slug))
      l.productId?.let { go(Route.Product(l.slug, it)) }
    }
    is ShopperLink.Order -> {
      stack = listOf(Route.Home, Route.Track(l.slug, l.token))
      enter(stack.last())
    }
    is ShopperLink.Campaign -> request({ api.campaign(l.code) }) { r ->
      val t = r.valueOrNull()
      if (t == null) linkNote = r.errorOrNull()?.let { ShopperText.error(it) } ?: ShopperText.linkBad
      else openLink(ShopperLinks.appLink(ShopperLink.Shop(t.slug)) + (t.campaign?.let { "?c=$it" } ?: ""))
    }
  }
  return true
}

