package ir.vil3ntec.tohid.core.sync

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** پاسخِ یک درخواست: [status] صفر = به سرور نرسید؛ [code] همان `error.code` ِ سرور */
data class V3InboxReply(val status: Int, val body: JsonObject? = null, val code: String = "")

/** لایهٔ HTTP ِ پلتفرم (با توکنِ همان کاربر؛ نشانی نسبت به `/api/v1`)؛ خطا را برمی‌گرداند، پرتاب نمی‌کند */
interface V3InboxTransport {
  suspend fun get(path: String): V3InboxReply
  suspend fun post(path: String, body: JsonObject): V3InboxReply
}

/** جای صفِ تصمیم‌ها روی دیسکِ پلتفرم (متنِ [V3InboxOutbox.encode]) */
interface V3InboxQueueStore {
  fun read(): String?
  fun write(text: String)
}

/**
 *  SYN-07 — صندوقِ تعارضِ v3: فهرست، صف و فرستادن، بی هیچ چیزِ پلتفرمی. ⛔ صدازننده هم‌زمانی را می‌بندد (یک [flush] در
 *  هر لحظه — قفلِ پلتفرم)؛ هر تصمیم **اول** روی دیسک می‌نشیند ([decide]) و بعد فرستاده می‌شود، پس بسته شدنِ برنامه یا
 *  قطعِ شبکه در میانه هیچ تصمیمی را گم نمی‌کند و تکرارش در سرور بی‌اثر است.
 */
class V3InboxClient(
  private val net: V3InboxTransport,
  private val store: V3InboxQueueStore,
  private val now: () -> Long,
) {
  /** نتیجهٔ فهرست: [page] null یعنی نرسید ([status]) */
  data class Listed(val page: V3Inbox.Page?, val status: Int, val code: String = "")

  /** نتیجهٔ یک تصمیمِ فرستاده: سرنوشت و (۲۰۰) ردیفِ تازهٔ سرور */
  data class Sent(val entry: V3InboxOutbox.Entry, val fate: V3InboxOutbox.Fate, val conflict: V3Inbox.Conflict?, val duplicate: Boolean, val code: String)

  suspend fun list(all: Boolean = false): Listed {
    val r = net.get("${V3Inbox.ROOT}?status=${if (all) "all" else V3Inbox.OPEN}&limit=200")
    return if (r.status in 200..299) Listed(V3Inbox.parsePage(r.body), r.status) else Listed(null, r.status, r.code)
  }

  fun queued(): List<V3InboxOutbox.Entry> = V3InboxOutbox.decode(store.read())

  /** تصمیم در صف (روی دیسک)؛ هنوز نرفته */
  fun decide(d: V3Inbox.Decision): V3InboxOutbox.Entry {
    val t = now()
    val e = V3InboxOutbox.Entry(d, t)
    store.write(V3InboxOutbox.encode(V3InboxOutbox.add(queued(), e, t)))
    return e
  }

  /**
   *  صف تا ته (حداکثر [max])، یکی‌یکی و به ترتیب. نخستین «نرسید» ([V3InboxOutbox.Fate.RETRY]) می‌ایستد: شبکه نیست یا
   *  سرور نمی‌پذیرد، پس بقیه هم نمی‌روند و صف دست‌نخورده برای دورِ بعد می‌ماند.
   */
  suspend fun flush(max: Int = 50): List<Sent> {
    val out = ArrayList<Sent>()
    val all = queued()
    val kept = V3InboxOutbox.prune(all, now())
    if (kept.size != all.size) store.write(V3InboxOutbox.encode(kept))
    repeat(max) {
      val e = queued().firstOrNull() ?: return out
      val r = net.post(V3InboxOutbox.path(e), V3InboxOutbox.body(e))
      val fate = V3InboxOutbox.fate(r.status, r.code)
      store.write(V3InboxOutbox.encode(V3InboxOutbox.settle(queued(), e, fate)))
      val dup = (r.body?.get("duplicate") as? JsonPrimitive)?.booleanOrNull == true
      out += Sent(e, fate, if (fate == V3InboxOutbox.Fate.DONE) V3Inbox.parse(r.body) else null, dup, r.code)
      if (fate == V3InboxOutbox.Fate.RETRY) return out
    }
    return out
  }
}
