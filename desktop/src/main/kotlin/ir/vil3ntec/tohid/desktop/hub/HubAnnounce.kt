package ir.vil3ntec.tohid.desktop.hub

import ir.vil3ntec.tohid.core.hub.HubLimiter
import ir.vil3ntec.tohid.core.hub.HubMdns
import ir.vil3ntec.tohid.core.hw.Mdns
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketException

/**
 *  SYN-09 — پاسخ‌دهندهٔ mDNS/DNS-SD ِ هاب (`_tohid-hub._tcp.local`)، بی کتابخانه: چندپخشِ سادهٔ JDK و بایت‌سازِ
 *  `core/hub/HubMdns`. فقط به پرسشِ همین خدمت پاسخ می‌دهد؛ پرسشِ یک‌باره (درگاهِ گذرا، همان `MdnsProbe` ِ گوشی)
 *  تک‌پخش با همان شناسه و TTL ِ ۱۰ ثانیه، پرسشِ ۵۳۵۳ چندپخش.
 *
 *  ⛔ فقط شبکهٔ دکان: بستهٔ نشانیِ غیرخصوصی دور ریخته می‌شود؛ سقفِ پاسخ برای هر IP (۳۰ در دقیقه) تا هاب ابزارِ
 *     بازتابِ بسته نشود. کشف فقط نشانی می‌دهد؛ اعتماد از جفت‌سازی است — در TXT هیچ رازی نیست.
 *
 *  [bindPort]/[group] فقط برای آزمون روی loopback عوض می‌شوند (`group = null` ⇒ بی عضویتِ گروه).
 */
internal class HubAnnounce(
  private val instance: String,
  private val host: String,
  private val port: Int,
  private val ipOf: () -> String?,
  private val bindPort: Int = Mdns.PORT,
  private val group: String? = Mdns.GROUP,
  private val bindAddress: InetAddress? = null,
  private val sourceOk: (InetAddress) -> Boolean = HubServer.Companion::lanPeer,
) {
  @Volatile private var socket: MulticastSocket? = null
  private val limiter = HubLimiter(perMinute = 30, burst = 10)
  @Volatile var answered = 0
    private set

  fun start() {
    if (socket != null) return
    val s = MulticastSocket(null as java.net.SocketAddress?)
    s.reuseAddress = true
    s.bind(InetSocketAddress(bindAddress, bindPort))
    group?.let { g ->
      val addr = InetSocketAddress(InetAddress.getByName(g), bindPort)
      for (ni in lanInterfaces()) runCatching { s.joinGroup(addr, ni) }
    }
    runCatching { s.timeToLive = 255 }
    socket = s
    Thread({ loop(s) }, "tohid-hub-mdns").apply { isDaemon = true }.start()
  }

  fun stop() {
    socket?.close()
    socket = null
  }

  private fun loop(s: MulticastSocket) {
    val buf = ByteArray(9_000)
    while (!s.isClosed) {
      val p = DatagramPacket(buf, buf.size)
      try {
        s.receive(p)
      } catch (_: SocketException) {
        break
      } catch (_: Exception) {
        continue
      }
      runCatching { answer(s, p, buf.copyOf(p.length)) }
    }
  }

  private fun answer(s: MulticastSocket, p: DatagramPacket, bytes: ByteArray) {
    val from = p.address ?: return
    if (!sourceOk(from)) return
    val q = Mdns.questions(bytes) ?: return
    if (!HubMdns.asksForHub(q)) return
    if (!synchronized(limiter) { limiter.allow(from.hostAddress, System.currentTimeMillis()) }) return
    val ip = ipOf() ?: return
    val legacy = p.port != Mdns.PORT
    val txt = listOf("v=1", "port=$port")
    val reply = HubMdns.answer(
      id = if (legacy) q.id else 0, instance = instance, host = host, ip = ip, port = port, txt = txt,
      ttl = if (legacy) HubMdns.UNICAST_TTL else HubMdns.TTL, echoQuestion = legacy,
    )
    val to = if (legacy || group == null) InetSocketAddress(from, p.port) else InetSocketAddress(InetAddress.getByName(group), bindPort)
    s.send(DatagramPacket(reply, reply.size, to))
    answered++
  }

  companion object {
    fun lanInterfaces(): List<NetworkInterface> = runCatching {
      NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback && !it.isVirtual && !it.isPointToPoint && it.supportsMulticast() }
    }.getOrDefault(emptyList())

    /** نخستین IPv4 ِ خصوصیِ شبکهٔ دکانِ همین کامپیوتر */
    fun lanIpv4(): String? = lanInterfaces().flatMap { ni -> ni.inetAddresses.toList() }
      .filterIsInstance<java.net.Inet4Address>().firstOrNull { it.isSiteLocalAddress }?.hostAddress
  }
}
