package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.ReportEngine
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.fa
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.ui.theme.Radius
import ir.vil3ntec.tohid.ui.theme.Shape
import ir.vil3ntec.tohid.ui.theme.glassSurface
import ir.vil3ntec.tohid.ui.theme.Shop
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material3.Icon

private fun todayIso(): String =
  SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

/**
 *  داشبورد — پنل‌به‌پنل همان چیزی که نسخهٔ وب نشان می‌دهد.
 *
 *  ترتیب و عنوان‌ها عمداً یکی است: چهار کاشیِ بالا، روند معاملات، قرض‌داران،
 *  مصارف اخیر، مصارف بر اساس دسته، وضعیت انبار و خلاصهٔ امروز. کسی که با
 *  نسخهٔ وب کار کرده، اینجا دنبال چیزی نمی‌گردد.
 *
 *  عددها هم با همان فرمول‌ها حساب می‌شوند و از موتورهای مشترک می‌آیند، نه
 *  از حسابِ جداگانهٔ این صفحه.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(d: ShopData, onOpen: (String) -> Unit = {}) {
  val context = LocalContext.current
  val storeName = remember {
    context.getSharedPreferences("tohid", android.content.Context.MODE_PRIVATE)
      .getString("store_name", "") ?: ""
  }
  val today = todayIso()
  val monthPrefix = today.take(7)

  val todaySales = d.sales.filter { it.date == today && it.status != "cancelled" }
  val todayTotal = todaySales.sumOf { it.finalTotal }
  val todayExpense = d.expenses.filter { it.date == today }.sumOf { it.amount }
  val todayProfit = ReportEngine.sales(d, today, today).netProfit

  val debtorBalances = d.debtors.map { it to ShopStore.debt(d, it.id) }
  val owing = debtorBalances.filter { it.second > 0 }.sortedByDescending { it.second }
  val totalDebt = owing.sumOf { it.second }
  val expenseMonth = d.expenses.filter { it.date.startsWith(monthPrefix) }.sumOf { it.amount }
  val supplierDebt = d.suppliers.sumOf { ShopStore.supplierDebt(d, it.id) }.coerceAtLeast(0.0)

  val lowStock = d.products.filter { ShopStore.stockStatus(d, it) == "low" }
  val outOfStock = d.products.filter { ShopStore.stockStatus(d, it) == "out" }
  val warehouse = WarehouseEngine.summary(d)


  Column(
    Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp)
  ) {
    /* ---------------------------- سلام ---------------------------- */
    // اسمِ دکان و وقتِ روز — کوچک است ولی کاری می‌کند که برنامه انگار
    // برای همین یک نفر ساخته شده، نه برای «کاربر»
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(
          greeting(),
          style = MaterialTheme.typography.bodyMedium,
          color = Shop.colors.muted,
        )
        Spacer(Modifier.height(2.dp))
        Text(
          storeName.ifBlank { "دکان شما" },
          style = MaterialTheme.typography.headlineMedium,
          color = Shop.colors.text,
          fontWeight = FontWeight.Bold,
        )
      }
      TohidBadge(
        text = formatDate(today),
        tint = Shop.colors.primary,
        fill = Shop.colors.primaryTint,
      )
    }
    Spacer(Modifier.height(18.dp))

    /* ------------------------- حلقه‌های امروز ------------------------- */
    // سه عددی که فروشنده صبح اول وقت می‌خواهد بداند، هرکدام با کمانی که
    // می‌گوید نسبت به این ماه کجاست
    val monthSalesTotal = d.sales
      .filter { it.status != "cancelled" && it.date.startsWith(monthPrefix) }
      .sumOf { it.finalTotal }
    val bestDay = d.sales
      .filter { it.status != "cancelled" && it.date.startsWith(monthPrefix) }
      .groupBy { it.date }
      .maxOfOrNull { (_, list) -> list.sumOf { it.finalTotal } } ?: 0.0

    /*
     *  بالای صفحه.
     *
     *  روی گوشی زیرِ هم، روی تبلت کنارِ هم. کارتِ حلقه‌ها روی صفحهٔ پهن
     *  تنها می‌ماند و دو طرفش خالی؛ کنارش گذاشتنِ دو کارتِ طلب و بدهی،
     *  همان جای خالی را با چیزی پر می‌کند که فروشنده هر روز می‌خواهد
     *  ببیند.
     */
    val rings: @Composable () -> Unit = {
      TohidCard(glow = true) {
        Row(
          Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          StatRing(
            label = "فروش امروز",
            value = money(animatedMoney(todayTotal)),
            caption = "افغانی",
            // نسبت به بهترین روزِ همین ماه — سقفی که خودِ دکان ساخته
            fraction = if (bestDay > 0) (todayTotal / bestDay).toFloat() else 0f,
            tint = Shop.colors.primary,
          )
          StatRing(
            label = "سود امروز",
            value = money(animatedMoney(todayProfit)),
            caption = "افغانی",
            fraction = if (todayTotal > 0) (todayProfit / todayTotal).toFloat().coerceAtLeast(0f) else 0f,
            tint = if (todayProfit >= 0) Shop.colors.success else Shop.colors.danger,
          )
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(
            "فروش این ماه",
            style = MaterialTheme.typography.labelMedium,
            color = Shop.colors.muted,
          )
          TohidMoneyText(amount = monthSalesTotal, tint = Shop.colors.text)
        }
      }
    }

    val owedCard: @Composable (Modifier) -> Unit = { m ->
      TohidStatCard(
        label = "طلب از مشتریان",
        value = "${money(animatedMoney(totalDebt))} افغانی",
        tint = if (totalDebt > 0) Shop.colors.warning else Shop.colors.success,
        hint = "${owing.size.fa()} قرض‌دار",
        modifier = m,
        onClick = { onOpen("debtors") },
      )
    }
    val supplierCard: @Composable (Modifier) -> Unit = { m ->
      TohidStatCard(
        label = "بدهی به تأمین‌کننده",
        value = "${money(animatedMoney(supplierDebt))} افغانی",
        tint = if (supplierDebt > 0) Shop.colors.danger else Shop.colors.success,
        hint = "پرداخت‌نشده",
        modifier = m,
        onClick = { onOpen("purchasing") },
      )
    }

    if (isTablet()) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.weight(1f)) { rings() }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
          owedCard(Modifier.fillMaxWidth())
          supplierCard(Modifier.fillMaxWidth())
        }
      }
    } else {
      rings()
      Spacer(Modifier.height(14.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        owedCard(Modifier.weight(1f))
        supplierCard(Modifier.weight(1f))
      }
    }

    /* --------------------------- میان‌برها --------------------------- */
    // کارهایی که در طول روز بیشتر از همه زده می‌شوند، یک لمس فاصله دارند
    Spacer(Modifier.height(14.dp))
    Row(
      Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      ShortcutCard(Icons.Filled.PointOfSale, "فروش سریع", Shop.colors.primary, Modifier.weight(1f)) { onOpen("sale") }
      ShortcutCard(Icons.Filled.Inventory2, "ورود کالا", Shop.colors.accent, Modifier.weight(1f)) { onOpen("warehouse") }
      ShortcutCard(Icons.Filled.Groups, "قرض‌داران", Shop.colors.warning, Modifier.weight(1f)) { onOpen("debtors") }
      ShortcutCard(Icons.Filled.BarChart, "گزارش‌ها", Shop.colors.success, Modifier.weight(1f)) { onOpen("reports") }
    }

    /* ---------------------------- پنل‌ها ---------------------------- */
    /*
     *  روی گوشی زیرِ هم، روی تبلت دو ستون.
     *
     *  ستونِ یگانه روی صفحهٔ پهن دو ایراد داشت: عنوان و عددِ هر ردیف به
     *  دو لبهٔ مخالف می‌چسبیدند و وسطشان یک دستِ خالی می‌ماند، و برای
     *  دیدنِ پنلِ ششم باید خیلی اسکرول می‌شد در حالی که نصفِ صفحه بی‌کار
     *  بود. دو ستون هر دو را حل می‌کند.
     */
    Spacer(Modifier.height(16.dp))

    val attention = buildList {
      outOfStock.take(3).forEach { add(Triple(it.name, "تمام‌شده", "products")) }
      lowStock.take(3).forEach { add(Triple(it.name, "موجودی کم", "products")) }
      owing.take(2).forEach { add(Triple(it.first.name, "${money(it.second)} افغانی طلب", "debtors")) }
    }

    val panels = buildList<@Composable () -> Unit> {
      // «نیاز به توجه» فقط وقتی می‌آید که واقعاً چیزی هست؛ پنلِ خالیِ
      // «همه‌چیز خوب است» فقط جا می‌گیرد
      if (attention.isNotEmpty()) add {
        Panel {
          PanelHead("نیاز به توجه", "${attention.size.fa()} مورد")
          Spacer(Modifier.height(10.dp))
          attention.forEach { (name, note, target) ->
            LineRow(
              name,
              note,
              if (note == "تمام‌شده") Shop.colors.danger else Shop.colors.warning,
              onClick = { onOpen(target) },
            )
          }
        }
      }

      add {
        Panel {
          PanelHead("مصارف اخیر", "مشاهده همه") { onOpen("expenses") }
          Spacer(Modifier.height(8.dp))
          val recent = d.expenses.sortedByDescending { it.createdAt }.take(5)
          if (recent.isEmpty()) {
            EmptyNote("هنوز اطلاعاتی ثبت نشده")
          } else {
            recent.forEach { e ->
              LineRow(
                e.title.ifBlank { e.category.ifBlank { "مصرف" } },
                "${money(e.amount)} افغانی",
                Shop.colors.warning,
                detail = formatDate(e.date),
              )
            }
          }
        }
      }

      add {
        Panel {
          PanelHead("قرض‌داران", "مشاهده همه") { onOpen("debtors") }
          Spacer(Modifier.height(8.dp))
          if (owing.isEmpty()) {
            EmptyNote("هنوز اطلاعاتی ثبت نشده")
          } else {
            owing.take(5).forEach { (debtor, amount) ->
              LineRow(debtor.name, "${money(amount)} افغانی", Shop.colors.danger)
            }
          }
        }
      }

      add {
        Panel {
          PanelHead("وضعیت انبار", "مشاهده همه") { onOpen("products") }
          Spacer(Modifier.height(10.dp))
          ChipRow(
            listOf(
              "تعداد محصولات" to warehouse.products.fa(),
              "تعداد کارتن" to qty(warehouse.cartons),
              "تعداد واحد" to qty(warehouse.units),
              "ارزش تقریبی موجودی" to "${money(warehouse.value)} افغانی",
            )
          )
          if (lowStock.isNotEmpty() || outOfStock.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            (outOfStock + lowStock).take(5).forEach { p ->
              val out = ShopStore.stockStatus(d, p) == "out"
              LineRow(
                p.name,
                if (out) "تمام‌شده" else "موجودی کم",
                if (out) Shop.colors.danger else Shop.colors.warning,
                detail = "${qty(ShopStore.stock(d, p.id))}${if (p.unit.isNotBlank()) " ${p.unit}" else ""}",
              )
            }
          }
        }
      }

      add {
        Panel {
          PanelHead("روند معاملات", "این ماه")
          Spacer(Modifier.height(12.dp))
          val monthSales = d.sales.filter { it.status != "cancelled" && it.date.startsWith(monthPrefix) }
          if (monthSales.isEmpty()) {
            EmptyNote("هنوز فروشی ثبت نشده")
          } else {
            val byDay = monthSales.groupBy { it.date }.mapValues { (_, list) -> list.sumOf { it.finalTotal } }
            TrendChart(byDay.toSortedMap().values.toList())
            Spacer(Modifier.height(8.dp))
            Text(
              "جمع ماه: ${money(byDay.values.sum())} افغانی",
              style = MaterialTheme.typography.labelMedium,
              color = Shop.colors.muted,
            )
          }
        }
      }

      add {
        Panel {
          PanelHead("مصارف بر اساس دسته", "این ماه")
          Spacer(Modifier.height(8.dp))
          val byCategory = d.expenses
            .filter { it.date.startsWith(monthPrefix) }
            .groupBy { it.category.ifBlank { "بدون دسته" } }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }
          if (byCategory.isEmpty()) {
            EmptyNote("این ماه مصرفی ثبت نشده")
          } else {
            val max = byCategory.first().second
            byCategory.take(6).forEach { (name, amount) ->
              CategoryBar(name, amount, if (max > 0) (amount / max).toFloat() else 0f)
            }
          }
        }
      }

      add {
        Panel {
          PanelHead("خلاصه امروز", formatDate(today))
          Spacer(Modifier.height(10.dp))
          ChipRow(
            listOf(
              "فروش امروز" to "${money(todayTotal)} افغانی",
              "تعداد فروش امروز" to todaySales.size.fa(),
              "سود امروز" to "${money(todayProfit)} افغانی",
              "مصارف امروز" to "${money(todayExpense)} افغانی",
            )
          )
          Spacer(Modifier.height(8.dp))
          ChipRow(
            listOf(
              "بدهی تأمین‌کنندگان" to "${money(supplierDebt)} افغانی",
              "کالاهای کم‌موجودی" to lowStock.size.fa(),
              "کالاهای تمام‌شده" to outOfStock.size.fa(),
            )
          )
        }
      }
    }

    PanelGrid(panels)


    Spacer(Modifier.height(24.dp))
  }
}

/**
 *  پنل‌ها — روی گوشی یک ستون، روی تبلت دو ستون.
 *
 *  پخش‌شدن یکی‌درمیان است نه «نصفِ اول در ستونِ راست»: پنل‌ها قدهای
 *  متفاوتی دارند و اگر پشتِ سرِ هم در یک ستون بریزند، یک ستون بلند و
 *  آن یکی کوتاه می‌شود. یکی‌درمیان، دو ستون تقریباً هم‌قد درمی‌آیند.
 */
@Composable
private fun PanelGrid(panels: List<@Composable () -> Unit>) {
  val gap = 14.dp
  if (!isTablet()) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
      panels.forEach { it() }
    }
    return
  }
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
      panels.filterIndexed { i, _ -> i % 2 == 0 }.forEach { it() }
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
      panels.filterIndexed { i, _ -> i % 2 == 1 }.forEach { it() }
    }
  }
}

/* ============================ اجزای صفحه ============================ */

@Composable
private fun PanelHead(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
  Row(
    Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
    if (action != null) {
      Text(
        action,
        style = MaterialTheme.typography.labelMedium,
        color = if (onAction != null) Shop.colors.primary else Shop.colors.muted,
        modifier = if (onAction != null) Modifier.clickable(onClick = onAction) else Modifier,
      )
    }
  }
}

@Composable
private fun LineRow(
  title: String,
  value: String,
  tint: Color,
  detail: String? = null,
  onClick: (() -> Unit)? = null,
) {
  Row(
    Modifier
      .fillMaxWidth()
      .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
      .padding(vertical = 7.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    // نشانِ رنگیِ ریزِ سرِ ردیف. در ردیفی که عنوانش یک سر و عددش سرِ
    // دیگر است، همین نقطه چشم را نگه می‌دارد تا خطِ خالیِ وسط، دو طرف
    // را از هم جدا نکند.
    Box(
      Modifier
        .size(width = 3.dp, height = 18.dp)
        .clip(RoundedCornerShape(2.dp))
        .background(tint.copy(alpha = 0.55f))
    )
    Spacer(Modifier.width(10.dp))
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
      if (detail != null) {
        Text(detail, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
      }
    }
    Text(
      value,
      style = MaterialTheme.typography.labelLarge,
      color = tint,
      fontWeight = FontWeight.Bold,
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(items: List<Pair<String, String>>) {
  // قبلاً یک ردیفِ افقیِ اسکرول‌شونده بود: روی گوشی نصفش بیرونِ صفحه
  // می‌ماند و روی تبلت سمتِ چپش خالی. حالا کاشی‌ها می‌شکنند و سطر را پر
  // می‌کنند — هر اندازه صفحه‌ای که باشد.
  val perRow = if (isTablet()) 4 else 2
  FlowRow(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    maxItemsInEachRow = perRow,
  ) {
    items.forEach { (label, value) ->
      Column(
        Modifier
          .weight(1f)
          .clip(Shape.chip)
          .background(Shop.colors.surface2)
          .padding(horizontal = 14.dp, vertical = 12.dp)
      ) {
        Text(
          label,
          style = MaterialTheme.typography.labelSmall,
          color = Shop.colors.muted,
          maxLines = 1,
          overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
          value,
          style = MaterialTheme.typography.titleSmall,
          color = Shop.colors.text,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
private fun CategoryBar(name: String, amount: Double, ratio: Float) {
  Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(name, style = MaterialTheme.typography.labelMedium, color = Shop.colors.text)
      Text(
        "${money(amount)} افغانی",
        style = MaterialTheme.typography.labelSmall,
        color = Shop.colors.muted,
      )
    }
    Spacer(Modifier.height(4.dp))
    Box(
      Modifier
        .fillMaxWidth()
        .height(8.dp)
        .clip(RoundedCornerShape(4.dp))
        .background(Shop.colors.surface2)
    ) {
      Box(
        Modifier
          .fillMaxWidth(ratio.coerceIn(0.02f, 1f))
          .height(8.dp)
          .clip(RoundedCornerShape(4.dp))
          .background(Shop.colors.warning)
      )
    }
  }
}

/** نمودارِ ساده و بی‌کتابخانه — همان روندی که وب می‌کشد */
@Composable
private fun TrendChart(values: List<Double>) {
  val line = Shop.colors.primary
  val fill = Shop.colors.primaryTint
  Canvas(Modifier.fillMaxWidth().height(120.dp)) {
    if (values.isEmpty()) return@Canvas
    val max = values.maxOrNull() ?: 0.0
    if (max <= 0.0) return@Canvas
    val stepX = if (values.size > 1) size.width / (values.size - 1) else size.width
    val points = values.mapIndexed { i, v ->
      Offset(
        x = if (values.size > 1) stepX * i else size.width / 2,
        y = size.height - (v / max).toFloat() * (size.height - 8f) - 4f,
      )
    }
    // سطحِ زیر خط
    points.forEachIndexed { i, p ->
      if (i > 0) {
        val prev = points[i - 1]
        drawLine(color = line, start = prev, end = p, strokeWidth = 3f)
        drawRect(
          color = fill,
          topLeft = Offset(prev.x, minOf(prev.y, p.y)),
          size = androidx.compose.ui.geometry.Size(
            width = p.x - prev.x,
            height = size.height - minOf(prev.y, p.y),
          ),
        )
      }
    }
    points.forEach { p -> drawCircle(color = line, radius = 4f, center = p) }
  }
}

/** سلامِ متناسب با ساعت — همان چیزی که آدم به آدم می‌گوید */
private fun greeting(): String {
  val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
  return when {
    hour < 5 -> "شب بخیر"
    hour < 12 -> "صبح بخیر"
    hour < 17 -> "روز بخیر"
    else -> "شام بخیر"
  }
}

/**
 *  میان‌بُرِ یک کار.
 *
 *  آیکنِ رنگی در ظرفِ گرد، و نامِ کار زیرش. کوچک است تا چهارتایش در یک
 *  ردیف جا شود و اسکرول لازم نباشد.
 */
@Composable
private fun ShortcutCard(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  tint: Color,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  Column(
    modifier
      .glassSurface(Shape.card, Shop.colors.surface, Shop.colors.sheen, Shop.colors.border, glow = Shop.colors.glow)
      .clickable(onClick = onClick)
      .padding(vertical = 14.dp, horizontal = 8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      Modifier
        .size(40.dp)
        .clip(Shape.icon)
        .background(tint.copy(alpha = 0.16f)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
    Spacer(Modifier.height(8.dp))
    Text(
      label,
      style = MaterialTheme.typography.labelMedium,
      color = Shop.colors.text,
      maxLines = 1,
      textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
  }
}
