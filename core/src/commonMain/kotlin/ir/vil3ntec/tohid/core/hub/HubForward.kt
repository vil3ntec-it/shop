package ir.vil3ntec.tohid.core.hub

/** پاسخِ خامِ سرور؛ `status` صفر یعنی اصلاً نرسید */
data class HubReply(val status: Int, val body: String?)

/** سنجشِ توکنِ دستگاه نزدِ سرور */
enum class CloudCheck { VALID, INVALID, UNREACHABLE }

/**
 *  درگاهِ سرور برای هاب. ⛔ دسته با **توکنِ خودِ همان دستگاه** می‌رود، نه توکنِ هاب: نقش، مجوز و
 *  پیوندِ دستگاهِ سرور (`device_mismatch`) همان است که اگر دستگاه خودش می‌فرستاد.
 */
interface HubCloud {
  fun push(batch: HubBatch, token: String): HubReply
  fun verify(token: String, deviceId: String, app: String): CloudCheck
}

/** نگه‌دارندهٔ صف — `update` اتمی است و پیش از برگشتن روی دیسک می‌نشیند */
interface HubBox {
  fun read(): HubQueueState
  fun update(change: (HubQueueState) -> HubQueueState): HubQueueState
}

/**
 *  فرستادنِ صف به سرور، به ترتیب. تماس با سرور **بیرون** از قفلِ صف است؛ پس از پاسخ، دسته با `seq` ش
 *  از صفِ همان لحظه برداشته می‌شود (دسته‌ای که در این میان رسید گم نمی‌شود).
 *
 *  ⚠️ سقوط میانِ پاسخِ سرور و ذخیرهٔ صف ⇒ همان دسته دوباره می‌رود و سرور «duplicate» می‌دهد: ردیفِ دوم نه.
 */
class HubForwarder(private val box: HubBox, private val cloud: HubCloud, private val limits: HubLimits = HubLimits()) {

  data class Pass(
    val forwarded: Int = 0,
    val dropped: Int = 0,
    /** سرور در دسترس نبود یا شلوغ بود ⇒ دورِ بعد با عقب‌نشینی */
    val retry: Boolean = false,
    val upgrade: Boolean = false,
    /** دستگاه‌هایی که دسته دارند ولی توکنشان در حافظه نیست (پس از راه‌اندازیِ دوبارهٔ هاب) */
    val waiting: Set<String> = emptySet(),
    val authFailed: Set<String> = emptySet(),
  )

  /**
   *  @param tokenOf توکنِ حافظه‌ایِ دستگاه — هرگز روی دیسک نیست
   *  @param onAuthFailed سرور توکن را نپذیرفت: هاب از حافظه و از فهرستِ سنجیده‌ها برش می‌دارد
   */
  fun drain(tokenOf: (String) -> String?, onAuthFailed: (deviceId: String, token: String) -> Unit, max: Int = 100): Pass {
    val skip = HashSet<String>()
    val waiting = HashSet<String>()
    val authFailed = HashSet<String>()
    var forwarded = 0
    var dropped = 0
    for (i in 0 until max) {
      val b = HubQueue.next(box.read(), skip) ?: break
      val token = tokenOf(b.deviceId)
      if (token == null) {
        skip += b.deviceId
        waiting += b.deviceId
        continue
      }
      val reply = runCatching { cloud.push(b, token) }.getOrElse { HubReply(0, null) }
      when (HubQueue.outcomeOf(reply.status, reply.body)) {
        HubOutcome.DONE -> {
          val results = HubQueue.resultsOf(reply.body).orEmpty()
          box.update { HubQueue.settle(it, b, results, limits) }
          forwarded++
        }
        HubOutcome.DROPPED -> {
          box.update { HubQueue.drop(it, b) }
          dropped++
        }
        HubOutcome.AUTH -> {
          onAuthFailed(b.deviceId, token)
          skip += b.deviceId
          authFailed += b.deviceId
        }
        HubOutcome.RETRY -> return Pass(forwarded, dropped, retry = true, waiting = waiting, authFailed = authFailed)
        HubOutcome.UPGRADE -> return Pass(forwarded, dropped, upgrade = true, waiting = waiting, authFailed = authFailed)
      }
    }
    return Pass(forwarded, dropped, waiting = waiting, authFailed = authFailed)
  }

  companion object {
    /** ۵ ثانیه، دو برابر تا سقفِ ۵ دقیقه */
    fun backoffMs(attempt: Int): Long {
      var ms = 5_000L
      repeat(attempt.coerceIn(0, 10)) { ms *= 2 }
      return ms.coerceAtMost(300_000L)
    }
  }
}
