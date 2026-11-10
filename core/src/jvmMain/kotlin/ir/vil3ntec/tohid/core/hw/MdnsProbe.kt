package ir.vil3ntec.tohid.core.hw

import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import kotlin.random.Random

/**
 *  HW-12 — سوکتِ پرسشِ یک‌بارهٔ mDNS (منطق و ایمنی در `Mdns`)؛ همین روی اندروید و کامپیوتر (هدفِ jvm ِ core).
 *
 *  سوکت به نشانیِ **همان کارتِ شبکهٔ دکان** بسته می‌شود (و چندپخش از همان کارت می‌رود)، نه دادهٔ همراه و نه
 *  VPN؛ یک پرسش به ‎224.0.0.251:5353‎ از درگاهِ گذرا، و تا [timeoutMs] پاسخِ تک‌پخش (RFC 6762 §6.7). پاسخی پذیرفته
 *  است که از نشانیِ شبکهٔ دکان بیاید ([sourceOk])، پاسخ باشد و شناسه‌اش همان پرسش (یا صفر) باشد. هیچ بایتی به
 *  چاپگر فرستاده نمی‌شود. اجازهٔ «شبکهٔ محلی» ِ اندروید ۱۷ (HW-13) را صدازننده پیش‌تر می‌پرسد.
 *
 *  [group] و [port] فقط برای پاسخ‌دهندهٔ ساختگیِ آزمون روی loopback عوض می‌شوند (`MdnsProbeTest`).
 */
object MdnsProbe {

  fun ask(
    lanIp: String,
    timeoutMs: Int = Mdns.WAIT_MS,
    service: String = Mdns.PDL,
    group: String = Mdns.GROUP,
    port: Int = Mdns.PORT,
    sourceOk: (String) -> Boolean = Subnet::isPrivate,
  ): List<FoundPrinter> {
    val ip = lanIp.trim()
    if (LocalNetwork.ipv4(ip) == null || LocalNetwork.ipv4(group.trim()) == null) return emptyList()
    val local = InetAddress.getByName(ip)
    val id = Random.nextInt(1, 0x10000)
    val query = Mdns.query(service, id)
    val got = ArrayList<Mdns.Message>()
    MulticastSocket(InetSocketAddress(local, 0)).use { s ->
      runCatching { NetworkInterface.getByInetAddress(local)?.let { s.networkInterface = it } }
      runCatching { s.timeToLive = 255 }
      s.send(DatagramPacket(query, query.size, InetSocketAddress(InetAddress.getByName(group.trim()), port)))
      val end = System.nanoTime() + timeoutMs * 1_000_000L
      val buf = ByteArray(9_000)
      while (got.size < Mdns.MAX_PACKETS) {
        val leftMs = (end - System.nanoTime()) / 1_000_000L
        if (leftMs <= 0) break
        s.soTimeout = leftMs.toInt().coerceAtLeast(1)
        val packet = DatagramPacket(buf, buf.size)
        try {
          s.receive(packet)
        } catch (e: SocketTimeoutException) {
          break
        }
        if (!sourceOk(packet.address?.hostAddress.orEmpty())) continue
        val m = Mdns.parse(buf.copyOf(packet.length)) ?: continue
        if (m.response && (m.id == id || m.id == 0)) got += m
      }
    }
    return Mdns.printers(got, service)
  }
}
