package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.market.PublicProduct
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/*
 *  برنامهٔ خرید — حسابِ مشتری: سبد، ♥ و «فهرستِ خرید» همراهِ حساب (WEB-11، CHK-03) با **همان درها و همان قاعده‌های
 *  ادغامِ وب** (`server/public/store/catalog.js` و `acct.js`):
 *    سبد ── نخستین بارِ هر نشست ادغام (`/account/cart/merge`، بیشینهٔ مقدار)، سپس حساب جای سبدِ دستگاه؛ هر تغییرِ
 *           دستگاه ⇒ جایگزینی با نسخهٔ دیده‌شده (۴۰۹ ⇒ همان سبدِ دستگاه روی نسخهٔ تازه، دستگاه برنده)؛ پر ⇒ سبدِ دستگاه.
 *    ♥ ──── نخستین بارِ هر نشست همهٔ ♥ ِ دستگاه (کهنه‌ترین اول) در صف؛ هر ♥ یک کار؛ صف خالی ⇒ حساب حرفِ آخر.
 *    فهرست ─ ادغام، سپس هر تغییر یک کار («همه» یا «برداشتنِ یکی») روی نسخهٔ دیده‌شده؛ ۴۰۹ ⇒ همان کار روی فهرستِ تازه؛
 *           صف خالی ⇒ حساب حرفِ آخر؛ پر ⇒ فهرستِ دستگاه جای حساب؛ `too_many_lists` ⇒ فقط روی دستگاه.
 *  ⛔ قیمت، نام و موجودی همیشه از پاسخِ امروزِ سرور؛ فهرست فقط شناسه و مقدار. بی نشست ⇒ همه فقط روی دستگاه.
 *  ⛔ ورود فقط با بلیتِ تازهٔ کدِ واقعیِ سرور (`POST /:slug/account`)؛ سرورِ بی راهِ پیامک ⇒ ۵۰۳ (D-09)، هرگز کدِ ساختگی.
 */

@Serializable
data class AccountCustomer(val id: String = "", val phone: String = "", val name: String = "")

/** نشستِ حساب (`sc1.…`) — پاسخِ `POST /:slug/account`. ⛔ فقط در نگه‌دارِ رمزشدهٔ سکو، هرگز در [ShopperState]. */
@Serializable
data class AccountSession(val session: String, val expiresAt: Long, val customer: AccountCustomer = AccountCustomer())

/** قلمِ نوشتنی (سبد با یادداشت، فهرست بی یادداشت) */
@Serializable
data class AccountItem(val productId: String, val qtyMilli: Long, val note: String? = null)

@Serializable
internal data class AccountWrite(val version: Long? = null, val items: List<AccountItem>)

@Serializable
data class AccountCartLine(
  val productId: String,
  val qtyMilli: Long,
  val note: String = "",
  val available: Boolean = false,
  val name: String? = null,
  val unit: String? = null,
  val priceMinor: Long? = null,
  val maxQtyMilli: Long? = null,
)

@Serializable
data class AccountCart(val version: Long = 0, val lines: List<AccountCartLine> = emptyList())

@Serializable
data class AccountListLine(val productId: String, val qtyMilli: Long, val available: Boolean = false)

/** «فهرستِ خرید» ِ حساب: قلم‌ها و کالاهای همین حالا در ویترین (قیمتِ امروز) */
@Serializable
data class AccountList(val version: Long = 0, val lines: List<AccountListLine> = emptyList(), val items: List<PublicProduct> = emptyList())

@Serializable
data class FavoritesPage(val items: List<PublicProduct> = emptyList(), val max: Int = 200)

/** قلمِ «فهرستِ خرید» روی دستگاه — فقط شناسه و مقدار (همان `{id, q}` ِ وب)، هرگز قیمت */
@Serializable
data class ListItem(val id: String, val q: Long)

@Serializable
data class FavOp(val id: String, val add: Boolean)

/** کارِ صفِ فهرست: «همه» ([all]) یا «برداشتنِ یکی» ([rm]) — همان `['all', …]`/`['rm', id]` ِ وب */
@Serializable
data class ListOp(val all: List<ListItem>? = null, val rm: String? = null)

/** هم‌گامیِ یک دکان در یک نشست ([t] = `expiresAt` ِ همان نشست؛ نشستِ تازه ⇒ ادغامِ تازه) */
@Serializable
data class AccountSync(
  val t: Long = 0,
  val favOps: List<FavOp> = emptyList(),
  val listV: Long = 0,
  val listS: List<ListItem> = emptyList(),
  val listM: Boolean = false,
  val listOps: List<ListOp> = emptyList(),
  val listOff: Boolean = false,
  val cartV: Long = 0,
  val cartK: String = "",
  val cartM: Boolean = false,
  /** یادداشتِ قلم‌های سبدِ حساب (از وب) تا نوشتنِ برنامه آن را پاک نکند */
  val cartNotes: Map<String, String> = emptyMap(),
)

/** درهای روشنِ همین دکان (`features` ِ `GET /:slug`) */
data class AccountFeatures(val favorites: Boolean = false, val cart: Boolean = false)

/** چه چیزی هنوز در این دور از سرور تازه خوانده نشده (ورود به دکان، برگشتِ برنامه ⇒ هر سه) */
data class AccountPulls(val cart: Boolean = false, val fav: Boolean = false, val list: Boolean = false) {
  operator fun plus(o: AccountPulls) = AccountPulls(cart || o.cart, fav || o.fav, list || o.list)

  companion object {
    val ALL = AccountPulls(cart = true, fav = true, list = true)
  }
}

sealed class AccountStep {
  data class CartMerge(val items: List<AccountItem>) : AccountStep()
  data class CartPut(val version: Long, val items: List<AccountItem>) : AccountStep()
  data class CartPull(val mark: String) : AccountStep()
  data class Fav(val op: FavOp) : AccountStep()
  object FavPull : AccountStep()
  data class ListMerge(val items: List<ListItem>) : AccountStep()
  data class ListPut(val op: ListOp, val version: Long, val items: List<ListItem>) : AccountStep()
  object ListPull : AccountStep()
}

/** پاسخِ یک کار — فقط یکی از سه پر است */
class AccountReply(
  val cart: ShopperResult<AccountCart>? = null,
  val favs: ShopperResult<FavoritesPage>? = null,
  val fav: ShopperResult<Boolean>? = null,
  val list: ShopperResult<AccountList>? = null,
) {
  val error: ShopperError? get() = cart?.errorOrNull() ?: favs?.errorOrNull() ?: fav?.errorOrNull() ?: list?.errorOrNull()
}

/** حالِ تازه؛ [go] ⇒ کارِ بعد؛ [products] کالاهای امروزِ پاسخ (برای نمایش)؛ [signedOut] ⇒ نشست دیگر کار نمی‌کند (۴۰۱) */
data class AccountOutcome(val state: ShopperState, val go: Boolean, val products: List<PublicProduct> = emptyList(), val signedOut: Boolean = false)

object AccountRules {
  const val MAX_FAVS = 200
  const val MAX_LIST = CartRules.MAX_LINES
  const val MAX_SHOPS = 30
  private val ID = Regex("^[A-Za-z0-9_.:-]{1,80}$")

  /** شمارهٔ خودِ مشتری، پوشیده (CRM-15): `+93791114888` ⇒ `0791•••888` */
  fun maskPhone(e164: String): String {
    val local = if (e164.startsWith("+93")) "0" + e164.removePrefix("+93") else e164
    return if (local.length <= 7) local else local.take(4) + "•••" + local.takeLast(3)
  }

  fun cleanFavs(ids: List<String>): List<String> = ids.filter { ID.matches(it) }.distinct().take(MAX_FAVS)

  fun cleanList(xs: List<ListItem>): List<ListItem> =
    xs.filter { ID.matches(it.id) && it.q > 0 && it.q <= CartRules.MAX_QTY_MILLI }.distinctBy { it.id }.take(MAX_LIST)

  fun favsOf(s: ShopperState, slug: String): List<String> = s.favs[slug].orEmpty()

  fun listFor(s: ShopperState, slug: String): List<ListItem> = s.lists[slug].orEmpty()

  private fun <T> withKey(m: Map<String, List<T>>, slug: String, v: List<T>): Map<String, List<T>> =
    if (v.isEmpty()) m - slug else (mapOf(slug to v) + (m - slug)).entries.take(MAX_SHOPS).associate { it.key to it.value }

  /** نشستِ تازه (یا نخستین دورِ این دکان) ⇒ ♥ ِ دستگاه، کهنه‌ترین اول، در صف؛ فهرست و سبد «هنوز ادغام نشده» */
  fun begin(s: ShopperState, slug: String, expiresAt: Long): ShopperState {
    val st = s.sync[slug]
    if (st != null && st.t == expiresAt) return s
    val fresh = AccountSync(t = expiresAt, favOps = favsOf(s, slug).reversed().map { FavOp(it, true) })
    return s.copy(sync = s.sync + (slug to fresh))
  }

  /** خروج ⇒ همهٔ صف‌ها و نسخه‌ها پاک (♥، فهرست و سبد روی دستگاه می‌مانند) */
  fun signedOut(s: ShopperState): ShopperState = s.copy(sync = emptyMap())

  // ── کارهای دستگاه ──

  fun toggleFav(s: ShopperState, slug: String, id: String): ShopperState {
    if (!ID.matches(id)) return s
    val cur = favsOf(s, slug)
    val add = id !in cur
    val next = if (add) (listOf(id) + cur).take(MAX_FAVS) else cur - id
    val st = s.sync[slug]
    val sync = if (st == null) s.sync else s.sync + (slug to st.copy(favOps = st.favOps.filter { it.id != id } + FavOp(id, add)))
    return s.copy(favs = withKey(s.favs, slug, next), sync = sync)
  }

  private fun setList(s: ShopperState, slug: String, items: List<ListItem>, op: ListOp): ShopperState {
    val st = s.sync[slug]
    val sync = if (st == null || st.listOff) s.sync
      else s.sync + (slug to st.copy(listOps = if (op.all != null) listOf(op) else st.listOps + op))
    return s.copy(lists = withKey(s.lists, slug, items), sync = sync)
  }

  /** «نگه‌داشتنِ سبد» ⇒ فهرست = همان شناسه‌ها و مقدارها (بی قیمت) */
  fun saveCart(s: ShopperState, slug: String, cart: Cart): ShopperState {
    val items = cleanList(cart.lines.map { ListItem(it.productId, it.qtyMilli) })
    return setList(s, slug, items, ListOp(all = items))
  }

  fun removeFromList(s: ShopperState, slug: String, id: String): ShopperState =
    setList(s, slug, listFor(s, slug).filter { it.id != id }, ListOp(rm = id))

  fun clearList(s: ShopperState, slug: String): ShopperState = setList(s, slug, emptyList(), ListOp(all = emptyList()))

  /** «همه به سبد»: فقط کالاهای امروزِ سرور ([items] ِ پاسخِ `?ids=`) با همان مقدار تا سقف؛ قلمِ موجودِ سبد دست نمی‌خورد. @return (سبد، جامانده) */
  fun listToCart(cart: Cart, list: List<ListItem>, items: List<PublicProduct>, at: Long): Pair<Cart, Int> {
    var c = cart
    var gone = 0
    for (x in list) {
      val p = items.firstOrNull { it.id == x.id }
      if (p == null || !CartRules.canOrder(p)) { gone++; continue }
      if (CartRules.qtyOf(c, p.id) > 0) continue
      val added = CartRules.add(c, p, at)
      if (added === c) { gone++; continue }
      c = CartRules.setQty(added, p.id, x.q.coerceAtLeast(CartRules.STEP_MILLI), at)
    }
    return c to gone
  }

  // ── سبد: همان `items()`/`mark()` ِ acct.js ──

  fun cartItems(cart: Cart, notes: Map<String, String>): List<AccountItem> =
    cart.lines.sortedBy { it.productId }.map { AccountItem(it.productId, it.qtyMilli, notes[it.productId]?.takeIf { n -> n.isNotBlank() }) }

  fun mark(items: List<AccountItem>): String =
    items.sortedBy { it.productId }.joinToString("|") { it.productId + ":" + it.qtyMilli + ":" + (it.note ?: "").trim() }

  private fun serverItems(c: AccountCart): List<AccountItem> = c.lines.map { AccountItem(it.productId, it.qtyMilli, it.note.takeIf { n -> n.isNotBlank() }) }

  private fun notesOf(c: AccountCart): Map<String, String> = c.lines.filter { it.note.isNotBlank() }.associate { it.productId to it.note }

  /** سبدِ حساب ⇒ سبدِ دستگاه؛ قیمت و «هست؟» همان پاسخِ امروزِ سرور، عکسِ قلمِ هم‌شناسه می‌ماند */
  fun cartFromServer(slug: String, cur: Cart, c: AccountCart, at: Long): Cart {
    val old = cur.lines.associateBy { it.productId }
    val lines = c.lines.map { l ->
      val o = old[l.productId]
      CartLine(
        productId = l.productId, name = l.name ?: o?.name.orEmpty(), unit = l.unit ?: o?.unit.orEmpty(),
        priceMinor = l.priceMinor ?: o?.priceMinor ?: 0L, qtyMilli = l.qtyMilli, maxQtyMilli = l.maxQtyMilli,
        photo = o?.photo ?: false, photoVersion = o?.photoVersion, unavailable = !l.available,
      )
    }
    return Cart(slug, lines, at)
  }

  // ── گام‌ها ──

  /** کارِ بعدی برای [slug] — یا null (همه رسید). همیشه یک درخواست در هر بار، مثلِ وب. */
  fun next(s: ShopperState, slug: String, f: AccountFeatures, pulls: AccountPulls): AccountStep? {
    val st = s.sync[slug] ?: return null
    if (f.cart) {
      val items = cartItems(ShopperMemory.cartOf(s, slug), st.cartNotes)
      if (!st.cartM) return AccountStep.CartMerge(items)
      if (mark(items) != st.cartK) return AccountStep.CartPut(st.cartV, items)
    }
    if (f.favorites) {
      st.favOps.firstOrNull()?.let { return AccountStep.Fav(it) }
      if (!st.listOff) {
        if (!st.listM) return AccountStep.ListMerge(listFor(s, slug))
        st.listOps.firstOrNull()?.let { op -> return AccountStep.ListPut(op, st.listV, op.all ?: st.listS.filter { it.id != op.rm }) }
      }
    }
    if (f.cart && pulls.cart) return AccountStep.CartPull(mark(cartItems(ShopperMemory.cartOf(s, slug), st.cartNotes)))
    if (f.favorites && pulls.fav) return AccountStep.FavPull
    if (f.favorites && pulls.list && !st.listOff) return AccountStep.ListPull
    return null
  }

  /** پس از پاسخِ خواندنِ موفق، همان خواندن از فهرستِ این دور بیرون می‌رود */
  fun after(p: AccountPulls, step: AccountStep, reply: AccountReply): AccountPulls {
    if (reply.error != null) return p
    return when (step) {
      is AccountStep.CartPull -> p.copy(cart = false)
      AccountStep.FavPull -> p.copy(fav = false)
      AccountStep.ListPull -> p.copy(list = false)
      else -> p
    }
  }

  private fun put(s: ShopperState, slug: String, st: AccountSync): ShopperState = s.copy(sync = s.sync + (slug to st))

  private fun drop(ops: List<FavOp>, op: FavOp): List<FavOp> {
    val i = ops.indexOf(op)
    return if (i < 0) ops else ops.filterIndexed { j, _ -> j != i }
  }

  private fun dropList(ops: List<ListOp>, op: ListOp): List<ListOp> {
    val i = ops.indexOf(op)
    return if (i < 0) ops else ops.filterIndexed { j, _ -> j != i }
  }

  private fun listItems(l: AccountList): List<ListItem> = l.lines.map { ListItem(it.productId, it.qtyMilli) }

  /** حالِ تازه پس از پاسخِ [step] — حالِ [s] همان حالِ **اکنون** است (کارِ تازه در میانهٔ درخواست گم نمی‌شود). */
  fun apply(s: ShopperState, slug: String, step: AccountStep, reply: AccountReply, at: Long): AccountOutcome {
    val st = s.sync[slug] ?: return AccountOutcome(s, go = false)
    val e = reply.error
    if (e != null && e.status == 401) return AccountOutcome(signedOut(s), go = false, signedOut = true)
    return when (step) {
      is AccountStep.CartMerge, is AccountStep.CartPull -> {
        val c = reply.cart?.valueOrNull()
        val d = e?.let { cartIn(it) }
        when {
          c != null -> {
            val now = mark(cartItems(ShopperMemory.cartOf(s, slug), st.cartNotes))
            val seen = if (step is AccountStep.CartMerge) mark(step.items) else (step as AccountStep.CartPull).mark
            val synced = st.copy(cartV = c.version, cartK = mark(serverItems(c)), cartM = true, cartNotes = notesOf(c))
            //  سبدِ دستگاه در میانهٔ درخواست عوض شد ⇒ همان، روی نسخهٔ تازه (بی جای‌گزینیِ بی‌صدا)
            if (now != seen) AccountOutcome(put(s, slug, synced), go = true)
            else AccountOutcome(ShopperMemory.withCart(put(s, slug, synced), cartFromServer(slug, ShopperMemory.cartOf(s, slug), c, at)), go = true)
          }
          //  بیش از ۱۰۰ قلم ⇒ سبدِ دستگاه جای سبدِ حساب (هیچ چیز بی‌صدا گم نمی‌شود)
          e?.code == "cart_full" && d != null -> AccountOutcome(put(s, slug, st.copy(cartV = d.version, cartK = "", cartM = true)), go = true)
          else -> AccountOutcome(s, go = false)
        }
      }
      is AccountStep.CartPut -> {
        val c = reply.cart?.valueOrNull()
        val d = e?.let { cartIn(it) }
        when {
          c != null -> AccountOutcome(put(s, slug, st.copy(cartV = c.version, cartK = mark(serverItems(c)), cartNotes = notesOf(c))), go = true)
          e?.code == "cart_changed" && d != null -> AccountOutcome(put(s, slug, st.copy(cartV = d.version)), go = true)
          else -> AccountOutcome(s, go = false)
        }
      }
      is AccountStep.Fav -> when {
        e == null || e.status == 400 || e.status == 404 || e.status == 409 ->
          AccountOutcome(put(s, slug, st.copy(favOps = drop(st.favOps, step.op))), go = true)
        else -> AccountOutcome(s, go = false)
      }
      AccountStep.FavPull -> {
        val page = reply.favs?.valueOrNull() ?: return AccountOutcome(s, go = false)
        if (st.favOps.isNotEmpty()) return AccountOutcome(s, go = true, products = page.items)
        AccountOutcome(s.copy(favs = withKey(s.favs, slug, cleanFavs(page.items.map { it.id }))), go = true, products = page.items)
      }
      is AccountStep.ListMerge, is AccountStep.ListPut, AccountStep.ListPull -> applyList(s, slug, st, step, reply)
    }
  }

  private fun applyList(s: ShopperState, slug: String, st: AccountSync, step: AccountStep, reply: AccountReply): AccountOutcome {
    val l = reply.list?.valueOrNull()
    val e = reply.error
    val op = (step as? AccountStep.ListPut)?.op
    if (l != null) {
      val ops = if (op != null) dropList(st.listOps, op) else st.listOps
      val synced = st.copy(listV = l.version, listS = listItems(l), listM = true, listOps = ops)
      val out = put(s, slug, synced)
      //  صف خالی ⇒ فهرستِ حساب جای فهرستِ دستگاه
      return AccountOutcome(if (ops.isEmpty()) out.copy(lists = withKey(out.lists, slug, cleanList(listItems(l)))) else out, go = true, products = l.items)
    }
    val d = e?.let { listIn(it) }
    return when {
      e?.code == "list_changed" && d != null -> AccountOutcome(put(s, slug, st.copy(listV = d.version, listS = listItems(d))), go = true)
      //  ادغام جا نشد ⇒ فهرستِ دستگاه جای فهرستِ حساب
      e?.code == "list_full" && d != null ->
        AccountOutcome(put(s, slug, st.copy(listV = d.version, listS = listItems(d), listM = true, listOps = listOf(ListOp(all = listFor(s, slug))) + st.listOps)), go = true)
      e?.code == "too_many_lists" -> AccountOutcome(put(s, slug, st.copy(listOff = true)), go = true)
      op != null && e != null && (e.status == 400 || e.status == 404 || e.status == 409) ->
        AccountOutcome(put(s, slug, st.copy(listOps = dropList(st.listOps, op))), go = true)
      else -> AccountOutcome(s, go = false)
    }
  }

  /** سبد/فهرستِ تازهٔ درونِ ۴۰۹ (`cart_changed`/`cart_full`، `list_changed`/`list_full`) */
  fun cartIn(e: ShopperError): AccountCart? = e.data?.get("cart")?.let { decode(AccountCart.serializer(), it) }

  fun listIn(e: ShopperError): AccountList? = e.data?.get("list")?.let { decode(AccountList.serializer(), it) }

  private fun <T> decode(s: DeserializationStrategy<T>, el: JsonElement): T? =
    try { ShopperJson.decodeFromJsonElement(s, el) } catch (_: Exception) { null }

  /** یک کار روی سرور (هم‌گام؛ از نخِ پس‌زمینه) */
  fun send(api: ShopperApi, session: String, slug: String, step: AccountStep): AccountReply = when (step) {
    is AccountStep.CartMerge -> AccountReply(cart = api.mergeAccountCart(slug, session, step.items))
    is AccountStep.CartPut -> AccountReply(cart = api.putAccountCart(slug, session, step.version, step.items))
    is AccountStep.CartPull -> AccountReply(cart = api.accountCart(slug, session))
    is AccountStep.Fav -> AccountReply(fav = api.setFavorite(slug, session, step.op.id, step.op.add))
    AccountStep.FavPull -> AccountReply(favs = api.favorites(slug, session))
    is AccountStep.ListMerge -> AccountReply(list = api.mergeShoppingList(slug, session, step.items))
    is AccountStep.ListPut -> AccountReply(list = api.putShoppingList(slug, session, step.version, step.items))
    AccountStep.ListPull -> AccountReply(list = api.shoppingList(slug, session))
  }
}
