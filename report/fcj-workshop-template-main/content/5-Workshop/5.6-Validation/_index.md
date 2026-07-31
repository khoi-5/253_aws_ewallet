---
title: "Post-deployment validation"
date: 2024-01-01
weight: 6
chapter: false
pre: " <b> 5.6. </b> "
---

## Objective

Validate the production system from the user's perspective: the frontend loads through the primary domain, application workflows reach the backend, and Amazon SES delivers application email.

## Confirm system readiness

Our team accessed the system at `https://cloud-ewallet.com`. CloudFront serves the Amazon S3 frontend and routes `/api/*` requests through the Application Load Balancer to the EC2 backend.

Infrastructure health is documented in [Section 5.5](../5.5-Traffic-security/): the target group uses HTTP port `8080`, and the backend EC2 targets are **Healthy**. Therefore, this section does not repeat AWS Console evidence or expose an Actuator endpoint solely for screenshots.

After sign-in, the wallet page displayed the account information and current balance. This result also confirms that the frontend called an authenticated API and the backend retrieved data from Amazon RDS.

![Wallet page in production](/images/5-Workshop/5.6-Validation/production-wallet.png)

<p style="text-align: center;"><em>Figure 5.21. The user's wallet page loads successfully in production.</em></p>

## Validate money transfer

Our team transferred a simulated balance between two test accounts. Before submission, the application looked up the recipient phone number, displayed the matching account name, and accepted the amount and transaction note.

![Money-transfer form](/images/5-Workshop/5.6-Validation/transfer-form.png)

<p style="text-align: center;"><em>Figure 5.22. Recipient, amount, and note entered for a test transaction.</em></p>

After confirmation, the interface displayed **Transfer successfully**, the sender balance decreased from `210 USD` to `110 USD`, and the form was reset. This confirms that the request traveled through CloudFront and the ALB, the backend processed the transaction, and RDS stored the updated balance.

![Successful money transfer](/images/5-Workshop/5.6-Validation/transfer-success.png)

<p style="text-align: center;"><em>Figure 5.23. The transaction completed and the wallet balance was updated.</em></p>

## Validate email through Amazon SES

Our team used the forgot-password workflow to validate application email. The backend created the reset request, sent the message through Amazon SES, and the user received it from an address under `cloud-ewallet.com`. The link points to the password-reset page on the production domain.

![Password-reset email delivered through Amazon SES](/images/5-Workshop/5.6-Validation/ses-password-reset-email.png)

<p style="text-align: center;"><em>Figure 5.24. Password-reset email received successfully through Amazon SES.</em></p>

## Result

The validation confirmed three primary production flows:

- The user accesses the HTTPS frontend and signs in to view wallet information.
- Money transfer reaches the backend and updates database records.
- The backend sends application email through Amazon SES.

Together with the **Healthy** targets shown in Section 5.5, these results confirm that the CloudFront/WAF → ALB → Target Group → EC2 → RDS application flow and the Amazon SES integration operate as configured.