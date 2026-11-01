package ir.vil3ntec.tohid.core.orders

/**
 *  آینهٔ ماشینِ وضعیتِ سفارشِ آنلاین — همان `server/src/lib/order-states.js`.
 *
 *  ⛔ منبعِ حقیقت سرور است: هر گذار را سرور دوباره می‌سنجد و این‌جا فقط برای
 *  این است که برنامه دکمهٔ نامجاز نشان ندهد، پنجرهٔ دلیل بداند چه بپرسد و
 *  سفارش را در برگهٔ درست بگذارد.
 *  ⛔ نمی‌تواند بی‌صدا از سرور جدا شود: `OrderStatesTest` همهٔ ردیف‌های
 *  `core/test-vectors/order-states.json` را (که `server/scripts/order-vectors.js`
 *  از خودِ `order-states.js` می‌سازد) روی همین پرونده می‌دواند.
 *
 *  خالص است — نه شبکه، نه زمانِ سیستم، نه android.* — تا اندروید و کامپیوتر
 *  هر دو همین را بگیرند.
 */
object OrderStates {

  val TRANSITIONS: Map<String, List<String>> = linkedMapOf(
    "new" to listOf("accepted", "rejected", "auto_rejected", "cancelled_by_customer"),
    "accepted" to listOf("picking", "cancelled"),
    "picking" to listOf("packed", "cancelled"),
    "packed" to listOf("ready_for_pickup", "out_for_delivery", "cancelled"),
    "ready_for_pickup" to listOf("picked_up", "cancelled"),
    "out_for_delivery" to listOf("delivered", "delivery_failed"),
    "delivery_failed" to listOf("retry_scheduled"),
    "retry_scheduled" to listOf("out_for_delivery", "returned_to_store"),
    "returned_to_store" to listOf("restocked"),
    "delivered" to listOf("return_requested"),
    "picked_up" to listOf("return_requested"),
    "return_requested" to listOf("refunded_partial", "refunded_full"),
    "rejected" to emptyList(),
    "auto_rejected" to emptyList(),
    "cancelled_by_customer" to emptyList(),
    "cancelled" to emptyList(),
    "restocked" to emptyList(),
    "refunded_partial" to emptyList(),
    "refunded_full" to emptyList(),
  )

  val STATUSES: List<String> = TRANSITIONS.keys.toList()

  /** دلیل‌های مجاز؛ «other» همیشه توضیح می‌خواهد */
  val REASONS: Map<String, List<String>> = linkedMapOf(
    "rejected" to listOf("out_of_stock", "closed", "busy", "out_of_area", "other"),
    "auto_rejected" to listOf("timeout", "other"),
    "cancelled" to listOf("customer_request", "out_of_stock", "no_show", "other"),
    //  recalled: دکان پس از حرکتِ پیک لغو کرد و بسته برمی‌گردد (تحویل و پیک)
    "delivery_failed" to listOf("no_answer", "wrong_address", "refused", "recalled", "other"),
  )

  val REASON_REQUIRED: List<String> = listOf("rejected", "auto_rejected", "cancelled", "delivery_failed")

  /** فقط سیستم (زمان‌سنجِ قبول) — هیچ کاربری دستی نمی‌زند */
  val SYSTEM_ONLY: List<String> = listOf("auto_rejected")

  /** پول برمی‌گردد ⇒ دسترسیِ `orders.refund` */
  val MONEY_TARGETS: List<String> = listOf("refunded_partial", "refunded_full")

  /** برگه‌های بخشِ «سفارش‌ها» (ORD-12) */
  val TABS: List<String> = listOf("new", "preparing", "ready", "on_the_way", "scheduled", "done")
  val OPEN_TABS: List<String> = TABS - "done"

  /** کنندهٔ گذار: سیستم، یا کاربری که شاید دسترسیِ بازپرداخت دارد */
  data class Actor(val system: Boolean = false, val canRefund: Boolean = false)

  fun nextOf(status: String): List<String> = TRANSITIONS[status].orEmpty()

  fun canTransition(from: String, to: String): Boolean = TRANSITIONS[from]?.contains(to) == true

  fun isTerminal(status: String): Boolean = TRANSITIONS[status]?.isEmpty() == true

  /**
   *  همان `check`ِ سرور، با همان ترتیبِ سنجه‌ها: `"ok"` یا کدِ خطا
   *  (`invalid_transition`، `fulfillment_mismatch`، `system_only`،
   *  `permission_denied`، `reason_required`، `bad_reason`، `note_required`).
   */
  fun check(from: String, fulfillment: String, to: String, actor: Actor, reason: String = "", note: String = ""): String {
    if (!canTransition(from, to)) return "invalid_transition"
    if (to == "ready_for_pickup" && fulfillment != "pickup") return "fulfillment_mismatch"
    if (to == "out_for_delivery" && fulfillment != "delivery") return "fulfillment_mismatch"
    if (to in SYSTEM_ONLY && !actor.system) return "system_only"
    if (to in MONEY_TARGETS && !actor.system && !actor.canRefund) return "permission_denied"
    if (to in REASON_REQUIRED && reason.isEmpty()) return "reason_required"
    val allowed = REASONS[to]
    if (reason.isNotEmpty() && allowed != null && reason !in allowed) return "bad_reason"
    if (to == "cancelled" && from == "ready_for_pickup" && reason != "no_show") return "bad_reason"
    if (reason == "other" && note.isBlank()) return "note_required"
    return "ok"
  }

  /** دکمه‌های مجازِ یک کارت برای یک کاربر — همان `nextFor`ِ سرور */
  fun nextFor(status: String, fulfillment: String, canRefund: Boolean): List<String> =
    nextOf(status).filter { to ->
      var reason = REASONS[to]?.firstOrNull().orEmpty()
      if (to == "cancelled" && status == "ready_for_pickup") reason = "no_show"
      check(status, fulfillment, to, Actor(system = false, canRefund = canRefund), reason, "x") == "ok"
    }

  /** دکمه‌های کارت در برنامه: بی `orders.manage` هیچ */
  fun actions(status: String, fulfillment: String, canManage: Boolean, canRefund: Boolean): List<String> =
    if (!canManage) emptyList() else nextFor(status, fulfillment, canRefund)

  fun needsReason(to: String): Boolean = to in REASON_REQUIRED

  /** دلیل‌های پنجرهٔ «دلیل» — «آماده ⇐ لغو» فقط «نیامد» */
  fun reasonChoices(from: String, to: String): List<String> {
    val list = REASONS[to].orEmpty()
    return if (to == "cancelled" && from == "ready_for_pickup") list.filter { it == "no_show" } else list
  }

  /** برگهٔ یک سفارش؛ زمان‌بندی‌شده تا «قبول» نشده در «جدید» می‌ماند */
  fun tabOf(status: String, scheduledFor: Long?, at: Long): String = when (status) {
    "new" -> "new"
    "accepted" -> if (scheduledFor != null && scheduledFor > at) "scheduled" else "preparing"
    "picking", "packed" -> "preparing"
    "ready_for_pickup" -> "ready"
    "out_for_delivery", "delivery_failed", "retry_scheduled" -> "on_the_way"
    else -> "done"
  }
}

/** نام‌های فارسیِ بخشِ «سفارش‌ها» — همان `license/orders-ui.js`ِ وب */
object OrderText {
  val TAB = mapOf(
    "new" to "جدید", "preparing" to "در آماده‌سازی", "ready" to "آماده",
    "on_the_way" to "در مسیر", "scheduled" to "زمان‌بندی‌شده", "done" to "تمام‌شده",
  )
  val STATUS = mapOf(
    "new" to "جدید", "accepted" to "قبول‌شده", "rejected" to "ردشده", "auto_rejected" to "ردِ خودکار",
    "cancelled_by_customer" to "لغو از سوی مشتری", "picking" to "در حالِ جمع‌آوری", "packed" to "بسته‌بندی‌شده",
    "ready_for_pickup" to "آمادهٔ تحویل در دکان", "out_for_delivery" to "در مسیر", "delivered" to "تحویل‌شده",
    "delivery_failed" to "تحویل نشد", "retry_scheduled" to "تلاشِ دوباره", "returned_to_store" to "برگشت به دکان",
    "restocked" to "برگشت به انبار", "cancelled" to "لغوشده", "picked_up" to "مشتری تحویل گرفت",
    "return_requested" to "درخواستِ برگشت", "refunded_partial" to "بازپرداختِ بخشی", "refunded_full" to "بازپرداختِ کامل",
  )
  val ACTION = mapOf(
    "accepted" to "قبول", "rejected" to "رد", "auto_rejected" to "ردِ خودکار", "cancelled_by_customer" to "مشتری لغو کرد",
    "picking" to "شروعِ جمع‌آوری", "packed" to "بسته شد", "ready_for_pickup" to "آماده برای تحویل",
    "out_for_delivery" to "ارسال شد", "delivered" to "تحویل شد", "delivery_failed" to "تحویل نشد",
    "retry_scheduled" to "تلاشِ دوباره", "returned_to_store" to "برگشت به دکان", "restocked" to "برگشت به انبار",
    "cancelled" to "لغو", "picked_up" to "مشتری گرفت", "return_requested" to "درخواستِ برگشت",
    "refunded_partial" to "بازپرداختِ بخشی", "refunded_full" to "بازپرداختِ کامل",
  )
  val REASON = mapOf(
    "out_of_stock" to "ناموجود", "closed" to "دکان بسته است", "busy" to "شلوغ", "out_of_area" to "خارج از محدوده",
    "other" to "دلیلِ دیگر", "timeout" to "پایانِ مهلت", "customer_request" to "درخواستِ مشتری", "no_show" to "مشتری نیامد",
    "no_answer" to "جواب نداد", "wrong_address" to "نشانیِ غلط", "refused" to "مشتری نپذیرفت",
    "recalled" to "دکان پس از حرکتِ پیک برگرداند",
  )
  /** ماشینِ جدای «نقد هنگامِ تحویل» (رویدادِ `cod` در خطِ زمانی) */
  val COD = mapOf(
    "to_collect" to "در انتظارِ دریافت", "collected_by_courier" to "پیک گرفت",
    "collected_in_store" to "در دکان گرفته شد", "settled_to_shop" to "به دکان تسویه شد",
  )
  val SOURCE = mapOf(
    "phone" to "تلفنی", "telegram" to "تلگرام", "whatsapp" to "واتس‌اپ", "manual" to "دستی",
    "web" to "ویترین", "storefront" to "ویترین",
  )
  /** گذارهایی که دکمه‌شان «خطر» است */
  val DANGER = setOf("rejected", "cancelled", "cancelled_by_customer", "delivery_failed")
  val READY_MINUTES = listOf(10, 15, 20, 30, 45, 60, 90)

  fun tab(id: String) = TAB[id] ?: id
  fun status(id: String) = STATUS[id] ?: id
  fun action(id: String) = ACTION[id] ?: id
  fun reason(id: String) = REASON[id] ?: id
  fun source(id: String) = SOURCE[id] ?: id

  fun pay(paymentStatus: String, paymentMethod: String): String = when {
    paymentStatus == "paid" -> "پرداخت‌شده"
    paymentMethod == "cod" -> "نقد هنگامِ تحویل"
    else -> "پرداخت‌نشده"
  }

  /** مبلغِ واحدِ کوچک (یک‌صدم) ⇐ «1,250» یا «33.33» — بی ممیزِ شناور */
  fun money(minor: Long): String {
    val neg = minor < 0
    val a = if (neg) -minor else minor
    val whole = (a / 100).toString()
    val out = StringBuilder()
    whole.forEachIndexed { i, c ->
      if (i > 0 && (whole.length - i) % 3 == 0) out.append(',')
      out.append(c)
    }
    val cents = (a % 100).toInt()
    if (cents != 0) out.append('.').append(if (cents < 10) "0$cents" else cents.toString())
    return (if (neg) "-" else "") + out
  }

  /** مقدارِ هزارم ⇐ «1.5» */
  fun qty(milli: Long): String {
    val whole = milli / 1000
    val frac = (milli % 1000).toString().padStart(3, '0').trimEnd('0')
    return if (frac.isEmpty()) whole.toString() else "$whole.$frac"
  }

  fun ago(ms: Long, now: Long): String {
    val d = maxOf(0L, (now - ms) / 60_000)
    return when {
      d < 1 -> "همین حالا"
      d < 60 -> "$d دقیقه پیش"
      d < 24 * 60 -> "${d / 60} ساعت پیش"
      else -> "${d / (24 * 60)} روز پیش"
    }
  }
}

/**
 *  فرمِ «سفارشِ دستیِ کارمند» (ORD-21) ⇐ بدنهٔ `POST /shop/orders` — همان
 *  `buildCreateBody`ِ وب. مبلغ را سرور می‌سازد؛ این‌جا فقط مقدار به هزارم و
 *  قیمت به یک‌صدم می‌رود، بی ممیزِ شناور.
 */
object OrderDraft {
  data class LineInput(val name: String = "", val qty: String = "", val price: String = "")
  data class Form(
    val source: String = "phone",
    val fulfillment: String = "pickup",
    val name: String = "",
    val phone: String = "",
    val addressText: String = "",
    val landmark: String = "",
    val deliveryFee: String = "",
    /** «مشتری با چه مبلغی می‌پردازد؟» (CHK-14) — افغانیِ کامل؛ خالی ⇒ نگفته */
    val cashTender: String = "",
    val note: String = "",
    val test: Boolean = false,
    val lines: List<LineInput> = listOf(LineInput(qty = "1")),
    /** ترجیحِ کلیِ جایگزینی (CHK-15) — فقط وقتی `/config` ِ سرور `substitution` دارد نشان داده می‌شود */
    val substitution: String = "",
  )
  /**
   *  [productId]/[unit] (POS-38، `core/pos/DeliverLater`): قلمِ سبدِ صندوق با شناسهٔ کالا و واحد، تا فروشِ
   *  تحویل همان کالا را کم کند. تهی (پیش‌فرض، فرمِ سفارشِ دستی) ⇒ فرستاده نمی‌شوند و بدنه همان پیشین است.
   */
  data class Line(val name: String, val qtyMilli: Long, val unitPriceMinor: Long, val productId: String = "", val unit: String = "")
  data class Body(
    val source: String,
    val fulfillment: String,
    val customerName: String,
    val customerPhone: String,
    val addressText: String,
    val landmark: String,
    val deliveryFeeMinor: Long,
    /** CHK-14 — null ⇒ کلید فرستاده نمی‌شود */
    val cashTenderMinor: Long? = null,
    val lines: List<Line>,
    val note: String,
    val test: Boolean,
    /** '' ⇒ کلید فرستاده نمی‌شود (سرورِ کهنه و پرچمِ خاموش همان بدنهٔ پیشین را می‌بینند) */
    val substitution: String = "",
  )

  sealed class Result {
    data class Ok(val body: Body) : Result()
    data class Error(val message: String) : Result()
  }

  /** رقمِ فارسی/عربی ⇐ لاتین، «٫» ⇐ نقطه، جداکنندهٔ هزارگان و فاصله پاک */
  fun latin(s: String): String = buildString {
    for (c in s) when (c) {
      in '۰'..'۹' -> append('0' + (c - '۰'))
      in '٠'..'٩' -> append('0' + (c - '٠'))
      '٫' -> append('.')
      ',', '٬', ' ', '\t', '\n', ' ' -> {}
      else -> append(c)
    }
  }

  /** «1.5» با سه رقمِ اعشار ⇐ 1500؛ نامعتبر یا اعشارِ بیشتر ⇐ null */
  fun scaled(text: String, places: Int): Long? {
    val s = latin(text)
    val m = Regex("^(\\d{1,12})(?:\\.(\\d*))?$").matchEntire(s) ?: return null
    val frac = m.groupValues[2]
    if (frac.length > places) return null
    var pow = 1L
    repeat(places) { pow *= 10 }
    val f = (frac + "0".repeat(places)).take(places)
    return m.groupValues[1].toLong() * pow + (if (f.isEmpty()) 0L else f.toLong())
  }

  fun build(form: Form): Result {
    fun err(m: String) = Result.Error(m)
    val delivery = form.fulfillment == "delivery"
    val name = form.name.trim()
    if (name.isEmpty()) return err("نامِ مشتری لازم است")
    val phone = form.phone.trim()
    if (delivery && phone.isEmpty()) return err("برای ارسال، شمارهٔ مشتری لازم است")
    val addressText = form.addressText.trim()
    val landmark = form.landmark.trim()
    if (delivery && addressText.isEmpty() && landmark.isEmpty()) return err("نشانی یا نشانهٔ تحویل لازم است")

    val lines = mutableListOf<Line>()
    form.lines.forEachIndexed { i, r ->
      val ln = r.name.trim()
      val q = r.qty.trim()
      val p = r.price.trim()
      if (ln.isEmpty() && q.isEmpty() && p.isEmpty()) return@forEachIndexed
      if (ln.isEmpty()) return err("نامِ قلمِ ${i + 1} لازم است")
      val qtyMilli = scaled(q.ifEmpty { "1" }, 3)
      if (qtyMilli == null || qtyMilli == 0L) return err("مقدارِ «$ln» درست نیست")
      val unitPriceMinor = scaled(p, 2) ?: return err("قیمتِ «$ln» درست نیست")
      lines += Line(ln, qtyMilli, unitPriceMinor)
    }
    if (lines.isEmpty()) return err("دست‌کم یک قلم بنویسید")

    var fee = 0L
    if (delivery && form.deliveryFee.isNotBlank()) {
      fee = scaled(form.deliveryFee, 2) ?: return err("هزینهٔ ارسال درست نیست")
    }
    //  CHK-14: قالب این‌جا؛ «نه کمتر از جمع، پول خرد کمتر از یک اسکناسِ ۱۰۰۰» را سرور با جمعِ خودش می‌سنجد
    val tender = when (val t = if (delivery) DeliveryPrefs.parseTender(form.cashTender) else DeliveryPrefs.Tender.None) {
      is DeliveryPrefs.Tender.Error -> return err(t.message)
      is DeliveryPrefs.Tender.Ok -> t.minor
      DeliveryPrefs.Tender.None -> null
    }
    return Result.Ok(
      Body(
        source = form.source.ifBlank { "manual" },
        fulfillment = if (delivery) "delivery" else "pickup",
        customerName = name,
        customerPhone = phone,
        addressText = if (delivery) addressText else "",
        landmark = if (delivery) landmark else "",
        deliveryFeeMinor = if (delivery) fee else 0L,
        cashTenderMinor = tender,
        lines = lines,
        note = form.note.trim(),
        test = form.test,
        substitution = OrderSubstitution.orderCodeOrEmpty(form.substitution),
      ),
    )
  }
}
