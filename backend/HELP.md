# Cloud E-Wallet Backend

Java 17 Spring Boot REST backend using Spring Security, JDBC, Actuator, Spring Mail, JWT, and MySQL.

## Local startup

From the repository root, copy `.env.example` to ignored `.env.local`, replace `JWT_SECRET` with at least 32 random bytes, start Docker Desktop, and run:

```powershell
.\start-dev.ps1
```

The script starts local MySQL on port 3307 and the backend with `SPRING_PROFILES_ACTIVE=local`. The local `DevelopmentEmailService` logs verification/reset links and does not require SMTP credentials.

## Tests and build

```powershell
cd backend
mvn test
mvn package -DskipTests
```

The Maven wrapper can be used when its bootstrap is available. Current audit results are recorded in `PROJECT_STATUS.md`.

## Production runtime

Current runtime facts:

- one EC2 instance;
- one healthy target behind an internet-facing Application Load Balancer;
- container `ewallet-backend`;
- manual `docker run`;
- environment file `/home/ec2-user/ewallet-backend.env`;
- port mapping `8080:8080`;
- Amazon RDS MySQL;
- Amazon SES SMTP, with Resend SMTP retained as an environment-selected fallback.

CloudFront routes `/api/*` to the ALB over HTTP port 80. The ALB forwards to the backend on port 8080 and checks `/actuator/health`. EC2 accepts port 8080 only from the ALB security group; direct access through the EC2 public address is blocked. Docker Compose is for local MySQL only. Production does not use ECS or CI/CD.

The exact current production image tag is not verifiable from repository source, so use `<BACKEND_IMAGE>` in documentation and confirm the selected tag during deployment.

## Production configuration

Use the `prod` Spring profile and the names in `.env.production.example`.
`EMAIL_PROVIDER` accepts `ses` or `resend`; production defaults to `ses`. Only
the selected provider's host, port, username, password, and sender address are
required.

The production profile requires SMTP authentication and STARTTLS. Startup
validation rejects unsupported providers and missing selected-provider values
without exposing secrets. EC2 connects outbound to port 587; no inbound SMTP port
is required. Migration and rollback commands are in `../DEPLOYMENT.md`.

Keep `/home/ec2-user/ewallet-backend.env` outside Git. Do not move or rename it during repository cleanup.

## Health endpoints

- `/actuator/health`
- `/actuator/health/liveness`
- `/actuator/health/readiness`

Only health and info are exposed, and health details are hidden. The ALB target group uses `/actuator/health`.
