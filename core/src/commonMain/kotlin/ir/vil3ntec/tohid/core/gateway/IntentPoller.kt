package ir.vil3ntec.tohid.core.gateway

/**
 *  پاییدنِ یک قصد تا پایان — حلقهٔ `GET …?wait=N` (انتظارِ بلند)، نه کوبیدنِ پشتِ سرِ هم.
 *
 *  - وضعیتِ پایانی (paid، failed، expired، cancelled، refunded) ⇐ پایان با همان قصد.
 *  - ⛔ ساعتِ سرور داور است، نه ساعتِ دستگاه: قصدِ گذشته را خودِ سرور با همان خواندن `expired`
 *    می‌کند؛ پس تا سرور جواب می‌دهد حلقه فقط با وضعیتِ پایانی تمام می‌شود (ساعتِ جلوافتادهٔ
 *    دستگاه پرداختِ در جریان را «تمام» نمی‌کند).
 *  - بی‌نتی و خطای گذرا ⇐ صبر با پسروی (۱، ۲، ۴، ۸، ۱۵ ثانیه) و دوباره؛ اگر تا [OFFLINE_GIVE_UP_MS]
 *    پس از مهلت هم نرسید ⇐ [End.TimedOut]: پرداخت نامعلوم است و فروش بسته **نمی‌شود**
 *    (قصد نگه داشته می‌شود و بازکردنِ دوباره همان را می‌پاید).
 *  - سرورِ بی انتظار (یا پرِ انتظار) که زود برمی‌گردد ⇐ دست‌کم [MIN_ROUND_MS] میانِ دو خواندن.
 *  - بستنِ صفحه ⇐ لغوِ کوروتینِ صدازننده؛ `sleep` و لایهٔ HTTP همان‌جا می‌ایستند.
 *
 *  ساعت و خواب تزریق می‌شوند (آزمون‌پذیر، بی `System.currentTimeMillis`).
 */
class IntentPoller(
  private val client: HesabPayClient,
  private val now: () -> Long,
  private val sleep: suspend (Long) -> Unit,
  private val waitSeconds: Int = HesabPayClient.WAIT_SECONDS,
) {

  sealed interface Event {
    /** قصد عوض شد (وضعیت، مهلت، مرجع) */
    data class Update(val intent: PaymentIntent) : Event

    /** خواندن نشد — صفحه «در حالِ اتصال…» نشان می‌دهد؛ حلقه ادامه دارد */
    data class Trouble(val message: String) : Event
  }

  sealed interface End {
    /** وضعیتِ پایانی از سرور */
    data class Final(val intent: PaymentIntent) : End

    /** مهلت گذشت و سرور وضعیتِ پایانی نداد — پرداخت نامعلوم؛ فروش بسته نمی‌شود */
    data class TimedOut(val last: PaymentIntent) : End

    /** سرور این قصد را نمی‌شناسد (دکانِ دیگر یا پاک‌شده) */
    data class Lost(val message: String) : End
  }

  suspend fun run(start: PaymentIntent, onEvent: (Event) -> Unit = {}): End {
    var cur = start
    if (cur.status.terminal) return End.Final(cur)
    val backoff = Backoff()
    //  پس از گرفتاری، نخستین خواندنِ درست «Update» می‌دهد تا صفحه «در حالِ اتصال» را بردارد
    var troubled = false
    while (true) {
      val started = now()
      when (val r = client.fetch(cur.id, waitSeconds)) {
        is GatewayCall.Ok -> {
          backoff.reset()
          if (r.value != cur || troubled) { cur = r.value; onEvent(Event.Update(cur)) }
          troubled = false
          if (cur.status.terminal) return End.Final(cur)
          //  نگهبان: سروری که قصدِ بسیار گذشته را هنوز زنده می‌گوید (نباید بشود)
          if (pastBy(cur, SERVER_STUCK_MS)) return End.TimedOut(cur)
          val took = now() - started
          if (took < MIN_ROUND_MS) sleep(MIN_ROUND_MS - took)
        }
        is GatewayCall.Refused -> {
          if (r.code == "payment_not_found") return End.Lost(r.message)
          if (pastBy(cur, OFFLINE_GIVE_UP_MS)) return End.TimedOut(cur)
          troubled = true
          onEvent(Event.Trouble(r.message))
          sleep(backoff.next())
        }
        is GatewayCall.Offline -> {
          if (pastBy(cur, OFFLINE_GIVE_UP_MS)) return End.TimedOut(cur)
          troubled = true
          onEvent(Event.Trouble(r.message))
          sleep(backoff.next())
        }
        GatewayCall.Disabled -> {
          if (pastBy(cur, OFFLINE_GIVE_UP_MS)) return End.TimedOut(cur)
          troubled = true
          onEvent(Event.Trouble(""))
          sleep(backoff.next())
        }
      }
    }
  }

  private fun pastBy(i: PaymentIntent, ms: Long): Boolean {
    val exp = i.expiresAt ?: return false
    return now() > exp + ms
  }

  /** پسروی: ۱، ۲، ۴، ۸ و سقفِ ۱۵ ثانیه */
  class Backoff(private val first: Long = 1_000, private val cap: Long = 15_000) {
    private var next = first
    fun next(): Long = next.also { next = minOf(cap, next * 2) }
    fun reset() { next = first }
  }

  companion object {
    /** بی‌نت تا این‌قدر پس از مهلت هم دوباره پرسیده می‌شود (ساعتِ دستگاه و سرور یکی نیستند) */
    const val OFFLINE_GIVE_UP_MS = 90_000L

    /** سرورِ پاسخ‌گو که قصدِ این‌قدر گذشته را هنوز زنده می‌گوید ⇐ رها (نباید رخ دهد) */
    const val SERVER_STUCK_MS = 30 * 60_000L

    /** کمینهٔ فاصلهٔ دو خواندن وقتی سرور بی انتظار جواب داد */
    const val MIN_ROUND_MS = 2_000L
  }
}
