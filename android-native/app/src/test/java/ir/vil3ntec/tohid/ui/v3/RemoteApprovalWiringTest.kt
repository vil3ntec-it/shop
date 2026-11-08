package ir.vil3ntec.tohid.ui.v3

import ir.vil3ntec.tohid.ui.v3.shell.ShellPaths
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  STF-02 «پرسیدن از مدیر» و PRM-15 در کامپیوتر — سیم‌کشیِ رابط (Compose با آزمونِ JVM ساخته نمی‌شود، پس مثلِ
 *  `V3PortedFeaturesTest` با متنِ همان پرونده‌ها): برگهٔ تأییدِ گوشی (تخفیفِ صندوق و لغو) و پنجرهٔ تأییدِ کامپیوتر/برگشت
 *  همان `RemoteAsk` را دارند، برگشت نقشِ تأییدکننده را از سرور می‌گیرد، صندوقِ تأییدِ صاحب/مدیر در پوسته (گوشی و کامپیوتر)
 *  است، و «دادنِ اعتبارِ هدیه» در پروندهٔ مشتریِ کامپیوتر. منطق در `RemoteApprovalTest` (core) و `RemoteAskTest`.
 */
class RemoteApprovalWiringTest {

  private fun src(rel: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/$rel") }.firstOrNull { it.isFile }?.readText()
      ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  @Test
  fun `برگه و پنجرهٔ تأیید هر دو «پرسیدن از مدیر» را با همان حال دارند`() {
    val sheet = src("ui/v3/sales/approval/ApprovalSheet.kt")
    assertTrue(sheet.contains("rememberRemoteAsk(ask.need, shopId)"))
    assertTrue(sheet.contains("RemoteAskBlock(remote, reason, pinToo = true)"))
    //  بی تأییدکننده روی همین دستگاه ولی با پرچمِ روشن ⇒ فقط دلیل و پرسیدن (نه پیامِ «تأییدکننده‌ای نیست»)
    assertTrue(sheet.contains("block != null && !(asking && block == ApprovalBlock.NO_APPROVER)"))
    val dialog = src("ui/components/staff/ApprovalDialog.kt")
    assertTrue(dialog.contains("val remote = rememberRemoteAsk(need, shopId)"))
    assertTrue(dialog.contains("if (remote.offered) {"))
    val ask = src("ui/components/staff/RemoteAsk.kt")
    //  پرچمِ خاموش یا بی سرور ⇒ همان امروز؛ بستنِ برگه درخواستِ باز را پس می‌گیرد
    assertTrue(ask.contains("RemoteApproval.offered(config, decision, need.perm)"))
    assertTrue(ask.contains("DisposableEffect(state) { onDispose { state.withdraw() } }"))
  }

  @Test
  fun `تأییدِ از راهِ دور همان راهِ برگه را می‌رود — فاکتور، لغو و برگشت`() {
    assertTrue(src("data/DiscountGate.kt").contains("RemoteApproval.isRef(s)"))
    assertTrue(src("ui/v3/sales/checkout/CheckoutRoute.kt").contains("CheckoutSession.approval?.let(DiscountGate::json)"))
    assertTrue(src("ui/v3/sales/history/HistoryRoutes.kt").contains("slip?.let(ir.vil3ntec.tohid.data.DiscountGate::json)"))
    val ret = src("ui/components/staff/ReturnApprovalRequest.kt")
    assertTrue(ret.contains("role = r.deciderRole.ifBlank { \"staff\" }"))
  }

  @Test
  fun `صندوقِ تأییدِ صاحب و مدیر در پوستهٔ گوشی و کامپیوتر`() {
    assertTrue(src("ui/v3/shell/ShellNotice.kt").contains("ApprovalInboxNotice()"))
    val routes = src("ui/v3/shell/ShellRoutes.kt")
    assertTrue(routes.contains("V3Route(ShellPaths.APPROVALS, RouteKind.PAGE"))
    assertTrue(routes.contains("ApprovalInboxRoute()"))
    assertTrue(ShellPaths.APPROVALS == "approvals")
    val inbox = src("ui/v3/shell/ApprovalInbox.kt")
    //  فقط صاحب/مدیر از سرور می‌پرسند؛ دیدن با همان قاعدهٔ هسته (اختیارِ همان مقدار، نه خودش)
    assertTrue(inbox.contains("role !in RemoteApproval.APPROVER_ROLES"))
    assertTrue(inbox.contains("RemoteApproval.inbox(all, me, role, now)"))
    assertTrue(inbox.contains("V3ConfirmSheet("))
  }

  /** PRM-15 — همان «دادنِ اعتبارِ هدیه» ِ پروندهٔ گوشی در پروندهٔ مشتریِ کامپیوتر */
  @Test
  fun `پروندهٔ مشتریِ کامپیوتر — دادنِ اعتبارِ هدیه`() {
    assertTrue(src("ui/screens/DeskCustomers.kt").contains("StoreCreditGrantButton(store, d, id, ::toast,"))
  }
}
