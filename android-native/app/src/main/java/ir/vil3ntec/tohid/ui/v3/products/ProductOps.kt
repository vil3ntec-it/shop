package ir.vil3ntec.tohid.ui.v3.products

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.i18n.StrProducts
import ir.vil3ntec.tohid.data.PhotoStore
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.sync.PhotoSync
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.screens.newId
import ir.vil3ntec.tohid.ui.v3.V3Toasts
import ir.vil3ntec.tohid.data.ShopData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 *  کارهای کالا که چند صفحه دارند (فهرست، جزئیات، برگهٔ دسته) — یک‌جا، همان
 *  `apply`ِ صفحه‌های پیشین: نتیجهٔ `WarehouseEngine` یا نوشته و پیامِ «شد»، یا
 *  دلیلِ رد. همیشه روی **آخرین** دفتر (`store.data.value`) کار می‌کند، نه روی
 *  نسخه‌ای که هنگامِ کشیدنِ صفحه گرفته شده — و همان دفتر پایهٔ صریحِ ذخیره است ([commit]).
 */
@Stable
internal class ProductOps(
  private val store: ShopStore,
  private val toasts: V3Toasts,
  private val scope: CoroutineScope,
  private val context: Context,
) {
  /** [build] روی آخرین دفتر؛ همان دفتر پایهٔ ذخیره است (`ShopStore.save(base, next)`) */
  private fun commit(build: (ShopData) -> WarehouseEngine.Result, done: String?, after: (WarehouseEngine.Result.Ok) -> Unit = {}): Boolean {
    val base = store.data.value
    return when (val result = build(base)) {
      is WarehouseEngine.Result.Failed -> { toasts.show(result.message); false }
      is WarehouseEngine.Result.Ok -> {
        persist(scope, store, base, result.data)
        if (done != null) toasts.show(done)
        after(result)
        true
      }
    }
  }

  /** حذفِ یک کالا (پس از برگهٔ تأیید با `deleteWarning`) — عکسش هم از این دستگاه و همگام‌سازی می‌رود */
  fun delete(id: String, after: () -> Unit = {}) {
    val now = System.currentTimeMillis()
    commit({ d -> WarehouseEngine.deleteProduct(d, id, todayIso(), now, ::newId) }, StrProducts.productDeleted) {
      PhotoStore.delete(context, id)
      PhotoSync.removed(context, id)
      after()
    }
  }

  fun setActive(ids: Set<String>, active: Boolean, after: () -> Unit = {}) {
    val n = ids.size
    commit(
      { d -> WarehouseEngine.bulkUpdate(d, ids, todayIso(), System.currentTimeMillis(), ::newId) { it.copy(active = active) } },
      if (active) StrProducts.activated(n) else StrProducts.deactivated(n),
    ) { after() }
  }

  fun setCategory(ids: Set<String>, category: String, after: () -> Unit = {}) {
    commit(
      { d -> WarehouseEngine.bulkUpdate(d, ids, todayIso(), System.currentTimeMillis(), ::newId) { it.copy(category = category.trim()) } },
      StrProducts.categoryChanged(ids.size),
    ) { after() }
  }

  fun deleteMany(ids: Set<String>, after: () -> Unit = {}) {
    commit(
      { d -> WarehouseEngine.bulkDelete(d, ids, todayIso(), System.currentTimeMillis(), ::newId) },
      StrProducts.deleted(ids.size),
    ) { after() }
  }

  fun addCategory(name: String, after: (String) -> Unit = {}) {
    commit({ d -> WarehouseEngine.addCategory(d, name) }, StrProducts.categoryAdded) { after(it.id) }
  }

  fun removeCategory(name: String) {
    commit({ d -> WarehouseEngine.removeCategory(d, name) }, StrProducts.categoryDeleted)
  }
}

/**
 *  نوشتنِ دفتر که با بسته شدنِ صفحه نیمه‌کاره نمی‌ماند: فرم پس از «ذخیره» فوراً
 *  بسته می‌شود و `scope`ِ همان صفحه لغو می‌شود؛ نوشتن همان لحظه آغاز و بی‌لغو تمام می‌شود.
 *  [base] دفتری است که [next] از آن ساخته شد — پایهٔ صریحِ `ShopStore.save`.
 */
internal fun persist(scope: CoroutineScope, store: ShopStore, base: ShopData, next: ShopData) {
  scope.launch(start = CoroutineStart.UNDISPATCHED) { withContext(NonCancellable) { store.save(base, next) } }
}

@Composable
internal fun rememberProductOps(store: ShopStore, toasts: V3Toasts): ProductOps {
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  return remember(store, toasts) { ProductOps(store, toasts, scope, context) }
}
