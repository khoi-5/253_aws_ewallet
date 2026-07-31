---
title: "Proposal"
date: 2024-01-01
weight: 2
chapter: false
pre: " <b> 2. </b> "
---

# Cloud E-Wallet
## A simulated e-wallet system deployed on AWS

### 1. Executive summary

Our team proposes **Cloud E-Wallet**, a web application that simulates account registration and verification, profile and balance management, deposits, transfers, service payments, and transaction-history review. Administrators can view an overview and manage users, transactions, and services. The project is for learning and technical demonstration: it does not process real money, connect to a bank or payment gateway, or store card data.

The project also demonstrates a multi-tier AWS deployment in which the frontend, backend, and database are separated; requests are routed through appropriate layers; Amazon SES delivers transactional email; and Amazon CloudWatch supports resource health and metrics monitoring.

### 2. Problem statement

Even a simulated e-wallet must address authentication and authorization, consistent balance updates, transaction history, responsive user experience, personalized email, and service continuity when a server becomes unhealthy. A localhost-only environment cannot adequately demonstrate HTTPS, frontend/API routing, health checks, network separation, access control, or automatic instance replacement.

#### *Proposed solution*

Cloud E-Wallet stores the React static build in Amazon S3 and distributes it through Amazon CloudFront over HTTPS. AWS WAF is associated with CloudFront to inspect common HTTP/HTTPS request patterns before they reach an origin. CloudFront routes `/api/*` to an Application Load Balancer. The ALB uses a Target Group to distribute requests to two healthy EC2 backend instances managed by an Auto Scaling Group across two Availability Zones. Dockerized Spring Boot accesses Amazon RDS for MySQL and sends transactional email through Amazon SES SMTP. Amazon CloudWatch provides suitable service metrics and health visibility.

#### *Solution rationale*

- **S3 with CloudFront** keeps the static frontend off EC2, reduces backend load, provides HTTPS, and enables edge caching.
- **AWS WAF** filters or observes common web-request patterns before the origins. It complements, but does not replace, Spring Security, JWT, and backend validation.
- **ALB and the Target Group** provide a stable API entry point, health checks, and routing only to healthy targets.
- **The Auto Scaling Group** maintains two EC2 instances across two Availability Zones and can replace unhealthy instances. `Min = 0`, `Desired = 2`, and `Max = 2` favor application availability while limiting compute growth.
- **EC2 instances in private subnets** do not accept direct Internet connections. This placement reduces direct exposure and adds defense in depth between the ALB and backend.
- **Single-AZ RDS MySQL** provides a managed database while controlling cost, but remains the architecture's principal single point of failure.
- **Amazon SES** fits personalized transactional email whose recipients and content vary by user. Amazon SNS is better suited to notifications or alerts sent to fixed subscribers and is not an equivalent replacement for user-verification email.
- **CloudWatch** supports metrics and health monitoring for CloudFront, WAF, ALB, EC2, ASG, and RDS where those services expose the required telemetry.

### 3. Solution architecture

#### *Diagram*

![Cloud E-Wallet deployment architecture on AWS](/images/5-Workshop/5.1-Prerequisites/architecture.png)
<p align="center"><i>Cloud E-Wallet deployment architecture on AWS</i></p>

#### *Request flow*

1. **User → CloudFront/WAF:** The user sends an HTTPS request to a CloudFront distribution protected by AWS WAF.
2. **CloudFront → S3:** For frontend content, CloudFront retrieves the React static files from S3 and caches them at edge locations.
3. **CloudFront → ALB:** Requests matching `/api/*` are routed to the internet-facing Application Load Balancer.
4. **ALB → Target Group → EC2:** The ALB checks `/actuator/health` and forwards requests to healthy EC2 targets on port `8080`. The ASG manages EC2 lifecycle; application traffic does not flow through the ASG.
5. **EC2 → RDS:** The backend connects to RDS MySQL on port `3306` for transactional data processing.
6. **EC2 → SES:** The backend sends verification, verification-resend, password-reset, and other suitable transactional email through Amazon SES SMTP on port `587` with authentication and STARTTLS.
7. **Monitoring:** CloudWatch collects metrics and supports service-health monitoring; it is not part of the synchronous request path.

#### *AWS services*

| Component | Role |
| --- | --- |
| Amazon S3 | Stores the React static build in a private bucket |
| Amazon CloudFront | Provides HTTPS, caches frontend content, and routes `/api/*` to the ALB |
| AWS WAF | Inspects edge requests through a Web ACL associated with CloudFront |
| Application Load Balancer | Receives API traffic and distributes it to healthy targets |
| Target Group | Registers EC2 port `8080` and checks `/actuator/health` |
| Auto Scaling Group | Manages two EC2 instances across two AZs with `Min 0 / Desired 2 / Max 2` |
| Amazon EC2 | Runs the Dockerized Spring Boot backend |
| Amazon RDS for MySQL | Stores transactional data; the DB subnet group spans two private subnets, while the DB instance runs in Single-AZ mode |
| Amazon SES SMTP | Sends transactional email over authenticated STARTTLS on port `587` |
| Amazon CloudWatch | Provides suitable resource metrics and health visibility |
| Internet Gateway | Provides Internet routing for public subnets |
| NAT Gateway | Provides outbound Internet access for private-subnet resources without accepting direct inbound connections |
| AWS KMS | Manages encryption keys for applicable AWS resources |
| Security Groups | Restrict ALB → EC2 → RDS traffic and management connections |

#### *AWS WAF*

The CloudFront Web ACL uses the AWS Managed Rule Group `AWS-AWSManagedRulesCommonRuleSet`, the Core Rule Set with a capacity of **700 WCU**. It covers common categories such as missing or abnormal User-Agent headers, basic bad bots, SSRF attempts against EC2 metadata, LFI/RFI, restricted extensions, XSS, and request-size limits. Some rules use **Block**, while rules such as SizeRestrictions and CrossSiteScripting remain in **Count** so sampled requests can be reviewed before blocking. The report does not claim the use of Bot Control, Fraud Control, or paid Marketplace rule groups.

#### *Network and security design*

The architecture is deployed in a VPC in the Singapore AWS Region (`ap-southeast-1`) and distributed across two Availability Zones to improve availability. Each Availability Zone contains one public subnet and one private subnet.

The two backend EC2 instances run in private subnets across the two Availability Zones and are managed by an Auto Scaling Group. They have no public IP addresses and accept application traffic only from the Application Load Balancer.

Amazon RDS for MySQL is deployed in the private network tier. Its DB subnet group spans the two private subnets, allowing AWS to select an appropriate subnet for database placement. The system uses a Single-AZ RDS configuration, so only one DB instance is active in one Availability Zone at a time, without a synchronized standby instance in the other AZ.

Security Groups control access between tiers. The backend EC2 instances accept application traffic only from the Application Load Balancer, while RDS permits MySQL connections on port `3306` only from the EC2 Security Group.

The NAT Gateway is located in one public subnet and provides outbound Internet connectivity for the private EC2 instances. The Internet Gateway is attached directly to the VPC and does not belong to a specific subnet or Availability Zone.

Application inbound flow:

```text
User browser
  → Application Load Balancer: TCP 80/443
  → Target Group
  → EC2 backend: TCP 8080
  → Amazon RDS for MySQL: TCP 3306
```

The Security Groups follow least-access rules:

```text
ALB Security Group
  → allows inbound TCP 80/443 from the Internet

EC2 Security Group
  → allows inbound TCP 8080 only from the ALB Security Group

RDS Security Group
  → allows inbound TCP 3306 only from the EC2 Security Group
```

EC2 outbound flow:

```text
Private EC2 instance
  → private subnet route table
  → NAT Gateway in a public subnet
  → Internet Gateway
  → Internet or service using a public endpoint
```

The NAT Gateway supports only outbound connections initiated by resources in private subnets. It does not receive user requests and is not part of the application's inbound path.

Because the backend EC2 instances run in private subnets without public IP addresses, administrative SSH sessions must use a controlled private management path. The Security Group does not expose SSH directly from the Internet to the backend tier.
#### *Availability and scalability*

The ALB and ASG distribute compute across two Availability Zones. A desired capacity of `2` maintains two EC2 instances; when a target is unhealthy, the ALB stops routing to it and the ASG can create a replacement. With `Max = 2`, the configuration emphasizes application-tier recovery and availability rather than scaling beyond two instances. One NAT Gateway remains an outbound-path dependency, while Single-AZ RDS is the largest availability limitation, so the architecture is not presented as end-to-end HA.
## 4. Functional scope

### 4.1. Customer

- Register, verify/resend verification email, sign in, and sign out.
- Request and complete password reset.
- View/update profile and balance.
- Make simulated deposits, find recipients, transfer funds, and pay for services.
- Review transaction history.

### 4.2. Administrator

- View the overview dashboard.
- List and block/unblock users.
- Review transactions.
- Add, edit, activate, or deactivate services.

## 5. Technical implementation

The team analyzed requirements and designed the system; developed React, Spring Boot, and MySQL locally; containerized the backend; prepared VPC, subnets, Security Groups, and RDS; deployed EC2, ASG, ALB, S3, CloudFront, and WAF; integrated SES; and validated health, business flows, email delivery, and documentation.

- **Frontend:** React 19, TypeScript, and Vite; static build stored on S3 and distributed by CloudFront.
- **Backend:** Java 17, Spring Boot, Spring Security, JDBC, and Actuator; Dockerized on EC2 port `8080`.
- **Database:** Single-AZ RDS MySQL in private subnets; `utf8mb4`; database transactions and row-level locking protect balance updates.
- **Email:** SES SMTP in `ap-southeast-1`, port `587`, authentication, and STARTTLS.
- **Security:** BCrypt, expiring JWTs, `user`/`admin` roles, secrets outside Git, viewer HTTPS, CloudFront WAF, and Security-Group-restricted inbound traffic.
- **Operations:** Target Group health check at `/actuator/health`; ASG maintains two EC2 instances; CloudWatch supplies suitable metrics and health monitoring.

## 6. Implementation plan

| Stage | Work |
| --- | --- |
| Weeks 1–2 | Analyze requirements, study AWS, and design the database and architecture. |
| Weeks 3–5 | Develop authentication, authorization, profiles, wallets, transactions, and services. |
| Week 6 | Complete the administration UI and local testing. |
| Week 7 | Containerize the backend and prepare VPC, subnets, Security Groups, and RDS. |
| Week 8 | Deploy S3, CloudFront, WAF, ALB, Target Group, EC2, and ASG. |
| Week 9 | Integrate SES, validate production, and complete the report. |

## 7. Budget estimate

The following is an estimate for Singapore (`ap-southeast-1`) using On-Demand pricing and 730 hours per month, not an invoice. The `cloud-ewallet.com` domain cost **USD 10.98** as a one-time payment and is not repeated in monthly operating cost.

### Usage assumptions

All three scenarios preserve the deployed `Desired = 2` architecture: two `t3.micro` EC2 instances, two 8 GB gp3 EBS volumes, one Single-AZ `db.t4g.micro` RDS instance with 20 GB, and one ALB. Scenarios vary by ALB LCU, S3, CloudFront, SES, CloudWatch, and WAF requests.

| Item | Minimum | Average | Assumed maximum |
| --- | --- | --- | --- |
| EC2 and EBS | 2 `t3.micro`; 2 × 8 GB gp3 | Same | Same |
| RDS MySQL | 1 Single-AZ `db.t4g.micro`, 20 GB | Same | Same |
| ALB | Average 0.1 LCU | Average 0.3 LCU | Average 1 LCU |
| S3 | 1 GB, few requests | 5 GB, 30,000 GET and 3,000 PUT | 20 GB, 100,000 GET and 10,000 PUT |
| CloudFront | 5 GB, 50,000 requests | 30 GB, 250,000 requests | 100 GB, 1,000,000 requests |
| SES | 1,000 emails | 3,000 emails | 10,000 emails |
| CloudWatch | Basic metrics | Assumed 1 GB log ingestion | Assumed 5 GB log ingestion |
| AWS WAF | 1 Web ACL, 1 managed rule group, requests within estimate | Same | Same within 10 million requests |
| NAT Gateway | 1 gateway, 1 GB processed | 1 gateway, 5 GB processed | 1 gateway, 20 GB processed |

### Monthly recurring cost

| Service | Minimum (USD) | Average (USD) | Assumed maximum (USD) |
| --- | ---: | ---: | ---: |
| 2 EC2 `t3.micro` instances | 19.28 | 19.28 | 19.28 |
| 2 EBS gp3 volumes, 8 GB each | 1.60 | 1.60 | 1.60 |
| RDS MySQL `db.t4g.micro` + 20 GB | 21.74 | 21.74 | 21.74 |
| Application Load Balancer + LCU | 18.98 | 20.15 | 24.24 |
| AWS WAF | 12.00 | 12.00 | 12.00 |
| NAT Gateway (730 hours + data processing) | 43.13 | 43.37 | 44.25 |
| S3 storage and requests | 0.03 | 0.15 | 0.70 |
| CloudFront transfer and requests | 0.61 | 3.65 | 12.10 |
| Amazon SES | 0.16 | 0.48 | 1.60 |
| CloudWatch | 0.00 | 0.50 | 2.50 |
| **Estimated monthly operation** | **117.53** | **122.92** | **140.01** |
| **First month including domain** | **128.51** | **133.90** | **150.99** |

The approximately **USD 12/month** WAF estimate consists of one Web ACL (USD 5), one AWS Managed Rule Group (USD 1), and up to ten million requests under the stated assumption (USD 6). `AWSManagedRulesCommonRuleSet` has no additional subscription fee like Bot Control or Marketplace rules. This is not a fixed price: additional rules, request volume, logging, CAPTCHA, Bot Control, or paid managed rule groups can increase cost. ASG has no separate management charge; its workload cost appears as two EC2 instances and two EBS volumes. NAT Gateway is estimated using the Singapore assumption of USD 0.059/hour and USD 0.059/GB processed: minimum is `730 × 0.059 + 1 × 0.059`, average uses 5 GB, and assumed maximum uses 20 GB. The rates should be rechecked in AWS Pricing Calculator at deployment time.

- **Minimum – USD 117.53/month:** two backend instances remain active, demo traffic is low, CloudWatch uses basic metrics, and WAF remains within the estimate.
- **Average – USD 122.92/month:** the same compute/database architecture with moderate LCU, transfer, request, email, and monitoring usage.
- **Assumed maximum – USD 140.01/month:** still limited to two EC2 instances, but with higher LCU, S3, CloudFront, SES, and CloudWatch usage within the table.

The estimate excludes tax, Free Tier, snapshots, additional backups, data transfer outside the assumptions, and resources not listed. Actual prices vary by date, Region, and usage.

## 8. Risk assessment and mitigation

| Risk | Mitigation |
| --- | --- |
| An EC2 instance or AZ fails | ALB routes only to healthy targets; ASG maintains desired capacity and launches a replacement. |
| Instance bootstrap fails | Standardize the Launch Template, User Data, and Docker restart behavior; keep failed targets out through the ALB health check. |
| Scale-in or replacement interrupts requests | Use ALB deregistration delay/connection draining before removing a target. |
| Single-AZ RDS remains a single point of failure | Maintain backups and snapshots, and test data recovery regularly. |
| WAF false positives | Keep sensitive rules in Count, inspect sampled requests, and promote them to Block only after review. |
| WAF cost increases | Monitor request volume, rules, logging, and enabled paid capabilities. |
| NAT Gateway or outbound route fails | Check private-subnet routes, NAT Gateway state, and the Internet Gateway; keep NAT outside the inbound request path. |
| One NAT Gateway is an outbound dependency for two AZs | Monitor the NAT Gateway, route tables, and connectivity alerts to detect failures early. |
| New EC2 instances lack secrets or environment variables | Standardize Launch Template/User Data and validate runtime configuration before a target enters service. |
| Compute cost exceeds expectations | Cap the ASG at `Max = 2` and monitor AWS Billing/Cost Explorer. |
| Concurrent transactions produce inconsistent balances | Use database transactions, ACID behavior, and row-level locking. |
| SES bounce, complaint, or quota issues | Monitor sending statistics, bounces/complaints, and SES quotas. |

## 9. Results

- S3 and CloudFront deliver the static frontend over HTTPS.
- WAF inspects edge requests through the Core Rule Set using both Block and Count modes.
- ALB, the Target Group, and ASG improve backend availability with two EC2 instances across two Availability Zones.
- The Security Group design restricts CloudFront/Internet → ALB → EC2:8080 → RDS:3306; backend EC2 instances do not accept application traffic directly from the Internet.
- RDS MySQL provides consistent transactional storage, while Single-AZ remains the current availability limitation.
- SES provides personalized transactional email; CloudWatch supports metrics and service-health visibility.
- The architecture balances security, availability, and cost optimization for the project scope without claiming end-to-end high availability.
