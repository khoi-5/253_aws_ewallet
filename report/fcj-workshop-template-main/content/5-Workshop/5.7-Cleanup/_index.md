---
title: "Resource cleanup"
date: 2024-01-01
weight: 7
chapter: false
pre: " <b> 5.7. </b> "
---

## Cleanup steps

Cleanup follows dependency order to prevent accidental data loss and avoid residual resources continuing to incur charges.

1. **Back up required data:** export required records and create a final Amazon RDS snapshot before deleting the database.
2. **Stop the backend tier:** set the Auto Scaling Group desired capacity to `0`, wait for the backend EC2 instances to terminate, then delete the Auto Scaling Group and its Launch Template when they are no longer used.
3. **Clean up the Application Load Balancer:** delete the listener, the ALB, and the backend target group after no EC2 targets depend on them.
4. **Clean up AWS WAF and CloudFront:** disassociate the Web ACL, remove alternate domain names when necessary, disable and delete the CloudFront distribution, then delete the Web ACL after no resources remain associated with it.
5. **Clean up Amazon S3:** delete all objects and object versions from the frontend bucket, then delete the bucket after CloudFront no longer uses it.
6. **Delete Amazon RDS:** confirm that the final snapshot exists, then delete the DB instance and unused DB subnet group.
7. **Delete network resources:** delete the NAT Gateway first, release unused Elastic IP addresses, and then remove security groups, custom route tables, subnets, the Internet Gateway, and the VPC in dependency order.
8. **Clean up IAM:** detach policies and delete IAM roles or users dedicated to the project; deactivate and delete unused SES SMTP credentials.
9. **Clean up Amazon SES and DNS:** remove the SES identity, DKIM, MAIL FROM, ACM validation, and Cloudflare DNS records when the domain is no longer used for the website or email.
10. **Review costs:** check AWS Billing and Cost Explorer to confirm that no unexpected resources continue to incur charges.

Before each deletion, the team verifies the resource name, Region, and dependencies. CloudFront must be disabled before deletion; snapshots, Elastic IP addresses, NAT Gateway resources, and S3 data may continue to incur charges while they remain.