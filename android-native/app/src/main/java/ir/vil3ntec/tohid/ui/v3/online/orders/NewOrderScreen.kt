package ir.vil3ntec.tohid.ui.v3.online.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.OnlineFmt
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.i18n.StrOrdersHub
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.orders.OrderDraft
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.ui.components.pos.PhoneCatalogItem
import ir.vil3ntec.tohid.ui.components.pos.PhoneListRow
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.LocalV3Toasts
import ir.vil3ntec.tohid.ui.v3.V3AddChip
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.V3Collapse
import ir.vil3ntec.tohid.ui.v3.V3ConfirmSheet
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3Form
import ir.vil3ntec.tohid.ui.v3.V3FormBar
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Numbers
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Search
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3SwipeAction
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3ToggleRow
import ir.vil3ntec.tohid.ui.v3.sales.cart.CartModel
import ir.vil3ntec.tohid.ui.v3.sales.cart.CatalogEmpty
import ir.vil3ntec.tohid.ui.v3.sales.cart.CatalogModel
import ir.vil3ntec.tohid.ui.v3.sales.checkout.CustomerPickerSheet
import ir.vil3ntec.tohid.ui.v3.v3Shake

/** دو گامِ «سفارشِ دستی»: انتخابِ کالا (مثلِ سبد) و جزئیاتِ سفارش */
enum class ManualStep { PICK, DETAILS }

/** برچسب‌های آزمون */
const val MANUAL_FREE_LINE = "manual-free-line"
const val MANUAL_NEXT = "manual-next"

/**
 *  «سفارشِ دستی» (`orders/new`) — صاحب‌کار ۱۴۰۵/۰۸/۲۴: «محصولات رو انتخاب کنم — مثلِ سبد … و ساده».
 *
 *  گامِ ۱ (کالا): همان کاتالوگِ صندوق — جست‌وجو با اسکن (`V3Search`)، تراشه‌های دسته، ردیف‌های آرامِ فهرست (`PhoneListRow`:
 *  زدن ⇐ +۱، «−»، نگه داشتن ⇐ مقدار و فی) با موجودی و قیمتِ خودِ کالا، و ردیفِ «قلمِ دستی (بیرون از فهرست)». سبدِ این فرم
 *  جدا از صندوق است (`ManualOrderModel`، همان `CartModel`). نوارِ پایین «۳ قلم · جمع» و «ادامه».
 *
 *  گامِ ۲ (جزئیات، `V3Form`): اقلامِ برگزیده (زدن ⇐ مقدار/فی، کشیدن ⇐ حذف با «برگرداندن»، «+ افزودنِ کالا»، «+ قلمِ دستی»)،
 *  مشتری (نام اجباری، شماره، «از مشتری‌ها»)، تحویل «حضوری | ارسال» (نشانی فقط برای ارسال)، و زیرِ «بیشتر»: از کجا،
 *  یادداشت، کادرهای پشتِ پرچمِ سرور و «سفارشِ آزمایشی». نوارِ چسبانِ پایین جمع و «ثبتِ سفارش». ثبت یک کلیدِ یکتا دارد
 *  (صدازننده)؛ بدنه همان `OrderDraft.build` (قلمِ دستی بایت‌به‌بایت همان پیشین؛ کالا با `productId` و واحد).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewOrderScreen(
  sources: List<String>,
  busy: Boolean,
  failure: String?,
  onSubmit: (OrderDraft.Form) -> Unit,
  onBack: () -> Unit,
  start: OrderDraft.Form = OrderDraft.Form(),
  showErrors: Boolean = false,
  /** کادرهای پشتِ پرچمِ سرور (CHK-14 پولِ نقدِ مشتری، CHK-15 «اگر کالایی نبود») — `OrderExtrasAndroid.kt` */
  extras: @Composable (form: OrderDraft.Form, onForm: (OrderDraft.Form) -> Unit) -> Unit = { _, _ -> },
  /** دفترِ دکان: کالاها، دسته‌ها، موجودی و مشتری‌ها */
  d: ShopData = ShopData(),
  startCart: List<SalesEngine.CartLine> = emptyList(),
  startStep: ManualStep? = null,
  /** اسکنِ بارکد (`scan?for=order`) ⇒ کد؛ null ⇒ بی دکمهٔ اسکن */
  onScan: ((onCode: (String) -> Unit) -> Unit)? = null,
) {
  val toasts = LocalV3Toasts.current
  var form by remember { mutableStateOf(start.copy(lines = ManualOrderModel.freeLines(start))) }
  var cart by remember { mutableStateOf(startCart) }
  var step by rememberSaveable {
    mutableStateOf(startStep ?: if (showErrors || startCart.isNotEmpty() || ManualOrderModel.freeLines(start).isNotEmpty()) ManualStep.DETAILS else ManualStep.PICK)
  }
  var tried by remember { mutableIntStateOf(if (showErrors) 1 else 0) }
  /** برگهٔ قلم: کالا (شناسه) یا قلمِ دستی (شماره؛ −۱ ⇒ تازه) */
  var editProduct by remember { mutableStateOf<String?>(null) }
  var editFree by remember { mutableStateOf<Int?>(null) }
  var picking by remember { mutableStateOf(false) }
  var askDiscard by remember { mutableStateOf(false) }

  val merged = ManualOrderModel.merged(form, d, cart)
  val count = merged.lines.size
  val summary = count.takeIf { it > 0 }?.let { StrOnline.linesSummary(it, OnlineFmt.afn(ManualOrderModel.totalMinor(merged))) }
  val dirty = count > 0 || NewOrderModel.dirty(form.copy(lines = OrderDraft.Form().lines))
  fun freeLines(l: List<OrderDraft.LineInput>) { form = form.copy(lines = l) }
  fun note(text: String?) { text?.let { toasts?.show(it) } }
  fun apply(s: CartModel.Step) { s.lines?.let { cart = it }; note(s.note) }

  if (askDiscard) {
    V3ConfirmSheet(StrKit.discardTitle, StrKit.discardBody, StrKit.discard, onConfirm = onBack, onDismiss = { askDiscard = false })
  }

  when (step) {
    ManualStep.PICK -> {
      ManualOrderBack(enabled = dirty) { askDiscard = true }
      ManualCatalog(
        d = d,
        cart = cart,
        summary = summary,
        canNext = count > 0,
        onBack = { if (dirty) askDiscard = true else onBack() },
        onNext = { step = ManualStep.DETAILS },
        onTap = { item -> if (item.weighed) { editProduct = item.id } else { apply(CartModel.addOne(d, cart, item.id)) } },
        onCount = { item, n -> apply(CartModel.addCount(d, cart, item.id, n)) },
        onMinus = { item -> CartModel.minusOne(d, cart, item.id)?.let { cart = it } },
        onHold = { item -> editProduct = item.id },
        onScan = onScan?.let { scan ->
          {
            scan { code ->
              when (val r = CartModel.scan(d, ShopStore.barcodeIndex(d), cart, code)) {
                is CartModel.Scan.Added -> { cart = r.lines; note(CartModel.scanNote(r)) }
                null -> Unit
                else -> note(StrOrdersHub.unknownCode(code))
              }
            }
          }
        },
      )
    }
    ManualStep.DETAILS -> {
      ManualOrderBack { step = ManualStep.PICK }
      //  نامِ خالی ⇒ «مشتریِ حضوری» (هرگز سدِ ثبت نیست)
      val ready = NewOrderModel.withDefaultName(merged, StrOrdersHub.walkInCustomer)
      val errors = if (tried > 0) NewOrderModel.errors(ready) else NewOrderModel.Errors()
      ManualDetails(
        form = form,
        merged = merged,
        sources = sources,
        d = d,
        cart = cart,
        errors = errors,
        failure = failure,
        busy = busy,
        summary = summary,
        onForm = { form = it },
        onBack = { step = ManualStep.PICK },
        onSubmit = { if (NewOrderModel.errors(ready).isEmpty) onSubmit(ready) else tried++ },
        onEditProduct = { editProduct = it },
        onRemoveProduct = { id ->
          val before = cart
          cart = cart.filter { it.productId != id }
          toasts?.undo(StrOnline.lineRemoved) { cart = before }
        },
        onEditFree = { editFree = it },
        onRemoveFree = { i ->
          val before = form.lines
          freeLines(before.filterIndexed { j, _ -> j != i })
          toasts?.undo(StrOnline.lineRemoved) { freeLines(before) }
        },
        onAddProducts = { step = ManualStep.PICK },
        onPickCustomer = { picking = true },
        extras = extras,
      )
    }
  }

  editProduct?.let { id ->
    val p = ShopStore.product(d, id)
    if (p == null) { editProduct = null; return@let }
    val line = ManualOrderModel.inputOf(d, cart, id)
      ?: ManualOrderModel.inputOf(d, listOf(SalesEngine.CartLine(id, 1.0)), id)
      ?: return@let
    OrderLineSheet(
      index = 0,
      start = line,
      product = true,
      onSave = { n -> cart = ManualOrderModel.applyEdit(d, cart, id, n) },
      onDismiss = { editProduct = null },
      onDelete = if (cart.any { it.productId == id }) ({ cart = cart.filter { it.productId != id } }) else null,
    )
  }
  //  p0r3: قلمِ دستیِ تازه ساخته نمی‌شود (صاحب‌کار: «اینو بردار؛ قیمتِ جنس‌هایی که انتخاب می‌کنم بسه»)؛ قلمِ دستیِ سفارشی که از پیش
  //  داشت (تکرار/ویرایش) همان‌طور دیده، ویرایش و برداشته می‌شود — داده و همگام‌سازی دست نخورد
  editFree?.let { i ->
    val line = form.lines.getOrNull(i) ?: return@let
    OrderLineSheet(
      index = i,
      start = line,
      onSave = { n -> freeLines(form.lines.toMutableList().also { it[i] = n }) },
      onDismiss = { editFree = null },
      onDelete = {
        val before = form.lines
        freeLines(before.filterIndexed { j, _ -> j != i })
        toasts?.undo(StrOnline.lineRemoved) { freeLines(before) }
      },
    )
  }
  if (picking) {
    CustomerPickerSheet(
      d = d,
      selected = null,
      onPick = { id ->
        d.debtors.find { it.id == id }?.let { c ->
          form = form.copy(name = c.name.take(NewOrderModel.NAME_MAX), phone = c.phone.take(NewOrderModel.PHONE_MAX).ifBlank { form.phone })
        }
        picking = false
      },
      onNew = { picking = false },
      onClose = { picking = false },
    )
  }
}

/** گامِ ۱: کاتالوگِ صندوق روی سبدِ همین سفارش */
@Composable
private fun ManualCatalog(
  d: ShopData,
  cart: List<SalesEngine.CartLine>,
  summary: String?,
  canNext: Boolean,
  onBack: () -> Unit,
  onNext: () -> Unit,
  onTap: (PhoneCatalogItem) -> Unit,
  onCount: (PhoneCatalogItem, Double) -> Unit,
  onMinus: (PhoneCatalogItem) -> Unit,
  onHold: (PhoneCatalogItem) -> Unit,
  onScan: (() -> Unit)?,
) {
  var query by rememberSaveable { mutableStateOf("") }
  var picked by rememberSaveable { mutableStateOf<String?>(null) }
  val category = CatalogModel.keepCategory(picked, d.productCategories)
  val codes = ir.vil3ntec.tohid.ui.components.product.productInfoOn()
  val index = remember(d.products, d.sales, d.saleItems, codes) { CatalogModel.index(d, codes) }
  val inCart = remember(cart) { CartModel.inCart(cart) }
  val items = remember(index, query, category, inCart, d) { CatalogModel.items(d, index, query, category, inCart) }
  val empty = CatalogModel.empty(index, items.size, query)
  val chips: (@Composable () -> Unit)? = if (d.productCategories.isEmpty()) null else ({
    V3ChipRow {
      V3Chip(StrSale.allChip, category == null, { picked = null })
      d.productCategories.forEach { name -> V3Chip(name, category == name, { picked = if (category == name) null else name }) }
    }
  })

  V3Scaffold(
    title = StrOnline.manualOrder,
    onBack = onBack,
    notice = null,
    search = V3Search(query, { query = it }, StrSale.catalogHint, onScan = onScan),
    chips = chips,
    bottomBar = {
      Box(Modifier.testTag(MANUAL_NEXT)) { V3FormBar(StrOrdersHub.pickNext, onNext, primaryEnabled = canNext, summary = summary) }
    },
  ) { pad ->
    LazyColumn(
      Modifier.fillMaxSize(),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = pad.calculateBottomPadding() + 8.dp),
    ) {
      when (empty) {
        CatalogEmpty.NO_PRODUCTS -> item(key = "empty") {
          V3EmptyState(Icons.Outlined.Inventory2, StrOrdersHub.noProducts, body = StrOrdersHub.noProductsManual)
        }
        CatalogEmpty.NO_MATCH -> item(key = "empty") { V3EmptyState(Icons.Outlined.SearchOff, StrSale.noItemFor(query.trim())) }
        CatalogEmpty.EMPTY_CATEGORY -> item(key = "empty") { V3EmptyState(Icons.Outlined.Category, StrSale.emptyCategory) }
        CatalogEmpty.NONE -> itemsIndexed(items, key = { _, it -> it.id }) { i, item ->
          PhoneListRow(
            item, first = i == 0, last = i == items.lastIndex,
            onTap = {
              val q = CatalogModel.query(query, d)
              if (q.count != 1.0) { onCount(it, q.count); query = "" } else onTap(it)
            },
            onHold = onHold,
            onMinus = onMinus,
          )
        }
      }
    }
  }
}

/** گامِ ۲: اقلام، مشتری، تحویل و «بیشتر» */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManualDetails(
  form: OrderDraft.Form,
  merged: OrderDraft.Form,
  sources: List<String>,
  d: ShopData,
  cart: List<SalesEngine.CartLine>,
  errors: NewOrderModel.Errors,
  failure: String?,
  busy: Boolean,
  summary: String?,
  onForm: (OrderDraft.Form) -> Unit,
  onBack: () -> Unit,
  onSubmit: () -> Unit,
  onEditProduct: (String) -> Unit,
  onRemoveProduct: (String) -> Unit,
  onEditFree: (Int) -> Unit,
  onRemoveFree: (Int) -> Unit,
  onAddProducts: () -> Unit,
  onPickCustomer: () -> Unit,
  extras: @Composable (form: OrderDraft.Form, onForm: (OrderDraft.Form) -> Unit) -> Unit,
) {
  val delivery = form.fulfillment == "delivery"
  val products = ManualOrderModel.productInputs(d, cart)
  //  «بیشتر» خودش باز است وقتی چیزی در آن نوشته شده
  var more by rememberSaveable { mutableStateOf(form.note.isNotBlank() || form.test || form.source != OrderDraft.Form().source) }
  fun err(f: NewOrderModel.Field) = errors.fields[f]

  V3Form(
    title = StrOnline.manualOrder,
    primary = StrOnline.placeOrder,
    onPrimary = onSubmit,
    onBack = onBack,
    loading = busy,
    summary = summary,
  ) {
    if (failure != null) V3Message(V3Tone.WARNING, failure, Modifier.padding(top = 12.dp).v3Shake(failure))

    V3Section(StrOnline.itemsTitle, footer = err(NewOrderModel.Field.LINES)) {
      products.forEachIndexed { i, l ->
        if (i > 0) V3Divider(start = 64.dp)
        ItemRow(l, errors.line(i), Icons.Outlined.Inventory2, V3Hue.BRAND, { onEditProduct(l.productId) }, { onRemoveProduct(l.productId) })
      }
      form.lines.forEachIndexed { j, l ->
        if (products.isNotEmpty() || j > 0) V3Divider(start = 64.dp)
        ItemRow(l, errors.line(products.size + j), Icons.Outlined.ShoppingBag, V3Hue.AMBER, { onEditFree(j) }, { onRemoveFree(j) })
      }
      if (merged.lines.isNotEmpty()) V3Divider()
      FlowRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        V3AddChip(StrOrdersHub.addProducts, onAddProducts)
      }
    }

    V3Section(StrOnline.customer, action = if (d.debtors.isNotEmpty()) StrOrdersHub.pickCustomer else null, onAction = onPickCustomer) {
      Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        //  اختیاری: مشتریِ برگزیده نامش را خودش می‌آورد؛ خالی ⇒ «مشتریِ حضوری» (همان نوشتهٔ کم‌رنگِ کادر)
        V3TextField(
          form.name, { onForm(form.copy(name = it.take(NewOrderModel.NAME_MAX))) }, StrOnline.customerName,
          placeholder = StrOrdersHub.walkInCustomer, error = err(NewOrderModel.Field.NAME), modifier = Modifier.fillMaxWidth(),
        )
        V3TextField(
          form.phone, { onForm(form.copy(phone = it.take(NewOrderModel.PHONE_MAX))) }, StrOnline.phone,
          kind = V3FieldKind.PHONE, required = delivery, error = err(NewOrderModel.Field.PHONE), modifier = Modifier.fillMaxWidth(),
        )
      }
    }

    V3Section(StrOnline.fulfilment) {
      Column(Modifier.padding(12.dp)) {
        V3Segmented(
          listOf(StrOnline.pickup, StrOnline.delivery),
          if (delivery) 1 else 0,
          { onForm(form.copy(fulfillment = if (it == 1) "delivery" else "pickup")) },
        )
        V3Collapse(delivery) {
          Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            V3TextField(
              form.addressText, { onForm(form.copy(addressText = it.take(NewOrderModel.ADDRESS_MAX))) }, StrOnline.address,
              error = err(NewOrderModel.Field.ADDRESS), singleLine = false, modifier = Modifier.fillMaxWidth(),
            )
            V3TextField(form.landmark, { onForm(form.copy(landmark = it.take(NewOrderModel.LANDMARK_MAX))) }, StrOnline.landmark, modifier = Modifier.fillMaxWidth())
            V3TextField(
              form.deliveryFee, { onForm(form.copy(deliveryFee = it.take(NewOrderModel.NUMBER_MAX))) }, StrOnline.deliveryFee,
              kind = V3FieldKind.MONEY, error = err(NewOrderModel.Field.FEE), modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      }
    }

    V3Section(null) {
      V3ListRow(StrOrdersHub.more, subtitle = StrOrdersHub.moreHint, icon = Icons.Outlined.Tune, hue = V3Hue.SKY, onClick = { more = !more })
      V3Collapse(more) {
        Column {
          V3Divider()
          FlowRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sources.forEach { s -> V3Chip(StrOnline.orderSource(s), form.source == s, { onForm(form.copy(source = s)) }) }
          }
          V3TextField(
            form.note, { onForm(form.copy(note = it.take(NewOrderModel.NOTE_MAX))) }, StrOnline.note,
            singleLine = false, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
          )
          Column(Modifier.padding(horizontal = 4.dp)) { extras(form, onForm) }
          V3ToggleRow(StrOnline.testOrder, form.test, { onForm(form.copy(test = it)) }, subtitle = StrOnline.testHelper, icon = Icons.Outlined.Science, hue = V3Hue.VIOLET)
        }
      }
    }
    Spacer(Modifier.height(24.dp))
  }
}

/** ردیفِ یک قلم: نام، «۲ کیلو × ۱۵۰» و جمعِ همان قلم؛ زدن ⇐ برگه، کشیدن ⇐ حذف */
@Composable
private fun ItemRow(
  l: OrderDraft.LineInput,
  error: String?,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  hue: V3Hue,
  onClick: () -> Unit,
  onRemove: () -> Unit,
) {
  val price = l.price.takeIf { it.isNotBlank() }?.let(V3Numbers::group) ?: "—"
  V3ListRow(
    title = l.name,
    subtitle = error ?: StrOrdersHub.lineOf(l.qty.ifBlank { "1" }, l.unit, price),
    icon = icon,
    hue = hue,
    value = if (error == null) ManualOrderModel.lineMinor(l)?.let(OnlineFmt::afn) else null,
    trailing = if (error != null) ({ Icon(Icons.Outlined.ErrorOutline, contentDescription = error, tint = PhoneV3Roles.errorText(PhoneV3.colors)) }) else null,
    endActions = listOf(V3SwipeAction(Icons.Outlined.DeleteOutline, StrOnline.removeLine, V3ButtonKind.DANGER, onRemove)),
    onClick = onClick,
  )
}
