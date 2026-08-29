package ir.vil3ntec.tohid.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.data.PhotoStore
import ir.vil3ntec.tohid.data.ReportEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.toFaDigits
import ir.vil3ntec.tohid.ui.theme.Shape
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.Space
import kotlinx.coroutines.launch

/**
 *  صفحهٔ یک کالا.
 *
 *  تا حالا برای دیدنِ وضعیتِ یک کالا باید چند جا سر می‌زدی: قیمتش در
 *  محصولات، موجودی‌اش در انبار، فروشش در گزارش‌ها. اینجا همه‌اش یک‌جاست.
 *
 *  عددی که بیشتر از همه لازم است و هیچ‌جا نبود، «سودِ بالقوهٔ موجودی»
 *  است: اگر همهٔ آنچه در انبار مانده به قیمتِ امروز فروخته شود، چقدر سود
 *  می‌ماند. فروشنده با همین عدد تصمیم می‌گیرد قیمت را دست بزند یا نه.
 */
@Composable
fun ProductDetailScreen(
  store: ShopStore,
  d: ShopData,
  productId: String,
  onBack: () -> Unit,
  onEdit: () -> Unit,
  onEntry: () -> Unit,
) {
  val product = d.products.find { it.id == productId }
  if (product == null) {
    TohidErrorState(
      title = "این کالا دیگر نیست",
      description = "شاید حذف شده باشد. به فهرست برگردید.",
      onRetry = onBack,
    )
    return
  }

  val stock = ShopStore.stock(d, product.id)
  val status = ShopStore.stockStatus(d, product)
  val (sold, profit) = ReportEngine.productStat(d, product.id)
  val unitProfit = product.salePrice - product.purchasePrice
  val potential = unitProfit * stock.coerceAtLeast(0.0)

  val tint = when (status) {
    "out" -> Shop.colors.danger
    "low" -> Shop.colors.warning
    else -> Shop.colors.success
  }
  val label = when (status) {
    "out" -> "تمام‌شده"
    "low" -> "موجودی کم"
    else -> "موجودی کافی"
  }

  /* ---------------------------- عکس ---------------------------- */
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  // با عوض شدنِ عکس، همین عدد بالا می‌رود تا نسخهٔ کش‌شده دور ریخته شود
  var photoVersion by remember(productId) { mutableStateOf(0) }

  val pickPhoto = rememberLauncherForActivityResult(
    ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri == null) return@rememberLauncherForActivityResult
    PhotoStore.save(context, product.id, uri).onSuccess {
      // نشانهٔ عکس روی خودِ محصول می‌نشیند، همان فیلدی که وب می‌نویسد
      scope.launch {
        store.save(
          d.copy(products = d.products.map { if (it.id == product.id) it.copy(photo = true) else it })
        )
      }
      photoVersion++
    }
  }

  fun choosePhoto() {
    pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
  }

  val movements = d.stockMovements
    .filter { it.productId == product.id }
    .sortedByDescending { it.createdAt }
    .take(12)

  LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(Space.md)) {
    item {
      TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
        Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = Shop.colors.primary)
        Spacer(Modifier.width(Space.xxs + 2.dp))
        Text("بازگشت", color = Shop.colors.primary)
      }
      Spacer(Modifier.height(Space.xs))

      /* --------------------------- سربرگ --------------------------- */
      Row(verticalAlignment = Alignment.CenterVertically) {
        // عکس همیشه هست، حتی وقتی هنوز عکسی گرفته نشده: جای خالی با
        // نشانِ دوربین می‌گوید «اینجا می‌شود عکس گذاشت». قبلاً کلاً پنهان
        // بود و راهِ گذاشتنش فقط نگه‌داشتنِ انگشت روی کارتِ فهرست.
        Box(contentAlignment = Alignment.BottomEnd) {
          ProductPhoto(
            productId = product.id,
            size = 72.dp,
            version = photoVersion,
            modifier = Modifier.clickable { choosePhoto() },
          )
          Box(
            Modifier
              .offset(x = (-4).dp, y = (-4).dp)
              .size(22.dp)
              .clip(RoundedCornerShape(11.dp))
              .background(Shop.colors.primary),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              Icons.Filled.PhotoCamera,
              contentDescription = "عکس محصول",
              tint = Color.White,
              modifier = Modifier.size(13.dp),
            )
          }
        }
        Spacer(Modifier.width(Space.sm))
        Column(Modifier.weight(1f)) {
          Text(
            product.name,
            style = MaterialTheme.typography.headlineSmall,
            color = Shop.colors.text,
            fontWeight = FontWeight.Bold,
          )
          if (product.category.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(product.category, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
          }
          if (product.barcodes.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
              product.barcodes.joinToString("، ") { it.toFaDigits() },
              style = MaterialTheme.typography.labelSmall,
              color = Shop.colors.muted2,
            )
          }
        }
        IconButton(onClick = onEdit) {
          Icon(Icons.Filled.Edit, contentDescription = "ویرایش", tint = Shop.colors.primary)
        }
      }

      Spacer(Modifier.height(Space.md))

      /* -------------------------- موجودی -------------------------- */
      Row(
        Modifier
          .fillMaxWidth()
          .clip(Shape.card)
          .background(Shop.colors.surface)
          .border(1.dp, tint, Shape.card)
          .padding(Space.md),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(Modifier.weight(1f)) {
          Text("موجودی فعلی", style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
          Spacer(Modifier.height(Space.xxs))
          Text(
            "${qty(stock)}${if (product.unit.isNotBlank()) " ${product.unit}" else ""}",
            style = MaterialTheme.typography.headlineSmall,
            color = tint,
            fontWeight = FontWeight.Bold,
          )
        }
        TohidBadge(
          text = label,
          tint = tint,
          fill = when (status) {
            "out" -> Shop.colors.dangerTint
            "low" -> Shop.colors.warningTint
            else -> Shop.colors.successTint
          },
        )
      }

      Spacer(Modifier.height(Space.sm))

      /* -------------------------- قیمت‌ها -------------------------- */
      TohidCard {
        DetailLine("قیمت فروش", "${money(product.salePrice)} افغانی")
        DetailLine("قیمت خرید", "${money(product.purchasePrice)} افغانی")
        HorizontalDivider(Modifier.padding(vertical = Space.xs), color = Shop.colors.border)
        DetailLine(
          "سود هر ${product.unit.ifBlank { "واحد" }}",
          "${money(unitProfit)} افغانی",
          tint = if (unitProfit >= 0) Shop.colors.success else Shop.colors.danger,
        )
        DetailLine(
          "سود موجودی فعلی",
          "${money(potential)} افغانی",
          tint = if (potential >= 0) Shop.colors.success else Shop.colors.danger,
        )
        DetailLine("حداقل موجودی", qty(product.minStock))
      }

      Spacer(Modifier.height(Space.sm))

      /* --------------------------- فروش --------------------------- */
      Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        TohidStatCard(
          label = "فروخته‌شده",
          value = "${qty(sold)}${if (product.unit.isNotBlank()) " ${product.unit}" else ""}",
          hint = "از آغاز",
          modifier = Modifier.weight(1f),
        )
        TohidStatCard(
          label = "سود کسب‌شده",
          value = "${money(profit)} افغانی",
          tint = if (profit >= 0) Shop.colors.success else Shop.colors.danger,
          hint = "پس از مرجوعی",
          modifier = Modifier.weight(1f),
        )
      }

      Spacer(Modifier.height(Space.sm))
      TohidButton(text = "ورود کالا به انبار", onClick = onEntry, modifier = Modifier.fillMaxWidth())

      Spacer(Modifier.height(Space.lg))
      TohidSectionHeader("حرکت‌های اخیر")
    }

    if (movements.isEmpty()) {
      item {
        TohidCard {
          TohidEmptyState(
            title = "هنوز حرکتی ثبت نشده",
            description = "ورود کالا، فروش و مرجوعیِ این کالا اینجا فهرست می‌شود.",
          )
        }
      }
    } else {
      items(movements, key = { it.id }) { m ->
        TohidTransactionRow(
          title = movementLabelOf(m.type),
          subtitle = buildString {
            append(formatDate(m.date))
            if (m.notes.isNotBlank()) append(" — ${m.notes}")
          },
          amount = kotlin.math.abs(m.qty),
          tint = if (m.qty > 0) Shop.colors.success else Shop.colors.danger,
          currency = "",
        )
      }
    }

    item { Spacer(Modifier.height(Space.xl)) }
  }
}

@Composable
private fun DetailLine(label: String, value: String, tint: androidx.compose.ui.graphics.Color = Shop.colors.text) {
  Row(
    Modifier.fillMaxWidth().padding(vertical = Space.xxs),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
    Text(value, style = MaterialTheme.typography.bodyMedium, color = tint, fontWeight = FontWeight.Bold)
  }
}
