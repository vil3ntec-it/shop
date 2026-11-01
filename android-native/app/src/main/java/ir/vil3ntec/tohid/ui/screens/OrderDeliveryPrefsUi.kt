package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrdersConfigDto
import ir.vil3ntec.tohid.core.orders.DeliveryPrefs
import ir.vil3ntec.tohid.data.repo.OrdersRepository
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.Space
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/*
 *  دستورِ تحویلِ سفارش در کارتِ «سفارش‌ها» — CHK-14 («با اسکناسِ … می‌پردازد»، پول خرد) و CHK-24
 *  (ویرایش تا پیش از ارسال). قاعده‌ها در هستهٔ مشترک (`core/orders/DeliveryPrefs.kt`)؛ سرور دوباره
 *  همه را می‌سنجد و «ذخیره شد» فقط پس از پاسخِ او. جدا از `Orders.kt` تا آن پرونده بزرگ‌تر نشود.
 */

/**
 *  زیرِ «نشانی» ِ سفارشِ ارسال: نشانی و پین، «پیش از رسیدن زنگ بزنید»، «تحویل به…»، اسکناسِ مشتری و
 *  پول خرد؛ دکمهٔ ویرایش فقط با پرچمِ سرور (`deliveryPrefs`)، دسترسیِ `orders.manage` و پیش از حرکتِ بسته.
 */
@Composable
internal fun OrderDeliveryPrefsSection(o: OrderDto, cfg: OrdersConfigDto, repo: OrdersRepository, onUpdated: (OrderDto) -> Unit) {
  if (o.fulfillment != "delivery") return
  val c = Shop.colors
  var editing by remember(o.id) { mutableStateOf(false) }
  val bits = listOfNotNull(
    o.address.text.takeIf { it.isNotEmpty() },
    o.address.landmark.takeIf { it.isNotEmpty() }?.let { "نشانه: $it" },
    if (o.address.lat != null && o.address.lng != null) "پین: ${o.address.lat}, ${o.address.lng}" else null,
  )
  Text(bits.joinToString(" · ").ifEmpty { "—" }, color = c.text)
  val d = o.delivery
  val lines = listOfNotNull(
    "پیش از رسیدن زنگ بزنید".takeIf { d.callBeforeArrival },
    d.leaveWith.takeIf { it.isNotBlank() }?.let { "تحویل به: $it" },
  )
  if (lines.isNotEmpty()) Text(lines.joinToString(" · "), color = c.text, style = MaterialTheme.typography.bodySmall)
  d.cashTenderMinor?.let {
    Text(DeliveryPrefs.tenderLine(it, o.totalMinor), color = c.text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
  }
  if (cfg.deliveryPrefs && cfg.permissions.manage && DeliveryPrefs.editable(o.status, o.fulfillment)) {
    TextButton(onClick = { editing = true }) { Text("ویرایشِ دستورِ تحویل") }
  }
  if (editing) DeliveryPrefsDialog(o, repo, onDismiss = { editing = false }, onSaved = { editing = false; onUpdated(it) })
}

/** فرمِ سفارشِ دستی: «مشتری با چه مبلغی می‌پردازد؟» — فقط با پرچمِ سرور و فقط برای ارسال */
@Composable
internal fun CashTenderField(show: Boolean, value: String, onChange: (String) -> Unit) {
  if (!show) return
  TohidTextField(
    value, { onChange(it.take(14)) }, label = "مشتری با چه مبلغی می‌پردازد؟ (افغانی، اختیاری)",
    supporting = "تا پیک پول خرد بیاورد؛ مثلاً ۱۰۰۰",
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
  )
}

@Composable
private fun DeliveryPrefsDialog(o: OrderDto, repo: OrdersRepository, onDismiss: () -> Unit, onSaved: (OrderDto) -> Unit) {
  val c = Shop.colors
  val scope = rememberCoroutineScope()
  var call by remember { mutableStateOf(o.delivery.callBeforeArrival) }
  var leaveWith by remember { mutableStateOf(o.delivery.leaveWith) }
  var landmark by remember { mutableStateOf(o.address.landmark) }
  var tender by remember { mutableStateOf(o.delivery.cashTenderMinor?.let { (it / 100).toString() } ?: "") }
  var error by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }

  fun save() {
    val t = when (val p = DeliveryPrefs.parseTender(tender)) {
      is DeliveryPrefs.Tender.Error -> { error = p.message; return }
      is DeliveryPrefs.Tender.Ok -> p.minor
      DeliveryPrefs.Tender.None -> null
    }
    DeliveryPrefs.tenderError(t, o.totalMinor)?.let { error = it; return }
    val body = buildJsonObject {
      put("delivery", buildJsonObject {
        put("callBeforeArrival", JsonPrimitive(call))
        put("leaveWith", JsonPrimitive(leaveWith.trim()))
        put("cashTenderMinor", t?.let { JsonPrimitive(it) } ?: JsonNull)
      })
      put("address", buildJsonObject { put("landmark", JsonPrimitive(landmark.trim())) })
    }
    busy = true
    error = null
    scope.launch {
      repo.editDelivery(o.id, o.version, body)
        .onSuccess { onSaved(it) }
        //  ۴۰۹ (نسخهٔ کهنه، بسته رفته) پیامِ روشنِ سرور را دارد؛ هیچ چیز بی‌صدا رونویسی نمی‌شود
        .onFailure { error = it.userMessage }
      busy = false
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = c.surfaceSolid,
    title = { Text("دستورِ تحویل — سفارش #${o.orderNo}", color = c.text) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        Row(
          Modifier.fillMaxWidth().heightIn(min = Space.xxl + Space.md).clickable { call = !call },
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("پیش از رسیدن زنگ بزنید", Modifier.weight(1f), color = c.text)
          Switch(checked = call, onCheckedChange = { call = it })
        }
        TohidTextField(leaveWith, { leaveWith = it.take(80) }, label = "تحویل به (عضوِ خانواده، دربان…)")
        TohidTextField(landmark, { landmark = it.take(200) }, label = "نشانه")
        TohidTextField(
          tender, { tender = it.take(14) }, label = "مشتری با چه مبلغی می‌پردازد؟ (افغانی)",
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        if (error != null) {
          Spacer(Modifier.height(Space.xs))
          Text(error ?: "", color = c.danger, style = MaterialTheme.typography.bodySmall)
        }
      }
    },
    confirmButton = { TextButton(onClick = { save() }, enabled = !busy) { Text(if (busy) "در حالِ ذخیره…" else "ذخیره") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
  )
}
