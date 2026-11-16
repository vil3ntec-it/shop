package ir.vil3ntec.tohid.ui.v3.online.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrOrderFlow
import ir.vil3ntec.tohid.core.i18n.StrSupportAsk
import ir.vil3ntec.tohid.core.model.OrderArrivalDto
import ir.vil3ntec.tohid.core.orders.OrderFlow
import ir.vil3ntec.tohid.core.support.SupportProblem
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.account.AccountPaths
import ir.vil3ntec.tohid.ui.v3.account.support.supportAsk
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.online.OnlinePaths
import ir.vil3ntec.tohid.ui.v3.online.storefront.StorefrontModel

/**
 *  «چرا سفارشِ آنلاین نمی‌رسد؟» (`arrival` ِ `/shop/orders/config`، `lib/order-arrival.js`) — هر دروازه با سرخط، «چه
 *  باید کرد» و دکمهٔ همان کار (ویترین، اشتراک، پشتیبانی). شکستِ بی‌صدا همان باگ بود؛ این کارت آن را می‌گوید و **هیچ
 *  دروازه‌ای را باز نمی‌کند** (اشتراک و پرچم‌ها تصمیمِ صاحب محصول‌اند). سرِ صفِ «سفارش‌های آنلاین» و تنظیماتِ
 *  «سفارشِ آنلاین» همین را می‌کشند. `showOk`: «همه چیز باز است» هم (فقط در تنظیمات).
 */
@Composable
internal fun OrderArrivalCardV3(
  arrival: OrderArrivalDto,
  onFix: (OrderFlow.Fix) -> Unit,
  modifier: Modifier = Modifier,
  showOk: Boolean = false,
  /** «از پشتیبانی بخواه» برای دروازهٔ کارِ پشتیبانی (`SupportProblem.forOrderBlocker`)؛ null ⇒ همان «پشتیبانی» ِ ساده */
  onAsk: ((String) -> Unit)? = null,
) {
  if (arrival.ok && arrival.warnings.isEmpty() && !showOk) return
  Column(modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    if (arrival.ok && showOk) V3Message(V3Tone.SUCCESS, StrOrderFlow.arrivalOk)
    arrival.blockers.forEachIndexed { i, b ->
      val fix = OrderFlow.fixOf(b.fix)
      //  کارِ پشتیبانی ⇒ درخواستِ ساخت‌یافته با یک لمس (کد هرگز روی صفحه نیست)
      val ask = if (fix == OrderFlow.Fix.SUPPORT && onAsk != null) SupportProblem.forOrderBlocker(b.code) else null
      V3Message(
        if (i == 0) V3Tone.WARNING else V3Tone.INFO,
        StrOrderFlow.blocker(b.code),
        body = StrOrderFlow.blockerHow(b.code),
        action = if (ask != null) StrSupportAsk.button else fixLabel(fix),
        onAction = if (ask != null) ({ onAsk?.invoke(ask) }) else fixLabel(fix)?.let { { onFix(fix) } },
      )
    }
    arrival.warnings.forEach { w ->
      V3Message(V3Tone.INFO, StrOrderFlow.blocker(w.code), body = StrOrderFlow.blockerHow(w.code))
    }
  }
}

/** دکمهٔ هر «چه کسی درستش می‌کند» — کارِ سرور دکمه ندارد */
internal fun fixLabel(fix: OrderFlow.Fix): String? = when (fix) {
  OrderFlow.Fix.STOREFRONT -> StrOrderFlow.fixStorefront
  OrderFlow.Fix.PRODUCTS -> StrOrderFlow.fixProducts
  OrderFlow.Fix.SUBSCRIBE -> StrOrderFlow.fixSubscribe
  OrderFlow.Fix.SUPPORT -> StrOrderFlow.fixSupport
  OrderFlow.Fix.SERVER, OrderFlow.Fix.NONE -> null
}

/** «از پشتیبانی بخواه» ِ همین کد از صفحهٔ `screen` (نامک، فقط برای پشتیبانی) */
internal fun RouteScope.openAsk(code: String, screen: String) {
  nav.open(AccountPaths.supportAsk(code, mapOf("screen" to screen)))
}

/** دکمهٔ کارت ⇒ همان صفحهٔ برنامه (تنظیمِ ویترین، کالاهای ویترین، اشتراک، پشتیبانی) */
internal fun RouteScope.openFix(fix: OrderFlow.Fix) {
  when (fix) {
    OrderFlow.Fix.STOREFRONT -> nav.open(OnlinePaths.storefront(StorefrontModel.SETTINGS))
    OrderFlow.Fix.PRODUCTS -> nav.open(OnlinePaths.storefront(StorefrontModel.PRODUCTS))
    OrderFlow.Fix.SUBSCRIBE -> nav.open(AccountPaths.SUBSCRIPTION)
    OrderFlow.Fix.SUPPORT -> nav.open(AccountPaths.SUPPORT)
    OrderFlow.Fix.SERVER, OrderFlow.Fix.NONE -> Unit
  }
}

/**
 *  متنِ «پرداختِ مشتری» برای `payStep.state` ِ سرور (`lib/order-flow.payStateOf`); حالتِ ناشناختهٔ سرورِ تازه‌تر ⇒ همان
 *  نقدِ پیش‌فرض (مثلِ سرور).
 */
internal fun payStateText(state: String, pickup: Boolean, chosen: Boolean): String = when (state) {
  "awaiting_accept" -> StrOrderFlow.payAwaitingAccept
  "awaiting_choice" -> StrOrderFlow.payAwaitingChoice
  "online_pending" -> StrOrderFlow.payOnlinePending
  "paid_online" -> StrOrderFlow.payPaidOnline
  "cash_collected" -> StrOrderFlow.payCashCollected
  "cash_settled" -> StrOrderFlow.payCashSettled
  "refunded" -> StrOrderFlow.payRefunded
  "closed" -> StrOrderFlow.payClosed
  else -> when {
    pickup && chosen -> StrOrderFlow.payCashPickupChosen
    pickup -> StrOrderFlow.payCashPickup
    chosen -> StrOrderFlow.payCashDeliveryChosen
    else -> StrOrderFlow.payCashDelivery
  }
}
