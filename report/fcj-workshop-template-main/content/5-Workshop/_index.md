---
title: "Workshop"
date: 2024-01-01
weight: 5
chapter: false
pre: " <b> 5. </b> "
---

This section presents the Cloud E-Wallet deployment architecture on AWS and the process completed by our team. The content follows the dependencies among the database, backend, frontend, routing, security, and validation so that readers can trace the complete deployment flow from infrastructure to application.

![Cloud E-Wallet deployment architecture on AWS](/images/5-Workshop/5.1-Prerequisites/architecture.png)

<p style="text-align: center;"><em>Figure 5.1. Cloud E-Wallet deployment architecture on AWS.</em></p>

In this architecture, users send HTTPS requests to a CloudFront distribution protected by AWS WAF. The default behavior serves the React frontend from Amazon S3, while `/api/*` travels through the internet-facing ALB and Target Group to two EC2 instances in private subnets across two Availability Zones. An Auto Scaling Group with `Min 0 / Desired 2 / Max 2` manages these instances. The backend connects to Single-AZ RDS MySQL over the private network and uses Amazon SES. CloudWatch provides monitoring, KMS supports encryption-key management, and one NAT Gateway in a public subnet provides outbound Internet access for the private EC2 instances. Cloudflare manages only DNS and AWS/SES verification records.

Cloud E-Wallet uses simulated balances, does not process real money, does not connect to a payment gateway, and does not send card data to the backend.

## Procedure

| Step | Main work | Expected outcome |
| --- | --- | --- |
| [5.1. Prepare the environment](5.1-Prerequisites/) | Verify tools and source; identify the two IAM users and configure the frontend, backend, RDS, JWT, CORS, and Amazon SES configuration | Deployment environment is ready in the correct Region with no exposed secrets |
| [5.2. Deploy the database](5.2-Database-deployment/) | Configure private RDS, then initialize the schema and baseline data | The production database is ready for the backend |
| [5.3. Deploy the backend](5.3-Backend-deployment/) | Prepare EC2/Docker/SES, build the image, and release the Spring Boot container | The backend connects to RDS, sends email, and serves traffic through the ALB |
| [5.4. Deploy the frontend](5.4-Frontend-deployment/) | Configure S3/CloudFront, build React, and release the static files | The frontend is delivered over HTTPS through CloudFront or the custom domain |
| [5.5. Configure routing and security](5.5-Traffic-security/) | Configure WAF, the target group, ALB, ASG, CloudFront `/api/*`, Cloudflare DNS, and security groups | Traffic follows CloudFront/WAF → ALB → Target Group → EC2 without unnecessary direct exposure of EC2 or RDS |
| [5.6. Validate the deployment](5.6-Validation/) | Confirm backend health, sign-in, wallet information, money transfer, and password-reset email through Amazon SES | Primary production workflows operate end to end |
| [5.7. Clean up resources](5.7-Cleanup/) | Back up required data, verify dependencies, and stop or delete unused resources | Post-demo charges are reduced |
