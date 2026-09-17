package ir.vil3ntec.tohid.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.print.PrintJob
import ir.vil3ntec.tohid.print.ThermalPrinter
import ir.vil3ntec.tohid.ui.theme.Radius
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/* ============================ تسویه ============================ */

/**
 *  پنجرهٔ تسویه — تخفیف، نقدی/نسیه، مبلغِ پرداختی.
 *
 *  عددها همان‌جا و همان لحظه با `SalesEngine` حساب می‌شوند، نه با فرمولِ
 *  جداگانه‌ای در رابط کاربری. پس چیزی که اینجا دیده می‌شود دقیقاً همان
 *  چیزی است که ثبت خواهد شد.
 */
@Composable
fun CheckoutDialog(
  d: ShopData,
  cart: List<SalesEngine.CartLine>,
  presetDebtorId: String?,
  onDismiss: () -> Unit,
  onConfirm: (SalesEngine.Checkout) -> Unit,
) {
  // مثل نسخهٔ وب: اگر از سبد قرض‌داری انتخاب شده باشد، پنجره روی «نسیه»
  // باز می‌شود؛ وگرنه «نقدی». تخفیف هم پیش‌فرض درصدی است.
  val preset = presetDebtorId?.takeIf { id -> d.debtors.any { it.id == id } }

  var discountType by remember { mutableStateOf(SalesEngine.DiscountType.PERCENT) }
  var payment by remember { mutableStateOf(if (preset != null) SalesEngine.Payment.CREDIT else SalesEngine.Payment.CASH) }
  var discountText by remember { mutableStateOf("0") }
  var debtorId by remember { mutableStateOf(preset) }
  var paidText by remember { mutableStateOf(if (preset != null) "0" else "") }
  var debtorMenu by remember { mutableStateOf(false) }

  val checkout = SalesEngine.Checkout(
    discountType = discountType,
    discountValue = discountText.toDoubleOrNull() ?: 0.0,
    payment = payment,
    paidAmount = paidText.toDoubleOrNull() ?: 0.0,
    debtorId = debtorId,
  )
  val totals = SalesEngine.totals(d, cart, checkout)

  // در فروشِ نقدی مبلغِ پرداختی همیشه کلِ مبلغِ نهایی است
  val paid = if (payment == SalesEngine.Payment.CASH) totals.finalTotal
  else minOf(paidText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0, totals.finalTotal)
  val remaining = (totals.finalTotal - paid).coerceAtLeast(0.0)

  Dialog(onDismissRequest = onDismiss) {
    DialogEntry {
    Surface(
      color = Shop.colors.surfaceSolid,
      shape = RoundedCornerShape(Radius.lg),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState())) {
        Text("تسویه فروش", style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
        Spacer(Modifier.height(14.dp))

        Text("نوع تخفیف", style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
        Spacer(Modifier.height(6.dp))
        Segmented(
          options = listOf("درصدی" to SalesEngine.DiscountType.PERCENT, "مبلغی" to SalesEngine.DiscountType.AMOUNT),
          selected = discountType,
          onSelect = { discountType = it },
        )

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
          value = discountText,
          onValueChange = { discountText = it.filter { c -> c.isDigit() || c == '.' } },
          label = { Text(if (discountType == SalesEngine.DiscountType.PERCENT) "تخفیف (٪)" else "تخفیف (افغانی)") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(14.dp))
        Text("نوع پرداخت", style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
        Spacer(Modifier.height(6.dp))
        Segmented(
          options = listOf("نقدی" to SalesEngine.Payment.CASH, "نسیه" to SalesEngine.Payment.CREDIT),
          selected = payment,
          onSelect = { payment = it },
        )

        /*
         *  مشتری — در نسیه اجباری، در نقدی اختیاری.
         *
         *  تا دیروز فروشِ نقدی به هیچ‌کس وصل نمی‌شد، پس «این مشتری امسال
         *  چقدر خرید کرده» فقط برای کسانی جواب داشت که نسیه برده بودند.
         *  حسابِ بدهی از تراکنش‌ها می‌آید نه از این انتخاب، پس مشتریِ
         *  فاکتورِ نقدی هیچ بدهی‌ای نمی‌سازد — فقط نامش روی فاکتور
         *  می‌نشیند.
         *
         *  فهرست هم دیگر یک `DropdownMenu`ِ بی‌انتها نیست؛ با جستجو.
         */
        Spacer(Modifier.height(12.dp))
        Text(
          if (payment == SalesEngine.Payment.CREDIT) "قرض‌دار" else "مشتری (اختیاری)",
          style = MaterialTheme.typography.labelMedium,
          color = Shop.colors.muted,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            onClick = { if (d.debtors.isNotEmpty()) debtorMenu = true },
            modifier = Modifier.weight(1f),
          ) {
            Text(
              debtorId?.let { id -> d.debtors.find { it.id == id }?.name }
                ?: if (d.debtors.isEmpty()) "— هنوز کسی ثبت نشده —" else "انتخاب",
            )
          }
          if (debtorId != null) {
            TextButton(onClick = { debtorId = null }) { Text("برداشتن") }
          }
        }
        if (debtorMenu) {
          SearchablePicker(
            title = if (payment == SalesEngine.Payment.CREDIT) "انتخاب قرض‌دار" else "انتخاب مشتری",
            options = d.debtors,
            idOf = { it.id },
            nameOf = { it.name },
            onClose = { debtorMenu = false },
            onPick = { debtorId = it; debtorMenu = false },
          )
        }

        if (payment == SalesEngine.Payment.CREDIT) {
          Spacer(Modifier.height(12.dp))
          OutlinedTextField(
            value = paidText,
            onValueChange = { paidText = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text("مبلغ پرداختی") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
          )
        }

        Spacer(Modifier.height(16.dp))
        Panel {
          MoneyRow("جمع اقلام", totals.subtotal)
          MoneyRow("تخفیف", totals.discount)
          HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Shop.colors.border)
          MoneyRow("مبلغ نهایی", totals.finalTotal, strong = true)
          MoneyRow("مبلغ پرداختی", paid)
          MoneyRow("باقی‌مانده", remaining, tint = if (remaining > 0) Shop.colors.warning else null)
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("انصراف") }
          Button(
            onClick = { onConfirm(checkout.copy(paidAmount = paid, debtorId = debtorId)) },
            modifier = Modifier.weight(1f),
          ) { Text("ثبت فروش") }
        }
      }
    }
    }
  }
}

@Composable
private fun <T> Segmented(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
  Row(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Radius.sm))
      .background(Shop.colors.surface2)
      .padding(3.dp),
  ) {
    options.forEach { (label, value) ->
      val active = value == selected
      Box(
        Modifier
          .weight(1f)
          .clip(RoundedCornerShape(Radius.sm))
          .background(if (active) Shop.colors.primary else Color.Transparent)
          .clickable { onSelect(value) }
          .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          label,
          style = MaterialTheme.typography.labelLarge,
          color = if (active) Color.White else Shop.colors.muted,
        )
      }
    }
  }
}

@Composable
private fun MoneyRow(label: String, amount: Double, strong: Boolean = false, tint: Color? = null) {
  Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(
      label,
      style = if (strong) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
      color = if (strong) Shop.colors.text else Shop.colors.muted,
    )
    Text(
      "${money(amount)} ؋",
      style = if (strong) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
      color = tint ?: Shop.colors.text,
      fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal,
    )
  }
}

/* ============================ فاکتور ============================ */

/**
 *  فاکتور — همان چیدمانِ نسخهٔ وب، به‌علاوهٔ چاپ روی چاپگرِ حرارتی.
 */
@Composable
fun InvoiceDialog(
  d: ShopData,
  sale: Sale,
  onDismiss: () -> Unit,
  onMessage: (String) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var printer by remember { mutableStateOf(false) }

  val items = remember(sale.id) { d.saleItems.filter { it.saleId == sale.id } }
  val debtor = sale.debtorId?.let { id -> d.debtors.find { it.id == id } }

  Dialog(onDismissRequest = onDismiss) {
    DialogEntry {
    Surface(
      color = Shop.colors.surfaceSolid,
      shape = RoundedCornerShape(Radius.lg),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState())) {
        Text(
          "فاکتور فروش #${sale.invoiceNumber?.let { plain(it) } ?: "—"}",
          style = MaterialTheme.typography.titleMedium,
          color = Shop.colors.text,
        )
        Spacer(Modifier.height(4.dp))
        Text(
          "${formatDate(sale.date)} — ${if (sale.paymentMethod == "credit") "نسیه" else "نقدی"}",
          style = MaterialTheme.typography.labelSmall,
          color = Shop.colors.muted,
        )

        Spacer(Modifier.height(14.dp))
        items.forEach { item ->
          val product = d.products.find { it.id == item.productId }
          val name = SalesEngine.itemName(d, item)
          Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(Modifier.weight(1f)) {
              Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                color = Shop.colors.text,
              )
              Text(
                "${money(item.quantity)}${product?.unit?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""} × ${money(item.unitPrice)}",
                style = MaterialTheme.typography.labelSmall,
                color = Shop.colors.muted,
              )
            }
            Text(money(item.totalPrice), style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
          }
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = Shop.colors.border)
        Spacer(Modifier.height(10.dp))

        MoneyRow("جمع اقلام", sale.total)
        MoneyRow("تخفیف", sale.discount)
        MoneyRow("مبلغ نهایی", sale.finalTotal, strong = true)
        MoneyRow("پرداختی", sale.paidAmount)
        MoneyRow("باقی‌مانده", sale.remaining, tint = if (sale.remaining > 0) Shop.colors.warning else null)
        if (debtor != null) {
          Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("قرض‌دار", style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
            Text(debtor.name, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
          }
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("بستن") }
          Button(onClick = { printer = true }, modifier = Modifier.weight(1f)) { Text("چاپ") }
        }
      }
    }
    }
  }

  if (printer) {
    PrinterDialog(
      onDismiss = { printer = false },
      onPrint = { address, width ->
        printer = false
        onMessage("در حال فرستادن به چاپگر…")
        scope.launch {
          val error = PrintJob.printSale(context, d, sale, address, width)
          onMessage(error ?: "فاکتور به چاپگر فرستاده شد")
        }
      },
    )
  }
}

/* ============================ چاپگر ============================ */

/**
 *  انتخابِ چاپگر — با هر سه راهی که چاپگرِ دکان ممکن است وصل باشد.
 *
 *  ── چرا سه راه ────────────────────────────────────────────────────
 *  تا دیروز فقط بلوتوث بود. ولی چاپگرِ هر دکان یک‌جور وصل می‌شود:
 *
 *    • **بلوتوث** — چاپگرهای کوچکِ سیار
 *    • **وای‌فای** — چاپگرهای ۸۰ میلی‌متریِ روی پیشخان، با یک IP
 *    • **سیم** — چاپگر با کابلِ OTG
 *
 *  هیچ فهرستِ ساختگی ساخته نمی‌شود: بلوتوث فقط جفت‌شده‌ها را نشان
 *  می‌دهد و سیم فقط دستگاهی که واقعاً چاپگر است. اگر چیزی نبود، همین
 *  گفته می‌شود — نه اینکه دکمهٔ چاپ بی‌صدا کاری نکند.
 */
@Composable
private fun PrinterDialog(onDismiss: () -> Unit, onPrint: (String, Int) -> Unit) {
  val context = LocalContext.current
  val prefs = remember { context.getSharedPreferences("tohid", android.content.Context.MODE_PRIVATE) }

  //  چاپگرِ دفعهٔ قبل — تا فروشنده هر بار از نو انتخاب نکند
  val saved = remember { ThermalPrinter.Link.parse(prefs.getString("printer_address", null)) }

  var width by remember { mutableStateOf(prefs.getInt("printer_width", ThermalPrinter.WIDTH_58MM)) }

  //  همان راهی که دفعهٔ قبل کار می‌کرد، باز هم اول باز می‌شود
  var kind by remember {
    mutableStateOf(
      when (saved) {
        is ThermalPrinter.Link.Network -> "net"
        is ThermalPrinter.Link.Usb -> "usb"
        else -> "bt"
      }
    )
  }

  /* ---------------------------- بلوتوث ---------------------------- */

  var granted by remember {
    mutableStateOf(
      android.os.Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED
    )
  }
  val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

  var paired by remember { mutableStateOf(emptyList<ThermalPrinter.Printer>()) }
  LaunchedEffect(granted, kind) {
    paired = if (granted && kind == "bt") ThermalPrinter.paired(context) else emptyList()
  }
  var btAddress by remember {
    mutableStateOf((saved as? ThermalPrinter.Link.Bluetooth)?.address)
  }

  /* ---------------------------- وای‌فای ---------------------------- */

  var host by remember {
    mutableStateOf((saved as? ThermalPrinter.Link.Network)?.host.orEmpty())
  }
  var port by remember {
    mutableStateOf(
      ((saved as? ThermalPrinter.Link.Network)?.port ?: ThermalPrinter.RAW_PORT).toString()
    )
  }

  /* ---------------------------- سیم ---------------------------- */

  var usbList by remember { mutableStateOf(emptyList<ThermalPrinter.Printer>()) }
  var usbName by remember { mutableStateOf((saved as? ThermalPrinter.Link.Usb)?.deviceName) }
  //  با هر بار باز شدنِ برگه دوباره نگاه می‌کنیم: کابل ممکن است همین
  //  حالا وصل شده باشد
  LaunchedEffect(kind) {
    if (kind == "usb") usbList = ThermalPrinter.usbPrinters(context)
  }

  /**
   *  اجازهٔ دسترسی به چاپگرِ سیمی.
   *
   *  اندروید این را با یک پنجرهٔ خودش می‌گیرد و جوابش به شکلِ یک
   *  Broadcast برمی‌گردد. بدونِ اجازه `openDevice` خالی برمی‌گردد و
   *  چاپ بی‌آنکه معلوم شود چرا، نمی‌شود.
   */
  fun askUsb(deviceName: String) {
    val manager = ThermalPrinter.usbManager(context) ?: return
    val device = ThermalPrinter.usbDevice(context, deviceName) ?: return
    if (manager.hasPermission(device)) return
    val intent = android.app.PendingIntent.getBroadcast(
      context, 0,
      android.content.Intent(USB_PERMISSION_ACTION).setPackage(context.packageName),
      android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE,
    )
    runCatching { manager.requestPermission(device, intent) }
  }

  /* ---------------------------- کدام چاپگر انتخاب شده ---------------------------- */

  val link: ThermalPrinter.Link? = when (kind) {
    "bt" -> btAddress?.let { ThermalPrinter.Link.Bluetooth(it) }
    "net" -> host.trim().takeIf { it.isNotBlank() }?.let {
      ThermalPrinter.Link.Network(it, port.trim().toIntOrNull() ?: ThermalPrinter.RAW_PORT)
    }
    else -> usbName?.let { ThermalPrinter.Link.Usb(it) }
  }

  Dialog(onDismissRequest = onDismiss) {
    DialogEntry {
    Surface(color = Shop.colors.surfaceSolid, shape = RoundedCornerShape(Radius.lg), modifier = Modifier.fillMaxWidth()) {
      Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState())) {
        Text("چاپ فاکتور", style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
        Spacer(Modifier.height(12.dp))

        Text("عرض کاغذ", style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
        Spacer(Modifier.height(6.dp))
        Segmented(
          options = listOf("۵۸ میلی‌متر" to ThermalPrinter.WIDTH_58MM, "۸۰ میلی‌متر" to ThermalPrinter.WIDTH_80MM),
          selected = width,
          onSelect = { width = it; prefs.edit().putInt("printer_width", it).apply() },
        )

        Spacer(Modifier.height(16.dp))
        Text("چاپگر با چه چیزی وصل است؟", style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
        Spacer(Modifier.height(6.dp))
        Segmented(
          options = listOf("بلوتوث" to "bt", "وای‌فای" to "net", "سیم" to "usb"),
          selected = kind,
          onSelect = { kind = it },
        )

        Spacer(Modifier.height(16.dp))

        when (kind) {
          /* ---------------- بلوتوث ---------------- */
          "bt" -> {
            if (!granted) {
              Text(
                "برای دیدن چاپگرها، اجازهٔ بلوتوث لازم است.",
                style = MaterialTheme.typography.bodySmall,
                color = Shop.colors.muted,
              )
              Spacer(Modifier.height(8.dp))
              Button(onClick = { ask.launch(Manifest.permission.BLUETOOTH_CONNECT) }) { Text("اجازه دادن") }
            } else if (paired.isEmpty()) {
              Text(
                "چاپگری پیدا نشد. اول چاپگر را در تنظیمات بلوتوث گوشی جفت کنید، بعد اینجا برگردید.",
                style = MaterialTheme.typography.bodySmall,
                color = Shop.colors.muted,
              )
            } else {
              paired.forEach { p ->
                PrinterRow(
                  title = p.name,
                  subtitle = p.address,
                  selected = btAddress == p.address,
                  onSelect = { btAddress = p.address },
                )
              }
            }
          }

          /* ---------------- وای‌فای ---------------- */
          "net" -> {
            Text(
              "نشانیِ چاپگر در شبکهٔ دکان. روی خودِ چاپگر یا در برگهٔ تنظیماتش نوشته شده.",
              style = MaterialTheme.typography.bodySmall,
              color = Shop.colors.muted,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
              value = host,
              onValueChange = { host = it },
              label = { Text("نشانیِ IP") },
              placeholder = { Text("192.168.1.50") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
              value = port,
              onValueChange = { port = it.filter { c -> c.isDigit() }.take(5) },
              label = { Text("درگاه") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
              "تقریباً همهٔ چاپگرها روی ۹۱۰۰ گوش می‌دهند؛ تا وقتی چاپگرتان چیزِ دیگری نگفته، دست نزنید.",
              style = MaterialTheme.typography.labelSmall,
              color = Shop.colors.muted2,
            )
          }

          /* ---------------- سیم ---------------- */
          else -> {
            if (usbList.isEmpty()) {
              Text(
                "چاپگرِ سیمی پیدا نشد. کابل را وصل کنید و همین برگه را دوباره باز کنید.",
                style = MaterialTheme.typography.bodySmall,
                color = Shop.colors.muted,
              )
            } else {
              usbList.forEach { p ->
                PrinterRow(
                  title = p.name,
                  subtitle = p.address.substringAfterLast('/'),
                  selected = usbName == p.address,
                  onSelect = { usbName = p.address; askUsb(p.address) },
                )
              }
              Spacer(Modifier.height(6.dp))
              Text(
                "اولین بار اندروید اجازه می‌خواهد؛ «همیشه» را بزنید تا هر بار نپرسد.",
                style = MaterialTheme.typography.labelSmall,
                color = Shop.colors.muted2,
              )
            }
          }
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("انصراف") }
          Button(
            enabled = link != null,
            onClick = {
              val chosen = link ?: return@Button
              prefs.edit().putString("printer_address", chosen.save()).apply()
              onPrint(chosen.save(), width)
            },
            modifier = Modifier.weight(1f),
          ) { Text("چاپ") }
        }
      }
    }
    }
  }
}

/** نامِ کارِ اجازهٔ USB — فقط داخلِ همین برنامه معنا دارد */
private const val USB_PERMISSION_ACTION = "ir.vil3ntec.tohid.USB_PERMISSION"

/** یک ردیف از فهرستِ چاپگرها — بلوتوث و سیم هر دو همین شکل را دارند */
@Composable
private fun PrinterRow(title: String, subtitle: String, selected: Boolean, onSelect: () -> Unit) {
  Row(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Radius.sm))
      .background(if (selected) Shop.colors.primaryTint else Color.Transparent)
      .clickable { onSelect() }
      .padding(10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    RadioButton(selected = selected, onClick = onSelect)
    Spacer(Modifier.width(6.dp))
    Column {
      Text(title, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
      Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    }
  }
}

/* ============================ انتخاب‌ها ============================ */

/** انتخابِ قرض‌دار برای فروشِ نسیه */
@Composable
fun DebtorPicker(
  d: ShopData,
  selected: String?,
  onClose: () -> Unit,
  onPick: (String?) -> Unit,
) {
  var search by remember { mutableStateOf("") }
  val shown = d.debtors.filter { search.isBlank() || it.name.contains(search.trim(), ignoreCase = true) }

  Dialog(onDismissRequest = onClose) {
    Surface(color = Shop.colors.surfaceSolid, shape = RoundedCornerShape(Radius.lg), modifier = Modifier.fillMaxWidth()) {
      Column(Modifier.padding(16.dp).heightIn(max = 520.dp)) {
        Text("انتخاب قرض‌دار", style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
          value = search,
          onValueChange = { search = it },
          placeholder = { Text("نام قرض‌دار") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))

        if (shown.isEmpty()) {
          EmptyNote(if (d.debtors.isEmpty()) "هنوز قرض‌داری ثبت نشده است" else "قرض‌داری پیدا نشد")
        } else {
          LazyColumn(Modifier.weight(1f, fill = false)) {
            items(shown, key = { it.id }) { debtor ->
              Row(
                Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(Radius.sm))
                  .background(if (selected == debtor.id) Shop.colors.primaryTint else Color.Transparent)
                  .clickable { onPick(debtor.id) }
                  .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(Modifier.weight(1f)) {
                  Text(debtor.name, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
                  Text(
                    debtStateText(d, debtor.id),
                    style = MaterialTheme.typography.labelSmall,
                    color = Shop.colors.muted,
                  )
                }
              }
            }
          }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text("بستن") }
          if (selected != null) {
            Button(onClick = { onPick(null) }, modifier = Modifier.weight(1f)) { Text("بدون قرض‌دار") }
          }
        }
      }
    }
  }
}

/**
 *  تعداد و قیمتِ یک ردیفِ سبد — با تایپ، نه با زدنِ پیاپیِ «+».
 *
 *  دو چیز را با هم حل می‌کند: فروشِ عمده که با دکمهٔ «+» بیست‌وپنج بار
 *  زدن می‌خواست، و چانه‌زنی که تا دیروز فقط با دستکاریِ تخفیفِ کلِ فاکتور
 *  ممکن بود — و آن، سودِ همان کالا را در گزارش غلط می‌کرد.
 */
@Composable
fun CartLineDialog(
  name: String,
  unit: String,
  quantity: Double,
  unitPrice: Double,
  basePrice: Double,
  custom: Boolean,
  onDismiss: () -> Unit,
  onConfirm: (quantity: Double, price: Double?) -> Unit,
) {
  var qtyText by remember { mutableStateOf(qty(quantity)) }
  var priceText by remember { mutableStateOf(qty(unitPrice)) }
  var manual by remember { mutableStateOf(custom) }

  val parsedQty = qtyText.toDoubleOrNull()
  val parsedPrice = priceText.toDoubleOrNull()
  val ok = parsedQty != null && parsedQty > 0 && (!manual || (parsedPrice != null && parsedPrice >= 0))

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      color = Shop.colors.surfaceSolid,
      shape = RoundedCornerShape(Radius.lg),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(Modifier.padding(18.dp)) {
        Text(name, style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
        Spacer(Modifier.height(12.dp))

        NumberField(
          value = qtyText,
          onValueChange = { qtyText = it },
          label = if (unit.isNotBlank()) "تعداد ($unit)" else "تعداد",
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(
            checked = manual,
            onCheckedChange = {
              manual = it
              if (!it) priceText = qty(basePrice)
            },
          )
          Spacer(Modifier.width(4.dp))
          Text(
            "قیمت دستی برای این ردیف",
            style = MaterialTheme.typography.bodyMedium,
            color = Shop.colors.text,
          )
        }

        if (manual) {
          Spacer(Modifier.height(6.dp))
          AmountField(
            value = priceText,
            onValueChange = { priceText = it },
            label = "قیمت هر واحد",
          )
          Spacer(Modifier.height(4.dp))
          Text(
            "قیمت خودِ کالا ${money(basePrice)} ؋ است. این تغییر فقط روی همین فاکتور اثر دارد.",
            style = MaterialTheme.typography.labelSmall,
            color = Shop.colors.muted,
          )
        }

        Spacer(Modifier.height(14.dp))
        val shownPrice = if (manual) (parsedPrice ?: 0.0) else basePrice
        Text(
          "جمع این ردیف: ${money((parsedQty ?: 0.0) * shownPrice)} ؋",
          style = MaterialTheme.typography.titleSmall,
          color = Shop.colors.text,
        )

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
          TextButton(onClick = onDismiss) { Text("انصراف") }
          Button(
            enabled = ok,
            onClick = { onConfirm(parsedQty ?: 0.0, if (manual) parsedPrice else null) },
          ) { Text("ثبت") }
        }
      }
    }
  }
}

/**
 *  قلمِ آزاد — چیزی که در فهرستِ کالاها نیست.
 *
 *  کیسه، یک جنسِ تک، خدمتی که کالا نیست. تا دیروز راهی نبود جز ساختنِ
 *  یک کالای واقعی در فهرست، و نتیجه‌اش فهرستِ شلوغی از «متفرقه ۱،
 *  متفرقه ۲» بود که خودش گزارش‌ها را کند می‌کرد.
 */
@Composable
fun FreeLineDialog(
  onDismiss: () -> Unit,
  onConfirm: (label: String, price: Double, quantity: Double) -> Unit,
) {
  var label by remember { mutableStateOf("") }
  var priceText by remember { mutableStateOf("") }
  var qtyText by remember { mutableStateOf("1") }

  val parsedPrice = priceText.toDoubleOrNull()
  val parsedQty = qtyText.toDoubleOrNull()
  val ok = label.isNotBlank() && parsedPrice != null && parsedPrice > 0 &&
    parsedQty != null && parsedQty > 0

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      color = Shop.colors.surfaceSolid,
      shape = RoundedCornerShape(Radius.lg),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(Modifier.padding(18.dp)) {
        Text("قلم آزاد", style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
        Spacer(Modifier.height(4.dp))
        Text(
          "چیزی که در فهرست کالاها نیست. روی موجودی اثر نمی‌گذارد و کالای تازه‌ای هم ثبت نمی‌کند.",
          style = MaterialTheme.typography.bodySmall,
          color = Shop.colors.muted,
        )
        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
          value = label,
          onValueChange = { label = it },
          label = { Text("نام") },
          placeholder = { Text("مثلاً: کیسه") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        AmountField(value = priceText, onValueChange = { priceText = it }, label = "قیمت هر واحد")
        Spacer(Modifier.height(12.dp))
        NumberField(
          value = qtyText,
          onValueChange = { qtyText = it },
          label = "تعداد",
          modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(14.dp))
        Text(
          "جمع: ${money((parsedQty ?: 0.0) * (parsedPrice ?: 0.0))} ؋",
          style = MaterialTheme.typography.titleSmall,
          color = Shop.colors.text,
        )

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
          TextButton(onClick = onDismiss) { Text("انصراف") }
          Button(
            enabled = ok,
            onClick = { onConfirm(label.trim(), parsedPrice ?: 0.0, parsedQty ?: 1.0) },
          ) { Text("افزودن") }
        }
      }
    }
  }
}
