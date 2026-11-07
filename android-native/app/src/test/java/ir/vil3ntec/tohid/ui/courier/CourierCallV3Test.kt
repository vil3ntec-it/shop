package ir.vil3ntec.tohid.ui.courier

import ir.vil3ntec.tohid.core.courier.CourierFlow
import ir.vil3ntec.tohid.core.courier.MissionCard
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrCourierApp
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.ui.v3.nav.SourceGraph
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  DLV-02 در پیکِ نسخهٔ ۳: خطِ «ناحیه · دروازهٔ رنگ» (`MissionView.landmarkFirst`) و «تماس با مشتری»
 *  (`CourierRepository.callNumber` ⇐ `openDialer`). هر دو فقط در `CourierHomeLegacy.kt` بودند؛ دستهٔ ۱۳ (#855) آن را
 *  بی جانشین پاک کرد و سه کمک‌کار بی صدازننده ماندند — CI سبز بود چون هیچ آزمونی صدازننده را نمی‌سنجید. این آزمون
 *  همان را می‌سنجد: از ریشهٔ برنامه (`AppRoot`) تا شماره‌گیر راه هست، کارت و جزئیات هر دو را می‌کشند، و دروازه
 *  همان دروازهٔ پیشین است (پرچمِ دکان + `CourierFlow.canCall`)، نه بازتر.
 */
class CourierCallV3Test {

  @After fun reset() { I18n.source = { Lang.FA } }

  private val repo: File = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "android-native").isDirectory }
  private val courier = File(repo, "android-native/app/src/main/java/ir/vil3ntec/tohid/ui/courier")

  /** بدنهٔ یک تابعِ سطحِ بالا (تا `}` ِ ستونِ اول) */
  private fun body(file: String, signature: String): String {
    val text = File(courier, file).readText()
    val from = text.indexOf(signature)
    assertTrue("$signature در $file نیست", from >= 0)
    val to = text.indexOf("\n}\n", from)
    return text.substring(from, if (to < 0) text.length else to)
  }

  /* ------------------------------- دروازه ------------------------------- */

  @Test fun `دکمه فقط با پرچمِ دکان و فقط پس از قبول تا پایان — همان دروازهٔ پیشین`() {
    val on = CourierExtras(callEnabled = true)
    for (s in CourierFlow.STATUSES) {
      val c = MissionCard("a$s", 1, s)
      assertFalse("پرچمِ خاموش ⇒ هرگز ($s)", CourierExtras().canCall(c))
      assertEquals("وضعیتِ $s", CourierFlow.canCall(s), on.canCall(c))
    }
    assertFalse("کارِ تازه (پیش از قبول) شماره نمی‌گیرد", on.canCall(MissionCard("n", 1, "requested")))
    assertTrue(on.canCall(MissionCard("m", 1, "moving")))
    //  پرچم از همان پاسخِ `/courier/me` ِ نگه‌داشته می‌آید که رابطِ پیشین می‌خواند
    assertTrue(body("CourierChecksV3.kt", "internal data class CourierExtras(").contains("store.callEnabled)"))
  }

  @Test fun `کارت و جزئیات دکمه را فقط از همان دروازه می‌گیرند`() {
    val home = File(courier, "CourierHome.kt").readText()
    assertTrue(home.contains("val callFor: (MissionCard) -> (() -> Unit)? = { c -> if (extras.canCall(c)) ({ onCallCustomer(c) }) else null }"))
    assertEquals("کارتِ «مسیر» و جزئیات", 2, Regex("""onCall = callFor\(c\)""").findAll(home).count())
    assertEquals("هیچ راهِ دیگری به تماس نیست", 1, Regex("""onCallCustomer\(c\)""").findAll(home).count())
  }

  /* ------------------------------- صدازننده‌ها ------------------------------- */

  @Test fun `کارتِ مسیر و جزئیاتِ مأموریت خطِ ناحیه و دروازه و دکمهٔ تماس را می‌کشند`() {
    val card = body("CourierMissionCard.kt", "internal fun MissionCardView(")
    assertTrue("کارت: landmarkFirst", card.contains("landmarkLines(c)"))
    assertTrue("کارت: دکمهٔ تماس", card.contains("CourierSecondary(StrCourierApp.callCustomer, onCall,"))
    val detail = body("CourierDetail.kt", "private fun RouteCard(")
    assertTrue("جزئیات: landmarkFirst", detail.contains("landmarkLines(c)"))
    assertTrue("جزئیات: دکمهٔ تماس", detail.contains("CourierSecondary(StrCourierApp.callCustomer, onCall,"))
    assertTrue(body("CourierDetail.kt", "internal fun CourierDetailPage(").contains("RouteCard(c, shopName, live, onNavigate, onCall)"))
    //  همان کمک‌کارهای پیشین، نه رونوشت
    assertTrue(body("CourierCall.kt", "internal fun landmarkLines(").contains("MissionView.landmarkFirst(c, StrCourierApp::areaLine, StrCourierApp::gateLine)"))
    val wire = body("CourierDialAndroid.kt", "internal suspend fun callCustomerFrom(")
    assertTrue(wire.contains(".callNumber(it)"))
    assertTrue(wire.contains("openDialer(context, phone)"))
  }

  @Test fun `از ریشهٔ برنامه تا شماره‌گیر و خطِ نشانه راه هست`() {
    val graph = SourceGraph.of(File(repo, "android-native/app/src/main/java"))
    val reached = graph.reach(listOf("ir.vil3ntec.tohid.ui.AppRoot"))
    val need = listOf("CourierHome", "CourierBoard", "MissionCardView", "CourierDetailPage", "landmarkLines", "callCustomerFrom", "callCustomer", "openDialer")
      .map { "ir.vil3ntec.tohid.ui.courier.$it" }
    for (n in need) assertTrue("$n تعریف نشده", n in graph.declared)
    assertEquals("بی صدازننده از AppRoot (همان لغزشِ دستهٔ ۱۳)", emptyList<String>(), need.filterNot { it in reached })
  }

  /* ------------------------------- رفتار ------------------------------- */

  private val card = MissionCard("as-1", 1043, "moving")

  @Test fun `شماره همان لحظه به شماره‌گیر می‌رود و پیامی نمی‌ماند`() = runBlocking {
    val dialled = ArrayList<String>()
    var asked = ""
    val text = callCustomer(card, { id -> asked = id; ApiResult.Success("0791234567") }) { dialled += it }
    assertNull(text)
    assertEquals("as-1", asked)
    assertEquals(listOf("0791234567"), dialled)
  }

  @Test fun `بی شماره، بی شماره‌گیر و بی اینترنت پیامِ روشن`() = runBlocking {
    val never: (String) -> Unit = { error("نباید شماره‌گیر باز شود: $it") }
    assertEquals(StrCourierApp.customerPhoneMissing, callCustomer(card, { ApiResult.Success(" ") }, never))
    assertEquals(StrCourierApp.dialerFailed, callCustomer(card, { ApiResult.Success("0791234567") }) { throw IllegalStateException("no dialer") })
    assertEquals(StrCourierApp.callOnlineOnly, callCustomer(card, { ApiResult.Failure(ApiFailure.Offline()) }, never))
    assertEquals(StrCourierApp.callOnlineOnly, callCustomer(card, { ApiResult.Failure(ApiFailure.Timeout()) }, never))
    //  سرور رد کرد (پرچمِ خاموش، تمام‌شده، بی شماره…) ⇒ پیامِ خودِ سرور
    assertEquals("این مأموریت تمام شده است", callCustomer(card, { ApiResult.Failure(ApiFailure.Conflict("این مأموریت تمام شده است", "not_live")) }, never))
  }

  @Test fun `نمای جدولیِ تبلت هم ناحیه و دروازه را دارد، نه فقط نشانه`() {
    val c = MissionCard("a", 1, "moving", landmark = "پشتِ مسجد", addressText = "کوچهٔ سوم", area = "کارتهٔ چهار", gateColor = "سبز")
    assertEquals(listOf("پشتِ مسجد", "ناحیه: کارتهٔ چهار · دروازهٔ سبز"), rowLines(c))
    //  بی نشانه ⇒ نشانیِ متنی در خطِ اول (پیش‌تر «—»)
    assertEquals(listOf("کوچهٔ سوم", "ناحیه: کارتهٔ چهار · دروازهٔ سبز"), rowLines(c.copy(landmark = "")))
    assertEquals(listOf("پشتِ مسجد"), rowLines(c.copy(area = "", gateColor = "")))
    assertEquals(emptyList<String>(), rowLines(MissionCard("b", 2, "delivered")))
    val row = body("CourierMissionCard.kt", "internal fun MissionRow(")
    assertTrue("ردیف از همان کمک‌کار", row.contains("val lines = rowLines(c)"))
    assertTrue(row.contains("lines.getOrNull(1)?.let"))
    assertTrue(body("CourierCall.kt", "internal fun rowLines(").contains("landmarkLines("))
  }

  @Test fun `برچسبِ ناحیه و دروازه به زبانِ صفحه است و مقدار دست نمی‌خورد`() {
    val c = MissionCard("a", 1, "moving", landmark = "پشتِ مسجد", addressText = "کوچهٔ سوم", area = "کارتهٔ چهار", gateColor = "سبز")
    assertEquals(listOf("پشتِ مسجد", "ناحیه: کارتهٔ چهار · دروازهٔ سبز", "کوچهٔ سوم"), landmarkLines(c))
    I18n.source = { Lang.EN }
    assertEquals(listOf("پشتِ مسجد", "Area: کارتهٔ چهار · Gate: سبز", "کوچهٔ سوم"), landmarkLines(c))
    assertEquals("Call the customer", StrCourierApp.callCustomer)
    I18n.source = { Lang.PS }
    assertEquals("سیمه: کارتهٔ چهار · دروازه: سبز", landmarkLines(c)[1])
    I18n.source = { Lang.PRS }
    assertTrue("نامِ ناحیه در دری همان", landmarkLines(c)[1].contains("کارتهٔ چهار"))
    //  پایان‌یافته ⇒ نشانی پاک (`MissionView.forDisplay`) ⇒ هیچ خطی
    assertEquals(emptyList<String>(), landmarkLines(c.copy(landmark = "", addressText = "", area = "", gateColor = "")))
  }
}
