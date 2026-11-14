package ir.vil3ntec.tohid.core.hub

import ir.vil3ntec.tohid.core.hw.Mdns
import ir.vil3ntec.tohid.core.hw.Subnet

/**
 *  SYN-09 — پیدا کردنِ هابِ کامپیوتر در شبکهٔ دکان با mDNS/DNS-SD (RFC 6762/6763)، بی کتابخانه: همان بایت‌خوانِ امنِ
 *  `core/hw/Mdns` (چاپگرها) و همان پرسشِ یک‌بارهٔ `MdnsProbe`.
 *
 *  - نوعِ خدمت `_tohid-hub._tcp.local`؛ PTR ⇐ نامِ نمونه ⇐ SRV (درگاه، میزبان) ⇐ A (IPv4 ِ خصوصی) و TXT ِ `v=1`.
 *  - ⛔ کشف فقط **نشانی** می‌دهد، نه اعتماد: اعتماد از جفت‌سازی است (`HubPair`؛ سنجاقِ QR یا اثباتِ کد). پس در TXT
 *    هیچ راز و هیچ چیزی که گوشی به آن تکیه کند نیست.
 *  - پاسخ به پرسشِ یک‌باره (درگاهِ گذرا) تک‌پخش با همان شناسه و TTL ِ کوتاه (§6.7)؛ به پرسشِ ۵۳۵۳ چندپخش.
 */
object HubMdns {
  const val SERVICE: String = "_tohid-hub._tcp.local"
  /** پاسخِ «legacy unicast» — RFC 6762 §6.7: TTL ≤ ۱۰ ثانیه */
  const val UNICAST_TTL: Long = 10
  const val TTL: Long = 120
  private const val TYPE_ANY = 255
  private const val CLASS_IN = 1
  private const val CACHE_FLUSH = 0x8000

  data class Found(val ip: String, val port: Int, val name: String)

  /** پرسشی دربارهٔ همین خدمت (PTR یا ANY)؟ */
  fun asksForHub(q: Mdns.Query): Boolean {
    val svc = SERVICE.split('.')
    return q.questions.any { (n, t) -> (t == Mdns.TYPE_PTR || t == TYPE_ANY) && Mdns.sameName(n, svc) }
  }

  /**
   *  پاسخِ کامل: پرسش (برای تک‌پخش)، PTR در پاسخ‌ها، SRV/TXT/A در افزوده‌ها.
   *  @param instance برچسبِ نمونه (ASCII، بی نقطه) — مثلاً «Tohid Hub 3f2a»
   *  @param host برچسبِ میزبان (بی `.local`)
   */
  fun answer(id: Int, instance: String, host: String, ip: String, port: Int, txt: List<String>, ttl: Long, echoQuestion: Boolean): ByteArray {
    val svc = SERVICE.split('.')
    val inst = listOf(label(instance)) + svc
    val target = listOf(label(host), "local")
    val ipBytes = ip.split('.').map { it.toInt().toByte() }.toByteArray()
    require(ipBytes.size == 4) { "IPv4 لازم است" }
    val out = Bytes()
    out.u16(id); out.u16(0x8400)
    out.u16(if (echoQuestion) 1 else 0); out.u16(1); out.u16(0); out.u16(3)
    if (echoQuestion) { out.name(svc); out.u16(Mdns.TYPE_PTR); out.u16(CLASS_IN) }
    out.record(svc, Mdns.TYPE_PTR, CLASS_IN, ttl) { it.name(inst) }
    out.record(inst, Mdns.TYPE_SRV, CLASS_IN or CACHE_FLUSH, ttl) { it.u16(0); it.u16(0); it.u16(port); it.name(target) }
    out.record(inst, Mdns.TYPE_TXT, CLASS_IN or CACHE_FLUSH, ttl) { b ->
      for (s in txt) { val e = s.encodeToByteArray().take(255).toByteArray(); b.u8(e.size); b.raw(e) }
      if (txt.isEmpty()) b.u8(0)
    }
    out.record(target, Mdns.TYPE_A, CLASS_IN or CACHE_FLUSH, ttl) { it.raw(ipBytes) }
    return out.bytes()
  }

  /** پاسخ‌ها ⇒ هاب‌ها: همان زنجیرهٔ `Mdns.printers` برای این خدمت؛ فقط IPv4 ِ پذیرفته ([accept]) */
  fun hubs(messages: List<Mdns.Message>, accept: (String) -> Boolean = Subnet::isPrivate): List<Found> {
    val svc = SERVICE.split('.')
    val all = messages.filter { it.response }.flatMap { it.records }
    val out = ArrayList<Found>()
    for (r in all) {
      if (r.type != Mdns.TYPE_PTR || r.ttl == 0L || !Mdns.sameName(r.name, svc)) continue
      if (r.ptr.size != svc.size + 1 || !Mdns.sameName(r.ptr.drop(1), svc)) continue
      val srv = all.firstOrNull { it.type == Mdns.TYPE_SRV && it.ttl > 0 && it.port > 0 && Mdns.sameName(it.name, r.ptr) } ?: continue
      val a = all.firstOrNull { it.type == Mdns.TYPE_A && it.ttl > 0 && Mdns.sameName(it.name, srv.target) && accept(it.ipv4) } ?: continue
      val f = Found(a.ipv4, srv.port, Mdns.clean(r.ptr[0]).ifBlank { a.ipv4 })
      if (out.none { it.ip == f.ip && it.port == f.port }) out += f
    }
    return out
  }

  /** برچسبِ امن: ASCII ِ چاپی، بی نقطه، ۱ تا ۶۳ بایت */
  fun label(s: String): String {
    val c = s.filter { it.code in 33..126 && it != '.' || it == ' ' }.trim().take(63)
    return c.ifEmpty { "tohid-hub" }
  }

  private class Bytes {
    private val b = ArrayList<Byte>(512)
    fun u8(v: Int) { b += (v and 0xFF).toByte() }
    fun u16(v: Int) { u8(v shr 8); u8(v) }
    fun u32(v: Long) { u16((v shr 16).toInt() and 0xFFFF); u16((v and 0xFFFF).toInt()) }
    fun raw(x: ByteArray) { for (y in x) b += y }
    fun name(labels: List<String>) {
      for (l in labels) { val e = l.encodeToByteArray(); require(e.size in 1..63); u8(e.size); raw(e) }
      u8(0)
    }
    fun record(name: List<String>, type: Int, cls: Int, ttl: Long, data: (Bytes) -> Unit) {
      name(name); u16(type); u16(cls); u32(ttl)
      val d = Bytes().also(data).bytes()
      u16(d.size); raw(d)
    }
    fun bytes(): ByteArray = b.toByteArray()
  }
}
