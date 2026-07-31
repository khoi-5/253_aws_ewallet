# Cloud E-Wallet Frontend

The active frontend is `frontend/`: React 19, TypeScript, Vite, React Router, Axios, Zustand, and Zod.

It is responsive and deployed to Amazon S3 through CloudFront at `https://cloud-ewallet.com`. Deployment remains manual.

Cloudflare provides DNS. CloudFront serves the S3 frontend for its default behavior and routes `/api/*` to the internet-facing Application Load Balancer. The previous direct CloudFront-to-EC2 API origin has been removed.

## Implemented UI

- Registration, login/logout, verification/resend, forgot/reset password, and profile editing.
- Wallet balance, simulated deposit, transfer, service payment, and transaction history.
- Admin dashboard, users, transactions, and service management.
- Deposit sender shown as `Bank Card` in customer and admin history.
- Authenticated, debounced recipient lookup by phone. The full name appears in a read-only field and clears when the phone changes or is invalid, unavailable, blocked, wallet-less, an admin, or self-owned.
- Service banners use the backend-provided service name.
- Responsive customer/admin layouts and corrected mobile welcome-heading wrapping.
- No visible demo labels in the deployed interface.

The bank-card form is presentation-only. Card number, cardholder, expiry, CVV, and funding source stay in temporary React state and are never sent to the backend. The deposit API payload remains `amount` plus optional `description`.

## API configuration

`src/apis/axiosClient.ts` reads `VITE_API_BASE_URL`, removes a trailing slash, and appends `/api`.

- When configured, that origin is used.
- In development without a configured value, the origin is `http://localhost:8080`.
- In production without a configured value, requests use same-origin `/api`.

`frontend/.env.production` is a local Vite build-time file and is ignored in this checkout. Its value is public once compiled, so it must never contain a private token or credential. Keep it separate from backend and EC2 runtime files.

## Commands

```powershell
npm install
npm run dev
npm run build
npm run lint
```

There is no automated frontend test script.

## Production

Build `frontend/dist/`, upload its contents to the production S3 bucket, and invalidate CloudFront path `/*`. Bucket/distribution identifiers are intentionally not stored in this repository. This audit did not deploy or invalidate anything.

Browser traffic to CloudFront uses HTTPS. The current origin connection from CloudFront to the ALB uses HTTP; HTTPS on that hop is a possible future hardening step.
