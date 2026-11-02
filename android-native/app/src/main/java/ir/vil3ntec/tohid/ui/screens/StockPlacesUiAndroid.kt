package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
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
import ir.vil3ntec.tohid.core.i18n.StrPlaces
import ir.vil3ntec.tohid.data.Repack
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.StockPlaces
import ir.vil3ntec.tohid.data.StockPlacesPref
import ir.vil3ntec.tohid.todayIso
import kotlinx.coroutines.launch

/**
 *  مرزِ پلتفرمِ INV-20/21: کلیدهای همین دستگاه (`StockPlacesPref`)، نامِ کاربر، ساعت و نوشتن روی دفترِ همین لحظهٔ دیسک
 *  (`StockPlacesPref.record`). هر پنجره یک کلید دارد که پس از هر ثبتِ موفق تازه می‌شود ⇒ دو ضربه یک ثبت.
 */

/** بخشِ «محل‌ها و بسته‌بندیِ دوباره» با خواندن و نوشتنِ `StockPlacesPref` — تا `SettingsLegacy` یک خط بخواهد */
@Composable
fun StockPlacesSettingsWired(context: android.content.Context) {
  var places by remember { mutableStateOf(StockPlacesPref.places(context)) }
  var repack by remember { mutableStateOf(StockPlacesPref.repack(context)) }
  StockPlacesSettings(
    places = places,
    repack = repack,
    onPlaces = { places = it; StockPlacesPref.setPlaces(context, it) },
    onRepack = { repack = it; StockPlacesPref.setRepack(context, it) },
  )
}

/**
 *  دکمه‌ها و پنجره‌های انبار. هر دو تنظیم خاموش (پیش‌فرض) یا صندوق‌دار ⇒ هیچ (همان انبارِ امروز). موتورِ تازهٔ موجودی
 *  روشن ⇒ انتقال همان «دفترِ موجودی» است (با سری و بها) و این‌جا فقط بسته‌بندی می‌آید.
 */
@Composable
fun StockPlacesEntry(context: android.content.Context, store: ShopStore, d: ShopData, stockOn: Boolean, toast: (String) -> Unit) {
  val placesOn = remember { StockPlacesPref.places(context) } && !stockOn
  val repackOn = remember { StockPlacesPref.repack(context) }
  if (!placesOn && !repackOn) return
  if (remember { ShopRole.current(context) } == ShopRole.STAFF) return
  val scope = rememberCoroutineScope()
  val user = remember(context) { runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrDefault("") }
  var openPlaces by remember { mutableStateOf(false) }
  var openRepack by remember { mutableStateOf(false) }
  var key by remember { mutableStateOf(newId()) }

  fun write(has: (ShopData, String) -> Boolean, done: String, build: (ShopData, String) -> StockPlaces.Result) {
    val k = key
    scope.launch {
      when (val r = StockPlacesPref.record(store, k, has, build)) {
        is StockPlacesPref.Saved.Ok -> { toast(if (r.again) StrPlaces.again else done); if (key == k) key = newId() }
        is StockPlacesPref.Saved.Bad -> toast(r.message)
        StockPlacesPref.Saved.NotSaved -> toast(StrPlaces.notSaved)
      }
    }
  }

  if (placesOn) {
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = { openPlaces = true }, modifier = Modifier.fillMaxWidth()) {
      Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(6.dp))
      Text(StrPlaces.openPlaces)
    }
  }
  if (repackOn) {
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = { openRepack = true }, modifier = Modifier.fillMaxWidth()) {
      Icon(Icons.Filled.Inventory2, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(6.dp))
      Text(StrPlaces.openRepack)
    }
  }

  if (openPlaces) {
    StockPlacesDialog(
      d,
      onDismiss = { openPlaces = false },
      onAdd = { name -> write(StockPlaces::hasPlace, StrPlaces.placeAdded) { cur, k -> StockPlaces.addPlace(cur, k, name, user, todayIso(), System.currentTimeMillis()) } },
      onArchive = { name -> write(StockPlaces::hasPlace, StrPlaces.placeArchived) { cur, k -> StockPlaces.archivePlace(cur, k, name, user, todayIso(), System.currentTimeMillis()) } },
      onTransfer = { pid, from, to, q, reason ->
        write(StockPlaces::has, StrPlaces.moved) { cur, k -> StockPlaces.transfer(cur, k, pid, from, to, q, reason, user, todayIso(), System.currentTimeMillis(), ::newId) }
      },
    )
  }
  if (openRepack) {
    RepackDialog(
      d,
      places = if (placesOn) remember(d) { StockPlaces.places(d) } else null,
      onDismiss = { openRepack = false },
      onBreak = { from, packs, to, per, loc, note ->
        write(Repack::has, StrPlaces.savedRepack) { cur, k -> Repack.breakPack(cur, k, from, packs, to, per, loc, note, user, todayIso(), System.currentTimeMillis(), ::newId) }
      },
      onBuild = { bundle, n, parts, loc, note ->
        write(Repack::has, StrPlaces.savedRepack) { cur, k -> Repack.build(cur, k, bundle, n, parts, loc, note, user, todayIso(), System.currentTimeMillis(), ::newId) }
      },
    )
  }
}
