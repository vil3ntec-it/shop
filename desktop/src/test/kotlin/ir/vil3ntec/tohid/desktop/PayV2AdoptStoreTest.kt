package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.core.atrest.AtRestException
import ir.vil3ntec.tohid.data.CashShift
import ir.vil3ntec.tohid.data.Expense
import ir.vil3ntec.tohid.data.PayV2Default
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopPolicy
import ir.vil3ntec.tohid.data.ShopStore
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 *  e-paymethods — قدمِ شروعِ برنامه (`ShopStore.adoptPayDefault` ⇐ `PayV2Pref.adopt`) روی کلیدِ واقعیِ همین دستگاه
 *  (`tohid-policy:pay_v2`): دستگاهِ هرگز‌انتخاب‌نکرده «ترکیبی و شیفت» می‌شود و از آن پس همان می‌ماند؛ انتخابِ صریحِ خاموش یا روشن
 *  دست نمی‌خورد؛ صندوقِ بازی که عددش عوض می‌شود جابه‌جایی را تا بسته شدن نگه می‌دارد؛ دفترِ خوانده‌نشده و دفترِ آموزشی هیچ.
 *  کلیدِ پیشینِ دستگاهِ آزمون پس از هر آزمون برمی‌گردد.
 */
class PayV2AdoptStoreTest {

  private val ctx = DesktopContext
  private val prefs get() = ctx.getSharedPreferences("tohid-policy", 0)
  private var saved: Boolean? = null

  @Before fun up() {
    saved = if (prefs.contains("pay_v2")) prefs.getBoolean("pay_v2", false) else null
    prefs.edit().remove("pay_v2").commit()
  }

  @After fun down() {
    val e = prefs.edit()
    saved?.let { e.putBoolean("pay_v2", it) } ?: e.remove("pay_v2")
    e.commit()
  }

  private fun store(d: ShopData, training: Boolean = false): ShopStore = ShopStore(ctx, training).also { s ->
    @Suppress("UNCHECKED_CAST")
    (ShopStore::class.java.getDeclaredField("_data").apply { isAccessible = true }.get(s) as MutableStateFlow<ShopData>).value = d
  }

  private var seq = 0
  private fun opened(opening: Double): ShopData =
    (CashShift.open(ShopData(), opening, "علی", "2026-11-16", 100, { "pa${seq++}" }) as CashShift.Result.Ok).data

  @Test fun neverChoseAndNoOpenTillAdoptsAndStaysChosen() {
    assertFalse(ShopPolicy.payV2(ctx))
    assertEquals(PayV2Default.Step.ADOPT, store(ShopData()).adoptPayDefault())
    assertTrue(ShopPolicy.payV2(ctx))
    assertTrue(ir.vil3ntec.tohid.data.PayV2Pref.chosen(ctx))
    //  شروعِ بعدی: پذیرفته‌شده مثلِ انتخابِ صریح است
    assertEquals(PayV2Default.Step.KEEP, store(opened(100.0).copy(expenses = listOf(Expense(id = "x", amount = 999.0, createdAt = 150)))).adoptPayDefault(200))
    assertTrue(ShopPolicy.payV2(ctx))
  }

  @Test fun explicitChoiceIsNeverChanged() {
    ShopPolicy.setPayV2(ctx, false)
    assertEquals(PayV2Default.Step.KEEP, store(ShopData()).adoptPayDefault())
    assertFalse(ShopPolicy.payV2(ctx))
    ShopPolicy.setPayV2(ctx, true)
    assertEquals(PayV2Default.Step.KEEP, store(ShopData()).adoptPayDefault())
    assertTrue(ShopPolicy.payV2(ctx))
  }

  @Test fun anOpenTillThatWouldReadDifferentlyWaitsWithoutWriting() {
    val d = opened(100.0).copy(expenses = listOf(Expense(id = "big", amount = 500.0, createdAt = 150)))
    assertEquals(PayV2Default.Step.WAIT, store(d).adoptPayDefault(200))
    assertFalse(prefs.contains("pay_v2"))
    assertFalse(ShopPolicy.payV2(ctx))
    //  صندوقِ بازِ هم‌عدد (فروشِ نقدیِ ساده) ⇒ همان دم
    val same = opened(100.0).copy(sales = listOf(Sale(id = "s", finalTotal = 50.0, createdAt = 150)))
    assertEquals(PayV2Default.Step.ADOPT, store(same).adoptPayDefault(200))
    assertTrue(ShopPolicy.payV2(ctx))
  }

  @Test fun anUnreadableOrTrainingLedgerNeverDecides() {
    val unreadable = store(ShopData())
    @Suppress("UNCHECKED_CAST")
    (ShopStore::class.java.getDeclaredField("_unreadable").apply { isAccessible = true }.get(unreadable) as MutableStateFlow<AtRestException.Kind?>).value =
      AtRestException.Kind.KEY_MISSING
    assertNull(unreadable.adoptPayDefault())
    assertNull(store(ShopData(), training = true).adoptPayDefault())
    assertFalse(prefs.contains("pay_v2"))
  }
}
