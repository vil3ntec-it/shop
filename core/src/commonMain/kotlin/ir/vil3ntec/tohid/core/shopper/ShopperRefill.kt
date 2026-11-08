package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.market.PublicOrderLine
import ir.vil3ntec.tohid.core.market.PublicProduct
import kotlinx.serialization.Serializable

/*
 *  MKT-06 — «وقتِ دوباره خریدن؟»: یادآوری درونِ برنامه برای کالای مصرفی (چای، برنج، پودرِ لباس…).
 *
 *  دکان در فرمِ کالا «چرخهٔ مصرف» (روز) می‌گذارد؛ ویترین آن را در نمای عمومیِ کالا (`consumptionDays`) می‌دهد. برنامه
 *  هر کالایی را که از **همین دستگاه** سفارش داده با زمانِ همان سفارش نگه می‌دارد ([Bought]، فقط روی دستگاه، هرگز همگام
 *  نمی‌شود) و چرخه را هر چند ساعت یک بار از خودِ سرور تازه می‌کند (`GET …/products?ids=`)؛ کالایی که چرخه‌اش رسید
 *  کارتی روی خانه می‌گیرد. «افزودن به سبد» کالا را **از پاسخِ تازهٔ سرور** (قیمت، سقف و موجودیِ امروز) به سبد می‌برد —
 *  هیچ قیمتی از دستگاه یا از سفارشِ پیشین نمی‌آید؛ ثبت همان برگهٔ ثبت با بازقیمت‌گذاریِ سرور و تأییدِ خودِ مشتری است.
 *  ⛔ هیچ سفارشی در پس‌زمینه ثبت نمی‌شود. یادآوریِ بیرون از برنامه (Push) منتظرِ O-01 است.
 */

/** آخرین خریدِ یک کالا از همین دستگاه */
@Serializable
data class Bought(
  val slug: String,
  val productId: String,
  val name: String = "",
  val unit: String = "",
  /** مقدارِ همان سفارش (هزارم) — پیشنهادِ مقدارِ سبد، نه قیمت */
  val qtyMilli: Long = CartRules.STEP_MILLI,
  /** زمانِ همان سفارش (ساعتِ سرور) */
  val at: Long,
  /** چرخهٔ مصرفِ سرور در آخرین خواندن؛ null ⇒ بی یادآوری */
  val days: Int? = null,
  val checkedAt: Long = 0,
  /** «بعداً» تا این زمان */
  val snoozeUntil: Long = 0,
  /** «دیگر نه» — تا خریدِ بعدیِ همین کالا */
  val muted: Boolean = false,
)

/** کارتِ یک دکان روی خانه */
data class RefillCard(val slug: String, val storeTitle: String, val items: List<Bought>)

object Refills {
  const val MAX = 60
  const val MAX_DAYS = 365
  /** حداکثر کالا در یک کارت */
  const val PER_CARD = 5
  const val DAY_MS = RepeatPlans.DAY_MS
  /** چرخه‌ها هر شش ساعت یک بار از سرور (یک درخواست برای هر دکان) */
  const val CHECK_MS = 6 * 60 * 60 * 1000L

  private fun key(b: Bought) = b.slug + "\u0000" + b.productId

  /** سفارشِ تازه از همین دستگاه: هر قلم آخرین خریدِ همان کالاست؛ «بعداً/دیگر نه» ِ همان کالا پاک؛ چرخهٔ پیشین می‌ماند */
  fun record(list: List<Bought>, slug: String, lines: List<PublicOrderLine>, at: Long): List<Bought> {
    val prev = list.associateBy(::key)
    val fresh = lines.filter { it.productId.isNotBlank() && it.qtyMilli > 0 }.distinctBy { it.productId }.map { l ->
      val p = prev[slug + "\u0000" + l.productId]
      Bought(slug, l.productId, l.name, l.unit, l.qtyMilli, at, p?.days, p?.checkedAt ?: 0)
    }
    val keys = fresh.map(::key).toSet()
    return (fresh + list.filter { key(it) !in keys }).sortedByDescending { it.at }.take(MAX)
  }

  /** دکان ⇐ کالاهایی که چرخه‌شان باید دوباره از سرور خوانده شود (هرگز خوانده نشده، یا [CHECK_MS] گذشته) */
  fun stale(list: List<Bought>, now: Long): Map<String, List<String>> =
    list.filter { it.checkedAt == 0L || now - it.checkedAt >= CHECK_MS }.groupBy({ it.slug }, { it.productId })

  /** پاسخِ `?ids=` ِ سرور: چرخهٔ امروز؛ کالایی که پرسیده شد ولی دیگر در ویترین نیست ⇒ بی چرخه */
  fun withCycles(list: List<Bought>, slug: String, asked: List<String>, items: List<PublicProduct>, now: Long): List<Bought> {
    val by = items.associateBy { it.id }
    val ask = asked.toSet()
    return list.map { b ->
      if (b.slug != slug || b.productId !in ask) b
      else {
        val p = by[b.productId]
        b.copy(days = p?.consumptionDays?.takeIf { it in 1..MAX_DAYS }, checkedAt = now, name = p?.name ?: b.name)
      }
    }
  }

  /** موعد: یک روز پیش از پایانِ چرخه (چرخهٔ یک‌روزه همان روز) */
  fun dueAt(b: Bought): Long? = b.days?.takeIf { it in 1..MAX_DAYS }?.let { d -> b.at + (if (d > 1) d - 1 else d) * DAY_MS }

  fun isDue(b: Bought, now: Long): Boolean = !b.muted && b.snoozeUntil <= now && (dueAt(b)?.let { it <= now } ?: false)

  /** کارت‌های خانه: هر دکان یکی، کهنه‌ترین موعد اول؛ [titles] نامِ دکان از «دکان‌های اخیر» */
  fun cards(list: List<Bought>, now: Long, titles: Map<String, String>): List<RefillCard> =
    list.filter { isDue(it, now) }.sortedBy { dueAt(it) }.groupBy { it.slug }
      .map { (slug, items) -> RefillCard(slug, titles[slug]?.ifBlank { null } ?: slug, items.take(PER_CARD)) }

  private fun onDue(list: List<Bought>, slug: String, now: Long, change: (Bought) -> Bought): List<Bought> =
    list.map { if (it.slug == slug && isDue(it, now)) change(it) else it }

  /** «بعداً» (فردا دوباره) — همچنین پس از «افزودن به سبد» تا کارت تا ثبتِ سفارش دوباره نیاید */
  fun snooze(list: List<Bought>, slug: String, now: Long): List<Bought> = onDue(list, slug, now) { it.copy(snoozeUntil = now + DAY_MS) }

  /** «دیگر نه» — تا خریدِ بعدیِ همان کالا */
  fun mute(list: List<Bought>, slug: String, now: Long): List<Bought> = onDue(list, slug, now) { it.copy(muted = true) }

  /**
   *  سبد از کالاهای **امروزِ** سرور (پاسخِ `?ids=`): قیمت، سقف و موجودیِ همان پاسخ؛ مقدار همان مقدارِ سفارشِ پیشین (تا سقف).
   *  کالایی که از پیش در سبد است دست نمی‌خورد؛ ناسفارشی یا نبود ⇒ بیرون. @return (سبد، شمارِ جامانده)
   */
  fun cartFrom(cart: Cart, due: List<Bought>, items: List<PublicProduct>, at: Long): Pair<Cart, Int> {
    var c = cart
    var gone = 0
    for (b in due) {
      val p = items.firstOrNull { it.id == b.productId }
      if (p == null || !CartRules.canOrder(p)) { gone++; continue }
      if (CartRules.qtyOf(c, p.id) > 0) continue
      val added = CartRules.add(c, p, at)
      if (added === c) { gone++; continue }
      c = CartRules.setQty(added, p.id, b.qtyMilli.coerceAtLeast(CartRules.STEP_MILLI), at)
    }
    return c to gone
  }

  /** دکان نشانی‌اش را عوض کرد (S3) */
  fun moved(list: List<Bought>, from: String, to: String): List<Bought> = list.map { if (it.slug == from) it.copy(slug = to) else it }
}
