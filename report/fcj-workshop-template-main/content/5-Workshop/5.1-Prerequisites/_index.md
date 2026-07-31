---
title: "Deployment prerequisites"
date: 2024-01-01
weight: 1
chapter: false
pre: " <b> 5.1. </b> "
---

## Objective

Prepare tools, source, accounts, and configuration before creating or updating AWS resources.

## Tools

| Component | Check | Purpose |
| --- | --- | --- |
| Java 17 | `java -version` | Build Spring Boot |
| Node.js/npm | `node --version`, `npm --version` | Build React/Vite |
| Docker | `docker --version` | Build and run backend image |
| Git | `git --version` | Source management |

| AWS account | Sign in | Appropriate S3, CloudFront, EC2, ALB, RDS, and SES access |
| Cloudflare | Review zone | Manage `cloud-ewallet.com` and Amazon SES verification records |

## IAM permissions

The project uses two IAM users with separate responsibilities, avoiding the root account for routine deployment work:

| IAM user | Access method | Project responsibility |
| --- | --- | --- |
| `khoi_admin` | AWS Management Console sign-in | Manages S3, CloudFront, EC2, the Application Load Balancer, RDS, SES, and CloudWatch. In the internship environment, the user receives `AdministratorAccess` through `admin-group`. |
| `ses-smtp-user-cloud-ewallet` | Programmatic access through SES SMTP credentials; no Console sign-in | Allows the Spring Boot backend to authenticate with Amazon SES SMTP and send account-verification or password-reset email. This user is not used for builds or infrastructure administration. |

`khoi_admin` has broad permissions to support hands-on deployment across several AWS services. In a long-term production environment, administrative permissions should be reduced according to least privilege, and MFA should be enabled for Console sign-in. Credentials for `ses-smtp-user-cloud-ewallet` are stored only in the EC2 environment file and never included in source code, screenshots, or the report.

![Project IAM user list](/images/5-Workshop/5.1-Prerequisites/IAM_USER.png)



## Prepare the environment configuration

Before building and deploying, our team separates the frontend and backend configuration.

### Frontend

The frontend reads `frontend/.env.production` during the build. This file defines `VITE_API_BASE_URL`, which is the API address called by the React application after deployment to S3 and CloudFront. Vite variables are embedded in the compiled JavaScript, so this file must not contain passwords or secrets.

The frontend can be configured in either of the following ways:

```dotenv
# Option 1: Use the default CloudFront domain
# Replace <CLOUDFRONT_DISTRIBUTION_DOMAIN> with the distribution-specific domain
VITE_API_BASE_URL=https://<CLOUDFRONT_DISTRIBUTION_DOMAIN>

# Example format: https://dxxxxxxxxxxxxx.cloudfront.net
```

```dotenv
# Option 2: Use a custom domain that points to CloudFront
VITE_API_BASE_URL=https://cloud-ewallet.com
```

Each CloudFront distribution has its own domain, which must be copied from the AWS Console; it is a public identifier rather than a password or secret. Our project uses `https://cloud-ewallet.com`. Keep only the applicable `VITE_API_BASE_URL` value in the real file. After changing it, rebuild the frontend and upload it to S3; create a `/*` invalidation if CloudFront still caches the previous build.

### Backend

The backend on EC2 reads `/home/ec2-user/ewallet-backend.env` when the Docker container starts. The following block uses the project's real variable names while replacing secret values with placeholders:

```dotenv
SPRING_PROFILES_ACTIVE=prod

DB_URL=jdbc:mysql://<RDS_ENDPOINT>:3306/<DB_NAME>?useUnicode=true&characterEncoding=UTF-8&useSSL=true&requireSSL=true&serverTimezone=UTC
DB_USERNAME=<DB_USERNAME>
DB_PASSWORD=<DB_PASSWORD>

JWT_SECRET=<STRONG_RANDOM_SECRET_AT_LEAST_32_BYTES>
JWT_EXPIRATION=3600

FRONTEND_BASE_URL=https://cloud-ewallet.com
CORS_ALLOWED_ORIGINS=https://cloud-ewallet.com

MAIL_DEVELOPMENT_LOG_ENABLED=false
EMAIL_VERIFICATION_MINUTES=1440
PASSWORD_RESET_MINUTES=30
EMAIL_PROVIDER=ses

SES_SMTP_HOST=email-smtp.ap-southeast-1.amazonaws.com
SES_SMTP_PORT=587
SES_SMTP_USERNAME=<SES_SMTP_USERNAME>
SES_SMTP_PASSWORD=<SES_SMTP_PASSWORD>
SES_MAIL_FROM_ADDRESS=noreply@cloud-ewallet.com
```

The variables are grouped by purpose:

| Configuration group | Variables | Purpose |
| --- | --- | --- |
| Database | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Connect the backend to Amazon RDS for MySQL |
| Authentication | `JWT_SECRET` | Sign and verify access tokens |
| Domain and CORS | `FRONTEND_BASE_URL`, `CORS_ALLOWED_ORIGINS` | Generate user-facing links and allow only the intended frontend to call the API |
| Email service | `EMAIL_PROVIDER=ses`, the `SES_SMTP_*` variables, and `SES_MAIL_FROM_ADDRESS` | Connect to Amazon SES SMTP for verification and password-reset email |

The populated environment file is stored only on the deployment host and is never committed to Git. Source code and report examples use `.env.production.example` with placeholders such as `<DB_ENDPOINT>`, `<JWT_SECRET>`, and `<SES_SMTP_PASSWORD>`.

## Pre-deployment validation

- The source contains the `frontend/`, `backend/`, and `database/` directories.
- `frontend/.env.production` points to the production API and contains no secret.
- `.env.production.example` contains placeholders rather than real credentials.
- `/home/ec2-user/ewallet-backend.env` is created directly on EC2 with restricted file permissions.
- The AWS account has the required least-privilege permissions.
- The team has agreed on Singapore (`ap-southeast-1`), the naming convention, and the resources to deploy.

## Configure the VPC

Our team created `ewallet-vpc` in Singapore (`ap-southeast-1`) with IPv4 CIDR `10.0.0.0/16`. The VPC spans two Availability Zones and contains four subnets: one public and one private subnet in each AZ. The internet-facing ALB uses both public subnets, while the ASG-managed backend EC2 instances use the two private subnets. These private subnets are also included in the Amazon RDS DB subnet group. The Internet Gateway is attached to the VPC, and a NAT Gateway in one public subnet provides outbound access for the private EC2 instances.

| Availability Zone | Public subnet | Private subnet |
| --- | --- | --- |
| `ap-southeast-1a` | `ewallet-subnet-public1-ap-southeast-1a` | `ewallet-subnet-private1-ap-southeast-1a` |
| `ap-southeast-1b` | `ewallet-subnet-public2-ap-southeast-1b` | `ewallet-subnet-private2-ap-southeast-1b` |

The two private subnets use private route tables and have no direct inbound path from the Internet. RDS has no public access; its DB subnet group spans both Availability Zones, while the Single-AZ DB instance accepts port `3306` only from the backend security group. DNS resolution and DNS hostnames are enabled so resources can resolve the required endpoints and hostnames.

![Cloud E-Wallet VPC resource map](/images/5-Workshop/5.1-Prerequisites/vpc-resource-map.png)

<p style="text-align: center;"><em>Figure 5.2. Cloud E-Wallet VPC resource map across two Availability Zones.</em></p>

The resource map summarizes the VPC, four subnets, route tables, and Internet Gateway. Detailed DB subnet group, security-group, and ALB settings are presented in their corresponding deployment sections.

## Edge protection and compute resources

The CloudFront distribution is associated with an AWS WAF Web ACL using `AWS-AWSManagedRulesCommonRuleSet` (700 WCU). Selected rules use Block; SizeRestrictions and CrossSiteScripting remain in Count for observation before blocking. The deployment architecture uses a Launch Template and an Auto Scaling Group with `Min = 0`, `Desired = 2`, and `Max = 2`, placing two EC2 instances in private subnets across two Availability Zones. The private EC2 instances use the NAT Gateway in a public subnet and the Internet Gateway for outbound connections when required.
## Result

Every team member follows one deployment process without sharing secrets through source code or reports.
