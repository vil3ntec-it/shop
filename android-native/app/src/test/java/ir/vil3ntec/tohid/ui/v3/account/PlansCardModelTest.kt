package ir.vil3ntec.tohid.ui.v3.account

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.model.PlanDto
import ir.vil3ntec.tohid.core.model.PlansDto
import ir.vil3ntec.tohid.ui.v3.account.subscription.PlansModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 *  کارت‌های بازشوندهٔ «اشتراک و قیمت‌ها» (`PlanCard`): صرفه‌جویی از قیمت‌های سرور حساب می‌شود، نه
 *  عددِ نوشته؛ ماهِ تقویمی از `amount`/`unit` ِ سرور؛ و فقط یک کارت در هر لحظه باز است.
 */
class PlansCardModelTest {

  @Before fun fa() { I18n.source = { Lang.FA } }

  private fun dto(vararg p: PlanDto) = PlansDto(plans = p.toList())

  private fun sampleOptions() = PlansModel.options(dto(
    PlanDto(code = "m1", price = 500, amount = 1, unit = "month", days = 30),
    PlanDto(code = "m6", price = 2500, amount = 6, unit = "month", days = 180, badge = "پیشنهاد"),
    PlanDto(code = "y1", price = 4000, amount = 1, unit = "year", days = 365),
  ))

  @Test fun savingComesFromServerPricesAgainstTheShortestPlan() {
    val o = sampleOptions()
    assertNull("پلنِ پایه صرفه‌جویی ندارد", PlansModel.saving(o[0], o))
    //  ۶ × ۵۰۰ = ۳۰۰۰ ⇐ ۲۵۰۰: ۵۰۰ کمتر (٪۱۷)؛ ۱۲ × ۵۰۰ = ۶۰۰۰ ⇐ ۴۰۰۰: ۲۰۰۰ کمتر (٪۳۳)
    val six = PlansModel.saving(o[1], o)!!
    assertEquals(500L, six.amount); assertEquals(17, six.percent); assertEquals("m1", six.base.code)
    val year = PlansModel.saving(o[2], o)!!
    assertEquals(2000L, year.amount); assertEquals(33, year.percent)
  }

  @Test fun savingFollowsServerPricesAndCalendarMonths() {
    val o = PlansModel.options(dto(
      PlanDto(code = "a", title = "ماهانه", price = 400, amount = 1, unit = "month", days = 30),
      PlanDto(code = "b", title = "۶ ماهه", price = 2000, amount = 6, unit = "month", days = 180),
      PlanDto(code = "c", title = "۱ ساله", price = 3000, amount = 1, unit = "year", days = 365),
    ))
    assertEquals(listOf(1, 6, 12), o.map { it.months })
    assertEquals(400L, PlansModel.saving(o[1], o)!!.amount)
    //  یک سال = ۱۲ ماهِ تقویمی (۴۸۰۰)، نه ۳۶۵ / ۳۰ ماه
    assertEquals(1800L, PlansModel.saving(o[2], o)!!.amount)
    assertEquals(38, PlansModel.saving(o[2], o)!!.percent)
  }

  @Test fun daysAreTheFallbackWhenTheUnitHasNoMonthsAndNoSavingMeansNoChip() {
    val o = PlansModel.options(dto(
      PlanDto(code = "w", title = "هفته", price = 100, amount = 1, unit = "week", days = 7),
      PlanDto(code = "m", title = "ماه", price = 300, amount = 1, unit = "month", days = 30),
      PlanDto(code = "x", title = "گران", price = 9000, amount = 1, unit = "year", days = 365),
    ))
    assertEquals(0, o[0].months)
    //  ۱۰۰ × ۳۰ / ۷ = ۴۲۸٫۶ ⇐ ۳۰۰: ۱۲۹ کمتر
    assertEquals(129L, PlansModel.saving(o[1], o)!!.amount)
    assertNull("گران‌تر از خریدِ پیاپی ⇒ بی نشانِ صرفه‌جویی", PlansModel.saving(o[2], o))
    assertEquals(0, PlansModel.monthsOf(3, "day"))
    assertEquals(24, PlansModel.monthsOf(2, "year"))
  }

  @Test fun oneCardOpenAtATimeAndTheSelectedOneIsOpenFirst() {
    val o = sampleOptions()
    val picked = PlansModel.pick(o, null)
    assertEquals("m6", PlansModel.openCard(o, picked, PlansModel.UNTOUCHED))
    assertEquals("y1", PlansModel.openCard(o, picked, "y1"))
    assertNull(PlansModel.openCard(o, picked, null))
    assertEquals("کدِ رفته ⇒ همان انتخاب‌شده", "m6", PlansModel.openCard(o, picked, "gone"))
    assertEquals("y1", PlansModel.toggle("y1", "m6"))
    assertNull(PlansModel.toggle("y1", "y1"))
    assertEquals("m1", PlansModel.toggle("m1", null))
  }
}
