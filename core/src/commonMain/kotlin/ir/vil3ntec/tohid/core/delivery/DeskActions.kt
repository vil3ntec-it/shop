package ir.vil3ntec.tohid.core.delivery

/**
 *  کارهای کارمند روی یک مأموریت — آینهٔ `check()`ِ `delivery-states.js` برای
 *  `actor.kind = 'staff'` تا میز دکمهٔ بی‌اثر نشان ندهد. سرور هر کار را
 *  دوباره می‌سنجد؛ این فقط «چه چیزی دیده شود» است.
 *
 *  ⛔ گذارهای خودِ پیک (قبول، گرفتنِ بسته، در راه، رسیدم) این‌جا نیست؛ آن‌ها
 *     در برنامهٔ پیک‌اند. کارمند برای پیکِ دکان فقط پس‌گرفتن/فراخوانی،
 *     «تحویل شد / ناموفق» به جای پیک (با توضیح) و «بسته برگشت» دارد.
 */
object DeskActions {

  enum class Act {
    /** دریافتِ حضوری: کد ⇒ تحویل */
    PICKUP_HANDOVER,
    /** کدِ تازه (کدِ قبلی باطل، قفل باز) */
    NEW_CODE,
    /** پیش از حرکت: پس‌گرفتن با دلیل */
    WITHDRAW,
    /** شرکتِ بیرونی: بسته به شرکت داده شد */
    HAND_TO_COMPANY,
    /** تحویل شد (شرکتِ بیرونی؛ یا به جای پیکِ دکان با توضیح) */
    DELIVER,
    FAIL,
    /** ناموفق ⇒ تلاشِ دوباره (شرکتِ بیرونی) */
    RETRY,
    /** بسته به دکان برگشت (دریافتِ فیزیکی) */
    RETURN,
    /** لغو پس از حرکتِ پیکِ دکان */
    RECALL,
    /** ناموفق ⇒ اصلاحِ نشانیِ سفارش تا پیک دوباره برود (DLV-09؛ `POST /assignments/:id/address`) */
    FIX_ADDRESS,
  }

  private val DEPARTED = setOf("picked_up", "moving", "arrived", "failed")

  fun of(m: Mission, p: DeskPerms): List<Act> {
    if (!m.live) return emptyList()
    val out = ArrayList<Act>()
    when (m.mode) {
      "pickup" -> {
        //  سرور «تحویل شد»ِ حضوری را فقط از «آمادهٔ تحویل» می‌پذیرد (order_not_ready)
        if (m.status == "assigned" && p.handover && m.orderStatus in setOf("", "ready_for_pickup")) out += Act.PICKUP_HANDOVER
        if (m.status == "assigned" && p.assign && m.hasCode && !m.codeUsed) out += Act.NEW_CODE
        if (m.status == "assigned" && p.assign) out += Act.WITHDRAW
      }
      "external" -> {
        if (p.assign) {
          when (m.status) {
            "assigned" -> { out += Act.HAND_TO_COMPANY; out += Act.WITHDRAW }
            "picked_up", "moving", "arrived" -> { out += Act.DELIVER; out += Act.FAIL }
            "failed" -> { out += Act.RETRY }
          }
        }
        if (m.status in DEPARTED && p.handover) out += Act.RETURN
      }
      "shop_courier" -> {
        if (m.status in setOf("requested", "assigned") && p.assign) out += Act.WITHDRAW
        if (m.status in setOf("picked_up", "moving", "arrived") && p.assign) { out += Act.DELIVER; out += Act.FAIL }
        if (m.status in DEPARTED && p.assign && !m.recallRequested) out += Act.RECALL
        if (m.status in DEPARTED && p.handover) out += Act.RETURN
      }
    }
    //  کارِ بعدی پس از ناموفق (DLV-09): تلاشِ دوباره، اصلاحِ نشانی یا برگشت. سرور
    //  اصلاح را برای هر مأموریتِ زندهٔ ارسال می‌پذیرد؛ میز فقط پس از ناموفق پیشنهادش می‌کند.
    if (m.status == "failed" && m.mode != "pickup" && p.assign) out += Act.FIX_ADDRESS
    return out
  }

  /** «تحویل شد» برای پیکِ دکان را کارمند فقط با توضیح ثبت می‌کند (`note_required`) */
  fun needsNote(m: Mission, act: Act): Boolean =
    m.mode == "shop_courier" && (act == Act.DELIVER || act == Act.FAIL)

  /** وضعیتِ مقصدِ هر کار (بدنهٔ `POST /assignments/:id/status`) */
  fun target(act: Act): String? = when (act) {
    Act.PICKUP_HANDOVER, Act.DELIVER -> "delivered"
    Act.WITHDRAW -> "withdrawn"
    Act.HAND_TO_COMPANY -> "picked_up"
    Act.FAIL -> "failed"
    Act.RETRY -> "moving"
    Act.RETURN -> "returned"
    Act.NEW_CODE, Act.RECALL, Act.FIX_ADDRESS -> null
  }

  fun label(act: Act): String = when (act) {
    Act.PICKUP_HANDOVER -> "تحویل با کد"
    Act.NEW_CODE -> "کدِ تازه"
    Act.WITHDRAW -> "پس‌گرفتن"
    Act.HAND_TO_COMPANY -> "به شرکت داده شد"
    Act.DELIVER -> "تحویل شد"
    Act.FAIL -> "ناموفق"
    Act.RETRY -> "تلاشِ دوباره"
    Act.RETURN -> "بسته برگشت"
    Act.RECALL -> "فراخوانی"
    Act.FIX_ADDRESS -> "اصلاحِ نشانی"
  }
}
