package ir.vil3ntec.tohid.core.orders

/**
 *  جریانِ سفارشِ آنلاین پس از ثبت (درخواستِ صاحب‌کار ۱۴۰۵/۰۸/۱۳) — خالص (commonMain)، آینهٔ
 *  `server/src/lib/order-flow.js` و `order-arrival.js`؛ آزمون: `OrderFlowTest`.
 *
 *  ⛔ منبعِ حقیقت سرور است: «فاکتور شد» را `POST /shop/orders/:id/invoice` یک بار می‌نشاند و هر گذار را دوباره
 *  می‌سنجد؛ این‌جا فقط برای این است که برنامه دکمهٔ درست را با گام‌های درست نشان دهد.
 */
object OrderFlow {

  /** قلم‌ها قطعی‌اند و کالا هنوز برنگشته — همان `INVOICE_STATUSES` ِ سرور (`order-flow.test.js` دو فهرست را می‌سنجد) */
  val INVOICE_STATUSES: List<String> = listOf(
    "packed", "ready_for_pickup", "out_for_delivery", "delivery_failed", "retry_scheduled", "picked_up", "delivered",
  )

  /** «چه کسی درستش می‌کند» ِ هر دروازه (`fix` ِ `arrival`) */
  enum class Fix { SUPPORT, SUBSCRIBE, STOREFRONT, PRODUCTS, SERVER, NONE }

  fun fixOf(raw: String): Fix = when (raw) {
    "support" -> Fix.SUPPORT
    "subscribe" -> Fix.SUBSCRIBE
    "storefront" -> Fix.STOREFRONT
    "products" -> Fix.PRODUCTS
    "server" -> Fix.SERVER
    else -> Fix.NONE
  }

  /**
   *  گام‌های دکمهٔ «به فاکتور»:
   *  @property before گذارهای پیش از فاکتور (بسته‌بندی؛ برای «دریافت از دکان» ِ نقدی «آمادهٔ دریافت» هم)
   *  @property recordNow فروشِ سفارش همین حالا در دفتر (`OrderFulfillment.fulfill`)؛ `false` ⇒ فروش را گذارِ [after]
   *    (تحویل به مشتری) می‌نویسد — همان فروشِ تحویل با زمانِ «فاکتور شد»
   *  @property after گذارهای پس از «فاکتور شد» (فقط «مشتری گرفت» ِ دریافت از دکان)
   */
  data class Plan(val before: List<String>, val recordNow: Boolean, val after: List<String>) {
    /** دکمه با «بسته شد» شروع می‌شود */
    val packs: Boolean get() = "packed" in before
    /** دکمه کالا را به مشتری هم می‌دهد */
    val handsOver: Boolean get() = "picked_up" in after
  }

  /**
   *  دکمهٔ «به فاکتور» برای این سفارش — یا null (دکمه‌ای نیست).
   *  - فقط از «قبول‌شده» به بعد (پیش از قبول قیمت و قلم هنوز توافق نشده) و هنوز فاکتورنشده؛ آزمایشی و پرداختِ
   *    آنلاینِ در جریان نه (همان ۴۰۹ ِ سرور).
   *  - پیش از «بسته شد» نخست بسته‌بندی ([pack] = همان «بسته شد» ِ کارتِ آماده‌سازی؛ null ⇒ این حساب نمی‌تواند):
   *    پس از بسته‌بندی هیچ قلمی عوض نمی‌شود.
   *  - «دریافت از دکان» ِ پرداخت‌نشده: فاکتور همان تحویل به مشتری است — نقد همان لحظه سرِ پیشخوان به دخل می‌آید
   *    (`OrderFulfillment.atCounter`)؛ فاکتورِ زودتر نقدی می‌ساخت که هنوز نرسیده.
   *  - بقیه (ارسال، یا پرداخت‌شدهٔ آنلاین): فاکتور همین حالا؛ نقدِ ارسال تا تسویهٔ پیک امانت است (`custody = courier`).
   */
  fun plan(
    status: String,
    fulfillment: String,
    paymentStatus: String,
    isTest: Boolean,
    invoiced: Boolean,
    pack: List<String>?,
  ): Plan? {
    if (isTest || invoiced || paymentStatus == "pending") return null
    val packing = status == "accepted" || status == "picking"
    if (!packing && status !in INVOICE_STATUSES) return null
    if (packing && (pack == null || pack.lastOrNull() != "packed")) return null
    val before = if (packing) pack.orEmpty().toMutableList() else mutableListOf()
    val at = if (packing) "packed" else status
    val paid = paymentStatus == "paid"
    if (fulfillment == "pickup" && !paid && (at == "packed" || at == "ready_for_pickup")) {
      if (at == "packed") before += "ready_for_pickup"
      return Plan(before, recordNow = false, after = listOf("picked_up"))
    }
    return Plan(before, recordNow = true, after = emptyList())
  }

  /** حال‌های پرداخت (`payStep.state` ِ سرور) که هنوز کاری از مشتری می‌خواهند */
  fun waitsForCustomer(state: String): Boolean = state == "awaiting_accept" || state == "awaiting_choice"

  /**
   *  «پرداخت پس از پذیرش» و مشتری هنوز روشِ پرداخت را برنگزیده (`awaiting_choice`)، ولی دکمه فروش را همین حالا با نقد
   *  می‌نویسد ([Plan.recordNow]) ⇒ پس از آن سرور پرداختِ آنلاینِ مشتری را می‌بندد (`order_invoiced`) — فروشنده پیش از
   *  زدن باید بداند. «دریافت از دکان» ِ نقدی (فاکتور همراهِ تحویل سرِ پیشخوان) نه: مشتری همان‌جا می‌پردازد.
   */
  fun closesOnlineChoice(payState: String, plan: Plan): Boolean = payState == "awaiting_choice" && plan.recordNow
}
