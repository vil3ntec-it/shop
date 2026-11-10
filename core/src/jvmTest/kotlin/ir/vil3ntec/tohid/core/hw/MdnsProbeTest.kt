package ir.vil3ntec.tohid.core.hw

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 *  HW-12 — `MdnsProbe` با پاسخ‌دهندهٔ ساختگیِ mDNS روی loopback (سوکتِ UDP ِ واقعی، بی چاپگر): پرسشِ رسیده
 *  همان `Mdns.query` است و پاسخِ تک‌پخشِ بردارهای dnspython (`mdns-v1.json`) چاپگر می‌شود؛ شناسهٔ دیگر،
 *  فرستندهٔ بیرون از شبکهٔ دکان و بستهٔ بدشکل کنار می‌روند؛ بی پاسخ پس از مهلت هیچ.
 */
class MdnsProbeTest {

  private val v = run {
    val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
      .firstOrNull { File(it, "core/test-vectors/mdns-v1.json").isFile }
      ?: error("core/test-vectors/mdns-v1.json پیدا نشد")
    Json.parseToJsonElement(File(root, "core/test-vectors/mdns-v1.json").readText()).jsonObject
  }

  private fun bytes(h: String) = ByteArray(h.length / 2) { h.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

  /** بستهٔ نخستِ بردارِ `printers` با این نام */
  private fun packets(name: String): List<ByteArray> =
    v.getValue("printers").jsonArray.map { it.jsonObject }.first { it.getValue("name").jsonPrimitive.content == name }
      .getValue("packets").jsonArray.map { bytes(it.jsonPrimitive.content) }

  private fun malformed(name: String): ByteArray =
    bytes(v.getValue("malformed").jsonArray.map { it.jsonObject }.first { it.getValue("name").jsonPrimitive.content == name }
      .getValue("hex").jsonPrimitive.content)

  /** یک پرسش می‌گیرد و [answers] را به همان درگاه پس می‌فرستد؛ شناسهٔ پاسخ = `reid(شناسهٔ پرسش)` */
  private class Fake(private val answers: List<ByteArray>, private val reid: (Int) -> Int = { it }) : AutoCloseable {
    val socket = DatagramSocket(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
    @Volatile var query: ByteArray? = null
    private val worker = thread(isDaemon = true, name = "fake-mdns") {
      runCatching {
        val buf = ByteArray(1_500)
        val p = DatagramPacket(buf, buf.size)
        socket.receive(p)
        val q = buf.copyOf(p.length)
        query = q
        val id = reid(((q[0].toInt() and 0xFF) shl 8) or (q[1].toInt() and 0xFF))
        for (a in answers) {
          val reply = a.copyOf()
          reply[0] = (id shr 8).toByte()
          reply[1] = id.toByte()
          socket.send(DatagramPacket(reply, reply.size, p.socketAddress))
        }
      }
    }
    val port: Int get() = socket.localPort

    override fun close() {
      worker.join(3_000)
      socket.close()
    }
  }

  private fun ask(fake: Fake, timeoutMs: Int = 1_500, sourceOk: (String) -> Boolean = { it == "127.0.0.1" }) =
    MdnsProbe.ask("127.0.0.1", timeoutMs, group = "127.0.0.1", port = fake.port, sourceOk = sourceOk)

  @Test fun `پرسشِ رسیده همان پرسشِ PTR است و پاسخِ تک‌پخش چاپگر می‌شود`() {
    Fake(packets("epson-full")).use { fake ->
      val found = ask(fake)
      val q = assertNotNull(fake.query)
      val id = ((q[0].toInt() and 0xFF) shl 8) or (q[1].toInt() and 0xFF)
      assertTrue(id in 1..0xFFFF)
      assertEquals(Mdns.query(Mdns.PDL, id).toList(), q.toList())
      assertEquals(listOf("net:192.168.1.50:9100" to "EPSON TM-m30"), found.map { it.link to it.name })
    }
  }

  @Test fun `چند بسته از یک پاسخ‌دهنده با هم خوانده می‌شوند و بستهٔ بدشکل کنار می‌رود`() {
    Fake(listOf(malformed("pointer-loop")) + packets("A record in a later packet")).use { fake ->
      assertEquals(listOf("net:192.168.0.31:9100" to "Bar"), ask(fake).map { it.link to it.name })
    }
  }

  @Test fun `پاسخ با شناسهٔ دیگر کنار می‌رود`() {
    Fake(packets("epson-full"), reid = { (it % 0xFFFF) + 1 }).use { fake ->
      assertEquals(emptyList(), ask(fake, timeoutMs = 600))
    }
  }

  @Test fun `فرستندهٔ بیرون از شبکهٔ خصوصیِ دکان پذیرفته نیست`() {
    //  پیش‌فرضِ `sourceOk` همان `Subnet.isPrivate` است و 127.0.0.1 در RFC1918 نیست
    Fake(packets("epson-full")).use { fake ->
      assertEquals(emptyList(), MdnsProbe.ask("127.0.0.1", 600, group = "127.0.0.1", port = fake.port))
    }
  }

  @Test fun `بی پاسخ پس از مهلت هیچ و نشانیِ نادرست بی سوکت هیچ`() {
    Fake(emptyList()).use { fake ->
      val t0 = System.nanoTime()
      assertEquals(emptyList(), ask(fake, timeoutMs = 300))
      assertTrue((System.nanoTime() - t0) / 1_000_000L < 2_500)
    }
    assertEquals(emptyList(), MdnsProbe.ask("not-an-ip"))
    assertEquals(emptyList(), MdnsProbe.ask("192.168.1.5", group = "mdns.example"))
  }
}
