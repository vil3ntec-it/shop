package ir.vil3ntec.tohid.ui.v3.online.orders

import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.data.OrderFulfillment
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.invoiceLabel
import ir.vil3ntec.tohid.ui.OrdersLive

/**
 *  هشدارهای «فروشِ تحویل» (`OrderFulfillment`، #340) روی کیتِ نسخهٔ ۳ — همان شرط‌های صفحهٔ
 *  پیشینِ `ui/screens/Orders.kt`، خالص (آزمون: `OrderFulfillNotesTest`). موجودی فقط در دفترِ
 *  همین دکان کم می‌شود؛ این‌جا فقط گفته می‌شود چه مانده و دکمهٔ کارِ بعدی کدام است.
 *  همه پشتِ پرچمِ سرورِ `saleOnHandover` اند (`OrdersLive.canRecord`/`rolloutNotice`).
 */
object OrderFulfillNotes {

  enum class Act {
    /** «دوباره ثبت کن» — `OrdersLive.reconcile(force)` */
    RETRY_ALL,
    /** باز کردنِ نخستین سفارشِ بررسی */
    OPEN_REVIEW,
    /** «ثبتِ فروش» / «لغوِ فروشِ تحویل» — `OrdersLive.postById` */
    POST,
    /** «بررسی شد» ِ صاحب — `OrdersLive.ackReview` */
    ACK,
  }

  /** [warn]: رنگِ هشدار؛ وگرنه آگاهی (برچسبِ فاکتورِ فروشِ تحویل) */
  data class Note(val key: String, val title: String, val body: String = "", val act: Act? = null, val actLabel: String? = null, val warn: Boolean = true, val orderId: String? = null)

  /** سرِ صف: یادآوریِ دورهٔ گذار، بررسیِ صاحب، تحویلِ بی‌فروش، منتظرِ صاحب/مدیر، کمبود */
  fun queue(
    rollout: Boolean,
    review: List<OrdersLive.Unposted>,
    unposted: List<OrdersLive.Unposted>,
    waiting: List<OrdersLive.Unposted>,
    shortfalls: List<OrdersLive.Unposted>,
    canRefund: Boolean,
  ): List<Note> = buildList {
    if (rollout) add(Note("rollout", OrdersLive.ROLLOUT_NOTE))
    if (review.isNotEmpty() && canRefund) add(
      Note("review", StrOnline.ffReviewCount(review.size), lines(review), Act.OPEN_REVIEW, StrOnline.ffReview, orderId = review.first().orderId),
    )
    if (unposted.isNotEmpty()) add(Note("unposted", StrOnline.ffUnpostedCount(unposted.size), lines(unposted), Act.RETRY_ALL, StrOnline.ffRetryAll))
    if (waiting.isNotEmpty()) add(Note("waiting", StrOnline.ffWaitingCount(waiting.size), (listOf(StrOnline.ffWaitingBody) + waiting.take(3).map { it.message }).joinToString("\n")))
    if (shortfalls.isNotEmpty()) add(Note("short", StrOnline.ffShortCount(shortfalls.size), lines(shortfalls)))
  }

  /**
   *  جزئیاتِ یک سفارش. [manage]: `orders.manage`؛ [canRecord]/[canReverse]: `OrdersLive.canRecord/canReverse`
   *  برای همین سفارش؛ [waiting]: منتظرِ دستگاهِ صاحب یا مدیر؛ [since]: کفِ سرور (`handoverSince`).
   */
  fun detail(
    ledger: ShopData,
    o: OrderDto,
    review: List<OrdersLive.Unposted>,
    unposted: List<OrdersLive.Unposted>,
    since: Long,
    manage: Boolean,
    canRefund: Boolean,
    canRecord: Boolean,
    canReverse: Boolean,
    waiting: Boolean,
  ): List<Note> = buildList {
    val sold = OrderFulfillment.saleOf(ledger, o.id)
    val mine = review.firstOrNull { it.orderId == o.id }
    when {
      sold != null -> add(Note("sold", StrOnline.ffSold(sold.invoiceLabel() ?: "—"), warn = false))
      mine != null -> add(Note("review", StrOnline.ffReviewTitle, mine.message, if (canRefund) Act.ACK else null, StrOnline.ffAck))
      OrderFulfillment.needsSale(ledger, o, since) -> {
        val able = manage && canRecord && !waiting
        add(Note(
          "unposted", StrOnline.ffNoSaleTitle,
          unposted.firstOrNull { it.orderId == o.id }?.message ?: if (able) StrOnline.ffNoSaleBody else OrdersLive.SKIPPED_NOTE,
          if (able) Act.POST else null, StrOnline.ffRecord,
        ))
      }
    }
    if (sold != null && OrderFulfillment.needsReverse(ledger, o)) {
      //  پیش‌تر «مرجوعیِ» دستی گرفته ⇒ لغوِ خودکار نه؛ صاحب بررسی می‌کند و «بررسی شد» می‌زند
      val blocked = OrderFulfillment.reverseBlock(ledger, o)
      val able = blocked == null && manage && canReverse && !waiting
      val ackable = blocked != null && canRefund && mine != null
      add(Note(
        "reverse", StrOnline.ffReverseTitle,
        blocked ?: if (able) StrOnline.ffReverseBody else OrdersLive.SKIPPED_REVERSE_NOTE,
        if (able) Act.POST else if (ackable) Act.ACK else null,
        if (able) StrOnline.ffReverse else StrOnline.ffAck,
      ))
    }
  }

  private fun lines(rows: List<OrdersLive.Unposted>): String = rows.take(3).joinToString("\n") { it.message }
}
