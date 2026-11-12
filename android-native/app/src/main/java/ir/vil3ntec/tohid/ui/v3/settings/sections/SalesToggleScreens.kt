package ir.vil3ntec.tohid.ui.v3.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.i18n.StrRounding
import ir.vil3ntec.tohid.core.pay.TillRounding
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.core.i18n.StrShift
import ir.vil3ntec.tohid.data.CashRoundPref
import ir.vil3ntec.tohid.data.DebtReminderPref
import ir.vil3ntec.tohid.data.HeldRules
import ir.vil3ntec.tohid.data.ShopPolicy
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.ui.components.pos.HeldInvoicePrefs
import ir.vil3ntec.tohid.ui.screens.DebtReminderTemplateEditor
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.settings.SettingsBlock
import ir.vil3ntec.tohid.ui.v3.settings.SettingsNote
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPageFrame

/*
 *  کلیدهای ساده‌ی «فروش و پرداخت» که از تنظیماتِ رابطِ امروز (رابطِ قدیمِ پاک‌شده) به نسخهٔ ۳
 *  آمدند — همان کلید، همان پیش‌فرض و همان نوشتن؛ فقط رویِ کیت (`V3Segmented` + توضیح، مثلِ «کالای منقضی»).
 */

/** خاموش | روشن — برچسب‌های همان رابطِ امروز */
private val OFF_ON: List<String> get() = listOf(StrShell.switchOff, StrShell.switchOn)

/** `settings/sales/payments`: «نقد و نسیه (امروز) | ترکیبی و شیفت» — همان `ShopPolicy.setPayV2` (PAY، v3 A4) */
@Composable
internal fun RouteScope.PaySettingsScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  var payV2 by remember { mutableStateOf(ShopPolicy.payV2(context)) }
  var blind by remember { mutableStateOf(ShopPolicy.blindCount(context)) }
  var cashStep by remember { mutableStateOf(CashRoundPref.step(context)) }
  SettingsPageFrame(title = SettingsLayout.labelOf(SettingsLayout.PAY), onBack = onBack) {
    item(key = "choice") {
      SettingsBlock {
        V3Segmented(
          options = listOf(StrShell.payLegacy, StrShell.payMixed),
          selected = if (payV2) 1 else 0,
          onSelect = {
            payV2 = it == 1
            ShopPolicy.setPayV2(context, payV2)
          },
        )
      }
      SettingsNote(StrShell.payNote)
    }
    //  PAY-13: شمارشِ کورِ شاگرد — فقط با «ترکیبی و شیفت» معنا دارد؛ پیش‌فرض روشن (همان رفتارِ پیشین)
    if (payV2) item(key = "blind") {
      SettingsBlock {
        PartTitle(StrShift.blindSettingTitle)
        V3Segmented(
          options = OFF_ON,
          selected = if (blind) 1 else 0,
          onSelect = {
            blind = it == 1
            ShopPolicy.setBlindCount(context, blind)
          },
        )
      }
      SettingsNote(StrShift.blindSettingNote)
    }
    //  PAY-10: پرداخت با ارزِ نقد و نرخِ دکان — فقط با «ترکیبی و شیفت»؛ پیش‌فرض خاموش (`FxTillPref`)
    if (payV2) item(key = "fx") { FxTillSettings() }
    //  PAY-06: روش‌های پنلِ پرداخت — دکان روشی را که نمی‌پذیرد برمی‌دارد (`PayMethodsPref`، خالی ⇒ همهٔ روش‌های امروز)
    if (payV2) item(key = "methods") { PayMethodsSettings() }
    //  PAY-09: گردکردنِ نقدی (خاموش | ۵ | ۱۰ افغانی) — همان `CashRoundPref.set` ِ رابطِ امروز، پیش‌فرض خاموش
    item(key = "rounding") {
      SettingsBlock {
        V3Segmented(
          options = listOf(StrRounding.off, StrRounding.five, StrRounding.ten),
          selected = TillRounding.STEPS.indexOf(cashStep).coerceAtLeast(0),
          onSelect = {
            cashStep = TillRounding.STEPS[it]
            CashRoundPref.set(context, cashStep)
          },
        )
      }
      SettingsNote(StrRounding.line + " — " + StrRounding.note)
    }
  }
}

/** `settings/sales/credit-limit`: سقفِ اعتبار در صندوق (CRM-03، S07) — همان `ShopPolicy.setCreditGate`، پیش‌فرض خاموش */
@Composable
internal fun RouteScope.CreditGateScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  var on by remember { mutableStateOf(ShopPolicy.creditGate(context)) }
  SettingsPageFrame(
    title = SettingsLayout.labelOf(SettingsLayout.CREDIT_GATE),
    subtitle = if (on) StrShell.creditGateOn else StrSettings.off,
    onBack = onBack,
  ) {
    item(key = "choice") {
      SettingsBlock {
        V3Segmented(
          options = OFF_ON,
          selected = if (on) 1 else 0,
          onSelect = {
            on = it == 1
            ShopPolicy.setCreditGate(context, on)
          },
        )
      }
      SettingsNote(StrShell.creditGateNote)
    }
  }
}

/**
 *  `settings/data/customer-reports`: گزارش‌های مشتری (CRM-04/05/06/08/10/14) — همان `ShopPolicy.setCrmReports`، ردهٔ B،
 *  پیش‌فرض خاموش. گوشی و کامپیوتر هر دو ردیف دارند (دورِ ۱۴): ادغامِ تکراری‌ها، تصویرِ صورت‌حساب، گروه و لیستِ قیمت
 *  و سنِ بدهی و RFM ِ گوشی هم پشتِ همین کلیدِ همین دستگاه‌اند؛ توضیحِ هر سکو کارهای همان سکو را می‌گوید.
 */
@Composable
internal fun RouteScope.CrmReportsScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  var on by remember { mutableStateOf(ShopPolicy.crmReports(context)) }
  val desk = ir.vil3ntec.tohid.ui.Platform.DESKTOP
  SettingsPageFrame(
    title = SettingsLayout.labelOf(SettingsLayout.CRM_REPORTS),
    subtitle = if (!on) StrSettings.off else if (desk) StrShell.crmShownInReports else StrShell.crmShownPhone,
    onBack = onBack,
  ) {
    item(key = "choice") {
      SettingsBlock {
        V3Segmented(
          options = OFF_ON,
          selected = if (on) 1 else 0,
          onSelect = {
            on = it == 1
            ShopPolicy.setCrmReports(context, on)
          },
        )
      }
      SettingsNote(if (desk) StrShell.crmNote else StrShell.crmNotePhone)
    }
    //  CRM-06: متنِ یادآوری (همین دستگاه) — زبانهٔ «یادآوریِ امروز» ِ کامپیوتر و دکمه‌های «پیامک/واتس‌اپ» ِ حسابِ مشتری
    //  در گوشی و کامپیوتر (`DebtReminder.forDebtor`)؛ پس روی هر دو سکو، فقط با همین کلیدِ روشن
    if (on) item(key = "reminder") {
      var reminderText by remember { mutableStateOf(DebtReminderPref.template(context)) }
      SettingsBlock {
        DebtReminderTemplateEditor(
          saved = reminderText,
          onSave = { t -> DebtReminderPref.set(context, t).also { if (it == null) reminderText = DebtReminderPref.template(context) } },
          onReset = { DebtReminderPref.reset(context); reminderText = DebtReminderPref.template(context) },
          onMessage = { toasts.show(it) },
        )
      }
    }
  }
}

/** `settings/sales/held-invoices`: چند فاکتورِ هم‌زمان (POS-12) — همان `HeldInvoicePrefs.set`، فقط همین دستگاه، پیش‌فرض خاموش */
@Composable
internal fun RouteScope.HeldInvoicesScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  HeldInvoicePrefs.load(context)
  val on = HeldInvoicePrefs.on.value
  SettingsPageFrame(
    title = SettingsLayout.labelOf(SettingsLayout.HELD),
    subtitle = if (on) StrShell.switchOn else StrShell.switchOff,
    onBack = onBack,
  ) {
    item(key = "choice") {
      SettingsBlock {
        V3Segmented(
          options = OFF_ON,
          selected = if (on) 1 else 0,
          onSelect = { HeldInvoicePrefs.set(context, it == 1) },
        )
      }
      SettingsNote(StrPos.heldNote(plain(HeldRules.MAX_OPEN)))
    }
    //  POS-12 «پاک‌سازیِ خودکارِ کهنه‌ها»: عوض کردنش همان مجوزِ لغوِ فاکتور (همان `HeldInvoiceSettings` ِ رابطِ امروز)
    if (on) item(key = "autoClean") {
      val canCancel = remember { ir.vil3ntec.tohid.data.ShopRole.canCancelSale(context) }
      val auto = HeldInvoicePrefs.autoClean.value
      SettingsNote(StrPos.heldAutoClean(plain(HeldRules.STALE_HOURS)))
      if (canCancel) {
        SettingsBlock { V3Segmented(OFF_ON, if (auto) 1 else 0, { HeldInvoicePrefs.setAutoClean(context, it == 1) }) }
      } else {
        SettingsNote((if (auto) StrShell.switchOn else StrShell.switchOff) + " — " + StrPos.heldAutoCleanLocked)
      }
      SettingsNote(StrPos.heldAutoCleanNote)
    }
  }
}
