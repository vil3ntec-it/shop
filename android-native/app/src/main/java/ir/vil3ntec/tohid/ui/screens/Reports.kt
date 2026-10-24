package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Print
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.data.ReportCsv
import ir.vil3ntec.tohid.print.ReportPrint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.data.ReportEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.theme.Radius
import ir.vil3ntec.tohid.ui.theme.Shop

/** نام بخش‌های گزارش — همان چهار زبانهٔ نسخهٔ وب */
private val SECTION_TITLES = linkedMapOf(
  "sales" to "سود و فروش",
  "products" to "محصولات",
  "debtors" to "قرض‌داران",
  "purchases" to "خرید",
  "expiry" to "انقضا",
  "stock" to "گردش موجودی",
)

/**
 *  گزارش‌ها.
 *
 *  همان چهار بخشِ نسخهٔ وب: سود و فروشِ یک بازه، وضعیتِ محصولات، حسابِ
 *  قرض‌داران و گردشِ موجودی. حساب‌ها در `ReportEngine` است، اینجا فقط
 *  نشان داده می‌شود.
 */
@Composable
fun ReportsScreen(d: ShopData) {
  val context = LocalContext.current
  //  «گزارشِ تازه» (پیش‌فرض خاموش) ⇒ خلاصهٔ امروز و سنِ بدهی از هستهٔ مشترکِ گزارش
  ReportFresh.load(context)
  if (ReportFresh.on.value) { PhoneFreshReports(d); return }
  val scope = rememberCoroutineScope()
  var pending by remember { mutableStateOf<ReportCsv.Sheet?>(null) }

  // نوشتن فایل با انتخابگر خودِ اندروید — بدون اجازهٔ ذخیره‌سازی
  val saveCsv = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument("text/csv")
  ) { uri ->
    val sheet = pending
    pending = null
    if (uri == null || sheet == null) return@rememberLauncherForActivityResult
    scope.launch {
      runCatching {
        withContext(Dispatchers.IO) {
          context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(ReportCsv.text(sheet).toByteArray(Charsets.UTF_8))
          } ?: error("فایل ساخته نشد")
        }
      }
    }
  }
  /*
   *  شاگرد بخشِ «سود و فروش» را نمی‌بیند.
   *
   *  آنجا سودِ ناخالص، سودِ خالص، پرسودترین و کم‌سودترین کالا هست —
   *  یعنی قیمتِ خریدِ دکان. بقیهٔ بخش‌ها (محصولات، قرض‌داران، گردشِ
   *  موجودی) کارِ روزانهٔ خودش است و باز می‌ماند.
   */
  val canSeeMoney = remember { ir.vil3ntec.tohid.data.ShopRole.canSeeMoney(context) }
  val sections = remember(canSeeMoney) {
    //  خرید هم قیمتِ خریدِ دکان است — همان قاعدهٔ «سود و فروش»
    if (canSeeMoney) SECTION_TITLES else SECTION_TITLES.filterKeys { it != "sales" && it != "purchases" }
  }

  var range by rememberSaveable { mutableStateOf(ReportEngine.Range.MONTH) }
  var section by rememberSaveable { mutableStateOf(if (canSeeMoney) "sales" else "products") }
  var stockProduct by rememberSaveable { mutableStateOf<String?>(null) }

  //  ماه و سالِ خورشیدیِ انتخاب‌شده — پیش‌فرض همین ماه
  val now = remember { ir.vil3ntec.tohid.Jalali.ofIso(todayIso()) }
  var jy by rememberSaveable { mutableStateOf(now?.year ?: 1405) }
  var jm by rememberSaveable { mutableStateOf(now?.month ?: 1) }
  val (from, to) = ReportEngine.rangeOf(range, todayIso(), jy, jm)

  LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
    item {
      Text(
        if (canSeeMoney) "سود، فروش و مصارف در بازهٔ دلخواه"
        else "محصولات، قرض‌داران و گردش موجودی در بازهٔ دلخواه",
        style = MaterialTheme.typography.bodySmall,
        color = Shop.colors.muted,
      )
      Spacer(Modifier.height(8.dp))
      ReportFreshSwitch()
      Spacer(Modifier.height(10.dp))

      Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        sections.forEach { (id, label) ->
          FilterChip(selected = section == id, onClick = { section = id }, label = { Text(label) })
        }
      }

      Spacer(Modifier.height(10.dp))
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
          onClick = {
            val sheet = ReportCsv.of(section, d, from, to, todayIso())
            if (sheet.isEmpty) return@OutlinedButton
            pending = sheet
            saveCsv.launch("${sheet.name}.csv")
          },
          modifier = Modifier.weight(1f),
        ) {
          Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(17.dp))
          Spacer(Modifier.width(6.dp))
          Text("خروجی اکسل (CSV)")
        }
        OutlinedButton(
          onClick = {
            val sheet = ReportCsv.of(section, d, from, to, todayIso())
            ReportPrint.print(context, sheet, SECTION_TITLES[section] ?: "گزارش")
          },
          modifier = Modifier.weight(1f),
        ) {
          Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(17.dp))
          Spacer(Modifier.width(6.dp))
          Text("چاپ گزارش")
        }
      }

      Spacer(Modifier.height(14.dp))
    }

    when (section) {
      //  `rememberSaveable` ممکن است «sales» را از اجرای قبلی برگرداند،
      //  وقتی هنوز نقش معلوم نبود. سدِ دوم همین‌جاست.
      "sales" -> if (canSeeMoney) salesSection(this, d, range, from, to, jy, jm, { y, m -> jy = y; jm = m }) { range = it }
      "products" -> productsSection(this, d)
      "debtors" -> debtorsSection(this, d)
      "purchases" -> if (canSeeMoney) purchasesSection(this, d, from, to)
      "expiry" -> expirySection(this, d)
      else -> stockSection(this, d, stockProduct) { stockProduct = it }
    }
  }
}

/* ---------------------------- سود و فروش ---------------------------- */

private fun salesSection(
  scope: androidx.compose.foundation.lazy.LazyListScope,
  d: ShopData,
  range: ReportEngine.Range,
  from: String,
  to: String,
  jy: Int,
  jm: Int,
  onPick: (Int, Int) -> Unit,
  onRange: (ReportEngine.Range) -> Unit,
) = with(scope) {
  item {
    Row(
      Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      listOf(
        ReportEngine.Range.TODAY to "امروز",
        ReportEngine.Range.WEEK to "۷ روز",
        ReportEngine.Range.MONTH to "این ماه",
        ReportEngine.Range.JMONTH to "ماه…",
        ReportEngine.Range.JYEAR to "سال…",
        ReportEngine.Range.ALL to "از آغاز",
      ).forEach { (value, label) ->
        FilterChip(selected = range == value, onClick = { onRange(value) }, label = { Text(label) })
      }
    }
    //  گزارشِ ماهانه و سالانه: یک ماه یا سالِ خورشیدی، با پیکان جلو و عقب
    if (range == ReportEngine.Range.JMONTH || range == ReportEngine.Range.JYEAR) {
      val monthly = range == ReportEngine.Range.JMONTH
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {
          if (monthly) { if (jm == 1) onPick(jy - 1, 12) else onPick(jy, jm - 1) } else onPick(jy - 1, jm)
        }) { Icon(Icons.Filled.ChevronRight, contentDescription = "قبلی") }
        Text(
          if (monthly) "${ir.vil3ntec.tohid.JALALI_MONTHS[jm - 1]} $jy" else "سال $jy",
          style = MaterialTheme.typography.titleSmall,
          color = Shop.colors.text,
        )
        IconButton(onClick = {
          if (monthly) { if (jm == 12) onPick(jy + 1, 1) else onPick(jy, jm + 1) } else onPick(jy + 1, jm)
        }) { Icon(Icons.Filled.ChevronLeft, contentDescription = "بعدی") }
      }
    }
    Spacer(Modifier.height(6.dp))
    if (range != ReportEngine.Range.ALL) {
      Text(
        "${formatDate(from)} تا ${formatDate(to)}",
        style = MaterialTheme.typography.labelSmall,
        color = Shop.colors.muted2,
      )
    }
    Spacer(Modifier.height(12.dp))

    val r = ReportEngine.sales(d, from, to)

    /*
     *  وقتی عدد منفی است، اسمش «ضرر» است نه «سودِ منفی».
     *
     *  رنگِ قرمز بود ولی برچسب همان «سود خالص» می‌ماند — و کسی که
     *  عددها را سریع نگاه می‌کند، «سود» می‌خواند و رد می‌شود. کلمه‌ی
     *  درست، خودش هشدار است.
     */
    val lost = r.netProfit < 0
    StatTile(
      label = if (lost) "ضرر خالص" else "سود خالص",
      value = "${money(kotlin.math.abs(r.netProfit))} ؋",
      tint = if (lost) Shop.colors.danger else Shop.colors.success,
      hint = "${plain(r.count)} فاکتور",
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))

    Panel {
      ReportRow("جمع اقلام", r.gross)
      ReportRow("تخفیف", r.discount, negative = true)
      ReportRow("فروش خالص", r.net, strong = true)
      HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Shop.colors.border)
      ReportRow("بهای تمام‌شدهٔ کالا", r.cogs, negative = true)
      if (r.returnAmount > 0) ReportRow("مرجوعی", r.returnAmount, negative = true)
      ReportRow(if (r.grossProfit < 0) "ضرر ناخالص" else "سود ناخالص", r.grossProfit, strong = true)
      HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Shop.colors.border)
      ReportRow("مصارف", r.expenses, negative = true)
      ReportRow(if (lost) "ضرر خالص" else "سود خالص", r.netProfit, strong = true)
    }

    //  فروشِ هر کاربر — وقتی بیش از یک فروشنده در بازه هست
    val sellers = ReportEngine.bySeller(d, from, to)
    if (sellers.size > 1) {
      Spacer(Modifier.height(12.dp))
      Text("فروش هر کاربر", style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
      Spacer(Modifier.height(6.dp))
      Panel {
        sellers.forEach { st ->
          Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${st.seller} — ${plain(st.count)} فاکتور", style = MaterialTheme.typography.bodySmall, color = Shop.colors.text)
            Text("${money(st.net)} ؋", style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text, fontWeight = FontWeight.Bold)
          }
        }
      }
    }

    Spacer(Modifier.height(10.dp))
    Text(
      "سود هر قلم از قیمت خرید ثبت‌شده روی همان فاکتور حساب می‌شود، نه قیمت امروز.",
      style = MaterialTheme.typography.labelSmall,
      color = Shop.colors.muted2,
    )
  }
}

@Composable
private fun ReportRow(label: String, amount: Double, strong: Boolean = false, negative: Boolean = false) {
  Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(
      label,
      style = if (strong) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
      color = if (strong) Shop.colors.text else Shop.colors.muted,
    )
    Text(
      "${if (negative && amount > 0) "−" else ""}${money(amount)} ؋",
      style = if (strong) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
      color = when {
        strong && amount < 0 -> Shop.colors.danger
        negative && amount > 0 -> Shop.colors.muted
        else -> Shop.colors.text
      },
      fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal,
    )
  }
}

/* ---------------------------- محصولات ---------------------------- */

private fun productsSection(
  scope: androidx.compose.foundation.lazy.LazyListScope,
  d: ShopData,
) = with(scope) {
  item {
    val r = ReportEngine.products(d)

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      StatTile("تمام‌شده", plain(r.out.size), Shop.colors.danger, modifier = Modifier.weight(1f))
      StatTile("موجودی کم", plain(r.low.size), Shop.colors.warning, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
    StatTile(
      "ارزش موجودی (به بهای خرید)",
      "${money(r.inventoryValue)} ؋",
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.dp))

    RankPanel("پرفروش‌ترین", r.topSelling) { "${qty(it.quantity)} فروخته‌شده" }
    RankPanel("کم‌فروش‌ترین", r.slowest) { "${qty(it.quantity)} فروخته‌شده" }
    RankPanel("پرسودترین", r.mostProfitable) { "${money(it.profit)} ؋" }
    RankPanel("کم‌سودترین", r.leastProfitable) { "${money(it.profit)} ؋" }

    if (r.out.isNotEmpty() || r.low.isNotEmpty()) {
      SectionTitle("نیاز به توجه")
      Panel {
        r.out.forEach { IssueRow(it.name, "تمام‌شده", Shop.colors.danger) }
        r.low.forEach { IssueRow(it.name, "موجودی کم", Shop.colors.warning) }
      }
      Spacer(Modifier.height(16.dp))
    }
  }
}

@Composable
private fun RankPanel(
  title: String,
  rows: List<ReportEngine.ProductStat>,
  label: (ReportEngine.ProductStat) -> String,
) {
  SectionTitle(title)
  Panel {
    if (rows.isEmpty()) {
      EmptyNote("داده‌ای موجود نیست")
    } else {
      rows.forEach { stat ->
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(stat.product.name, style = MaterialTheme.typography.bodySmall, color = Shop.colors.text)
          Text(label(stat), style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
        }
      }
    }
  }
  Spacer(Modifier.height(16.dp))
}

@Composable
private fun IssueRow(name: String, label: String, tint: Color) {
  Row(
    Modifier.fillMaxWidth().padding(vertical = 5.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(name, style = MaterialTheme.typography.bodySmall, color = Shop.colors.text)
    Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
  }
}

/* ---------------------------- قرض‌داران ---------------------------- */

private fun debtorsSection(
  scope: androidx.compose.foundation.lazy.LazyListScope,
  d: ShopData,
) = with(scope) {
  val rows = ReportEngine.debtors(d)

  item {
    StatTile(
      "جمع طلب از مشتریان",
      "${money(rows.sumOf { it.remaining.coerceAtLeast(0.0) })} ؋",
      Shop.colors.warning,
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
  }

  if (rows.isEmpty()) {
    item { Panel { EmptyNote("قرض‌داری ثبت نشده") } }
  } else {
    items(rows, key = { it.debtor.id }) { row ->
      Column(
        Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(Radius.md))
          .background(Shop.colors.surface)
          .border(1.dp, Shop.colors.border, RoundedCornerShape(Radius.md))
          .padding(14.dp)
      ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(row.debtor.name, style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
          Text(
            when {
              row.remaining > 0 -> "${money(row.remaining)} ؋"
              row.remaining < 0 -> "${money(-row.remaining)} ؋ موجودی"
              else -> "تسویه شده"
            },
            style = MaterialTheme.typography.titleSmall,
            color = when {
              row.remaining > 0 -> Shop.colors.warning
              row.remaining < 0 -> Shop.colors.success
              else -> Shop.colors.muted
            },
          )
        }
        Spacer(Modifier.height(4.dp))
        Text(
          "فروش نسیه: ${money(row.given)} — پرداخت‌شده: ${money(row.received)}",
          style = MaterialTheme.typography.labelSmall,
          color = Shop.colors.muted,
        )
      }
      Spacer(Modifier.height(8.dp))
    }
  }
}

/* ------------------------------ خرید ------------------------------ */

private fun purchasesSection(
  scope: androidx.compose.foundation.lazy.LazyListScope,
  d: ShopData,
  from: String,
  to: String,
) = with(scope) {
  val r = ReportEngine.purchases(d, from, to)
  item {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      StatTile("جمع خرید", "${money(r.total)} ؋", Shop.colors.primary, modifier = Modifier.weight(1f))
      StatTile("بدهی به تأمین‌کننده", "${money(r.debt)} ؋", Shop.colors.warning, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(12.dp))
  }
  if (r.rows.isEmpty()) {
    item { Panel { EmptyNote("در این بازه خریدی ثبت نشده") } }
  } else {
    item {
      Panel {
        Text("سهمِ هر تأمین‌کننده", style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
        Spacer(Modifier.height(8.dp))
        r.bySupplier.forEach { (name, total) ->
          Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
            Text("${money(total)} ؋", style = MaterialTheme.typography.bodyMedium, color = Shop.colors.muted)
          }
        }
      }
      Spacer(Modifier.height(12.dp))
    }
    val names = d.products.associate { it.id to it.name }
    items(r.rows, key = { it.id }) { p ->
      Row(
        Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(Radius.md))
          .background(Shop.colors.surface)
          .border(1.dp, Shop.colors.border, RoundedCornerShape(Radius.md))
          .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(Modifier.weight(1f)) {
          Text(names[p.productId] ?: "—", style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
          Text(
            "${ir.vil3ntec.tohid.formatDate(p.date)} · ${qty(p.quantity)} ${p.unit}",
            style = MaterialTheme.typography.labelSmall,
            color = Shop.colors.muted,
          )
        }
        Text("${money(p.totalAmount)} ؋", style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
      }
      Spacer(Modifier.height(8.dp))
    }
  }
}

/* ------------------------------ انقضا ------------------------------ */

private fun expirySection(
  scope: androidx.compose.foundation.lazy.LazyListScope,
  d: ShopData,
) = with(scope) {
  val lots = ir.vil3ntec.tohid.data.ExpiryEngine.lots(d, ir.vil3ntec.tohid.todayIso())
  item {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      StatTile("منقضی", lots.count { it.expired }.toString(), Shop.colors.danger, modifier = Modifier.weight(1f))
      StatTile(
        "تا ${ir.vil3ntec.tohid.data.ExpiryEngine.NEAR_DAYS} روز",
        lots.count { !it.expired && it.daysLeft <= ir.vil3ntec.tohid.data.ExpiryEngine.NEAR_DAYS }.toString(),
        Shop.colors.warning,
        modifier = Modifier.weight(1f),
      )
    }
    Spacer(Modifier.height(12.dp))
  }
  if (lots.isEmpty()) {
    item { Panel { EmptyNote("محموله‌ای با تاریخِ انقضا در انبار نمانده") } }
  } else {
    items(lots, key = { it.entry.id }) { l ->
      Row(
        Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(Radius.md))
          .background(Shop.colors.surface)
          .border(1.dp, Shop.colors.border, RoundedCornerShape(Radius.md))
          .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(Modifier.weight(1f)) {
          Text(l.product.name, style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
          Text(
            "${ir.vil3ntec.tohid.formatDate(l.entry.expiryDate)}" +
              (if (l.entry.batch.isNotBlank()) " · سری ${l.entry.batch}" else "") +
              " · ${qty(l.remaining)} ${l.product.unit}",
            style = MaterialTheme.typography.labelSmall,
            color = Shop.colors.muted,
          )
        }
        Text(
          if (l.expired) "منقضی" else "${l.daysLeft} روز",
          style = MaterialTheme.typography.titleSmall,
          color = when {
            l.expired -> Shop.colors.danger
            l.daysLeft <= ir.vil3ntec.tohid.data.ExpiryEngine.NEAR_DAYS -> Shop.colors.warning
            else -> Shop.colors.success
          },
        )
      }
      Spacer(Modifier.height(8.dp))
    }
  }
}

/* -------------------------- گردشِ موجودی -------------------------- */

private fun stockSection(
  scope: androidx.compose.foundation.lazy.LazyListScope,
  d: ShopData,
  productId: String?,
  onProduct: (String?) -> Unit,
) = with(scope) {
  item {
    Row(
      Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      FilterChip(selected = productId == null, onClick = { onProduct(null) }, label = { Text("همه") })
      d.products.forEach { p ->
        FilterChip(
          selected = productId == p.id,
          onClick = { onProduct(if (productId == p.id) null else p.id) },
          label = { Text(p.name) },
        )
      }
    }
    Spacer(Modifier.height(12.dp))
  }

  val rows = ReportEngine.stockLedger(d, productId)
  if (rows.isEmpty()) {
    item { Panel { EmptyNote("هنوز حرکتی ثبت نشده") } }
  } else {
    items(rows, key = { it.id }) { m ->
      val product = d.products.find { it.id == m.productId }
      Row(
        Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(Radius.sm))
          .background(Shop.colors.surface)
          .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(Modifier.weight(1f)) {
          Text(
            product?.name ?: "(محصول حذف‌شده)",
            style = MaterialTheme.typography.bodyMedium,
            color = Shop.colors.text,
          )
          Text(
            "${formatDate(m.date)} — ${movementLabelOf(m.type)}${if (m.notes.isNotBlank()) " — ${m.notes}" else ""}",
            style = MaterialTheme.typography.labelSmall,
            color = Shop.colors.muted,
          )
        }
        Text(
          "${if (m.qty > 0) "+" else "−"}${qty(kotlin.math.abs(m.qty))}",
          style = MaterialTheme.typography.titleSmall,
          color = if (m.qty > 0) Shop.colors.success else Shop.colors.danger,
        )
      }
      Spacer(Modifier.height(6.dp))
    }
  }
}

/** همان برچسب‌های STOCK_MOVEMENT_LABELS نسخهٔ وب */
fun movementLabelOf(type: String): String = when (type) {
  "purchase_in" -> "ورود خرید"
  "sale" -> "فروش"
  "customer_return" -> "مرجوعی مشتری"
  "sale_cancel" -> "لغو فروش"
  "supplier_return" -> "برگشت به تأمین‌کننده"
  "adjustment" -> "اصلاح موجودی"
  "damage" -> "ضایعات"
  //  انتقال بین محل‌ها (موتورِ تازهٔ موجودی): دو ردیف، خروجیِ منفی و ورودیِ مثبت
  "transfer_out" -> "انتقالِ خروجی"
  "transfer_in" -> "انتقالِ ورودی"
  "transfer" -> "انتقال"
  else -> type
}
