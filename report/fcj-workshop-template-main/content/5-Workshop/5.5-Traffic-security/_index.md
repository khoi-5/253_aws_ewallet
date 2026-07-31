---
title: "Configure ALB, CloudFront, DNS, and network security"
date: 2024-01-01
weight: 5
chapter: false
pre: " <b> 5.5. </b> "
---

## Objective

Complete the request path from users to the frontend and backend while restricting network access along CloudFront → Application Load Balancer → EC2 → Amazon RDS.

## AWS WAF at CloudFront

The CloudFront distribution is protected by a Web ACL using the AWS Managed Rule Group `AWS-AWSManagedRulesCommonRuleSet` (Core Rule Set, 700 WCU). WAF inspects HTTP/HTTPS requests before they reach the S3 or ALB origin. Covered categories include abnormal User-Agent headers, basic bad bots, SSRF against EC2 metadata, LFI/RFI, restricted extensions, XSS, and request-size limits. Selected rules use Block; SizeRestrictions and CrossSiteScripting use Count so sampled requests can be reviewed first. WAF does not replace JWT, Spring Security, or backend validation.

## Auto Scaling Group and Target Group

The ASG uses `Min = 0`, `Desired = 2`, and `Max = 2` to maintain two backend EC2 instances across two Availability Zones. Requests flow from the ALB through the Target Group to EC2; the ASG manages lifecycle and replacement rather than carrying traffic. If a target fails, the ALB stops routing to it while the ASG restores desired capacity. RDS remains Single-AZ, so this fault tolerance applies to the application tier only.
## Configure the target group

Our team created the backend target group with these settings:

- Target type: EC2 instance.
- Protocol: HTTP.
- Port: `8080`.
- Health check path: `/actuator/health`.
- Two ASG-managed backend EC2 instances are registered as targets across two Availability Zones.

The ALB forwards traffic to EC2 only after the health check succeeds and the target becomes **Healthy**.

![Healthy backend Target Group](/images/5-Workshop/5.5-Traffic-security/target-group-healthy.png)

<p style="text-align: center;"><em>Figure 5.18. The target group uses HTTP port 8080 and the backend EC2 target is Healthy.</em></p>

## Configure the Application Load Balancer

The internet-facing Application Load Balancer runs in the public subnets of `ewallet-vpc`. Its HTTP listener on port `80` forwards requests to backend target-group port `8080`.

The browser connects to CloudFront over HTTPS. CloudFront connects to the ALB origin, and the ALB forwards the request to the Spring Boot container on EC2.

![Application Load Balancer details and listener](/images/5-Workshop/5.5-Traffic-security/alb-details-listener.png)

<p style="text-align: center;"><em>Figure 5.19. Internet-facing ALB with an HTTP port 80 listener forwarding to the backend target group.</em></p>

## Restrict access with security groups

Security groups use source relationships instead of exposing application ports directly to the Internet:

| Resource | Required inbound rule | Purpose |
| --- | --- | --- |
| ALB | HTTP port `80` for the current public listener | Receive API requests from CloudFront |
| EC2 backend | TCP `8080` with the ALB security group as source | Allow only the ALB to call Spring Boot |
| Amazon RDS | MySQL `3306` with the EC2 security group as source | Allow only the backend to connect to the database |

In the deployment architecture, EC2 is placed in private subnets and does not accept application traffic directly from the Internet. Port `8080` accepts traffic only from the ALB security group, while required outbound connections use the NAT Gateway in a public subnet.

![ALB security group inbound rules](/images/5-Workshop/5.5-Traffic-security/alb-security-group-inbound.png)

<p style="text-align: center;"><em>Figure 5.20. The ALB security group allows HTTP port 80 and HTTPS port 443 from the Internet.</em></p>

The EC2 inbound rules are shown in [Section 5.3.1](../5.3-Backend-deployment/5.3.1-Backend-environment/), and the RDS inbound rule is shown in [Section 5.2.1](../5.2-Database-deployment/5.2.1-RDS-configuration/), so the screenshots are not duplicated here.

## Route the API through CloudFront

The distribution has two origins and two behaviors:

- Default `(*)` uses the S3 frontend origin with `Managed-CachingOptimized`.
- `/api/*` uses `ewallet-alb-origin` with `Managed-CachingDisabled`, preventing unintended API-response caching.

CloudFront origins and behaviors are documented in [Section 5.4.1](../5.4-Frontend-deployment/5.4.1-S3-CloudFront-configuration/). This section focuses on their role in the end-to-end routing flow.

## Configure DNS in Cloudflare

Cloudflare manages DNS for `cloud-ewallet.com`; it does not replace CloudFront as the CDN. The apex domain and `www` point to CloudFront, while certificate-validation, DKIM, Custom MAIL FROM/SPF, and DMARC records use values supplied by ACM or Amazon SES. AWS and email verification records remain **DNS only**. SES domain identity, Easy DKIM, and SMTP credentials are covered in [Section 5.3.1](../5.3-Backend-deployment/5.3.1-Backend-environment/).

## Validation

The available deployment evidence confirms that:

- `https://cloud-ewallet.com` loads the S3 frontend through CloudFront.
- CloudFront routes `/api/*` requests to the ALB and then EC2 port `8080`.
- The EC2 target is **Healthy** in the target group.
- Backend EC2 has no direct application path from the Internet; port `8080` accepts traffic only from the ALB security group.
- EC2 connects to RDS on port `3306` according to the security-group source rule.
- The domain, certificate, and SES verification records operate through Cloudflare DNS.

The deployment evidence confirms the routing components, the Healthy target state, and the security-group chain. The private-subnet placement and NAT outbound route are documented in the VPC configuration in Section 5.1.