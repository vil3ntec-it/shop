# ------------------------------------------------------------------
#  سرور فروشگاه
#  همین image روی کامپیوتر خانگی، VPS و سرور ابری یکسان کار می‌کند.
# ------------------------------------------------------------------
FROM node:22-alpine

# pg_dump و psql برای پشتیبان‌گیری و بازیابی لازم‌اند؛ نسخه با
# postgres:16 در docker-compose هماهنگ است.
RUN apk add --no-cache postgresql16-client tini

ENV NODE_ENV=production
WORKDIR /app

# اول فقط فایل وابستگی‌ها تا لایه‌ی نصب کش شود
COPY package.json package-lock.json* ./
RUN npm ci --omit=dev || npm install --omit=dev

COPY . .

# اجرا با کاربر غیر root
RUN mkdir -p /app/data/backups && chown -R node:node /app
USER node

EXPOSE 3000

# tini سیگنال‌ها را درست می‌رساند تا خاموش شدن تمیز باشد
ENTRYPOINT ["/sbin/tini", "--"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD node -e "fetch('http://127.0.0.1:'+(process.env.PORT||3000)+'/api/health').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"

CMD ["node", "src/index.js"]
