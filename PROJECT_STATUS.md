# Cloud E-Wallet Project Status

Last documentation audit: 2026-07-31

Cloud E-Wallet is a deployed simulated-funds learning application, not a real-money wallet.

## Final implemented state

| Area | Status | Current state |
| --- | --- | --- |
| Customer application | Implemented | Registration, authentication, email/account recovery, profile, wallet operations, recipient lookup, services, history |
| Administration | Implemented | Dashboard, user block/unblock, transaction review, service lifecycle |
| Frontend | Deployed | Responsive React/TypeScript/Vite static build on S3 through CloudFront |
| Backend | Deployed | Dockerized Spring Boot on two EC2 targets across two AZs behind an ALB |
| Data and email | Deployed | Amazon RDS MySQL and Amazon SES SMTP; Resend fallback |
| Application-tier availability | Implemented | ALB and ASG maintain two EC2 instances across two AZs; RDS remains Single-AZ |
| Compute management | Deployed | ASG Min 0 / Desired 2 / Max 2 maintains backend instances; application release remains manual |

## Target production architecture

```text
Users → CloudFront + AWS WAF
          |-- Default (*) → S3 frontend
          `-- /api/* → internet-facing ALB in 2 public subnets
                         → Target Group (:8080, /actuator/health)
                         → 2 EC2 instances in private application subnets
                           ASG Min 0 / Desired 2 / Max 2
                         → Single-AZ RDS MySQL in a private database subnet
                         → SES SMTP over authenticated STARTTLS :587

Private EC2 outbound → NAT Gateway in one public subnet → Internet Gateway
```

CloudFront is the browser-facing HTTPS endpoint. WAF inspects requests before an origin. The ALB routes through the Target Group to healthy EC2 targets; ASG manages instance lifecycle and replacement. The NAT Gateway serves outbound connections initiated by private EC2 instances and is not part of inbound request routing. Cloudflare supplies DNS and verification records only.

## Network security and availability boundaries

- The target VPC design spans two AZs, with two public subnets for the internet-facing ALB and NAT Gateway placement, two private application subnets for EC2, and a private database subnet for RDS.
- The Internet Gateway is attached to the VPC. One NAT Gateway in a public subnet provides outbound access for the private EC2 instances.
- ALB accepts public TCP `80/443`; EC2 accepts `8080` only from the ALB SG; RDS accepts `3306` only from the EC2 SG.
- ASG maintains two backend instances across two AZs and can replace an unhealthy instance.
- A single NAT Gateway remains an outbound-path dependency, and Single-AZ RDS remains the principal database availability limit; the design is not end-to-end HA.
## Application evidence

Source review confirms:

- BCrypt registration/login, signed expiring JWTs, user/admin authorization, and logout.
- Database-backed blocked-user enforcement at login and on protected requests; the frontend clears rejected sessions.
- Email verification, resend verification, forgot-password, and reset-password.
- Profile retrieval/update, wallet balance, recipient lookup, simulated deposit, transfer, active-service listing/payment, and transaction history.
- Admin dashboard, user management, transaction management, and service add/edit/activate/deactivate.
- UTF-8 backend responses, `utf8mb4` database configuration, and responsive frontend layouts.
- Deposit card fields remain client-side only; the API receives `amount` and optional `description`.

## Email and account tokens

The backend uses one Spring Mail/`JavaMailSender` service with provider-neutral
business logic. `EMAIL_PROVIDER=ses` selects Amazon SES SMTP in production;
`EMAIL_PROVIDER=resend` selects the retained rollback provider. Selection is
case-insensitive, only the active provider is validated, and both use
authentication and required STARTTLS. `MAIL_DEVELOPMENT_LOG_ENABLED` is fixed to
`false` in the production profile.

The `account_tokens` table stores hashed tokens for `EMAIL_VERIFICATION` and `PASSWORD_RESET`. Validity and one-time use are controlled by `expires_at` and `used_at`. No Spring scheduler, EventBridge job, or AWS Lambda token cleanup exists.

## Database

Final main tables:

1. `users`
2. `account_tokens`
3. `user_profiles`
4. `admin_profiles`
5. `wallets`
6. `services`
7. `transactions`

The schemas use `utf8mb4`/`utf8mb4_unicode_ci` for Vietnamese text.

## Validation evidence

Repository test output from the documented final audit supports:

- Backend: 72 tests passed, 0 failures, 0 errors, 0 skipped.
- Backend package build passed.
- Frontend TypeScript/Vite production build passed.
- Frontend lint passed.
- No frontend automated test script exists.

Documented production smoke coverage includes registration, email verification, login, profile retrieval/update, recipient lookup, simulated deposit, transfer, service payment, transaction history, admin workflows, and verification/reset email flows. Infrastructure checks cover a healthy ALB target, CloudFront `/api/*` routing, and blocked direct EC2 backend access.

An unauthenticated protected API request returning `401 Unauthorized` confirms that the request reached Spring Security. By itself, it does not prove that every business workflow succeeds.

## Environment separation

- `.env.example`: safe local-development template.
- `.env.local`: real local values; ignored by Git.
- `.env.production.example`: safe production template.
- Root `ewallet-backend.env`: real production backend environment; must be ignored by Git.
- `/home/ec2-user/ewallet-backend.env`: production copy used by Docker on EC2.
- `frontend/.env.production`: Vite build-time production configuration; ignored by Git.

No secret value belongs in documentation. SES and Resend credentials remain
external to Git. See [DEPLOYMENT.md](DEPLOYMENT.md) for migration, verification,
rollback, and future CI/CD secret-injection guidance.

The correctly and incorrectly spelled production environment filenames
(`ewallet-backend.env` and `ewallet-bakend.env`) are ignored and untracked. Their
contents were not inspected.

## Future improvements

2. Evaluate Systems Manager Session Manager for private-instance administration.
4. Add CI/CD for automated build, testing, image publishing, and deployment.
5. Move Docker images from Docker Hub to Amazon ECR.
7. Add HTTPS directly between CloudFront and the ALB if required.
9. Add scheduled cleanup for expired account tokens using Spring scheduling or AWS Lambda with EventBridge.
10. Integrate a real payment gateway for actual card or bank deposits.

These items are not implemented in the current version.
