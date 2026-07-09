# TrendCopy AI

TrendCopy AI turns public AI/product trends into publish-ready posts for Xiaohongshu, X/Twitter, and newsletters. The app has:

- Static frontend pages at the project root.
- A Spring Boot backend in `backend/`.
- A Vercel API proxy in `api/proxy.js` so the frontend can call Railway through `/api/...`.

## Local Development

Local secrets live in `backend/config/application-local.yml`. This file is ignored by git.

```powershell
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Open:

```text
http://localhost:8080
```

## Railway Backend

Railway uses the root `Dockerfile` and `railway.json`.

Production backend:

```text
https://trendcopy-api-production.up.railway.app
```

Required Railway variables:

```text
QWEN_API_KEY=
QWEN_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
QWEN_MODEL=qwen-plus

GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
GOOGLE_REDIRECT_URI=https://trendcopy.asia/api/auth/google/callback

ALIPAY_GATEWAY_URL=https://openapi.alipay.com/gateway.do
ALIPAY_APP_ID=
ALIPAY_MERCHANT_PRIVATE_KEY=
ALIPAY_PUBLIC_KEY=
ALIPAY_RETURN_URL=https://trendcopy.asia/pricing.html
ALIPAY_NOTIFY_URL=https://trendcopy-api-production.up.railway.app/api/payments/alipay/notify

FIRECRAWL_API_KEY=

SUPABASE_DB_URL=
SUPABASE_DB_USER=
SUPABASE_DB_PASSWORD=

RESEND_API_KEY=
EMAIL_FROM=TrendCopy AI <login@your-domain.com>
EMAIL_CODE_MINUTES=10
APP_URL=https://trendcopy.asia
```

## Vercel Frontend

Vercel serves the static frontend and proxies `/api/...` to Railway.

Production frontend:

```text
https://trendcopy.asia
```

Required Vercel variable:

```text
BACKEND_URL=https://trendcopy-api-production.up.railway.app
```

The `/dashbord` and `/dashboard` routes rewrite to `dashboard.html`.

## OAuth Callback Notes

For Google, configure the callback URL to the production domain:

```text
https://trendcopy.asia/api/auth/google/callback
```

This lets the browser keep the frontend-domain session cookie while Vercel proxies the callback to Railway.

## Email Login And Registration

Email auth uses a ChatGPT-style flow:

- `POST /api/auth/email/start` checks whether the email already exists.
- New emails receive a one-time 6-digit registration code.
- `POST /api/auth/email/verify-registration` verifies the registration code.
- `POST /api/auth/email/set-password` creates the account password.
- Existing emails use `POST /api/auth/email/password-login`.

Without `RESEND_API_KEY` and `EMAIL_FROM`, the backend prints the code in logs for development. With Resend configured, users receive the code by email.

Supabase can be used as the relational database because Supabase is hosted Postgres. Set:

```text
SUPABASE_DB_URL=jdbc:postgresql://...
SUPABASE_DB_USER=...
SUPABASE_DB_PASSWORD=...
```

The backend creates these tables automatically if they do not exist:

```sql
users
email_login_codes
subscriptions
usage_events
```

## Alipay Notes

For computer website pay:

- `ALIPAY_RETURN_URL` can point to the Vercel pricing page.
- `ALIPAY_NOTIFY_URL` should point to the Railway backend notify endpoint.
- Alipay cannot notify `localhost`; production payment callbacks require a public HTTPS URL.
