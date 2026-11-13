package ir.vil3ntec.tohid.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.stock.ExpiryGate
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.pay.platform.HesabPayEnv
import ir.vil3ntec.tohid.ui.components.pos.GatewayHook
import ir.vil3ntec.tohid.ui.components.staff.CreditApprovalDialog
import ir.vil3ntec.tohid.ui.v3.sales.checkout.CheckoutExpiry
import ir.vil3ntec.tohid.ui.v3.sales.checkout.HesabTillModel

/**
 *  S22 «منقضی فقط با مجوز» **پیش از گرفتنِ پول** در درگاه‌های صندوقِ کامپیوتر (حساب‌پیِ خودکار و ساده، کارت‌خوانِ پنل) —
 *  همان ترتیبِ صندوقِ گوشیِ نسخهٔ ۳ (`CheckoutRoute.hesabPay`): نیازِ منقضیِ نپوشیده (`CheckoutExpiry.uncovered`) ⇒ پنجرهٔ PIN
 *  پیش از باز شدنِ برگهٔ پول (`GatewayHook.ready`)؛ پیش‌سنجیِ «ثبت‌شدنی است؟» با همان `expiryGate` و همان تأیید
 *  (`HesabTillModel.precheck`) ⇒ بی تأیید هیچ قصدی ساخته و «پول رسید»ی پذیرفته نمی‌شود. تأیید به همین فاکتور بسته است
 *  ([rememberDeskExpiryPay] با کلیدِ فاکتور)، فقط همان کالاها تا همان مقدار را می‌پوشاند (`ExpiryGate.covers`) و همان را ثبتِ
 *  پس از پول (`DeskSale.finish`) و `SalesEngine.record` دوباره می‌سنجند.
 *  ⛔ پیش‌تر پیش‌سنجی بی `expiryGate` بود، تأیید فقط پس از تسویه پرسیده می‌شد و «انصراف» از آن تسویهٔ گرفته‌شده را دور می‌انداخت.
 */
internal class DeskExpiryPay {
  /** تأییدِ مدیر برای همین فاکتور که پیش از پول گرفته شد */
  var approval by mutableStateOf<ExpiryGate.Approval?>(null)
  /** نیازِ منقضی‌ای که همین حالا پیش از پول پرسیده می‌شود */
  var ask by mutableStateOf<Map<String, Long>?>(null)

  /** قلابِ درگاه‌ها روی [checkout] (با `expiryGate` ِ همین دستگاه) و تأییدِ پیش از پول */
  fun hook(env: HesabPayEnv, saleKey: String?, d: ShopData, cart: List<SalesEngine.CartLine>, checkout: SalesEngine.Checkout): GatewayHook {
    val c = checkout.copy(expiryApproval = approval)
    return GatewayHook(
      env, saleKey,
      ready = { CheckoutExpiry.uncovered(d, cart, c, ir.vil3ntec.tohid.todayIso())?.let { need -> ask = need; false } ?: true },
    ) { saleKey?.let { HesabTillModel.precheck(d, cart, c, it, ir.vil3ntec.tohid.todayIso()) } }
  }
}

/** حالِ تأییدِ پیش از پول — با فاکتورِ تازه (یا سبدِ خالی) از نو */
@Composable
internal fun rememberDeskExpiryPay(saleKey: String?): DeskExpiryPay = remember(saleKey) { DeskExpiryPay() }

/** پنجرهٔ PIN ِ «فروشِ کالای منقضی» (همان `CreditApprovalDialog` ِ سقفِ اعتبار با عنوان) ⇒ تأییدِ همین کالاها و تا همین مقدار */
@Composable
internal fun DeskExpiryDialog(d: ShopData, need: Map<String, Long>, seller: String, onApproved: (ExpiryGate.Approval) -> Unit, onDismiss: () -> Unit) {
  val context = LocalContext.current
  CreditApprovalDialog(
    summary = SalesEngine.expiredNeedText(d, need), debtorId = "", addMinor = 0L,
    selfRole = remember { ShopRole.current(context) }, selfName = seller,
    requesterUserId = remember { runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountId }.getOrDefault("") },
    onApproved = { ok -> onApproved(CheckoutExpiry.approval(ok, need)) },
    onDismiss = onDismiss, title = StrPos.expiredSaleTitle,
  )
}
