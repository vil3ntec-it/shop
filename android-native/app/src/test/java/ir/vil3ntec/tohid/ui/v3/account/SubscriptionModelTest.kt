package ir.vil3ntec.tohid.ui.v3.account

import ir.vil3ntec.tohid.core.model.PlanDiscountDto
import ir.vil3ntec.tohid.core.model.PlanDto
import ir.vil3ntec.tohid.core.model.PlansDto
import ir.vil3ntec.tohid.sync.License
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.account.subscription.PlansModel
import ir.vil3ntec.tohid.ui.v3.account.subscription.SubscriptionModel
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** «اشتراکِ من» از تپش و «اشتراک و قیمت‌ها» — هیچ عدد و رنگی جز حرفِ سرور ساخته نمی‌شود */
class SubscriptionModelTest {

  private fun sub(vararg kv: Pair<String, Any>): JsonObject = buildJsonObject {
    kv.forEach { (k, v) ->
      when (v) {
        is Boolean -> put(k, JsonPrimitive(v))
        is Number -> put(k, JsonPrimitive(v))
        else -> put(k, JsonPrimitive(v.toString()))
      }
    }
  }

  @Test
  fun `دائمی شمارش ندارد، تمام‌شده همان را می‌گوید`() {
    val forever = SubscriptionModel.hero(sub("permanent" to true, "active" to true, "plan" to "y1", "progress" to 10))
    assertEquals("دائمی ✓", forever.headline); assertEquals(1f, forever.progress); assertEquals("دائمی ✓", forever.ends)
    val ended = SubscriptionModel.hero(sub("active" to false, "status" to "expired", "daysLeft" to 0, "color" to "red"))
    assertEquals("اشتراک تمام شده", ended.headline); assertEquals(SubscriptionModel.Tone.RED, ended.tone)
    val live = SubscriptionModel.hero(sub("active" to true, "status" to "active", "daysLeft" to 12, "progress" to 140, "color" to "green"))
    assertEquals("12 روز مانده", live.headline); assertEquals(1f, live.progress); assertEquals("فعال", live.status)
  }

  @Test
  fun `پیامِ زیرِ کارت — هفت روزِ آخر یا تمام‌شده`() {
    assertEquals(SubscriptionModel.Notice.ENDING, SubscriptionModel.notice(sub("active" to true, "daysLeft" to 3)))
    assertEquals(SubscriptionModel.Notice.NONE, SubscriptionModel.notice(sub("active" to true, "daysLeft" to 9)))
    assertEquals(SubscriptionModel.Notice.NONE, SubscriptionModel.notice(sub("active" to true, "permanent" to true, "daysLeft" to 3)))
    assertEquals(SubscriptionModel.Notice.EXPIRED, SubscriptionModel.notice(sub("active" to false, "status" to "expired")))
    assertEquals(SubscriptionModel.Notice.NONE, SubscriptionModel.notice(sub("active" to false, "status" to "none")))
  }

  @Test
  fun `فهرست‌ها برچسبِ سرور را نشان می‌دهند`() {
    val p = SubscriptionModel.payments(listOf(sub("plan" to "m6", "amountLabel" to "2,500 افغانی")))
    assertEquals(SubscriptionModel.Line("m6", "2,500 افغانی"), p.single())
    assertEquals(10, SubscriptionModel.notices(List(14) { sub("title" to "t$it") }).size)
  }

  @Test
  fun `پلن‌ها — از سرور، وگرنه پیش‌فرض؛ انتخاب و تخفیف`() {
    assertEquals(listOf("m1", "m6", "y1"), PlansModel.options(null).map { it.code })
    val dto = PlansDto(plans = listOf(
      PlanDto(code = "a", title = "ماهانه", price = 400, fullPrice = 500, days = 30, discount = PlanDiscountDto(label = "")),
      PlanDto(code = "b", title = "سالانه", price = 3600, days = 365, badge = "پیشنهاد ما"),
      PlanDto(code = "c", title = "خاموش", price = 1, days = 30, active = false),
    ))
    val options = PlansModel.options(dto)
    assertEquals(listOf("a", "b"), options.map { it.code })
    assertEquals("b", PlansModel.pick(options, null).code)
    assertEquals("a", PlansModel.pick(options, "a").code)
    assertEquals("b", PlansModel.pick(options, "gone").code)
    assertTrue(options[0].discounted); assertEquals(20, options[0].offPercent)
    assertEquals("٪20 تخفیف", PlansModel.discountText(options[0]))
    assertEquals("گرفتن اشتراک ماهانه — 400 افغانی", PlansModel.cta(options[0], "افغانی"))
  }

  @Test
  fun `کارتِ حالت — مجوز، دورهٔ سرور، یا دعوت`() {
    val ending = PlansModel.state(License.State.ACTIVE, 3, 0, "۶ ماهه", false, 0, false, 0, 7)
    assertEquals(V3Tone.ERROR, ending.tone); assertEquals("اشتراک رو به پایان است", ending.title)
    assertTrue(ending.body.contains("3 روز مانده")); assertTrue(ending.body.contains("پلن: ۶ ماهه"))
    val grace = PlansModel.state(License.State.GRACE, 0, 0, null, false, 0, false, 0, 7)
    assertEquals("تمدید کنید", grace.body)
    val trial = PlansModel.state(License.State.NONE, 0, 0, null, true, 5, true, 0, 7)
    assertTrue(trial.title.startsWith("دورهٔ آزمایشی"))
    val invite = PlansModel.state(null, 0, 0, null, false, 0, false, 0, 7)
    assertEquals(V3Tone.INFO, invite.tone); assertEquals("7 روز رایگان", invite.title)
  }

  @Test
  fun `کدِ اشتراک`() {
    assertTrue(PlansModel.canRedeem("123456", signedIn = true))
    assertFalse(PlansModel.canRedeem("123456", signedIn = false))
    assertFalse(PlansModel.canRedeem("12345", signedIn = true))
    assertTrue(PlansModel.offlineOk(5000).contains("دائمی"))
    assertTrue(PlansModel.offlineOk(30).contains("30 روز"))
  }
}
