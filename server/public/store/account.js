/*
 * ویترین — «حسابِ من» (CRM-13، `/api/store/<slug>/account/history`): خریدهای آنلاین و حضوری و ماندهٔ حسابِ قرض
 * در همین دکان، با شمارهٔ تأییدشده. فقط با `features.accountHistory` (پرچمِ `shop.storefront.account_history`)
 * و دیربار از store.js؛ بخش را همین پرونده می‌سازد ⇒ پرچمِ خاموش، صفحه همان امروز.
 * ⛔ همهٔ متن با textContent (`K.el`)؛ پیوندِ فاکتور فقط مسیرِ `/r/…` ِ همین مبدأ. امتیاز (PRM-13) هنوز نیست.
 */
(function () {
  'use strict';
  var el = K.el; var t = K.t;
  var RECEIPT = /^\/r\/[A-Za-z0-9]{4,16}\/[A-Za-z0-9_-]{8,64}$/;
  var LOC = K.lang === 'en' ? 'en-GB' : 'fa-AF';
  /** شمارهٔ فاکتور (`B1-42`) با رقمِ همین زبان */
  function no(s) { return String(s).replace(/\d/g, function (d) { return K.fa(Number(d), 0); }); }
  /** روزِ `YYYY-MM-DD` به تقویم و رقمِ همین زبان (نیم‌روزِ همان روز تا منطقهٔ زمانی روز را عوض نکند) */
  function day(iso) {
    try { return new Date(iso + 'T12:00:00Z').toLocaleDateString(LOC, { dateStyle: 'medium', timeZone: 'UTC' }); } catch (e) { return no(iso); }
  }

  /** `ctx` از store.js: `api(path, opts)` و `root` (همان `.wrap` ِ «سفارش‌های من») */
  window.StoreAccount = function (ctx) {
    var api = ctx.api;
    var box = null;

    function when(x) { return x.at ? K.when(x.at) : x.date ? day(x.date) : ''; }
    function balanceText(m) {
      if (m > 0) return t('ah.owe', { m: K.money(m) });
      if (m < 0) return t('ah.credit', { m: K.money(-m) });
      return t('ah.settled');
    }
    function row(href, icon, title, sub, end) {
      var inner = [K.icon(icon), el('span', { class: 'grow' }, el('b', { text: title }), el('small', { text: sub })), end];
      var card = href ? el('a', { class: 'card prow', attrs: { href: href } }) : el('div', { class: 'card prow' });
      inner.forEach(function (c) { card.appendChild(c); });
      return el('li', {}, card);
    }
    function endOf(money, pillText) {
      var end = el('span', { class: 'end' }, el('span', { text: money }));
      if (pillText) end.appendChild(el('span', { class: 'pill plain', text: pillText }));
      return end;
    }

    var DRAW = {
      online: function (o) {
        return row('#/o/' + encodeURIComponent(o.token), 'receipt', t('od.no', { n: K.fa(o.orderNo, 0) }), K.when(o.createdAt),
          endOf(K.money(o.totalMinor), K.order(o.status)));
      },
      purchases: function (p) {
        var sub = when(p) + t('sep') + t('ah.items', { n: K.fa(p.itemsCount, 0) });
        var kind = p.kind === 'credit' ? t('ah.k.credit') : p.kind === 'cash' ? t('ah.k.cash') : t('ah.k.other');
        return row(RECEIPT.test(p.receipt || '') ? p.receipt : '', 'receipt', p.invoiceNo ? t('ah.inv', { n: no(p.invoiceNo) }) : t('ah.buy'),
          sub, endOf(K.money(p.totalMinor), kind));
      },
      ledger: function (e) {
        var title = (e.kind === 'debt' ? t('ah.debt') : t('ah.pay')) + (e.invoiceNo ? t('sep') + t('ah.inv', { n: no(e.invoiceNo) }) : '');
        var sub = when(e) + (e.dueDate ? t('sep') + t('ah.due', { d: day(e.dueDate) }) : '') + t('sep') + t('ah.after', { m: balanceText(e.balanceAfterMinor) });
        return row('', 'cash', title, sub, endOf((e.kind === 'debt' ? '+' : '−') + K.money(e.amountMinor), ''));
      },
    };

    function block(name, titleKey, page) {
      var ul = el('ul', { class: 'olist', attrs: { id: 'ah-' + name } });
      var none = el('p', { class: 'muted', text: t('ah.none'), hidden: page.items.length > 0 });
      var more = el('button', { class: 'btn btn-ghost', text: t('ah.more'), attrs: { type: 'button' }, hidden: !page.nextCursor });
      var cursor = page.nextCursor;
      var add = function (items) { items.forEach(function (x) { ul.appendChild(DRAW[name](x)); }); };
      add(page.items);
      more.addEventListener('click', function () {
        more.disabled = true;
        api('/account/history/' + name + '?cursor=' + encodeURIComponent(cursor)).then(function (r) {
          add(r.items); cursor = r.nextCursor; more.hidden = !cursor; more.disabled = false;
        }).catch(function (e) { more.disabled = false; K.toast(e.message); });
      });
      return el('div', { class: 'stack' }, el('h3', { text: t(titleKey) }), ul, none, more);
    }

    function render(r) {
      K.clear(box);
      box.appendChild(el('h2', { attrs: { id: 'ah-title' }, text: t('ah.title') }));
      if (r.linked && r.balanceMinor !== null && r.balanceMinor !== undefined) {
        box.appendChild(el('div', { class: 'card', attrs: { id: 'ah-balance' } },
          el('span', { class: 'muted', text: t('ah.balance') + t('sep') }), el('b', { text: balanceText(r.balanceMinor) })));
      }
      if (!r.linked) box.appendChild(el('p', { class: 'muted', attrs: { id: 'ah-unlinked' }, text: t('ah.unlinked') }));
      box.appendChild(block('online', 'ah.online', r.online));
      if (r.linked) {
        box.appendChild(block('purchases', 'ah.store', r.purchases));
        box.appendChild(block('ledger', 'ah.ledger', r.ledger));
      }
      box.setAttribute('aria-busy', 'false');
      box.setAttribute('data-state', 'ready');
    }

    /** بخش را می‌سازد و می‌خواند؛ موفق ⇒ `true` (فهرستِ قدیمِ سفارش‌ها جایش را به همین می‌دهد) */
    function open() {
      if (!box) {
        box = el('section', { class: 'stack', attrs: { id: 'my-account', 'aria-labelledby': 'ah-title' } });
        ctx.root.appendChild(box);
      }
      K.clear(box);
      box.hidden = false;
      box.setAttribute('aria-busy', 'true');
      box.setAttribute('data-state', 'loading');
      box.appendChild(el('h2', { attrs: { id: 'ah-title' }, text: t('ah.title') }));
      box.appendChild(el('p', { class: 'muted', attrs: { role: 'status' }, text: t('ah.loading') }));
      return api('/account/history').then(function (r) { render(r); return true; }, function (e) {
        K.clear(box);
        box.appendChild(el('h2', { attrs: { id: 'ah-title' }, text: t('ah.title') }));
        var note = el('p', { attrs: { role: 'alert' } });
        box.appendChild(note);
        K.note(note, 'err', e.message);
        box.setAttribute('aria-busy', 'false');
        box.setAttribute('data-state', 'error');
        return false;
      });
    }

    return { open: open };
  };
})();
