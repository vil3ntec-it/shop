package ir.vil3ntec.tohid.core.pay

import ir.vil3ntec.tohid.core.gateway.HesabPayShop
import ir.vil3ntec.tohid.core.gateway.Payee
import ir.vil3ntec.tohid.core.gateway.SimpleAccount
import ir.vil3ntec.tohid.core.gateway.TillHesab
import ir.vil3ntec.tohid.data.PayFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  e-paymethods — روش‌های پرداختِ صندوق (`core/pay/TillMethods`): ترتیبِ نقد، کارت، حساب‌پی، کیف پول، حواله، نسیه؛ روشِ
 *  برداشتهٔ دکان کاشی نمی‌گیرد؛ حالِ حساب‌پی از `/config` ِ همان دکان (آماده، ۲۴ ساعتِ در انتظار، راه‌اندازی‌نشده، خاموش).
 */
class TillMethodsTest {

  private val now = 1_800_000_000_000L
  private val payee = Payee("دکانِ احمد", "1222", "hp1_fp")
  private val simple = TillHesab(false, HesabPayShop.SIMPLE, SimpleAccount("0799111222", ""), payee = payee, flag = true)

  private fun kinds(t: List<TillMethods.Tile>) = t.map { it.kind }

  @Test fun `همهٔ روش‌های دکان به ترتیب، نقد و نسیه همیشه`() {
    val t = TillMethods.tiles(PayFlow.panelMethods(), TillMethods.Hesab.Ready(auto = false))
    assertEquals(
      listOf(TillMethods.Kind.CASH, TillMethods.Kind.CARD, TillMethods.Kind.HESABPAY, TillMethods.Kind.WALLET, TillMethods.Kind.TRANSFER, TillMethods.Kind.CREDIT),
      kinds(t),
    )
    assertEquals(listOf("cash", "afpay_card", null, "e_money", "hawala", null), t.map { it.method })
    assertTrue(t.all { it.enabled && it.until == null })
  }

  @Test fun `روشی که دکان برداشته کاشی نمی‌گیرد؛ کیف پول تا یکی روشن است می‌ماند`() {
    val off = setOf("afpay_card", "hawala", "e_money")
    val t = TillMethods.tiles(PayFlow.panelMethods(off = off), TillMethods.Hesab.Off)
    assertEquals(listOf(TillMethods.Kind.CASH, TillMethods.Kind.WALLET, TillMethods.Kind.CREDIT), kinds(t))
    //  بی حساب‌پیِ آماده، ثبتِ دستیِ حساب‌پی نخستین کیف پول است
    assertEquals("hesabpay", t.single { it.kind == TillMethods.Kind.WALLET }.method)
    val none = TillMethods.tiles(PayFlow.panelMethods(off = PayFlow.toggleableMethods().map { it.id }.toSet()), TillMethods.Hesab.Off)
    assertEquals(listOf(TillMethods.Kind.CASH, TillMethods.Kind.CREDIT), kinds(none))
  }

  @Test fun `حساب‌پیِ آماده — همان در ِ NFC و QR ِ صندوق (گیرندهٔ معلوم + خودکار یا ساده با پرچم)`() {
    assertEquals(TillMethods.Hesab.Ready(auto = false), TillMethods.hesab(simple, now))
    assertEquals(TillMethods.Hesab.Ready(auto = true), TillMethods.hesab(TillHesab(true, HesabPayShop.AUTO, null, payee = payee, flag = true), now))
    //  ساده با پرچمِ خاموش ⇒ هیچ (نه کاشی، نه پیوند) — `false` ِ صریحِ پنل هنوز خاموش می‌کند
    assertEquals(TillMethods.Hesab.Off, TillMethods.hesab(simple.copy(flag = false), now))
    //  بی گیرندهٔ معلوم (سرورِ کهنه) ⇒ نه
    assertEquals(TillMethods.Hesab.Off, TillMethods.hesab(simple.copy(payee = null), now))
    //  بی نت (`HesabPayEnv.till` ⇒ OFF) ⇒ هیچ
    assertEquals(TillMethods.Hesab.Off, TillMethods.hesab(TillHesab.OFF, now))
  }

  @Test fun `حسابِ تازه در ۲۴ ساعتِ امنیتی و بی حسابِ فعال ⇒ کاشیِ خاموش با زمانِ فعال شدن؛ پس از آن نه`() {
    val until = now + 6 * 3_600_000L
    val pending = TillHesab(false, HesabPayShop.OFF, null, flag = true, pendingUntil = until)
    assertEquals(TillMethods.Hesab.Pending(until), TillMethods.hesab(pending, now))
    val t = TillMethods.tiles(PayFlow.panelMethods(), TillMethods.hesab(pending, now)).single { it.kind == TillMethods.Kind.HESABPAY }
    assertFalse(t.enabled)
    assertEquals(until, t.until)
    //  مهلت گذشت ولی نمای صندوق هنوز کهنه است ⇒ نه کاشی و نه پیوند (خواندنِ بعدیِ سرور حساب را فعال نشان می‌دهد)
    assertEquals(TillMethods.Hesab.Off, TillMethods.hesab(pending, until + 1))
    //  حسابِ فعال هست (پول هنوز به حسابِ کهنه) ⇒ آماده، هرچه در انتظار باشد
    assertEquals(TillMethods.Hesab.Ready(auto = false), TillMethods.hesab(simple.copy(pendingUntil = until), now))
  }

  @Test fun `راه‌اندازی‌نشده ⇒ هیچ کاشی و فقط Setup (پیوندِ صاحب)`() {
    val off = TillHesab(false, HesabPayShop.OFF, null, flag = true)
    assertEquals(TillMethods.Hesab.Setup, TillMethods.hesab(off, now))
    assertFalse(TillMethods.tiles(PayFlow.panelMethods(), TillMethods.Hesab.Setup).any { it.kind == TillMethods.Kind.HESABPAY })
  }

  @Test fun `با حساب‌پیِ آماده کیف پولِ پیش‌فرض ثبتِ دستیِ حساب‌پی نیست`() {
    val wallet = TillMethods.tiles(PayFlow.panelMethods(), TillMethods.Hesab.Ready(false)).single { it.kind == TillMethods.Kind.WALLET }
    assertEquals("e_money", wallet.method)
    //  فقط ثبتِ دستیِ حساب‌پی مانده ⇒ همان (کاشیِ کیف پول گم نمی‌شود)
    val only = PayFlow.panelMethods(off = setOf("e_money", "my_money", "atoma_pay", "m_paisa"))
    assertEquals("hesabpay", TillMethods.tiles(only, TillMethods.Hesab.Ready(false)).single { it.kind == TillMethods.Kind.WALLET }.method)
  }

  @Test fun `پیکربندیِ صندوق از سرور — pendingUntil فقط عددِ مثبت`() {
    val body = mapOf("hesabpay" to mapOf("enabled" to false, "flag" to true, "mode" to "off", "pendingUntil" to 1_800_000_360_000L))
    val t = TillHesab.fromMap(body)
    assertEquals(1_800_000_360_000L, t.pendingUntil)
    assertNull(t.payee)
    assertTrue(t.flag)
    //  عدد از JSON ممکن است Double یا Int برسد
    assertEquals(1_800_000_360_000L, TillHesab.fromMap(mapOf("hesabpay" to mapOf("flag" to true, "mode" to "off", "pendingUntil" to 1.80000036E12))).pendingUntil)
    assertNull(TillHesab.fromMap(mapOf("hesabpay" to mapOf("flag" to true, "mode" to "off", "pendingUntil" to 0))).pendingUntil)
    assertNull(TillHesab.fromMap(mapOf("hesabpay" to mapOf("flag" to true, "mode" to "off"))).pendingUntil)
    //  سرورِ کهنه بی `flag` ⇒ همان `enabled`
    assertFalse(TillHesab.fromMap(mapOf("hesabpay" to mapOf("enabled" to false, "mode" to "off"))).flag)
  }
}
