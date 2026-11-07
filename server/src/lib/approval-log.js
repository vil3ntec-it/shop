'use strict';
/**
 * سابقهٔ تأییدهای پذیرفته (STF-02، `GET /api/shop/approvals`، پشتِ `audit.view`) — هر ردیفِ `shop_approvals`: برگهٔ
 * امضاشده یا «درخواستِ تأیید» ِ مصرف‌شده (`key_id='remote'`)، با نامِ تأییدکننده و درخواست‌کننده. فقط خواندن.
 * (از `staff-approval.js` جدا شد تا آن پرونده زیرِ سقفِ اندازه بماند؛ `staff-approval.listApprovals` همین را صدا می‌زند.)
 */
const { many } = require('../db');

async function listApprovals(shopId, { limit = 100 } = {}) {
  const cap = Math.min(Math.max(1, Number(limit) || 100), 500);
  const rows = await many(
    `SELECT a.*, ua.name AS approver_name, ur.name AS requester_name
       FROM shop_approvals a
       LEFT JOIN users ua ON ua.id = a.approver_id
       LEFT JOIN users ur ON ur.id = a.requester_id
      WHERE a.shop_id=$1 ORDER BY a.used_at DESC LIMIT $2`,
    [shopId, cap]
  );
  return rows.map((r) => ({
    nonce: r.nonce, opId: r.op_id, doc: r.doc, perm: r.perm,
    pctBp: r.pct_bp === null ? null : Number(r.pct_bp), amount: r.amount === null ? null : Number(r.amount),
    approverId: r.approver_id, approverName: r.approver_name || '',
    requesterId: r.requester_id, requesterName: r.requester_name || '',
    keyId: r.key_id, deviceId: r.device_id, path: r.path, reason: r.reason,
    issuedAt: Number(r.issued_at), usedAt: Number(r.used_at),
  }));
}

module.exports = { listApprovals };
