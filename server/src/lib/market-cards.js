'use strict';
/**
 * کارتِ عمومیِ دکان در بازارچه (بی شناسهٔ داخلی، شماره، صاحب یا مختصات)، مختصاتِ درشت، فاصله، ترتیب و
 * جست‌وجوی نامِ دکان — از `lib/market.js` جدا شد تا زیرِ سقفِ اندازه بماند؛ رفتار همان. دسته‌های همان
 * پرونده تزریق می‌شوند.
 */
const { now } = require('../db');
const sf = require('./storefront');
const views = require('./public-views');
const { badRequest } = require('../middleware/errors');

module.exports = ({ CATEGORIES }) => {
const ACCEPT_RANK = { accepting: 0, delayed: 1, later: 2 };
const NEW_MS = 30 * 24 * 60 * 60 * 1000;

/** مختصاتِ درشت (یک‌صدمِ درجه) از ورودی — یا null. */
function geoOf(lat, lng) {
  if ((lat === undefined || lat === '' || lat === null) && (lng === undefined || lng === '' || lng === null)) return null;
  const a = Number(lat);
  const b = Number(lng);
  if (!Number.isFinite(a) || !Number.isFinite(b) || Math.abs(a) > 90 || Math.abs(b) > 180) {
    throw badRequest('موقعیت معتبر نیست', 'bad_location');
  }
  return { lat: Math.round(a * 100), lng: Math.round(b * 100) };
}

/** فاصلهٔ دو نقطهٔ درشت به کیلومتر، گردشده به نیم کیلومتر (کمینه ۰٫۵). */
function distanceKm(a, b) {
  const rad = (x) => (x / 100) * Math.PI / 180;
  const dLat = rad(b.lat - a.lat);
  const dLng = rad(b.lng - a.lng);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLng / 2) ** 2;
  const km = 2 * 6371 * Math.asin(Math.min(1, Math.sqrt(h)));
  return Math.max(0.5, Math.round(km * 2) / 2);
}

function categoryOf(key) {
  return CATEGORIES.find((c) => c.key === key) || null;
}

/** کارتِ عمومیِ دکان — بی شناسهٔ داخلی، شماره، صاحب یا مختصات. */
function cardOf(e, near, at = now()) {
  const r = e.row;
  const s = e.status;
  const cat = categoryOf(r.m_category);
  const hasGeo = near && r.m_lat !== null && r.m_lat !== undefined && r.m_lng !== null && r.m_lng !== undefined;
  return views.pub('shopCard', {
    slug: r.slug,
    title: r.title,
    tagline: r.tagline,
    accent: r.accent,
    category: cat ? cat.key : '',
    categoryLabel: cat ? cat.label : '',
    area: r.m_area || '',
    status: {
      badge: s.badge, orderAcceptance: s.orderAcceptance, connection: s.connection,
      lastConfirmedAt: s.lastConfirmedAt, declaredUntil: s.declaredUntil, timezone: s.timezone,
      localTime: s.localTime, hoursSet: s.hoursSet, holiday: s.holiday, today: s.today, notice: s.notice,
    },
    fulfillment: { delivery: r.accept_delivery === true, pickup: r.accept_pickup === true },
    deliveryFeeMinor: Number(r.delivery_fee_minor),
    minOrderMinor: Number(r.min_order_minor),
    currency: 'AFN',
    productCount: e.productCount,
    distanceKm: hasGeo ? distanceKm(near, { lat: Number(r.m_lat), lng: Number(r.m_lng) }) : null,
    //  «تازه» فقط از تاریخِ واقعیِ ساختِ ویترین (بی امتیازِ ساختگی؛ امتیازِ واقعی هنوز نیست)
    isNew: Number(r.created_at) > at - NEW_MS,
  });
}

function rank(near) {
  return (a, b) => {
    const x = (ACCEPT_RANK[a.status.orderAcceptance] ?? 3) - (ACCEPT_RANK[b.status.orderAcceptance] ?? 3);
    if (x) return x;
    if (near) {
      const da = a.distanceKm === null ? Infinity : a.distanceKm;
      const db = b.distanceKm === null ? Infinity : b.distanceKm;
      if (da !== db) return da - db;
    }
    return String(a.title).localeCompare(String(b.title), 'fa') || String(a.slug).localeCompare(String(b.slug));
  };
}

function matchesShop(c, needle) {
  if (!needle) return true;
  return [c.title, c.tagline, c.area, c.categoryLabel].some((v) => sf.normalize(v).includes(needle));
}

  return { geoOf, distanceKm, categoryOf, cardOf, rank, matchesShop };
};
