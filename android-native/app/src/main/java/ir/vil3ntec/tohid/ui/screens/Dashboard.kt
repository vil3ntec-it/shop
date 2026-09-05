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
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.ReportEngine
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.fa
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.isoMillis
import ir.vil3ntec.tohid.daysText
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Brush
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
  //  شاگرد عددهای مالیِ دکان را نمی‌بیند — سود، ضرر و مصارف
  val canSeeMoney = remember { ir.vil3ntec.tohid.data.ShopRole.canSeeMoney(context) }
  val today = todayIso()
  val monthPrefix = today.take(7)

  /*
   *  همهٔ حساب‌های این صفحه، **یک بار** به ازای هر دفتر.
   *
   *  تا دیروز هیچ‌کدام داخلِ `remember` نبودند. یعنی هر بار که صفحه
   *  دوباره کشیده می‌شد — یک اسنک‌بار، عوض شدنِ تم، رسیدنِ یک تیکِ
   *  همگام‌سازی — تمامِ این حساب‌ها از نو انجام می‌شدند: سودِ امروز
   *  (که خودش کلِ فاکتورها و اقلام را می‌گردد)، بدهیِ تک‌تکِ قرض‌داران،
   *  بدهی به تک‌تکِ تأمین‌کننده‌ها، و وضعیتِ موجودیِ همهٔ کالاها. روی
   *  همان صفحه‌ای که بیشترین وقت باز است.
   *
   *  `d` تغییرناپذیر است، پس تا وقتی همان دفتر است جواب هم همان است.
   */
  val view = remember(d, today) { DashboardNumbers.of(d, today) }


  Column(
    Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp)
  ) {
    /* ---------------------------- سلام ---------------------------- */
    //  اسمِ دکان و وقتِ روز — کوچک است ولی کاری می‌کند که برنامه انگار
    //  برای همین یک نفر ساخته شده، نه برای «کاربر»
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(greeting(), fontSize = 12.sp, color = Shop.colors.muted)
        Spacer(Modifier.height(2.dp))
        Text(
          storeName.ifBlank { "دکان شما" },
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
          color = Shop.colors.text,
          maxLines = 1,
        )
      }
      LetterAvatar(storeName.ifBlank { "دکان" }, 36.dp)
    }
    Spacer(Modifier.height(14.dp))

    /* ------------------------- کارتِ قهرمان ------------------------- */
    HeroSalesCard(view)

    /* ------------------------- یک اقدامِ غالب ------------------------- */
    //  چهار میان‌برِ هم‌وزن، یعنی هیچ‌کدام. صفحه هر بار **یک** کار را
    //  پیشنهاد می‌دهد، آن هم بر اساس وضعیتِ واقعیِ دکان.
    Spacer(Modifier.height(12.dp))
    DominantAction(view, onOpen)

    /* -------------------------- وضعیت دکان -------------------------- */
    Spacer(Modifier.height(12.dp))
    ShopStatusPanel(view, canSeeMoney, onOpen)

    /* ------------------------- معاملات اخیر ------------------------- */
    Spacer(Modifier.height(12.dp))
    RecentDeals(d, onOpen)

    /* ------------------------- کارهای دیگر ------------------------- */
    Spacer(Modifier.height(12.dp))
    MoreActionsRow(onOpen)

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
      view.outOfStock.take(3).forEach { add(Triple(it.name, "تمام‌شده", "products")) }
      view.lowStock.take(3).forEach { add(Triple(it.name, "موجودی کم", "products")) }
      view.owing.take(2).forEach { add(Triple(it.first.name, "${money(it.second)} افغانی طلب", "debtors")) }
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
          if (view.owing.isEmpty()) {
            EmptyNote("هنوز اطلاعاتی ثبت نشده")
          } else {
            view.owing.take(5).forEach { (debtor, amount) ->
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
              "تعداد محصولات" to view.warehouse.products.fa(),
              "تعداد کارتن" to qty(view.warehouse.cartons),
              "تعداد واحد" to qty(view.warehouse.units),
              "ارزش تقریبی موجودی" to "${money(view.warehouse.value)} افغانی",
            )
          )
          if (view.lowStock.isNotEmpty() || view.outOfStock.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            (view.outOfStock + view.lowStock).take(5).forEach { p ->
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
            buildList {
              add("فروش امروز" to "${money(view.todayTotal)} افغانی")
              add("تعداد فروش امروز" to view.todaySales.size.fa())
              //  سود و مصارف، عددهای مالیِ دکان‌اند — نه کارِ شاگرد
              if (canSeeMoney) {
                add("سود امروز" to "${money(view.todayProfit)} افغانی")
                add("مصارف امروز" to "${money(view.todayExpense)} افغانی")
              }
            }
          )
          Spacer(Modifier.height(8.dp))
          ChipRow(
            listOf(
              "بدهی تأمین‌کنندگان" to "${money(view.supplierDebt)} افغانی",
              "کالاهای کم‌موجودی" to view.lowStock.size.fa(),
              "کالاهای تمام‌شده" to view.outOfStock.size.fa(),
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

/**
 *  عددهای صفحهٔ خانه، یک‌جا و یک‌بار.
 *
 *  بیرونِ `@Composable` نوشته شده تا هم داخلِ `remember` بنشیند و هم
 *  بشود بدونِ گوشی سنجیدش. همهٔ فرمول‌ها همان‌اند که بودند؛ فقط جایشان
 *  عوض شده و از جدول‌های آمادهٔ `ShopStore` استفاده می‌کنند به‌جای
 *  پیمایشِ دوباره.
 */
data class DashboardNumbers(
  val todaySales: List<ir.vil3ntec.tohid.data.Sale>,
  val todayTotal: Double,
  val todayExpense: Double,
  val todayProfit: Double,
  val owing: List<Pair<ir.vil3ntec.tohid.data.Debtor, Double>>,
  val totalDebt: Double,
  val expenseMonth: Double,
  val supplierDebt: Double,
  val lowStock: List<ir.vil3ntec.tohid.data.Product>,
  val outOfStock: List<ir.vil3ntec.tohid.data.Product>,
  val warehouse: WarehouseEngine.Summary,
  val monthSalesTotal: Double,
  val bestDay: Double,
  /** فروشِ هفت روزِ گذشته و هفت روزِ پیش از آن — برای جمله‌ی روایتِ داده */
  val weekTotal: Double,
  val prevWeekTotal: Double,
  /** سودِ خالصِ همین ماه */
  val monthNet: Double,
  /** قرض‌هایی که بیش از یک ماه است حرکتی نکرده‌اند */
  val overdue: List<Pair<ir.vil3ntec.tohid.data.Debtor, Double>>,
  val overdueTotal: Double,
  val overdueOldestDays: Long,
) {
  companion object {
    fun of(d: ShopData, today: String): DashboardNumbers {
      val monthPrefix = today.take(7)
      val stock = ShopStore.index(d)

      val todaySales = d.sales.filter { it.date == today && it.status != "cancelled" }

      //  یک گذر روی تراکنش‌ها، نه یک پیمایش به ازای هر قرض‌دار
      val byDebtor = d.transactions.groupBy { it.debtorId }
      val owing = d.debtors
        .map { debtor ->
          var amount = 0.0
          byDebtor[debtor.id]?.forEach { amount += if (it.type == "give") it.amount else -it.amount }
          debtor to amount
        }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }

      /*
       *  دو هفته‌ی گذشته، برای جمله‌ای که زیرِ عددِ قهرمان می‌نشیند.
       *
       *  محلی حساب می‌شود و از همان فاکتورهای روی گوشی — نه اینترنت،
       *  نه موتورِ تازه.
       */
      val nowMs = isoMillis(today)
      val dayMs = 24L * 60 * 60 * 1000
      var weekTotal = 0.0
      var prevWeekTotal = 0.0
      d.sales.filter { it.status != "cancelled" }.forEach { sale ->
        val age = (nowMs - isoMillis(sale.date)) / dayMs
        when {
          age in 0..6 -> weekTotal += sale.finalTotal
          age in 7..13 -> prevWeekTotal += sale.finalTotal
        }
      }

      /*
       *  «عقب‌افتاده» یعنی چه، وقتی وعده‌ای ثبت نمی‌شود؟
       *
       *  دفترِ قرض تاریخِ وعده ندارد. پس ملاک، سکوت است: قرضی که هنوز
       *  باز است و بیش از سی روز هیچ حرکتی — نه قرضِ تازه، نه پرداخت —
       *  روی آن نبوده. همان چیزی که دکاندار هم با آن حساب می‌کند.
       */
      val lastMove = d.transactions
        .groupBy { it.debtorId }
        .mapValues { (_, list) -> list.maxOf { isoMillis(it.date) } }
      val overdue = owing.filter { (debtor, _) ->
        val seen = lastMove[debtor.id] ?: 0L
        seen > 0L && (nowMs - seen) / dayMs > 30
      }
      val overdueOldest = overdue.maxOfOrNull { (debtor, _) ->
        ((nowMs - (lastMove[debtor.id] ?: nowMs)) / dayMs).coerceAtLeast(0)
      } ?: 0L

      //  فروشِ ماه یک بار گروه‌بندی می‌شود و هم جمعش از آن در می‌آید هم
      //  بهترین روزش — نه دو پیمایشِ جدا
      val monthByDay = d.sales
        .filter { it.status != "cancelled" && it.date.startsWith(monthPrefix) }
        .groupBy { it.date }
        .mapValues { (_, list) -> list.sumOf { it.finalTotal } }

      return DashboardNumbers(
        todaySales = todaySales,
        todayTotal = todaySales.sumOf { it.finalTotal },
        todayExpense = d.expenses.filter { it.date == today }.sumOf { it.amount },
        todayProfit = ReportEngine.sales(d, today, today).netProfit,
        owing = owing,
        totalDebt = owing.sumOf { it.second },
        expenseMonth = d.expenses.filter { it.date.startsWith(monthPrefix) }.sumOf { it.amount },
        supplierDebt = d.suppliers.sumOf { ShopStore.supplierDebt(d, it.id) }.coerceAtLeast(0.0),
        lowStock = d.products.filter { stock.status(it) == "low" },
        outOfStock = d.products.filter { stock.status(it) == "out" },
        warehouse = WarehouseEngine.summary(d),
        monthSalesTotal = monthByDay.values.sum(),
        bestDay = monthByDay.values.maxOrNull() ?: 0.0,
        weekTotal = weekTotal,
        prevWeekTotal = prevWeekTotal,
        monthNet = ReportEngine.sales(d, monthPrefix + "-01", today).netProfit,
        overdue = overdue,
        overdueTotal = overdue.sumOf { it.second },
        overdueOldestDays = overdueOldest,
      )
    }
  }
}

/* ==================== اجزای تازه‌ی صفحه‌ی خانه ==================== */

/**
 *  آواتارِ حرفی — حرفِ اولِ نام روی یک مربعِ نرمِ گرادینتی.
 *
 *  جای عکسِ نداشته را می‌گیرد بدونِ اینکه یک آدمکِ خاکستری بگذارد.
 */
@Composable
fun LetterAvatar(name: String, size: androidx.compose.ui.unit.Dp, radius: androidx.compose.ui.unit.Dp = size / 3f) {
  val colors = Shop.colors
  val letter = name.trim().firstOrNull()?.toString() ?: "؟"
  Box(
    Modifier
      .size(size)
      .clip(RoundedCornerShape(radius))
      .background(Brush.linearGradient(listOf(colors.primary, colors.accent))),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      letter,
      fontSize = (size.value * 0.42f).sp,
      fontWeight = FontWeight.Bold,
      color = Color.White,
    )
  }
}

/**
 *  کارتِ قهرمان — فروشِ امروز.
 *
 *  یک عدد، بزرگ، روی گرادینتِ برند؛ و زیرش یک جمله که می‌گوید این عدد
 *  خوب است یا بد. عدد بی‌مقایسه، فقط یک رقم است.
 */
@Composable
private fun HeroSalesCard(view: DashboardNumbers) {
  val colors = Shop.colors
  Column(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Radius.lg))
      .background(Brush.linearGradient(listOf(colors.primary, colors.accent)))
      .padding(18.dp),
  ) {
    Text("فروش امروز", fontSize = 12.sp, color = Color.White.copy(alpha = 0.86f))
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.Bottom) {
      Text(
        money(animatedMoney(view.todayTotal)),
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
      )
      Spacer(Modifier.width(4.dp))
      Text("؋", fontSize = 17.sp, color = Color.White.copy(alpha = 0.86f))
    }
    Spacer(Modifier.height(8.dp))
    Text(
      weekStory(view),
      fontSize = 11.5.sp,
      color = Color.White.copy(alpha = 0.9f),
    )
  }
}

/** جمله‌ی روایتِ داده — از همان فاکتورهای روی گوشی، بدونِ اینترنت */
private fun weekStory(view: DashboardNumbers): String {
  val now = view.weekTotal
  val before = view.prevWeekTotal
  return when {
    now <= 0.0 && before <= 0.0 -> "این هفته هنوز فروشی ثبت نشده"
    before <= 0.0 -> "این هفته ${money(now)} ؋ فروختی — هفتهٔ گذشته چیزی ثبت نشده بود"
    else -> {
      val change = ((now - before) / before * 100).toInt()
      when {
        change > 0 -> "این هفته ${change.fa()}٪ بیشتر از هفتهٔ گذشته فروختی"
        change < 0 -> "این هفته ${(-change).fa()}٪ کمتر از هفتهٔ گذشته فروختی"
        else -> "این هفته اندازهٔ هفتهٔ گذشته فروختی"
      }
    }
  }
}

/**
 *  یک اقدام، نه چهار میان‌برِ هم‌وزن.
 *
 *  ترتیبِ اولویت از خودِ دکان می‌آید: اول قرضِ خوابیده، بعد جنسِ رو به
 *  ختم، و اگر هیچ‌کدام نبود، کارِ همیشگی — فاکتورِ تازه.
 */
@Composable
private fun DominantAction(view: DashboardNumbers, onOpen: (String) -> Unit) {
  val colors = Shop.colors
  val overdue = view.overdue.size
  val short = view.lowStock.size + view.outOfStock.size

  val tint: Color
  val title: String
  val detail: String
  val action: String
  val target: String
  when {
    overdue > 0 -> {
      tint = colors.danger
      title = "${overdue.fa()} قرض عقب‌افتاده"
      detail = "مجموع ${money(view.overdueTotal)} ؋ — قدیمی‌ترین ${daysText(view.overdueOldestDays)}"
      action = "پیگیری قرض‌ها"
      target = "debtors"
    }
    short > 0 -> {
      tint = colors.warning
      title = "${short.fa()} جنس رو به ختم است"
      detail = "پیش از تمام‌شدن سفارش بده"
      action = "سفارش بده"
      target = "warehouse"
    }
    else -> {
      tint = colors.primary
      title = "دکان مرتب است"
      detail = "نه قرضِ خوابیده، نه جنسِ رو به ختم"
      action = "فاکتور جدید"
      target = "sale"
    }
  }

  Panel(Modifier.fillMaxWidth()) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(
        Modifier
          .size(38.dp)
          .clip(RoundedCornerShape(13.dp))
          .background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          if (overdue > 0 || short > 0) Icons.Filled.WarningAmber else Icons.Filled.PointOfSale,
          contentDescription = null,
          tint = tint,
          modifier = Modifier.size(20.dp),
        )
      }
      Spacer(Modifier.width(10.dp))
      Column(Modifier.weight(1f)) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.text)
        Spacer(Modifier.height(2.dp))
        Text(detail, fontSize = 11.sp, color = colors.muted)
      }
    }
    Spacer(Modifier.height(12.dp))
    ActionButton(action, tint) { onOpen(target) }
  }
}

/** دکمه‌ی گرادینتیِ تمام‌عرض — رنگش از کاری می‌آید که می‌کند */
@Composable
private fun ActionButton(text: String, tint: Color, onClick: () -> Unit) {
  var pressed by remember { mutableStateOf(false) }
  val second = if (tint == Shop.colors.danger) Shop.colors.warning else Shop.colors.accent
  Box(
    Modifier
      .fillMaxWidth()
      .height(48.dp)
      .pressScale(pressed)
      .clip(RoundedCornerShape(Radius.sm))
      .background(Brush.linearGradient(listOf(tint, second)))
      .clickable {
        pressed = true
        onClick()
      },
    contentAlignment = Alignment.Center,
  ) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
  }
  LaunchedEffect(pressed) {
    if (pressed) {
      kotlinx.coroutines.delay(120)
      pressed = false
    }
  }
}

/**
 *  وضعیت دکان — ردیف‌های فشرده به‌جای شبکه‌ی کاشی‌های رنگی.
 *
 *  کاشی‌های رنگی با هم رقابت می‌کردند و هیچ‌کدام برنده نبود. ردیف با
 *  جداکننده‌ی نازک، همان عددها را می‌دهد بدونِ سر و صدا.
 */
@Composable
private fun ShopStatusPanel(view: DashboardNumbers, canSeeMoney: Boolean, onOpen: (String) -> Unit) {
  val colors = Shop.colors
  Panel(Modifier.fillMaxWidth()) {
    Text("وضعیت دکان", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.text)
    Spacer(Modifier.height(6.dp))
    if (canSeeMoney) {
      StatusLine("مفاد خالص ماه", "${money(view.monthNet)} ؋", colors.success) { onOpen("reports") }
    }
    StatusLine("فروش ماه", "${money(view.monthSalesTotal)} ؋", colors.text) { onOpen("reports") }
    StatusLine("ارزش انبار", "${money(view.warehouse.value)} ؋", colors.warning) { onOpen("products") }
    StatusLine("قرض بیرون", "${money(view.totalDebt)} ؋", colors.danger) { onOpen("debtors") }
    StatusLine(
      "اجناس رو به ختم",
      (view.lowStock.size + view.outOfStock.size).fa(),
      colors.warning,
      last = true,
    ) { onOpen("products") }
  }
}

@Composable
private fun StatusLine(
  label: String,
  value: String,
  tint: Color,
  last: Boolean = false,
  onClick: () -> Unit,
) {
  val colors = Shop.colors
  Row(
    Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(vertical = 11.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, fontSize = 13.sp, color = colors.muted, modifier = Modifier.weight(1f))
    Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = tint)
  }
  if (!last) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border.copy(alpha = colors.border.alpha * 0.6f)))
  }
}

/**
 *  معاملات اخیر، با مانده‌ی جاری.
 *
 *  مبلغِ تنها می‌گوید «چقدر»، ولی نمی‌گوید «کجای روز». مانده‌ی جاری —
 *  جمعِ فروشِ همان روز تا همین فاکتور — همان چیزی است که دکاندار روی
 *  دفترِ کاغذی هم زیرِ هر خط می‌نویسد.
 */
@Composable
private fun RecentDeals(d: ShopData, onOpen: (String) -> Unit) {
  val colors = Shop.colors
  val recent = remember(d) {
    d.sales.filter { it.status != "cancelled" }.sortedByDescending { it.createdAt }.take(6)
  }
  //  مانده‌ی جاری: جمعِ فروشِ همان روز تا این فاکتور
  val running = remember(d, recent) {
    val byDay = d.sales.filter { it.status != "cancelled" }.groupBy { it.date }
    recent.associate { sale ->
      sale.id to (byDay[sale.date].orEmpty()
        .filter { it.createdAt <= sale.createdAt }
        .sumOf { it.finalTotal })
    }
  }
  Panel(Modifier.fillMaxWidth()) {
    PanelHead("معاملات اخیر", "مشاهده همه") { onOpen("sales") }
    Spacer(Modifier.height(6.dp))
    if (recent.isEmpty()) {
      EmptyNote("هنوز فاکتوری ثبت نشده")
      return@Panel
    }
    recent.forEachIndexed { index, sale ->
      val debt = sale.remaining > 0
      val tint = if (debt) colors.warning else colors.success
      Row(
        Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
          Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.14f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            if (debt) Icons.Filled.Groups else Icons.Filled.PointOfSale,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(17.dp),
          )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
          Text(
            "فاکتور ${(sale.invoiceNumber ?: 0).fa()}",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = colors.text,
          )
          Spacer(Modifier.height(1.dp))
          Text(
            "${if (debt) "قرضی" else "نقد"} · ${formatDate(sale.date)}",
            fontSize = 10.5.sp,
            color = colors.muted2,
          )
        }
        Column(horizontalAlignment = Alignment.End) {
          Text(
            "${money(sale.finalTotal)} ؋",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = colors.text,
          )
          Text(
            "ماندهٔ روز: ${money(running[sale.id] ?: 0.0)}",
            fontSize = 10.sp,
            color = colors.muted2,
          )
        }
      }
      if (index != recent.lastIndex) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border.copy(alpha = colors.border.alpha * 0.5f)))
      }
    }
  }
}

/**
 *  کارهای دیگر — یک ردیف، نه چهار کارتِ رنگی روی صفحه‌ی اصلی.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun MoreActionsRow(onOpen: (String) -> Unit) {
  val colors = Shop.colors
  var open by remember { mutableStateOf(false) }
  Row(
    Modifier
      .fillMaxWidth()
      .clip(Shape.card)
      .background(colors.surface)
      .clickable { open = true }
      .padding(horizontal = 16.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text("کارهای دیگر", fontSize = 13.sp, color = colors.text, modifier = Modifier.weight(1f))
    Text("فروش سریع · ورود کالا · گزارش‌ها", fontSize = 10.5.sp, color = colors.muted2)
  }
  if (open) {
    androidx.compose.material3.ModalBottomSheet(
      onDismissRequest = { open = false },
      containerColor = colors.surfaceSolid,
    ) {
      Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 28.dp)) {
        listOf(
          Triple("فروش سریع", Icons.Filled.PointOfSale, "sale"),
          Triple("ورود کالا", Icons.Filled.Inventory2, "warehouse"),
          Triple("قرض‌داران", Icons.Filled.Groups, "debtors"),
          Triple("گزارش‌ها", Icons.Filled.BarChart, "reports"),
        ).forEach { (title, icon, target) ->
          Row(
            Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .clickable { open = false; onOpen(target) }
              .padding(vertical = 12.dp, horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(title, fontSize = 14.sp, color = colors.text)
          }
        }
      }
    }
  }
}
