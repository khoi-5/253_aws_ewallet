---
title: "Frontend deployment"
date: 2024-01-01
weight: 4
chapter: false
pre: " <b> 5.4. </b> "
---

| Section | Content | Outcome |
| --- | --- | --- |
| [5.4.1. Install and configure S3 and CloudFront](5.4.1-S3-CloudFront-configuration/) | Create the private S3 bucket, OAC, distribution, and React route fallback | HTTPS frontend delivery infrastructure is ready |
| [5.4.2. Build and release the frontend through CloudFront](5.4.2-Frontend-release/) | Configure the API URL, lint/build, synchronize S3, invalidate, and smoke-test | The new frontend release is delivered through CloudFront |