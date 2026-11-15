package ir.vil3ntec.tohid.core.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 *  چند نشانیِ یک سرور برای همهٔ برنامه‌ها (SERVERS §۳): ترتیبِ جابه‌جایی، ردِ `serverId` ِ دیگر، به خاطر ماندنِ آخرین
 *  نشانیِ سالم، آموختنِ نشانی از خودِ سرور، و «تک‌نشانی/سنجاق‌نشده ⇒ دقیقاً همان یک بار» (برابر با پرچمِ خاموش).
 */
class ServerRouterTest {

  private val build = "https://api.vill3n.top"
  private val alt1 = "https://b.example.com"
  private val alt2 = "https://c.example.com"
  private val idA = "srv_0123456789abcdef0123"
  private val idB = "srv_fedcba9876543210fedc"

  private class MapStore : ServerRouter.Store {
    val map = LinkedHashMap<String, String>()
    var writes = 0
    override fun get(key: String): String? = map[key]
    override fun put(key: String, value: String?) {
      writes++
      if (value == null) map.remove(key) else map[key] = value
    }
  }

  private fun info(id: String?, publicUrls: List<String> = emptyList()) = ServerInfo(
    serverId = id, name = "سرور", version = "2.14.5", wireMin = 1, wireMax = 1,
    features = setOf("sync.v1"), publicUrl = "", legacy = id == null, publicUrls = publicUrls,
  )

  private class Net(val answers: Map<String, ServerInfo?>) {
    val asked = ArrayList<String>()
    val timeouts = ArrayList<Int>()
    fun ask(url: String, timeoutMs: Int): ServerInfo? {
      asked += url
      timeouts += timeoutMs
      return answers[url]
    }
  }

  private var clock = 1_000_000L

  private fun router(store: MapStore, net: Net) = ServerRouter(build, store, net::ask, { clock })

  private fun stored(store: MapStore, urls: List<String>, id: String?) {
    store.map[ServerRouter.K_PROFILE] = ServerProfiles(
      ServerProfiles.DEFAULT_ID,
      listOf(ServerProfile(id = ServerProfiles.DEFAULT_ID, name = "سرورِ اصلی", urls = urls, serverId = id)),
    ).encode()
  }

  private val offline = IllegalStateException("network")

  /* ------------------------------ همان رفتارِ پرچمِ خاموش ------------------------------ */

  @Test
  fun singleUrlRouterSendsOnceToBuildUrlAndRethrowsSameError() {
    val store = MapStore()
    val net = Net(emptyMap())
    val r = router(store, net)
    assertEquals(build, r.current())
    val calls = ArrayList<String>()
    val thrown = try {
      r.send({ true }) { base -> calls += base; throw offline }
      fail("باید همان خطا می‌آمد")
    } catch (e: IllegalStateException) { e }
    assertSame(offline, thrown)
    assertEquals(listOf(build), calls)
    assertTrue(net.asked.isEmpty(), "هیچ پرسشِ دیگری")
    assertEquals(0, store.writes, "مخزن دست نخورد")
    //  پاسخِ موفق ⇒ همان نتیجه، یک بار
    assertEquals("ok", r.send({ true }) { base -> calls += base; "ok" })
    assertEquals(listOf(build, build), calls)
  }

  @Test
  fun unpinnedProfileNeverFallsBackEvenWithAlternates() {
    val store = MapStore()
    stored(store, listOf(build, alt1), null)
    val net = Net(mapOf(alt1 to info(idA)))
    val calls = ArrayList<String>()
    assertIs<IllegalStateException>(runCatching { router(store, net).send({ true }) { b -> calls += b; throw offline } }.exceptionOrNull())
    assertEquals(listOf(build), calls)
    assertTrue(net.asked.isEmpty())
  }

  /* ------------------------------ جابه‌جایی ------------------------------ */

  @Test
  fun networkErrorFallsBackOnlyToUrlWithSamePinnedServerId() {
    val store = MapStore()
    stored(store, listOf(build, alt1, alt2), idA)
    val net = Net(mapOf(alt1 to info(idB), alt2 to info(idA)))
    val r = router(store, net)
    val calls = ArrayList<String>()
    val out = r.send({ true }) { b -> calls += b; if (b == build) throw offline else "از $b" }
    assertEquals("از $alt2", out)
    assertEquals(listOf(build, alt2), calls, "⛔ نشانیِ سرورِ دیگر (alt1) هرگز درخواست نگرفت")
    assertEquals(listOf(alt1, alt2), net.asked)
    assertEquals(listOf(ServerFailover.PUBLIC_TIMEOUT_MS, ServerFailover.PUBLIC_TIMEOUT_MS), net.timeouts)
    //  به خاطر می‌ماند — حتی برای روترِ تازه (برنامه دوباره باز شد)
    assertEquals(alt2, router(store, Net(emptyMap())).current())
    calls.clear()
    r.send({ true }) { b -> calls += b; "ok" }
    assertEquals(listOf(alt2), calls)
  }

  @Test
  fun lanAlternatesAreTriedBeforeInternetWithShortTimeout() {
    val lan = "https://192.168.1.10:3443"
    val store = MapStore()
    stored(store, listOf(build, alt1, lan), idA)
    val net = Net(mapOf(lan to null, alt1 to info(idA)))
    router(store, net).send({ true }) { b -> if (b == build) throw offline else b }
    assertEquals(listOf(lan, alt1), net.asked)
    assertEquals(listOf(ServerFailover.LAN_TIMEOUT_MS, ServerFailover.PUBLIC_TIMEOUT_MS), net.timeouts)
  }

  @Test
  fun httpErrorResponseIsNotUnreachableAndNeverFallsBack() {
    val store = MapStore()
    stored(store, listOf(build, alt1), idA)
    val net = Net(mapOf(alt1 to info(idA)))
    //  ۴۰۴ و ۵۰۳ پاسخ‌اند، نه استثنا
    assertEquals(404, router(store, net).send({ true }) { 404 })
    assertEquals(503, router(store, net).send({ true }) { 503 })
    assertTrue(net.asked.isEmpty())
    assertEquals(build, store.map[ServerRouter.K_CHOSEN])
  }

  @Test
  fun requestThatMayHaveArrivedIsNotResentButNextRequestUsesAlternate() {
    val store = MapStore()
    stored(store, listOf(build, alt1), idA)
    val net = Net(mapOf(alt1 to info(idA)))
    val r = router(store, net)
    val calls = ArrayList<String>()
    val e = runCatching { r.send({ false }) { b -> calls += b; throw offline } }.exceptionOrNull()
    assertSame(offline, e)
    assertEquals(listOf(build), calls, "کارِ دوم (مثلاً فرستادنِ کد) ساخته نشد")
    assertEquals(alt1, r.current())
  }

  @Test
  fun allAlternatesForeignOrDownKeepCurrentAndCoolDown() {
    val store = MapStore()
    stored(store, listOf(build, alt1, alt2), idA)
    val net = Net(mapOf(alt1 to info(idB), alt2 to null))
    val r = router(store, net)
    assertSame(offline, runCatching { r.send({ true }) { throw offline } }.exceptionOrNull())
    assertEquals(build, r.current(), "⛔ سرورِ دیگر برگزیده نشد")
    assertEquals(2, net.asked.size)
    //  بی اینترنت: تا پایانِ مهلت هر درخواست پرسشِ تازه نمی‌سازد
    runCatching { r.send({ true }) { throw offline } }
    assertEquals(2, net.asked.size)
    clock += ServerRouter.COOLDOWN_MS
    runCatching { r.send({ true }) { throw offline } }
    assertEquals(4, net.asked.size)
  }

  @Test
  fun resendOnAlternateThatAlsoFailsMovesToNextSameServerUrl() {
    val store = MapStore()
    stored(store, listOf(build, alt1, alt2), idA)
    val net = Net(mapOf(alt1 to info(idA), alt2 to info(idA)))
    val calls = ArrayList<String>()
    val out = router(store, net).send({ true }) { b -> calls += b; if (b == alt2) "ok" else throw offline }
    assertEquals("ok", out)
    assertEquals(listOf(build, alt1, alt2), calls)
    assertEquals(alt2, store.map[ServerRouter.K_CHOSEN])
  }

  /* ------------------------------ سنجشِ پس‌زمینه و آموختن ------------------------------ */

  @Test
  fun refreshPinsBuildServerAndLearnsOnlyAnnouncedUrlsOfTheSameServer() {
    val store = MapStore()
    val announced = listOf("https://b.example.com", "https://evil.example.com", "https://192.168.1.5", "https://c.example.com")
    val net = Net(mapOf(build to info(idA, announced), alt1 to info(idA), "https://evil.example.com" to info(idB), alt2 to null))
    val r = router(store, net)
    r.refresh()
    val p = r.profile()
    assertEquals(idA, p.serverId)
    assertEquals(listOf(build, alt1), p.urls, "فقط نشانیِ همان serverId؛ سرورِ دیگر و نرسیده نه")
    assertTrue("https://192.168.1.5" !in net.asked, "⛔ نشانیِ شبکهٔ محلی هرگز آموخته نمی‌شود")
    assertEquals(build, r.current())
  }

  @Test
  fun buildUrlAnsweringWithNewServerIdRepinsAndDropsLearnedUrls() {
    val store = MapStore()
    stored(store, listOf(build, alt1), idA)
    store.map[ServerRouter.K_CHOSEN] = alt1
    val net = Net(mapOf(build to info(idB), alt1 to info(idA)))
    val r = router(store, net)
    r.refresh()
    assertEquals(idB, r.profile().serverId)
    assertEquals(listOf(build), r.profile().urls, "⛔ نشانیِ آموختهٔ سرورِ پیشین دیگر «همان سرور» نیست")
    assertEquals(build, r.current())
  }

  @Test
  fun refreshReturnsToPreferredUrlWhenItRecoversAndRespectsIntervals() {
    val store = MapStore()
    stored(store, listOf(build, alt1), idA)
    store.map[ServerRouter.K_CHOSEN] = alt1
    val net = Net(mapOf(build to info(idA), alt1 to info(idA)))
    val r = router(store, net)
    r.refreshIfDue()
    assertEquals(build, r.current())
    val asked = net.asked.size
    //  روی نشانیِ برتر و سنجاق‌شده: تا LEARN_MS هیچ
    clock += ServerFailover.RECHECK_MS + 1
    r.refreshIfDue()
    assertEquals(asked, net.asked.size)
    clock += ServerLearn.LEARN_MS
    r.refreshIfDue()
    assertTrue(net.asked.size > asked)
  }

  @Test
  fun newBuildUrlInNewReleaseResetsPinSoNoFallback() {
    val store = MapStore()
    store.map[ServerRouter.K_PROFILE] = ServerProfiles(
      ServerProfiles.DEFAULT_ID,
      listOf(ServerProfile(id = ServerProfiles.DEFAULT_ID, name = "سرور", urls = listOf("https://old.example.com", alt1), serverId = idA)),
    ).encode()
    val r = router(store, Net(mapOf(alt1 to info(idA))))
    assertNull(r.profile().serverId)
    assertEquals(build, r.current())
    val calls = ArrayList<String>()
    runCatching { r.send({ true }) { b -> calls += b; throw offline } }
    assertEquals(listOf(build), calls)
  }

  /* ------------------------------ قاعده‌های core ------------------------------ */

  @Test
  fun learnCandidatesNeedPinnedSameServerAndPublicHttps() {
    val p = ServerProfile(id = "x", name = "x", urls = listOf(build), serverId = idA)
    val bad = listOf(
      "http://plain.example.com", "https://api.vill3n.top@evil.example", "https://10.0.0.5", "https://shop.local",
      "https://localhost", "https://x.example.com/?a", "ftp://x.example.com",
    )
    val i = info(idA, bad + listOf("https://ok.example.com", "https://ok.example.com/", build))
    assertEquals(listOf("https://ok.example.com"), ServerLearn.candidates(p, build, i))
    assertTrue(ServerLearn.candidates(p.copy(serverId = null), build, i).isEmpty(), "سنجاق‌نشده")
    assertTrue(ServerLearn.candidates(p, build, info(idB, listOf("https://ok.example.com"))).isEmpty(), "سرورِ دیگر")
    assertTrue(ServerLearn.candidates(p, "https://elsewhere.example.com", i).isEmpty(), "پاسخ از نشانیِ بیرون از پروفایل")
    val full = p.copy(urls = listOf(build, "https://a1.example.com", "https://a2.example.com", "https://a3.example.com"))
    assertTrue(ServerLearn.candidates(full, build, i).isEmpty(), "سقفِ MAX_URLS")
    assertNull(ServerLearn.adopt(p, "https://ok.example.com", info(idB)))
    assertNull(ServerLearn.adopt(p, "https://ok.example.com", null))
    assertEquals(listOf(build, "https://ok.example.com"), ServerLearn.adopt(p, "https://ok.example.com", info(idA))?.urls)
  }

  @Test
  fun serverInfoParsesAnnouncedUrlsDefensively() {
    val many = (1..12).joinToString(",") { "\"https://s$it.example.com\"" }
    val json = "{\"kind\":\"tohid-server\",\"serverId\":\"$idA\",\"wire\":{\"min\":1,\"max\":1}," +
      "\"publicUrls\":[\"https://a.example.com/\",\"https://u@evil.example\",42,null,\"https://a.example.com\",$many]}"
    val i = ServerInfo.parse(json)!!
    assertEquals("https://a.example.com", i.publicUrls.first())
    assertTrue(i.publicUrls.none { it.contains('@') })
    assertTrue(i.publicUrls.size <= ServerInfo.MAX_PUBLIC_URLS)
    //  سرورِ بی این کلید ⇒ فهرستِ خالی
    val plain = ServerInfo.parse("{\"kind\":\"tohid-server\",\"serverId\":\"$idA\",\"wire\":{\"min\":1,\"max\":1}}")!!
    assertTrue(plain.publicUrls.isEmpty())
  }

  @Test
  fun chooseTrustsDefaultPrimaryButOtherUrlsNeedSamePin() {
    val def = ServerProfile(id = ServerProfiles.DEFAULT_ID, name = "x", urls = listOf(build, alt1), serverId = idA)
    //  نشانیِ زمانِ ساخت هر سرورِ این خانواده را می‌پذیرد (سنجاقِ تازه کارِ صدازننده است)
    assertEquals(build, (ServerFailover.choose(def, null) { info(idB) } as ServerFailover.Pick.Chosen).url)
    //  نشانیِ دوم فقط با همان شناسه
    val down = mapOf(build to null, alt1 to info(idB))
    val none = ServerFailover.choose(def, null) { down[it] }
    assertIs<ServerFailover.Pick.None>(none)
    assertEquals(listOf(alt1), none.mismatched)
    val other = ServerProfile(id = "p1", name = "x", urls = listOf(alt1, alt2), serverId = idA)
    assertEquals(alt2, (ServerFailover.choose(other, null) { if (it == alt2) info(idA) else info(idB) } as ServerFailover.Pick.Chosen).url)
  }
}
