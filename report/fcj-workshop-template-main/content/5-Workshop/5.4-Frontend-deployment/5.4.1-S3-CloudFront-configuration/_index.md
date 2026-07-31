---
title: "Install and configure S3 and CloudFront"
date: 2024-01-01
weight: 1
chapter: false
pre: " <b> 5.4.1. </b> "
---

## Objective

Prepare the infrastructure that distributes the React frontend through Amazon S3 and Amazon CloudFront. This section summarizes the main configuration and uses the final resource states as evidence.

## Overall process

The team completed three stages:

1. Create a dedicated S3 bucket for frontend static files and keep it private.
2. Create a CloudFront distribution, configure S3 as the frontend origin, and use Origin Access Control for bucket access.
3. Attach the custom domain and TLS certificate, then configure behaviors that serve the frontend and route `/api/*` to the backend.

## Configure Amazon S3

The `khoi-ewallet-frontend-2026` bucket stores only the build output from `frontend/dist/`, including `index.html`, the `assets/` directory, and other static resources. Source code, `node_modules`, and `.env.production` are not uploaded.

The bucket remains private through **Block Public Access**. Users cannot retrieve objects directly from S3; CloudFront reads them through Origin Access Control, and the bucket policy limits access to the distribution.

![Block Public Access on the frontend bucket](/images/5-Workshop/5.4-Frontend-deployment/5.4.1-S3-CloudFront-configuration/s3-block-public-access.png)

<p style="text-align: center;"><em>Figure 5.11. Block Public Access enabled for the frontend S3 bucket.</em></p>

## Configure the CloudFront distribution

The `ewallet-frontend` distribution is the HTTPS entry point for the application. Its main settings are:

- Default root object: `index.html`.
- Custom domains: `cloud-ewallet.com` and `www.cloud-ewallet.com`.
- A custom SSL certificate for the project domain.
- HTTPS for viewers and the current TLS security policy of the distribution.
- An S3 origin for the React frontend.
- An Application Load Balancer origin for backend API traffic.

![CloudFront distribution overview](/images/5-Workshop/5.4-Frontend-deployment/5.4.1-S3-CloudFront-configuration/cloudfront-general.png)

<p style="text-align: center;"><em>Figure 5.12. CloudFront domain, TLS certificate, and default root object settings.</em></p>

## Configure origins

The distribution uses two origins:

| Origin | Type | Responsibility |
| --- | --- | --- |
| Frontend S3 bucket | Amazon S3 | Serve `index.html` and React static assets |
| `ewallet-alb-origin` | Elastic Load Balancing | Forward API requests to the Application Load Balancer |

The S3 origin uses Origin Access Control instead of making the bucket public. The ALB origin handles API requests only; its network and security-group configuration is covered in Section 5.5.

![CloudFront origins](/images/5-Workshop/5.4-Frontend-deployment/5.4.1-S3-CloudFront-configuration/cloudfront-origins.png)

<p style="text-align: center;"><em>Figure 5.13. The S3 frontend origin and Application Load Balancer origin.</em></p>

## Configure behaviors

CloudFront evaluates two behaviors in priority order:

- `/api/*` routes to `ewallet-alb-origin`, uses `Managed-CachingDisabled`, and redirects viewer HTTP requests to HTTPS.
- Default `(*)` routes to the S3 frontend origin and uses `Managed-CachingOptimized` for static resources.

This split allows the frontend and API to share `cloud-ewallet.com` while reducing CORS complexity because the browser communicates with one public domain.

![CloudFront behaviors](/images/5-Workshop/5.4-Frontend-deployment/5.4.1-S3-CloudFront-configuration/cloudfront-behaviors.png)

<p style="text-align: center;"><em>Figure 5.14. The `/api/*` behavior routes to ALB, while the default behavior routes to S3.</em></p>

For React Router, direct navigation to a route may return `403` or `404`. A custom error response can return `/index.html` with status `200`, after which each production route is verified. The custom domain and CloudFront, ACM, and Amazon SES DNS records are described in [Section 5.5](../../5.5-Traffic-security/).

## Attach AWS WAF to CloudFront

A Web ACL containing `AWS-AWSManagedRulesCommonRuleSet` (700 WCU) is associated with the distribution so requests are inspected before reaching the S3 or ALB origin. Rules that were verified as suitable use Block, while SizeRestrictions and CrossSiteScripting rules use Count so sampled requests can be reviewed before blocking. WAF complements rather than replaces JWT authentication, Spring Security, and backend validation.

![AWS WAF Web ACL and managed rules](/images/5-Workshop/5.4-Frontend-deployment/5.4.1-S3-CloudFront-configuration/waf-common-rule-set.png)

<p style="text-align: center;"><em>Figure 5.15. The CloudFront Web ACL uses AWS Managed Rules Common Rule Set to inspect requests.</em></p>

## Verification

The team confirms that the distribution uses the correct custom domains and TLS certificate, the default behavior serves the frontend from S3, and `/api/*` reaches ALB without caching API responses. The S3 bucket remains private, and the application home page loads through HTTPS on the production domain.

The frontend build and release procedure is covered in Section 5.4.2.