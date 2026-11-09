package ir.vil3ntec.tohid.ui.v3.online.storefront

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.LocaleText
import ir.vil3ntec.tohid.core.i18n.OnlineFmt
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.storefront.AreaRequests
import ir.vil3ntec.tohid.core.text.ContactMask
import ir.vil3ntec.tohid.data.repo.AreaRequestsRepository
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.ui.screens.Haptics
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.LocalV3Toasts
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Skeleton
import ir.vil3ntec.tohid.ui.v3.V3TextButton
import ir.vil3ntec.tohid.ui.v3.V3Type
import kotlinx.coroutines.launch

/**
 *  CHK-10/S34 — «وقتی ارسال به این‌جا رسید خبرم کن» در برگهٔ «ارسال و زمان» (همان فهرستِ `license/area-requests.js`
 *  ِ وبِ مدیریت): فقط وقتی `config.notifyArea` (صاحب/مدیر + `shop.storefront.notify_area`). شمار، کسانی که ناحیهٔ
 *  امروز محلشان را پوشانده (زنگ با شماره‌گیرِ دستگاه، وگرنه کپیِ شماره؛ «خبر دادم» ⇒ ردیف پاک) و تقاضا به ازای
 *  خانهٔ ۳۰۰ متری بی شماره. ⛔ سرور پیامی نمی‌فرستد (D-09).
 */
@Composable
internal fun AreaRequestsSection() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val toasts = LocalV3Toasts.current
  val uri = LocalUriHandler.current
  val clipboard = LocalClipboardManager.current
  val repo = remember(context) { AreaRequestsRepository(Backend.api(context)) }
  var view by remember { mutableStateOf<AreaRequests.View?>(null) }
  var error by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf<String?>(null) }
  suspend fun load() {
    repo.load().onSuccess { view = it; error = null }.onFailure { error = it.userMessage }
  }
  LaunchedEffect(Unit) { load() }

  V3Section(StrOnline.arTitle, footer = StrOnline.arHelp) {
    val v = view
    when {
      v == null && error == null -> Column(Modifier.padding(12.dp)) { V3Skeleton(rows = 2) }
      v == null -> V3ListRow(
        StrOnline.arLoadFailed, subtitle = error, icon = Icons.Outlined.Refresh, chevron = false,
        onClick = { scope.launch { load() } },
      )
      else -> {
        V3ListRow(StrOnline.arSummary(v.total, v.covered), tile = false, chevron = false)
        V3Divider()
        if (v.contacts.isEmpty()) V3ListRow(StrOnline.arNoContacts, tile = false, chevron = false)
        val now = System.currentTimeMillis()
        v.contacts.forEachIndexed { i, c ->
          if (i > 0) V3Divider()
          V3ListRow(
            title = LocaleText.phoneDisplay(c.phone),
            subtitle = StrOnline.arContactSub(c.zone, OnlineFmt.ago(c.requestedAt, now)),
            chevron = false,
            trailing = {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                //  CRM-15: صاحب «شمارهٔ مشتریان» را برای این کاربر بسته ⇒ سرور شماره را پوشیده می‌دهد؛ «زنگ» بی‌فایده است
                if (!ContactMask.isMasked(c.phone)) V3TextButton(StrOnline.call, {
                  runCatching { uri.openUri("tel:" + c.phone) }.onFailure {
                    clipboard.setText(AnnotatedString(c.phone))
                    toasts?.show(StrOnline.numberCopied)
                  }
                })
                V3Button(StrOnline.arNotified, {
                  if (busy == null) {
                    busy = c.id
                    scope.launch {
                      repo.notified(c.id)
                        .onSuccess { view = view?.let { AreaRequests.without(it, c.id) }; Haptics.confirm(context); toasts?.show(StrOnline.arNotifiedDone) }
                        .onFailure { toasts?.show(it.userMessage) }
                      busy = null
                      //  دیگری زودتر خبر داد یا ناحیه برداشته شد (۴۰۴/۴۰۹) ⇒ فهرستِ تازه، هرگز ردیفِ کهنه
                      load()
                    }
                  }
                }, kind = V3ButtonKind.SECONDARY, loading = busy == c.id, height = 36.dp)
              }
            },
          )
        }
        if (v.cells.isNotEmpty()) {
          V3Divider()
          Text(StrOnline.arCellsTitle, style = V3Type.subhead, color = PhoneV3.colors.inkMuted, modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp))
          v.cells.forEach { cell ->
            V3ListRow(
              StrOnline.arCell(cell.count, AreaRequests.coord(cell.lat), AreaRequests.coord(cell.lng)),
              subtitle = cell.coveredBy?.let(StrOnline::arCovered) ?: StrOnline.arOutside,
              icon = Icons.Outlined.Place, tile = false, chevron = false,
            )
          }
        }
      }
    }
  }
}
