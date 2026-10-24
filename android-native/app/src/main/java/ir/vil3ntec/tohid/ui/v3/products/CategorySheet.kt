package ir.vil3ntec.tohid.ui.v3.products

import ir.vil3ntec.tohid.ui.v3.v3AnimateItem
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrProducts
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.ui.screens.isDark
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3ConfirmSheet
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3SheetCloser
import ir.vil3ntec.tohid.ui.v3.V3SwipeAction
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Toasts
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3Stagger

/** کارِ برگهٔ دسته‌ها: انتخابِ یک دسته (فرم، فروشِ سریع)، مدیریت، یا دستهٔ گروهی */
internal sealed interface CategoryUse {
  /** `products/categories?pick=1&selected=` — انتخاب ⇐ نام گذاشته و برگه بسته می‌شود */
  class Pick(val selected: String?, val onPicked: (String) -> Unit) : CategoryUse
  /** `products/categories` — افزودن و حذف */
  data object Manage : CategoryUse
  /** `products/bulk/category?ids=` — همه را به یک دسته می‌برد */
  class Bulk(val ids: Set<String>, val onDone: () -> Unit) : CategoryUse
}

/**
 *  برگهٔ دسته‌ها: کادرِ «+ دستهٔ تازه» بالا (`WarehouseEngine.addCategory`)، فهرستِ
 *  دسته‌ها با تیکِ دستهٔ فعلی، و کشیدنِ ردیف ⇐ حذف با برگهٔ تأیید (کالاها حذف
 *  نمی‌شوند، `removeCategory`). مشترکِ فرمِ کالا، فروشِ سریع (G5، از راهِ مسیر) و
 *  تغییرِ گروهیِ دسته.
 */
@Composable
internal fun CategorySheet(store: ShopStore, data: ShopData, toasts: V3Toasts, use: CategoryUse, onDismiss: () -> Unit) {
  val c = PhoneV3.colors
  val ops = rememberProductOps(store, toasts)
  var name by remember { mutableStateOf("") }
  var confirm by remember { mutableStateOf<String?>(null) }
  val stagger = rememberV3Stagger()
  val title = when (use) {
    is CategoryUse.Pick -> StrProducts.pickCategory
    CategoryUse.Manage -> StrProducts.categoriesTitle
    is CategoryUse.Bulk -> StrProducts.bulkCategoryTitle(use.ids.size)
  }

  fun choose(closer: V3SheetCloser, category: String) {
    when (use) {
      //  نتیجه پیش از بستن گذاشته می‌شود: بستنِ برگه `onDismiss` (برگشتِ مسیر) را صدا می‌زند
      is CategoryUse.Pick -> { use.onPicked(category); closer.close() }
      is CategoryUse.Bulk -> ops.setCategory(use.ids, category) { use.onDone(); closer.close() }
      CategoryUse.Manage -> Unit
    }
  }

  V3Sheet(onDismiss = onDismiss, title = title) { closer ->
    fun add() {
      val v = name.trim()
      if (v.isEmpty()) return
      ops.addCategory(v) { added -> name = ""; choose(closer, added) }
    }
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      V3TextField(name, { name = it }, StrProducts.newCategory, Modifier.weight(1f), onImeAction = ::add)
      V3Button(StrKit.add, ::add, enabled = name.isNotBlank(), kind = V3ButtonKind.SECONDARY)
    }
    Spacer(Modifier.height(14.dp))
    val list = data.productCategories
    if (list.isEmpty()) {
      Text(StrProducts.noCategories, style = V3Type.callout, color = c.inkMuted, modifier = Modifier.fillMaxWidth())
    } else {
      LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp).clip(RoundedCornerShape(16.dp)).background(if (c.isDark) c.tile else c.bg)) {
        itemsIndexed(list, key = { _, s -> s }) { i, cat ->
          //  ورودِ پلکانی و جابه‌جاییِ نرم وقتی دسته‌ای اضافه یا حذف می‌شود
          Column(Modifier.v3AnimateItem(this).v3Stagger(stagger, cat, i)) {
          if (i > 0) V3Divider(start = 64.dp)
          val current = (use as? CategoryUse.Pick)?.selected == cat
          V3ListRow(
            title = cat,
            icon = Icons.Outlined.Category,
            minHeight = 52.dp,
            chevron = false,
            trailing = if (current) ({ Icon(Icons.Outlined.Check, contentDescription = StrKit.selected, tint = PhoneV3Roles.brandText(c), modifier = Modifier.size(20.dp)) }) else null,
            endActions = listOf(V3SwipeAction(Icons.Outlined.DeleteOutline, StrProducts.delete, V3ButtonKind.DANGER) { confirm = cat }),
            onClick = if (use is CategoryUse.Manage) null else ({ choose(closer, cat) }),
          )
          }
        }
      }
    }
  }

  confirm?.let { cat ->
    V3ConfirmSheet(
      title = StrProducts.deleteCategoryTitle,
      message = StrProducts.deleteCategoryBody(cat),
      confirm = StrProducts.delete,
      icon = Icons.Outlined.DeleteOutline,
      onConfirm = { ops.removeCategory(cat) },
      onDismiss = { confirm = null },
    )
  }
}
