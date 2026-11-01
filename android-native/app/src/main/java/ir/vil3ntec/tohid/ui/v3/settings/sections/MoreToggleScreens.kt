package ir.vil3ntec.tohid.ui.v3.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.i18n.StrChat
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.data.FraudTrailPref
import ir.vil3ntec.tohid.data.ShopPolicy
import ir.vil3ntec.tohid.data.StockActions
import ir.vil3ntec.tohid.data.StockLedger
import ir.vil3ntec.tohid.data.TillReceivePref
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.screens.newId
import ir.vil3ntec.tohid.ui.v3.V3ConfirmSheet
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.settings.SettingsBlock
import ir.vil3ntec.tohid.ui.v3.settings.SettingsNote
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPageFrame
import ir.vil3ntec.tohid.todayIso
import kotlinx.coroutines.launch

/*
 *  کلیدهای روشن/خاموشِ دیگرِ رابطِ امروز (`ui/legacy/SettingsLegacy.kt`) در نسخهٔ ۳ — همان کلید، همان
 *  پیش‌فرضِ خاموش و همان نوشتن (`SettingsPortTest`)؛ فقط رویِ کیت (`V3Segmented` + توضیح).
 */

private val SWITCH: List<String> get() = listOf(StrShell.switchOff, StrShell.switchOn)

/** یک کلیدِ خاموش | روشن با توضیح؛ `save` همان نوشتنِ رابطِ امروز */
@Composable
private fun RouteScope.ToggleScreen(key: String, onBack: (() -> Unit)?, initial: Boolean, onText: String, note: String, save: (Boolean) -> Unit) {
  var on by remember { mutableStateOf(initial) }
  SettingsPageFrame(title = SettingsLayout.labelOf(key), subtitle = if (on) onText else StrSettings.off, onBack = onBack) {
    item(key = "choice") {
      SettingsBlock { V3Segmented(SWITCH, if (on) 1 else 0, { on = it == 1; save(on) }) }
      SettingsNote(note)
    }
  }
}

/** `settings/sales/till-receive`: دریافتِ وجه یا قسط از مشتری در صندوق (POS-39)، پیش‌فرض خاموش */
@Composable
internal fun RouteScope.TillReceiveScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  ToggleScreen(SettingsLayout.TILL_RECEIVE, onBack, TillReceivePref.on(context), StrShell.switchOn, StrSettings.tillReceiveNote) {
    TillReceivePref.set(context, it)
  }
}

/** `settings/sales/stock-insight`: تحلیلِ موجودی در گزارشِ کالاها (INV-15/16)، فقط همین دستگاه */
@Composable
internal fun RouteScope.StockInsightScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  ToggleScreen(SettingsLayout.STOCK_INSIGHT, onBack, ShopPolicy.stockInsight(context), StrShell.stockInsightShown, StrShell.stockInsightNote) {
    ShopPolicy.setStockInsight(context, it)
  }
}

/** `settings/sales/stock-levels`: نقطهٔ سفارش و بیشینه در فرمِ کالا و «نیاز به سفارش» (INV-12) */
@Composable
internal fun RouteScope.StockLevelsScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  ToggleScreen(SettingsLayout.STOCK_LEVELS, onBack, ShopPolicy.stockLevels(context), StrShell.stockLevelsShown, StrShell.stockLevelsNote) {
    ShopPolicy.setStockLevels(context, it)
  }
}

/** `settings/sales/product-tools`: نامِ مشابه، چند بارکد، بسته و قیمتِ گروهی (PRD-19/04) */
@Composable
internal fun RouteScope.ProductToolsScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  ToggleScreen(SettingsLayout.PRODUCT_TOOLS, onBack, ShopPolicy.productTools(context), StrShell.switchOn, StrShell.productToolsNote) {
    ShopPolicy.setProductTools(context, it)
  }
}

/** `settings/security/fraud-trail`: ردِ لغو، مرجوعی و حذفِ ردیف (RPT-09)، فقط همین دستگاه */
@Composable
internal fun RouteScope.FraudTrailScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  ToggleScreen(SettingsLayout.FRAUD_TRAIL, onBack, FraudTrailPref.enabled(context), StrShell.fraudTrailOn, StrShell.fraudTrailNote) {
    FraudTrailPref.setEnabled(context, it)
  }
}

/** `settings/data/chat-storage`: نوارِ پیشرفت و سنجشِ جای دستگاه پیش از پرونده‌های گفت‌وگو */
@Composable
internal fun RouteScope.ChatGuardScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  ToggleScreen(SettingsLayout.CHAT_GUARD, onBack, ShopPolicy.chatMediaGuard(context), StrChat.guardOn, StrChat.guardNote) {
    ShopPolicy.setChatMediaGuard(context, it)
  }
}

/**
 *  `settings/sales/stock-mode`: موتورِ «موجودی و بها» — تنظیمِ دکان (ردیفِ `stock_mode` در دفترچهٔ ثبت، با
 *  همگام‌سازی به همهٔ دستگاه‌ها)، با تأیید؛ همان `StockModeSection` ِ رابطِ امروز (متن‌ها هم همان، فارسی).
 *  پرچمِ سرورِ `shop.stock_v2` خاموش ⇒ کلیدی نیست، فقط «خاموش».
 */
@Composable
internal fun RouteScope.StockModeScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val d = data
  val on = remember(d.auditLog) { StockLedger.modeOn(d) }
  var ask by remember { mutableStateOf<Boolean?>(null) }
  SettingsPageFrame(
    title = SettingsLayout.labelOf(SettingsLayout.STOCK_MODE),
    subtitle = if (StockLedger.serverFlag && on) "موتورِ تازه روشن (آزمایشی)" else StrSettings.off,
    onBack = onBack,
  ) {
    item(key = "mode") {
      if (!StockLedger.serverFlag) {
        SettingsNote(StrSettings.off)
        return@item
      }
      SettingsBlock { V3Segmented(SWITCH, if (on) 1 else 0, { pick -> if ((pick == 1) != on) ask = pick == 1 }) }
      SettingsNote(
        "روشن: بهای میانگینِ موزون با هزینهٔ جانبیِ خرید، سری و انقضا با برداشتِ FEFO سرِ صندوق، دفترِ حرکت با کاربر و دلیل، " +
          "شمارش با بازبینی، انتقال بین محل‌ها و «وظیفهٔ بررسی» برای موجودیِ منفی. برای همهٔ دستگاه‌های همین دکان یکی است.",
      )
      SettingsNote("آزمایشی: روشِ بهای تمام‌شده هنوز تصمیمِ باز است (D-23)، پس بها و سود «تقریبی»اند.")
    }
  }
  ask?.let { next ->
    V3ConfirmSheet(
      title = if (next) "موتورِ تازهٔ موجودی و بها روشن شود؟" else "موتورِ تازه خاموش شود؟",
      message = if (next) "روی همهٔ دستگاه‌های این دکان روشن می‌شود. موجودیِ هر کالا همان عددِ امروز می‌ماند." else "فروش و خرید به رفتارِ امروز برمی‌گردد. چیزی از دفتر پاک نمی‌شود.",
      confirm = if (next) "روشن کن" else "خاموش کن",
      danger = !next,
      onDismiss = { ask = null },
      onConfirm = {
        ask = null
        val user = runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrDefault("")
        scope.launch { store.save(StockActions.setMode(data, next, user, todayIso(), System.currentTimeMillis(), ::newId)) }
      },
    )
  }
}
