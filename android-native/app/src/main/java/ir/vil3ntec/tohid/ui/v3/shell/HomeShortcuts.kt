package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.CallReceived
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.v3.v3Pressable

/** شعاع‌های خانوادهٔ کارت‌ها (THEME-V2): کارت ۲۰، ردیف/کاشی ۱۴ */
internal object HomeCardSpec {
  val card = 20.dp
  val tile = 14.dp
}

private class Shortcut(val label: String, val icon: ImageVector, val route: String, val accent: HomeAccent, val haptic: Boolean = false)

/**
 *  چهار میان‌بر در یک ردیف (HOME-V2 §۲٫۳) — بی کادرِ دورِ ردیف. هر کدام رنگِ همراهِ خودش
 *  (آیکنِ رنگی روی پس‌زمینهٔ نرمِ همان رنگ، کاشیِ ۱۴) و مستقیم فرمِ خودش (`ShellTargets`).
 */
@Composable
internal fun HomeShortcuts(onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
  val p = PhoneV3.colors
  val s = Shop.colors
  val items = listOf(
    Shortcut(StrHome.newSale, Icons.Outlined.AddShoppingCart, ShellTargets.NEW_SALE, HomeAccents.NEW_SALE, haptic = true),
    Shortcut(StrHome.receive, Icons.Outlined.CallReceived, ShellTargets.RECEIVE, HomeAccents.RECEIVE),
    Shortcut(StrHome.stockEntry, Icons.Outlined.MoveToInbox, ShellTargets.STOCK_ENTRY, HomeAccents.STOCK_ENTRY),
    Shortcut(StrHome.history, Icons.Outlined.History, ShellTargets.HISTORY, HomeAccents.HISTORY),
  )
  Row(modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
    items.forEach { sc ->
      Column(
        Modifier
          .weight(1f)
          .clip(RoundedCornerShape(HomeCardSpec.tile))
          .v3Pressable(label = sc.label, haptic = sc.haptic) { onOpen(sc.route) }
          .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Box(
          Modifier.size(52.dp).clip(RoundedCornerShape(HomeCardSpec.tile)).background(HomeAccents.soft(s, sc.accent)),
          contentAlignment = Alignment.Center,
        ) { Icon(sc.icon, contentDescription = null, tint = HomeAccents.color(s, sc.accent), modifier = Modifier.size(24.dp)) }
        Spacer(Modifier.height(8.dp))
        Text(
          sc.label,
          color = p.ink,
          style = v3Text(12, FontWeight.Medium, 17),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        )
      }
    }
  }
}
