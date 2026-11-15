package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrDeskOff
import ir.vil3ntec.tohid.core.i18n.StrNav
import ir.vil3ntec.tohid.core.i18n.StrOrderFlow
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.v3.account.AccountPaths
import ir.vil3ntec.tohid.ui.v3.shell.ShellTargets

/**
 *  بخشِ **ساخته‌شده** که برای این دکان خاموش است (ردیفِ `DeskMenu.State.OFF`، شناسهٔ `off:…`) — برگهٔ کوتاهِ «این
 *  بخش چه می‌کند، خاموش است، چطور روشن می‌شود» به‌جای «در دست ساخت» ِ پیشین (صاحب‌کار ۱۴۰۵/۰۸/۲۳).
 *
 *  - روشن کردن کارِ پنلِ سرور است (برنامه کلیدی برای آن ندارد): «فروشگاه» ⇐ «تنظیمات و پرچم‌ها» ⇐ «پرچم‌ها» ⇐ دامنهٔ
 *    همین دکان ⇐ روشن کردنِ همان بخش. نامِ درونیِ پرچم فقط برای صاحبِ دکان، نوشتهٔ کوچک؛ هیچ نشانی و نامِ میزبانی نه.
 *  - صاحبِ دکان: «پیام به پشتیبانی برای روشن کردن» (گفت‌وگوی پشتیبانی با پیش‌نویسِ آماده)؛ دیگران: «برای روشن کردن با
 *    پشتیبانی تماس بگیرید» و دکمهٔ همان «پشتیبانی». سفارش‌ها برای صاحب/مدیر «چرا سفارشِ آنلاین نمی‌رسد» را هم دارد
 *    (`ShellTargets.ORDERS_SETUP`، همان کاشیِ خاموشِ خانه).
 *
 *  `info` خالص است (`DeskMenuTest`)؛ ⛔ این فایل بی Context است (راهِ آیفون)، نقش از منو می‌آید.
 */
object DeskOff {
  private class Section(
    val title: () -> String,
    val what: () -> String,
    /** نامِ درونیِ پرچم در پنل — فقط صاحبِ دکان می‌بیند */
    val flag: String,
    /** نامِ چیزی که در پنل روشن می‌شود (آماده‌سازی و تحویل ⇐ خودِ «سفارش‌های آنلاین») */
    val switchName: () -> String,
    val partOfOrders: Boolean = false,
  )

  private val SECTIONS: Map<String, Section> = linkedMapOf(
    "off:orders" to Section({ StrNav.onlineOrders }, { StrNav.ordersSub }, "shop.orders", { StrNav.onlineOrders }),
    "off:fulfil" to Section({ StrNav.fulfilment }, { StrDeskOff.fulfilWhat }, "shop.orders", { StrNav.onlineOrders }, partOfOrders = true),
    "off:storefront" to Section({ StrNav.storefrontMarket }, { StrNav.storefrontSub }, "shop.storefront", { StrNav.storefrontMarket }),
    "off:delivery" to Section({ StrNav.deliveryDeskCourier }, { StrNav.deliverySub }, "shop.delivery", { StrNav.deliveryDeskCourier }),
  )

  /** شناسه‌های ردیفِ خاموشی که برگه دارند */
  val ids: Set<String> get() = SECTIONS.keys

  data class Action(val label: String, val target: String, val primary: Boolean)

  data class Info(
    val title: String,
    val what: String,
    val status: String,
    val note: String?,
    val steps: List<String>,
    /** فقط صاحبِ دکان */
    val flagKey: String?,
    /** کسی که پیامِ آماده ندارد: «برای روشن کردن با پشتیبانی تماس بگیرید» */
    val contact: String?,
    val actions: List<Action>,
  )

  /**
   *  @param owner صاحبِ دکان (`ShopRole.OWNER`) — نامِ پرچم و پیامِ آماده به پشتیبانی
   *  @param canAdmin صاحب/مدیر — «چرا سفارشِ آنلاین نمی‌رسد» ِ تنظیمات
   */
  fun info(id: String, owner: Boolean, canAdmin: Boolean): Info? {
    val s = SECTIONS[id] ?: return null
    val name = s.switchName()
    val orders = s.flag == "shop.orders"
    return Info(
      title = s.title(),
      what = s.what(),
      status = if (owner || canAdmin) StrDeskOff.built else StrDeskOff.builtStaff,
      note = if (s.partOfOrders) StrDeskOff.partOf(name) else null,
      steps = listOf(StrDeskOff.stepPanel, StrDeskOff.stepTab, StrDeskOff.stepScope, StrDeskOff.stepTurnOn(name)),
      flagKey = if (owner) StrDeskOff.flagKey(s.flag) else null,
      contact = if (owner) null else StrDeskOff.callSupport,
      actions = buildList {
        if (owner) add(Action(StrDeskOff.askSupport, AccountPaths.support(StrDeskOff.supportDraft(name)), primary = true))
        else add(Action(Str.support, AccountPaths.SUPPORT, primary = true))
        if (orders && (owner || canAdmin)) add(Action(StrOrderFlow.arrivalTitle, ShellTargets.ORDERS_SETUP, primary = false))
      },
    )
  }
}

/** نشانِ «خاموش» — متن + زمینهٔ کم‌رنگ، نه رنگِ تنها (همان شکلِ نشانِ «در دست ساخت») */
@Composable
internal fun DeskOffBadge(modifier: Modifier = Modifier) {
  Text(
    StrDeskOff.badge,
    style = MaterialTheme.typography.labelSmall,
    color = Shop.colors.muted,
    maxLines = 1,
    modifier = modifier.clip(RoundedCornerShape(10.dp)).background(Shop.colors.muted.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 1.dp),
  )
}

/** برگهٔ بخشِ خاموش؛ `onOpen` همان راهِ منو (`nav.openFromRail`) */
@Composable
internal fun DeskOffSheet(info: DeskOff.Info, onOpen: (String) -> Unit, onDismiss: () -> Unit) {
  TohidBottomSheet(title = info.title, onDismiss = onDismiss) {
    SheetBody {
      DeskOffBadge()
      Text(info.status, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
      Text(StrDeskOff.whatTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Shop.colors.text, modifier = Modifier.padding(top = 4.dp))
      Text(info.what, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.muted)
      info.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted) }
      Text(StrDeskOff.howTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Shop.colors.text, modifier = Modifier.padding(top = 4.dp))
      info.steps.forEach { step ->
        Row(Modifier.fillMaxWidth()) {
          Text("•", style = MaterialTheme.typography.bodyMedium, color = Shop.colors.muted)
          Spacer(Modifier.width(8.dp))
          Text(step, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text, modifier = Modifier.weight(1f))
        }
      }
      info.flagKey?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted) }
      info.contact?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text, modifier = Modifier.padding(top = 4.dp)) }
      info.actions.forEach { a ->
        val go = { onDismiss(); onOpen(a.target) }
        if (a.primary) TohidButton(a.label, onClick = go, modifier = Modifier.fillMaxWidth())
        else TohidSecondaryButton(a.label, onClick = go, modifier = Modifier.fillMaxWidth())
      }
    }
  }
}
