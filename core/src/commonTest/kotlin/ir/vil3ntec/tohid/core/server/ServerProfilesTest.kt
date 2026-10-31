package ir.vil3ntec.tohid.core.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** پرچمِ `app.multi_server` — پروفایلِ سرور، کیو‌آر، قاعدهٔ نشانی، سنجاقِ هویت، جابه‌جاییِ نشانی و جداییِ داده */
class ServerProfilesTest {

  private val build = "https://api.vill3n.top"
  private val idA = "srv_0123456789abcdef0123"
  private val idB = "srv_fedcba9876543210fedc"

  private fun info(id: String?, legacy: Boolean = id == null) = ServerInfo(
    serverId = id, name = "سرور", version = "2.14.5", wireMin = 1, wireMax = 1,
    features = setOf("sync.v1"), publicUrl = "", legacy = legacy,
  )

  private fun ok(raw: String, lan: Boolean = false, dev: Boolean = false): ServerAddress.Parts {
    val v = ServerAddress.check(raw, lan, dev)
    assertIs<ServerAddress.Verdict.Ok>(v, "پذیرفته نشد: $raw ⇒ $v")
    return v.parts
  }

  private fun refused(raw: String, lan: Boolean = false, dev: Boolean = false): ServerAddress.Rejection {
    val v = ServerAddress.check(raw, lan, dev)
    assertIs<ServerAddress.Verdict.Rejected>(v, "باید رد می‌شد: $raw")
    return v.reason
  }

  /* ------------------------------ نشانی ------------------------------ */

  @Test
  fun publicHttpsIsAcceptedAndNormalized() {
    val p = ok("  API.Example.com/api/v1/ ")
    assertEquals("https://api.example.com", p.url)
    assertEquals(ServerAddress.HostKind.PUBLIC_NAME, p.kind)
    assertEquals("https://shop.example.com:8443/base", ok("https://shop.example.com:8443/base/api").url)
  }

  @Test
  fun plainHttpIsRefusedOutsideDevAndLoopback() {
    assertEquals(ServerAddress.Rejection.INSECURE, refused("http://api.example.com"))
    //  شبکهٔ محلی هم: http رمز ندارد و توکن را به همان وای‌فای می‌دهد
    assertEquals(ServerAddress.Rejection.INSECURE, refused("http://192.168.1.10:3000", lan = true))
    //  همین کامپیوتر (از شبکه بیرون نمی‌رود) با کلیدِ شبکهٔ محلی
    assertEquals("http://localhost:3000", ok("http://localhost:3000", lan = true).url)
    assertEquals(ServerAddress.HostKind.LOOPBACK, ok("http://127.0.0.1:3000", lan = true).kind)
    //  نسخهٔ آزمایشی
    ok("http://api.example.com", dev = true)
  }

  @Test
  fun lanAddressesNeedTheLanToggle() {
    val lan = listOf(
      "https://10.0.0.5", "https://172.16.0.1", "https://172.31.255.254", "https://192.168.1.10:3443",
      "https://169.254.3.4", "https://shop.local", "https://server.lan", "https://box.home.arpa",
      "https://shopserver", "https://[fd12:3456::1]:3443", "https://[fe80::1]",
    )
    for (u in lan) {
      assertEquals(ServerAddress.Rejection.LAN_OFF, refused(u), u)
      assertEquals(ServerAddress.HostKind.LAN, ok(u, lan = true).kind, u)
    }
    //  بیرون از بازه‌های خصوصی، شبکهٔ محلی نیست
    for (u in listOf("https://172.32.0.1", "https://172.15.0.1", "https://8.8.8.8", "https://[2001:db8::1]")) {
      assertEquals(ServerAddress.HostKind.PUBLIC_IP, ok(u).kind, u)
    }
    assertEquals(ServerAddress.Rejection.LAN_OFF, refused("https://localhost:3443"))
  }

  @Test
  fun deceptiveOrMalformedAddressesAreRefused() {
    assertEquals(ServerAddress.Rejection.USERINFO, refused("https://api.vill3n.top@evil.example"))
    assertEquals(ServerAddress.Rejection.USERINFO, refused("https://user:pw@api.example.com"))
    assertEquals(ServerAddress.Rejection.QUERY, refused("https://api.example.com/?next=evil"))
    assertEquals(ServerAddress.Rejection.QUERY, refused("https://api.example.com#x"))
    assertEquals(ServerAddress.Rejection.BAD_SCHEME, refused("ftp://api.example.com"))
    assertEquals(ServerAddress.Rejection.MISSING, refused("   "))
    for (bad in listOf("https://", "https://exa mple.com", "https://a..b", "https://-x.com", "https://x.com:99999",
      "https://x.com:abc", "https://[zz::1]", "https://x.com/../admin", "https://x_y.com")) {
      assertEquals(ServerAddress.Rejection.MALFORMED, refused(bad), bad)
    }
  }

  /* ------------------------------ پاسخِ سرور و سازگاری ------------------------------ */

  @Test
  fun serverInfoParsesAndRejectsForeignAnswers() {
    val body = """{"ok":true,"kind":"tohid-server","serverId":"$idA","name":"دکان‮مرکزی","version":"2.15.0",
      "wire":{"min":1,"max":2},"api":["v1"],"features":["sync.v1","sync.v3",""],"publicUrl":"https://shop.example.com/"}"""
    val i = assertNotNull(ServerInfo.parse(body))
    assertEquals(idA, i.serverId)
    assertEquals("دکانمرکزی", i.name, "نویسهٔ جهتِ متن پاک می‌شود")
    assertEquals(setOf("sync.v1", "sync.v3"), i.features)
    assertEquals("https://shop.example.com", i.publicUrl)
    assertFalse(i.legacy)
    assertTrue(i.supports("sync.v3"))
    //  پاسخِ سرورِ دیگر، شناسهٔ نادرست، یا بی بازهٔ قرارداد ⇒ هیچ
    assertNull(ServerInfo.parse(body.replace("tohid-server", "other")))
    assertNull(ServerInfo.parse(body.replace(idA, "srv_short")))
    assertNull(ServerInfo.parse(body.replace("\"wire\":{\"min\":1,\"max\":2},", "")))
    assertNull(ServerInfo.parse(body.replace("\"min\":1", "\"min\":3")))
    assertNull(ServerInfo.parse("<html>"))
    assertNull(ServerInfo.parse(null))
  }

  @Test
  fun legacyServerFallsBackToHealth() {
    val health = """{"ok":true,"service":"vill3n-auth","server":"online","database":"connected","version":"2.11.0"}"""
    val i = assertNotNull(ServerInfo.fromHealth(health))
    assertTrue(i.legacy)
    assertNull(i.serverId)
    assertEquals(ServerInfo.LEGACY_FEATURES, i.features)
    assertEquals(ServerCompat.Result.OK, ServerCompat.check(i))
    //  هر سرورِ دیگری که «ok» می‌گوید سرورِ این خانواده نیست
    assertNull(ServerInfo.fromHealth("""{"ok":true,"server":"online"}"""))
    assertNull(ServerInfo.fromHealth("""{"service":"vill3n-auth","server":"down"}"""))
  }

  @Test
  fun protocolTooOldOrTooNewIsRefusedWithPersianMessage() {
    assertEquals(ServerCompat.Result.OK, ServerCompat.check(info(idA)))
    val newer = info(idA).copy(wireMin = 2, wireMax = 3)
    assertEquals(ServerCompat.Result.APP_TOO_OLD, ServerCompat.check(newer))
    assertTrue(ServerCompat.check(newer).message!!.contains("برنامه را به‌روز"))
    val older = info(idA).copy(wireMin = 1, wireMax = 1)
    assertEquals(ServerCompat.Result.SERVER_TOO_OLD, ServerCompat.check(older, appWire = 2))
    assertTrue(ServerCompat.Result.SERVER_TOO_OLD.message!!.contains("سرور را به‌روز"))
  }

  /* ------------------------------ سنجاقِ هویت ------------------------------ */

  @Test
  fun identityPinBlocksAnotherServerBehindTheSameUrl() {
    assertEquals(ServerIdentity.Pin.MATCH, ServerIdentity.check(idA, idA))
    assertEquals(ServerIdentity.Pin.MISMATCH, ServerIdentity.check(idA, idB))
    //  سرورِ سنجاق‌شده‌ای که ناگهان شناسه نمی‌دهد (بازگشت به «قدیمی») هم بسته است
    assertEquals(ServerIdentity.Pin.MISMATCH, ServerIdentity.check(idA, null))
    assertEquals(ServerIdentity.Pin.FIRST_USE, ServerIdentity.check(null, idA))
    assertEquals(ServerIdentity.Pin.LEGACY_UNPINNED, ServerIdentity.check(null, null))
    val fp = ServerIdentity.fingerprint(idA)
    assertTrue(Regex("^[0-9A-F]{4}(-[0-9A-F]{4}){3}$").matches(fp), fp)
    assertEquals(fp, ServerIdentity.fingerprint(idA))
    assertNotEquals(fp, ServerIdentity.fingerprint(idB))
  }

  /* ------------------------------ کیو‌آر ------------------------------ */

  @Test
  fun qrPayloadRoundTripsAndIsValidated() {
    val pin = "AB:" + "cd".repeat(31)
    val text = ServerQr.encode(ServerQr.Payload(
      urls = listOf("https://shop.example.com/", "https://192.168.1.10:3443"),
      serverId = idA, name = "دکانِ \"مرکزی\"",
      certPins = mapOf("https://192.168.1.10:3443" to pin, "https://not-listed.example" to pin),
    ))
    assertTrue(text.startsWith(ServerQr.PREFIX))
    val read = assertIs<ServerQr.Read.Ok>(ServerQr.decode(text)).payload
    assertEquals(listOf("https://shop.example.com", "https://192.168.1.10:3443"), read.urls)
    assertEquals(idA, read.serverId)
    assertEquals("دکانِ \"مرکزی\"", read.name)
    assertEquals(mapOf("https://192.168.1.10:3443" to "ab" + "cd".repeat(31)), read.certPins)

    assertIs<ServerQr.Read.NotServerQr>(ServerQr.decode("https://shop.example.com"))
    assertIs<ServerQr.Read.Bad>(ServerQr.decode(ServerQr.PREFIX + "{"))
    assertIs<ServerQr.Read.Bad>(ServerQr.decode(ServerQr.PREFIX + """{"v":1,"url":"https://a.example"}"""))
    assertIs<ServerQr.Read.Bad>(ServerQr.decode(ServerQr.PREFIX + """{"v":1,"url":"https://a@b.example","serverId":"$idA"}"""))
    assertIs<ServerQr.Read.Bad>(ServerQr.decode(ServerQr.PREFIX + """{"v":1,"url":"https://a.example","serverId":"$idA","certs":{"https://a.example":"zz"}}"""))
    val future = assertIs<ServerQr.Read.Bad>(ServerQr.decode(ServerQr.PREFIX + """{"v":2,"url":"https://a.example","serverId":"$idA"}"""))
    assertTrue(future.message.contains("به‌روز"))
    assertIs<ServerQr.Read.Bad>(ServerQr.decode(ServerQr.PREFIX + "[" + "1,".repeat(2000) + "1]"))
    //  تودرتوی بی‌پایان همان‌جا رد می‌شود
    assertIs<ServerQr.Read.Bad>(ServerQr.decode(ServerQr.PREFIX + "[".repeat(50) + "]".repeat(50)))
  }

  /* ------------------------------ فهرستِ پروفایل‌ها ------------------------------ */

  @Test
  fun profilesKeepDefaultAndRefuseDuplicates() {
    var ps = ServerProfiles.initial(build)
    assertEquals(ServerProfiles.DEFAULT_ID, ps.active.id)
    assertEquals(build, ps.default.primary)
    ps = assertIs<ServerProfiles.Change.Ok>(ps.update(ps.default.copy(serverId = idA))).profiles

    val same = ServerProfile(id = "p1", name = "خانه", urls = listOf("https://home.example"), serverId = idA)
    assertEquals(ServerProfiles.Problem.SAME_AS_DEFAULT, assertIs<ServerProfiles.Change.Refused>(ps.add(same)).problem)
    val b = same.copy(serverId = idB)
    ps = assertIs<ServerProfiles.Change.Ok>(ps.add(b)).profiles
    assertEquals(ServerProfiles.Problem.SAME_SERVER, assertIs<ServerProfiles.Change.Refused>(ps.add(b.copy(id = "p2"))).problem)

    assertEquals(ServerProfiles.Problem.DEFAULT_FIXED, assertIs<ServerProfiles.Change.Refused>(ps.remove("default")).problem)
    ps = assertIs<ServerProfiles.Change.Ok>(ps.activate("p1")).profiles
    assertEquals(ServerProfiles.Problem.ACTIVE, assertIs<ServerProfiles.Change.Refused>(ps.remove("p1")).problem)
    assertEquals(ServerProfiles.Problem.PRIMARY_FIXED,
      assertIs<ServerProfiles.Change.Refused>(ps.update(ps.default.copy(urls = listOf("https://evil.example")))).problem)

    ps = assertIs<ServerProfiles.Change.Ok>(ps.addAlternate("default", "https://192.168.1.10:3443", "ef".repeat(32))).profiles
    assertEquals(listOf(build, "https://192.168.1.10:3443"), ps.default.urls)
    assertEquals("ef".repeat(32), ps.default.certPinOf("https://192.168.1.10:3443/"))
  }

  @Test
  fun profilesSurviveEncodeAndDecodeAndBadDataFallsBackToDefault() {
    var ps = ServerProfiles.initial(build)
    val p = ServerProfile(
      id = "p1", name = "سرورِ دکان", urls = listOf("https://192.168.1.10:3443", "https://shop.example.com"),
      serverId = idB, certPins = mapOf("https://192.168.1.10:3443" to "01".repeat(32)), lan = true,
      lastInfo = info(idB), checkedAt = 1_700_000_000_000, addedAt = 1_690_000_000_000,
    )
    ps = assertIs<ServerProfiles.Change.Ok>(ps.add(p)).profiles
    ps = assertIs<ServerProfiles.Change.Ok>(ps.activate("p1")).profiles
    val back = ServerProfiles.decode(ps.encode(), build)
    assertEquals(ps, back)

    //  دادهٔ خراب هرگز سرورِ ناشناسی نمی‌سازد
    assertEquals(ServerProfiles.initial(build), ServerProfiles.decode("{not json", build))
    assertEquals(ServerProfiles.initial(build), ServerProfiles.decode(null, build))
    //  پروفایلِ فعالِ ناموجود ⇒ پیش‌فرض
    assertEquals("default", ServerProfiles.decode(ps.encode().replace("\"active\":\"p1\"", "\"active\":\"gone\""), build).activeId)
  }

  @Test
  fun newBuildUrlReplacesDefaultPrimaryAndDropsItsPin() {
    var ps = ServerProfiles.initial(build)
    ps = assertIs<ServerProfiles.Change.Ok>(ps.update(ps.default.copy(serverId = idA))).profiles
    val moved = ServerProfiles.decode(ps.encode(), "https://api.new-domain.example")
    assertEquals("https://api.new-domain.example", moved.default.primary)
    assertNull(moved.default.serverId)
  }

  /* ------------------------------ جابه‌جاییِ نشانی ------------------------------ */

  @Test
  fun failoverPrefersLanThenInternetAndNeverCrossesServers() {
    val urls = listOf("https://shop.example.com", "https://192.168.1.10:3443", "https://backup.example.com")
    assertEquals(listOf("https://192.168.1.10:3443", "https://shop.example.com", "https://backup.example.com"),
      ServerFailover.order(urls))
    //  آخرین نشانیِ سالم در دستهٔ خودش جلو می‌افتد، ولی شبکهٔ محلی همچنان اول
    assertEquals(listOf("https://192.168.1.10:3443", "https://backup.example.com", "https://shop.example.com"),
      ServerFailover.order(urls, lastGood = "https://backup.example.com/"))
    assertEquals(ServerFailover.LAN_TIMEOUT_MS, ServerFailover.timeoutOf("https://192.168.1.10:3443"))
    assertEquals(ServerFailover.PUBLIC_TIMEOUT_MS, ServerFailover.timeoutOf("https://shop.example.com"))

    val p = ServerProfile(id = "p1", name = "x", urls = urls, serverId = idA, lan = true)
    val pick = ServerFailover.pick(p, listOf(
      ServerFailover.Probe("https://192.168.1.10:3443", info(idB)),   // سرورِ دیگری در شبکهٔ دکان
      ServerFailover.Probe("https://shop.example.com", null),         // نرسید
      ServerFailover.Probe("https://backup.example.com", info(idA)),
    ))
    val chosen = assertIs<ServerFailover.Pick.Chosen>(pick)
    assertEquals("https://backup.example.com", chosen.url)

    val none = assertIs<ServerFailover.Pick.None>(ServerFailover.pick(p, listOf(
      ServerFailover.Probe("https://192.168.1.10:3443", info(idB)),
      ServerFailover.Probe("https://shop.example.com", info(null)),   // سنجاق‌شده ولی بی شناسه
    )))
    assertEquals(listOf("https://192.168.1.10:3443", "https://shop.example.com"), none.mismatched)
  }

  @Test
  fun unpinnedProfileUsesOnlyItsPrimary() {
    val p = ServerProfile(id = "p1", name = "x", urls = listOf("https://a.example", "https://b.example"))
    assertEquals(ServerFailover.Judgement.ACCEPT, ServerFailover.judge(p, ServerFailover.Probe("https://a.example", info(null))))
    assertEquals(ServerFailover.Judgement.UNVERIFIABLE, ServerFailover.judge(p, ServerFailover.Probe("https://b.example", info(null))))
    assertEquals(ServerFailover.Judgement.UNVERIFIABLE, ServerFailover.judge(p, ServerFailover.Probe("https://b.example", info(idA))))
    assertEquals(ServerFailover.Judgement.UNREACHABLE, ServerFailover.judge(p, ServerFailover.Probe("https://a.example", null)))
  }

  /* ------------------------------ جداییِ داده ------------------------------ */

  @Test
  fun dataPartitionKeepsDefaultKeysAndSeparatesServers() {
    val def = ServerProfiles.initial(build).default
    //  ⛔ نامِ بایگانیِ امروز دست نمی‌خورد
    assertEquals("usr_1@shp_1", DataPartition.ledgerKey(def, "usr_1", "shp_1"))
    assertEquals("usr_1", DataPartition.ledgerKey(def, "usr_1", ""))

    val a = ServerProfile(id = "p1", name = "a", urls = listOf("https://a.example"), serverId = idA)
    val b = ServerProfile(id = "p2", name = "b", urls = listOf("https://b.example"), serverId = idB)
    val legacy = ServerProfile(id = "p3", name = "c", urls = listOf("https://c.example"))
    val user = "usr_0123456789abcdefghij"
    val keys = listOf(
      DataPartition.ledgerKey(def, user, "shp_0123456789abcdefghij"),
      DataPartition.ledgerKey(a, user, "shp_0123456789abcdefghij"),
      DataPartition.ledgerKey(a, user, "shp_0123456789abcdefghik"),   // دکانِ دیگر روی همان سرور
      DataPartition.ledgerKey(a, user + "x", "shp_0123456789abcdefghij"),
      DataPartition.ledgerKey(b, user, "shp_0123456789abcdefghij"),
      DataPartition.ledgerKey(legacy, user, "shp_0123456789abcdefghij"),
      DataPartition.deskKey(def), DataPartition.deskKey(a), DataPartition.deskKey(b),
    )
    assertEquals(keys.size, keys.toSet().size, "هر سرور و هر دکان بایگانیِ خودش")
    for (k in keys.drop(1)) {
      assertTrue(k.length <= 48 && k.all { it.isLetterOrDigit() }, "نامِ پرونده بی برش: $k")
    }
    //  همان سرور با پروفایلِ تازه (برداشتن و دوباره افزودن) همان بایگانی را می‌یابد
    assertEquals(DataPartition.ledgerKey(a, user, "s"), DataPartition.ledgerKey(a.copy(id = "p9", name = "z"), user, "s"))
    assertEquals("", DataPartition.scope(def))
  }

  /* ------------------------------ JSON ------------------------------ */

  @Test
  fun miniJsonRoundTripsAndRejectsGarbage() {
    val v = mapOf("a" to listOf(1L, 2.5, true, null, "x\"y\\z\n\u0001"), "b" to mapOf("c" to "دکان"))
    assertEquals(v, MiniJson.parse(MiniJson.encode(v)))
    assertEquals("😀", MiniJson.parse("\"\\ud83d\\ude00\""))
    for (bad in listOf("", "{", "[1,]", "{\"a\":}", "tru", "01x", "\"a", "{} x", "\"\u0001\"", "-", "1e")) {
      assertTrue(runCatching { MiniJson.parse(bad) }.isFailure, bad)
    }
    assertNull(MiniJson.obj("[1]"))
  }
}
