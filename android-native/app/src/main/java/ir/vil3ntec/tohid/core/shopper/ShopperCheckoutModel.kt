package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.text.LocaleFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicInteger

data class CheckoutState(
  val loading: Boolean = true,
  val loadFailure: ApiFailure? = null,
  val store: StoreProfile? = null,
  val verificationAvailable: Boolean = false,
  val cart: ShopCart? = null,
  /** قیمت یا موجودیِ سبد هنگامِ باز شدن تازه شد */
  val refreshed: RefreshNotice? = null,
  val form: CheckoutForm = CheckoutForm(),
  val slots: SlotList? = null,
  val slotsLoading: Boolean = false,
  val located: ZoneLookup? = null,
  val locating: Boolean = false,
  val totals: CheckoutTotals? = null,
  val problems: List<FormProblem> = emptyList(),
  /** یک بار «ثبت» زده شد ⇒ مسئله‌ها کنارِ میدان‌ها نشان داده می‌شوند */
  val attempted: Boolean = false,
  val submitting: Boolean = false,
  val issue: CheckoutIssue? = null,
  /** قیمت‌هایی که سرور هنگامِ ثبت عوض‌شده گفت — برای جملهٔ «اگر می‌پذیرید، دوباره بزنید» */
  val priceChanges: List<PriceChange> = emptyList(),
  /**
   *  شرایطِ دکان (هزینهٔ ارسال، حداقل، ناحیه‌ها) کهنه است و هنوز تازه نشد —
   *  ثبت بسته است تا همان بدنه با همان جمعِ کهنه دوباره نرود.
   */
  val termsStale: Boolean = false,
  val placed: PlacedOrder? = null,
) {
  val canSubmit: Boolean get() = !loading && !submitting && !termsStale && store != null && problems.isEmpty()
}

/**
 *  ثبتِ سفارش برای یک دکان — بی UI. صفحه فرم را با `edit` عوض می‌کند و
 *  `submit` را می‌زند؛ این‌جا جمع، مسئله‌ها، ناحیه، بازه، کلیدِ یکتایی و
 *  خواندنِ خطای سرور (S135، CHK-17/19) یک‌جا است.
 *
 *  ⛔ هیچ مبلغی این‌جا «قطعی» نیست: سرور همان لحظه قیمت و هزینه را حساب
 *  می‌کند و جمعِ دیده‌شده را می‌سنجد؛ اختلاف ⇒ ثبت نمی‌شود، سبد و شرایطِ
 *  دکان (هزینه، حداقل، ناحیه) از نو خوانده می‌شوند و تغییر نشان داده می‌شود.
 */
class ShopperCheckoutModel(
  private val api: ShopperApi,
  private val carts: ShopperCarts,
  private val tickets: ShopperTickets,
  private val keys: CheckoutKeys,
  private val book: ShopperOrderBook,
  private val storage: ShopperStorage,
  val slug: String,
) {
  private val flow = MutableStateFlow(CheckoutState(cart = carts.cart(slug)))
  val state: StateFlow<CheckoutState> = flow.asStateFlow()

  /** فقط پاسخِ آخرین «کدام ناحیه؟» و آخرین بازه‌ها اعمال می‌شود */
  private val locateGen = AtomicInteger()
  private val slotsGen = AtomicInteger()

  /** پروفایلِ ویترین، تازه کردنِ سبد، پر کردنِ فرم از دفعهٔ پیش و بازه‌ها */
  suspend fun load() {
    flow.update { it.copy(loading = true, loadFailure = null) }
    when (val home = api.store(slug)) {
      is ApiResult.Failure -> flow.update { it.copy(loading = false, loadFailure = home.error) }
      is ApiResult.Success -> {
        val store = home.value.store
        val notice = carts.refresh(slug, api)
        val saved = storage.load(ShopperStorage.CONTACT, SavedContact.serializer()) ?: SavedContact()
        val old = flow.value.form
        val ticket = tickets.current()
        val form = old.copy(
          fulfillment = old.fulfillment.takeIf { ShopperCheckout.offers(store, it) } ?: ShopperCheckout.defaultFulfillment(store),
          name = old.name.ifBlank { saved.name },
          phone = old.phone.ifBlank { ticket?.phone?.let(::localPhone).orEmpty() },
          addressText = old.addressText.ifBlank { saved.addressText },
          landmark = old.landmark.ifBlank { saved.landmark },
        )
        flow.update {
          it.copy(
            loading = false, store = store, verificationAvailable = home.value.verification.available,
            refreshed = notice?.takeUnless { n -> n.isQuiet },
          )
        }
        setForm(form)
        loadSlots()
      }
    }
  }

  /** هر تغییرِ فرم (جز روش و پین که درخواست دارند) */
  fun edit(change: (CheckoutForm) -> CheckoutForm) = setForm(change(flow.value.form))

  suspend fun setFulfillment(fulfillment: String) {
    if (fulfillment == flow.value.form.fulfillment) return
    setForm(flow.value.form.copy(fulfillment = fulfillment, slotStartsAt = null))
    flow.update { it.copy(slots = null) }
    loadSlots()
  }

  /** پینِ تحویل؛ ارسالِ ناحیه‌دار ⇒ «کدام ناحیه؟» از سرور */
  suspend fun setPin(pin: GeoPoint?) {
    setForm(flow.value.form.copy(pin = pin))
    locate()
  }

  /** @return ناحیه دانسته شد (یا لازم نبود) */
  private suspend fun locate(): Boolean {
    val gen = locateGen.incrementAndGet()
    val pin = flow.value.form.pin
    val store = flow.value.store
    if (pin == null || store == null || !store.delivery.zoned) {
      flow.update { it.copy(located = null, locating = false) }
      recompute()
      return true
    }
    flow.update { it.copy(located = null, locating = true) }
    val r = api.locate(slug, pin)
    if (gen != locateGen.get()) return false
    flow.update { it.copy(locating = false, located = r.valueOrNull()) }
    recompute()
    return r.isSuccess
  }

  suspend fun loadSlots() {
    val gen = slotsGen.incrementAndGet()
    val store = flow.value.store
    val f = flow.value.form.fulfillment
    if (store == null || !store.delivery.scheduling.of(f) || !ShopperCheckout.offers(store, f)) {
      flow.update { it.copy(slotsLoading = false) }
      return
    }
    flow.update { it.copy(slotsLoading = true) }
    val r = api.slots(slug, f)
    if (gen != slotsGen.get()) return
    flow.update { it.copy(slotsLoading = false, slots = r.valueOrNull() ?: it.slots) }
    recompute()
  }

  /** سبد از همین صفحه عوض شد (برداشتنِ ناموجودها، مقدار) ⇒ جمع و مسئله‌ها از نو */
  fun onCartChanged() = recompute()

  /** پس از تأییدِ شماره در برگهٔ تأیید */
  fun onVerified(ticket: PhoneTicket) {
    if (flow.value.form.phone.isBlank()) setForm(flow.value.form.copy(phone = localPhone(ticket.phone))) else recompute()
  }

  /**
   *  ثبت. @return سفارشِ ثبت‌شده — یا null (مسئلهٔ فرم یا خطای سرور در `state`).
   *  دوباره زدن پس از قطعِ شبکه با همان بدنه همان کلید را می‌فرستد؛ شرایطِ کهنه
   *  اول تازه می‌شود و تا نشده، چیزی نمی‌رود.
   */
  suspend fun submit(): PlacedOrder? {
    if (flow.value.submitting) return null
    flow.update { it.copy(attempted = true, issue = null) }
    if (flow.value.termsStale && !refreshTerms()) return null
    recompute()
    val s = flow.value
    val store = s.store ?: return null
    val cart = carts.cart(slug) ?: return null
    if (!s.canSubmit) return null
    val totals = s.totals ?: return null
    val body = ShopperCheckout.body(cart, s.form, totals)
    flow.update { it.copy(submitting = true) }
    return when (val r = api.checkout(slug, body, keys.keyFor(slug, body))) {
      is ApiResult.Success -> {
        keys.drop(slug)
        book.remember(slug, store.title, r.value.order)
        storage.save(
          ShopperStorage.CONTACT, SavedContact.serializer(),
          SavedContact(s.form.name.trim(), s.form.addressText.trim(), s.form.landmark.trim()),
        )
        carts.clear(slug)
        flow.update { it.copy(submitting = false, placed = r.value, cart = null, priceChanges = emptyList()) }
        r.value
      }
      is ApiResult.Failure -> {
        val issue = ShopperCheckout.interpret(r.error)
        val changes = apply(issue)
        flow.update { it.copy(submitting = false, issue = issue, priceChanges = changes, termsStale = staleAfter(issue)) }
        if (flow.value.termsStale) refreshTerms()
        recompute()
        null
      }
    }
  }

  /**
   *  هزینهٔ ارسال، حداقل، ناحیه‌ها و روش‌های دکان از نو — پس از خطایی که
   *  می‌گوید این‌ها عوض شده‌اند. پین باشد، «کدام ناحیه» هم از نو.
   *  @return تازه شد؛ نشد ⇒ ثبت بسته می‌ماند (`termsStale`)
   */
  private suspend fun refreshTerms(): Boolean {
    val home = api.store(slug).valueOrNull() ?: return false
    val store = home.store
    flow.update { it.copy(store = store, verificationAvailable = home.verification.available) }
    val form = flow.value.form
    if (!ShopperCheckout.offers(store, form.fulfillment)) {
      setForm(form.copy(fulfillment = ShopperCheckout.defaultFulfillment(store), slotStartsAt = null))
      flow.update { it.copy(slots = null) }
    }
    if (!locate()) return false
    loadSlots()
    flow.update { it.copy(termsStale = false) }
    recompute()
    return true
  }

  /** خطاهایی که یعنی «شرایطِ دکان عوض شده» — جمعِ کهنه دوباره نباید برود */
  private fun staleAfter(issue: CheckoutIssue): Boolean = when (issue) {
    is CheckoutIssue.PriceChanged, is CheckoutIssue.BelowMin, is CheckoutIssue.OutOfZone,
    is CheckoutIssue.ZoneNeeded, CheckoutIssue.FulfillmentGone -> true
    else -> false
  }

  /** اثرِ خطای سرور روی سبد، بلیت و بازه‌ها — تا دفعهٔ بعد درست برود */
  private fun apply(issue: CheckoutIssue): List<PriceChange> = when (issue) {
    is CheckoutIssue.PriceChanged -> carts.applyQuote(slug, issue.quote)
    is CheckoutIssue.Unavailable -> { carts.markUnavailable(slug, issue.productIds); emptyList() }
    is CheckoutIssue.OverLimit -> { carts.applyLimit(slug, issue.productId, issue.maxQtyMilli); emptyList() }
    //  فقط ۴۰۱ یعنی بلیتِ باطل؛ ۴۰۳ ِ «شماره فرق دارد» بلیت را نگه می‌دارد
    is CheckoutIssue.NeedsVerification -> { tickets.forget(); emptyList() }
    is CheckoutIssue.SlotNeeded -> {
      flow.update { st -> st.copy(slots = st.slots?.copy(slots = issue.slots)) }
      emptyList()
    }
    else -> emptyList()
  }

  private fun setForm(form: CheckoutForm) {
    flow.update { it.copy(form = form) }
    recompute()
  }

  private fun recompute() {
    val cart = carts.cart(slug)
    flow.update { s ->
      val store = s.store ?: return@update s.copy(cart = cart)
      val zone = ShopperCheckout.zoneOf(store, s.form, s.located)
      s.copy(
        cart = cart,
        totals = ShopperCheckout.totals(store, cart, s.form, zone),
        problems = ShopperCheckout.problems(store, cart, s.form, tickets, s.located, s.slots),
      )
    }
  }

  private fun localPhone(e164: String): String = LocaleFormat.formatPhone(e164)
}
