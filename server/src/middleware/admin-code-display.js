'use strict';

const config = require('../config');
const { forbidden } = require('./errors');

function requireTestCodeDisplay(req, res, next) {
  res.set('Cache-Control', 'no-store');
  if (!config.devCodes) {
    return next(forbidden('برای حفظ امنیت حساب، نمایش کد در پنل بسته است.', 'code_display_disabled'));
  }
  next();
}

module.exports = { requireTestCodeDisplay };
