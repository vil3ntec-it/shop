package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrCustomerCard
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.DebtorEngine
import ir.vil3ntec.tohid.data.ReportBridge
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

// i18n: سه‌زبانه

/*
 *  POS-31 / S18 گامِ ۴ — «مشتریِ تازه» در گزینشِ مشتریِ صندوقِ کامپیوترِ لمسی (`DebtorPicker(onAdd)` ⇐ `DeskSale`): همان
 *  `DebtorEngine.add` و `ShopStore.save` ِ صفحهٔ «مشتریان» (نقش در `roleRefused`) با ساعتِ مشترکِ `ReportBridge`.
 */

/**
 *  ثبتِ [name] روی دفترِ [d] تا پایانِ نوشتن: نادرست ⇒ پیامِ موتور (و هیچ نوشتن)؛ نوشته نشد (نقش، قفلِ نرم، دیسک) ⇒
 *  «ذخیره نشد»؛ نوشته شد ⇒ [onSaved] با شناسهٔ تازه و null.
 */
internal suspend fun tillNewCustomer(d: ShopData, name: String, store: ShopStore, onSaved: (String) -> Unit): String? =
  when (val r = DebtorEngine.add(d, DebtorEngine.DebtorDraft(name = name), ReportBridge.nowMs(), ::newId)) {
    is DebtorEngine.Result.Failed -> r.message
    is DebtorEngine.Result.Ok -> if (store.save(d, r.data)) { onSaved(r.id); null } else StrCustomerCard.notSaved
  }

/**
 *  دکمهٔ «مشتریِ تازه: «نام»» زیرِ کادرِ گزینش — فقط با [onAdd] و نامی که در فهرستِ [shown] نیست؛ خطای ثبت زیرش.
 *  ⛔ دو زدنِ تند یک مشتری می‌سازد: `adding` از زدن تا پایانِ [onAdd] (دکمه خاموش، زدنِ دوم هیچ)؛ ناموفق، استثنا یا
 *  بستنِ برگه ⇒ در `finally` آزاد؛ موفق ⇒ بسته می‌ماند تا برگه بسته شود (همان نام دوباره ساخته نمی‌شود). پیش‌تر هر زدن
 *  یک `DebtorEngine.add` روی همان دفترِ کهنه بود و `ShopStore.save` هر دو را می‌نشاند ⇒ دو مشتریِ هم‌نام.
 */
@Composable
internal fun TillNewCustomerButton(search: String, shown: List<Debtor>, onAdd: (suspend (name: String) -> String?)?) {
  var problem by remember { mutableStateOf<String?>(null) }
  val name = search.trim().takeIf { onAdd != null && it.isNotEmpty() && shown.none { s -> s.name.trim().equals(it, ignoreCase = true) } }
  var adding by remember(name) { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  if (name != null && onAdd != null) {
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = {
      if (!adding) {
        adding = true
        scope.launch {
          var saved = false
          try { problem = onAdd(name); saved = problem == null } finally { if (!saved) adding = false }
        }
      }
    }, enabled = !adding, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
      Text(StrSale.newCustomerNamed(name), maxLines = 1)
    }
  }
  problem?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Shop.colors.danger) }
}
