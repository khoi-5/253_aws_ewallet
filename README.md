# Cloud E-Wallet

Cloud E-Wallet is a deployed demonstration e-wallet using simulated balances. It is not a real-money wallet or payment-card system.

Production URL: `https://cloud-ewallet.com`

See [PROJECT_STATUS.md](PROJECT_STATUS.md) for deployment verification, current limitations, and the roadmap.

## Technology

| Layer | Implementation |
| --- | --- |
| Frontend | React 19, TypeScript, Vite, React Router, Axios, Zustand, Zod |
| Backend | Java 17, Spring Boot, Spring Security, JDBC, Actuator, Spring Mail |
| Authentication | BCrypt passwords and signed, expiring JWT access tokens |
| Data | MySQL 8 locally; Amazon RDS MySQL in production |
| Runtime | Docker Compose for local MySQL; Dockerized Spring Boot on EC2 |
| Email | Development link logging locally; Amazon SES SMTP in production; Resend fallback |

## Implemented functionality

Customers can register, verify or resend verification email, log in and out, recover/reset a password, view and update a profile, view a wallet balance, look up an eligible recipient by phone number, deposit simulated funds, transfer funds, pay active services, and view transaction history.

Administrators can view the dashboard, list and block/unblock users, inspect transactions, and add, edit, activate, or deactivate services. The JWT filter reloads account role and status from MySQL, so a blocked account is rejected on subsequent protected requests; the frontend clears the session on `401` or `ACCOUNT_BLOCKED`.

The responsive frontend and database use UTF-8/`utf8mb4` for Vietnamese content.

### Deposit boundary

Deposit is a simulation. Card number, cardholder name, expiry, CVV, and funding source exist only in temporary React state. The frontend sends only `amount` and an optional `description`; no card data is sent to the backend or stored. There is no bank, card processor, or real payment gateway integration.

## Production architecture

The report now presents the following target deployment model:

```text
Users → Amazon CloudFront protected by AWS WAF
          |-- Default (*) → private Amazon S3 React frontend
          `-- /api/* → internet-facing Application Load Balancer
                         → Target Group (:8080, /actuator/health)
                         → 2 private-subnet EC2 instances across 2 AZs
                           managed by ASG (Min 0 / Desired 2 / Max 2)
                         → private-subnet Single-AZ Amazon RDS MySQL
                         → Amazon SES SMTP (587, authenticated STARTTLS)

Private EC2 outbound → NAT Gateway in a public subnet
                       → Internet Gateway → Internet/public service endpoint
```

CloudFront is the browser-facing HTTPS endpoint. AWS WAF uses `AWS-AWSManagedRulesCommonRuleSet`; selected rules block requests while SizeRestrictions and CrossSiteScripting remain in Count for observation. CloudFront serves the frontend from S3 and routes `/api/*` to the ALB. The ALB forwards through the Target Group only to healthy EC2 instances. ASG manages instance lifecycle rather than carrying request traffic. Cloudflare is used only for DNS and AWS/SES verification records, not as an application proxy or CDN.

## EC2 and network security

The target VPC design spans two Availability Zones in `ap-southeast-1`. The internet-facing ALB is associated with two public subnets, while the two ASG-managed backend instances are placed in private application subnets. Single-AZ RDS MySQL remains in a private database subnet. One NAT Gateway in a public subnet provides outbound access for the private EC2 instances through the Internet Gateway; it is not part of the inbound application path.

- ALB security group: public TCP `80/443` according to the active listeners.
- EC2 security group: TCP `8080` only from the ALB security group; no direct backend traffic from the Internet.
- RDS security group: MySQL `3306` only from the EC2 security group.
- One NAT Gateway is a cost-conscious design but remains an outbound-path dependency across the two-AZ application tier.
- RDS is Single-AZ, so the design does not claim end-to-end high availability.
## Project structure

```text
khoi/
|-- backend/
|-- database/
|-- frontend/
|-- .env.example
|-- .env.production.example
|-- description.md
|-- docker-compose.yml
|-- PROJECT_STATUS.md
`-- README.md
```

## Environment files

| File | Purpose | Git policy |
| --- | --- | --- |
| `.env.example` | Safe local-development template | Tracked |
| `.env.local` | Real local runtime values | Ignored; never commit |
| `.env.production.example` | Safe production template | Tracked |
| `ewallet-backend.env` | Real production backend values prepared locally | Must be ignored; never commit |
| `/home/ec2-user/ewallet-backend.env` | EC2 copy loaded with Docker `--env-file` | Outside Git |
| `frontend/.env.production` | Vite production build-time configuration | Ignored; public after compilation |

Keep local, frontend build-time, and production runtime configuration separate.
Never place database, JWT, SMTP, or AWS secrets in documentation or Vite
configuration. See [DEPLOYMENT.md](DEPLOYMENT.md) for the SES migration and Resend
rollback procedure.

Local `MAIL_DEVELOPMENT_LOG_ENABLED=true` logs account links and avoids real SMTP.
SMTP mode requires valid credentials for the selected provider. Production must
use `MAIL_DEVELOPMENT_LOG_ENABLED=false`.

Example production backend launch:

```bash
docker run -d \
  --name ewallet-backend \
  --env-file /home/ec2-user/ewallet-backend.env \
  -p 8080:8080 \
  --restart unless-stopped \
  <BACKEND_IMAGE>
```

The repository does not provide authoritative evidence for the exact currently deployed image tag, so deployment documentation intentionally uses a placeholder.

## Database

The main tables are:

- `users`
- `account_tokens`
- `user_profiles`
- `admin_profiles`
- `wallets`
- `services`
- `transactions`

`account_tokens` stores SHA-256 token hashes for `EMAIL_VERIFICATION` and `PASSWORD_RESET`. `expires_at` controls expiry and `used_at` records one-time use. Scheduled cleanup, including AWS Lambda cleanup, is not implemented.

Local reset/seed uses `database/schema.sql`. Fresh RDS setup uses `database/rds/001_schema.sql`, a securely completed uncommitted copy of `002_admin_template.sql`, then `003_services_seed.sql`.

## Build and validation

```powershell
cd backend
.\mvnw.cmd test
.\mvnw.cmd package -DskipTests
```

```powershell
cd frontend
npm install
npm run build
npm run lint
```

There is no frontend automated test script. Verified repository and production validation results are recorded in [PROJECT_STATUS.md](PROJECT_STATUS.md).

## Future improvements

2. Evaluate Systems Manager Session Manager for private-instance administration.
4. Add CI/CD for automated build, testing, image publishing, and deployment.
5. Move Docker images from Docker Hub to Amazon ECR.
7. Add HTTPS directly between CloudFront and the ALB if required.
9. Add scheduled expired-token cleanup with Spring scheduling or AWS Lambda and EventBridge.
10. Integrate a real payment gateway for actual card or bank deposits.

These are planned improvements, not current features.
