package ir.vil3ntec.tohid.ui.v3.sales.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.AssignmentReturn
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.ui.theme.Corner
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Colors
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import ir.vil3ntec.tohid.ui.theme.SectionColors
import ir.vil3ntec.tohid.ui.theme.V3Pair
import ir.vil3ntec.tohid.ui.v3.V3Type

/*
 *  معنای رنگِ پول در تاریخچهٔ فروش (۶۵h، صاحب‌کار: «رنگ بده؛ بیشترِ مردم با رنگ می‌فهمن»): هر رنگ **یک** معنا دارد و
 *  همه‌جای صفحه همان است — ردیفِ فاکتور، خطِ «برگشت / خالص / قرضِ وصول‌شده» و سرخطِ روز. رنگ هرگز تنها نیست: کنارش
 *  همیشه واژه («نقد») یا آیکن (اسکناس) هست.
 *
 *    نقد            سبز        ⇐ پولِ دخل
 *    نسیه           کهربایی    ⇐ به حسابِ مشتری رفت (بدهی)
 *    کارت/حواله…    آبی        ⇐ غیرنقد (کارت، حواله، کیف پول، ترکیبی)
 *    برگشت و لغو    سرخ        ⇐ پولی که پس رفت یا فاکتوری که نیست
 *    خالص           خنثیِ پررنگ ⇐ فروش منهای برگشت
 *    قرضِ وصول‌شده  فیروزه‌ای  ⇐ پولِ قرض‌های پیشین — فروش نیست
 *    اینترنتی       آرام        ⇐ پولِ دخل نیست
 */

/** نوعِ پول در تاریخچه — هر کدام یک رنگ ([historyTone])، یک آیکن ([moneyIcon]) و همیشه یک واژه */
enum class MoneyKind { CASH, CREDIT, CARD, ONLINE, CANCELLED, RETURN, NET, DEBT_IN }

/** نوعِ پرداختِ یک فاکتور برای رنگِ ردیف — همان گروهِ فیلتر (`PayFlow.payGroup`) */
fun moneyKindOf(state: SaleState, payGroup: String): MoneyKind = when {
  state == SaleState.CANCELLED -> MoneyKind.CANCELLED
  state == SaleState.ONLINE -> MoneyKind.ONLINE
  payGroup == "credit" -> MoneyKind.CREDIT
  payGroup == PayFlow.CASH -> MoneyKind.CASH
  else -> MoneyKind.CARD
}

/**
 *  رنگِ هر معنا — **تنها** جای رنگِ پولِ تاریخچه. ۶۵s: از همان منبعِ رنگِ بخش‌ها (`ui/theme/SectionColors.kt`، خانواده‌های پالت،
 *  روز و شب، عدد روی تهِ‌رنگ ≥ ۴٫۵:۱): نقد = نعنایی (همان «رسیدها»)، نسیه = کهربایی، کارت/غیرنقد = آسمانی، برگشت و لغو =
 *  مرجانیِ سرخِ قرض، قرضِ وصول‌شده = صورتی (جدا از بنفشِ کارتِ فروش — «فروش نیست»)؛ `fg` عددِ پررنگ و `bg` تهِ‌رنگِ همان.
 *  جفت‌های کنارِ هم در کوررنگی جدا (`SectionColors.MONEY_PAIRS`). ردیف، خطِ نتیجه، سرخطِ روز و نمودارِ کارتِ خلاصه چیزی جز این
 *  نمی‌خوانند.
 */
fun historyTone(c: PhoneV3Colors, kind: MoneyKind): V3Pair {
  fun of(h: PhoneV3Palette.Hue) = SectionColors.ofHue(h, c.source.isDark).let { V3Pair(fg = it.strong, bg = it.tint) }
  return when (kind) {
    MoneyKind.CASH -> of(PhoneV3Palette.Hue.MINT)
    MoneyKind.CREDIT -> of(PhoneV3Palette.Hue.AMBER)
    MoneyKind.CARD -> of(PhoneV3Palette.Hue.SKY)
    MoneyKind.RETURN, MoneyKind.CANCELLED -> of(PhoneV3Palette.Hue.CORAL)
    MoneyKind.DEBT_IN -> of(PhoneV3Palette.Hue.PINK)
    MoneyKind.NET -> V3Pair(fg = c.ink, bg = c.tile)
    MoneyKind.ONLINE -> V3Pair(fg = c.inkMuted, bg = c.tile)
  }
}

/** آیکنِ همراهِ هر رنگ — رنگ‌کورها و چاپِ سیاه‌وسفید هم بفهمند */
fun moneyIcon(kind: MoneyKind): ImageVector = when (kind) {
  MoneyKind.CASH -> Icons.Outlined.Payments
  MoneyKind.CREDIT -> Icons.AutoMirrored.Outlined.MenuBook
  MoneyKind.CARD -> Icons.Outlined.CreditCard
  MoneyKind.ONLINE -> Icons.Outlined.Language
  MoneyKind.CANCELLED -> Icons.Outlined.Block
  MoneyKind.RETURN -> Icons.AutoMirrored.Outlined.AssignmentReturn
  MoneyKind.NET -> Icons.Outlined.Calculate
  MoneyKind.DEBT_IN -> Icons.Outlined.SouthWest
}

/** برای آزمون: نوعِ پولی که این گره رنگش را نشان می‌دهد (رنگ + واژه، نه رنگِ تنها) */
val MoneyKindKey = SemanticsPropertyKey<MoneyKind>("MoneyKind")
var SemanticsPropertyReceiver.moneyKind by MoneyKindKey

/** کاشیِ آیکنِ ردیف: آیکنِ همان رنگ روی زمینهٔ نرمِ همان جفت */
@Composable
internal fun MoneyTile(kind: MoneyKind, size: Dp = 40.dp) {
  val tone = historyTone(PhoneV3.colors, kind)
  Box(
    Modifier.size(size).clip(RoundedCornerShape(Corner.CARD)).background(tone.bg),
    contentAlignment = Alignment.Center,
  ) {
    Icon(moneyIcon(kind), contentDescription = null, tint = tone.fg, modifier = Modifier.size(size * 0.53f))
  }
}

/** «● نسیه» — نقطه و واژه به همان رنگ؛ نقطه تزئینی است و صفحه‌خوان فقط واژه را می‌خواند */
@Composable
internal fun MoneyWord(kind: MoneyKind, word: String, modifier: Modifier = Modifier, style: TextStyle = V3Type.footnote) {
  val tone = historyTone(PhoneV3.colors, kind)
  Row(modifier.semantics(mergeDescendants = true) { moneyKind = kind }, verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(tone.fg))
    Spacer(Modifier.width(5.dp))
    Text(word, style = style, color = tone.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}
