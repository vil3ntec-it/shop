package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.courier.CourierQueue
import ir.vil3ntec.tohid.core.courier.MissionCard
import ir.vil3ntec.tohid.core.courier.MissionView
import ir.vil3ntec.tohid.core.courier.QueueState
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrCourier
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 *  DLV-20 — اصل: پیک زیرِ فشارِ شمارشِ معکوس نیست؛ زمان‌سنج فقط برای فروشگاه است. سرور به کارتِ پیک
 *  `readyAt`/`scheduledFor` نمی‌دهد (`delivery` ⇒ ۱۰/۱۰)؛ این‌جا سمتِ برنامهٔ پیک: حتی اگر روزی سرور
 *  زمانی بفرستد، کارتِ مأموریت آن را نمی‌گیرد، مدلِ کارت جایی برای مهلت ندارد و نوشته‌های حالتِ پیک
 *  در هیچ زبانی شمارشِ معکوس یا «عجله» نمی‌گویند.
 */
class CourierNoCountdownTest {

  @After fun reset() { I18n.source = { Lang.FA } }

  private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

  private val base = """
    "id":"A","orderNo":12,"status":"moving","live":true,
    "address":{"landmark":"نزدیکِ مسجد","text":"کوچهٔ سوم"},
    "customer":{"name":"احمد","phoneMasked":"0791•••567"},
    "itemsCount":2,"paymentMethod":"cod","expectedCollectMinor":1240000,"fareMinor":5000,"updatedAt":99
  """

  @Test fun `زمانِ فرستاده‌شده از سرور در کارتِ پیک نمی‌نشیند`() {
    val plain = CourierWire.card(obj("{$base}"))!!
    val timed = CourierWire.card(
      obj("""{$base,"readyAt":1700000000000,"scheduledFor":1700000600000,"promisedAt":1700000900000,"deadline":1700001000000,"etaMinutes":12,"slaMinutes":30}"""),
    )!!
    assertEquals(plain, timed)
    //  نمایش هم همان کارت است — شمارشی ساخته نمی‌شود
    assertEquals(MissionView.forDisplay(plain, QueueState()), MissionView.forDisplay(timed, QueueState()))
    assertEquals(plain.status, CourierQueue.projectedStatus(timed.status, timed.id, QueueState()))
  }

  @Test fun `مدلِ کارتِ مأموریت فیلدِ مهلت یا زمان‌سنج ندارد`() {
    val banned = listOf("ready", "schedul", "deadline", "promis", "countdown", "eta", "due", "sla", "timer", "remaining", "expire")
    val fields = MissionCard::class.java.declaredFields.filter { !Modifier.isStatic(it.modifiers) }.map { it.name }
    assertTrue(fields.contains("updatedAt"))
    val hits = fields.filter { f -> banned.any { f.lowercase().contains(it) } }
    assertTrue("فیلدِ زمان‌سنج در کارتِ پیک: $hits", hits.isEmpty())
  }

  @Test fun `نوشته‌های حالتِ پیک در هر سه زبان شمارشِ معکوس ندارند`() {
    val banned = listOf(
      "دقیقه مانده", "ثانیه", "شمارشِ معکوس", "شمارش معکوس", "زمانِ باقی", "عجله", "دیر کرد",
      "countdown", "minutes left", "seconds left", "time left", "hurry", "you are late",
      "دقیقې پاتې", "ثانیې", "بیړه",
    )
    val getters = StrCourier.javaClass.declaredMethods
      .filter { Modifier.isPublic(it.modifiers) && !Modifier.isStatic(it.modifiers) && !it.isSynthetic }
      .filter { it.returnType == String::class.java && it.parameterCount == 0 }
    assertTrue(getters.size > 10)
    for (lang in Lang.entries) {
      I18n.source = { lang }
      for (m in getters) {
        val text = (m.invoke(StrCourier) as String).lowercase()
        val hit = banned.firstOrNull { text.contains(it) }
        assertEquals("${m.name} در ${lang.code}: $text", null, hit)
      }
    }
  }
}
