---
title: "Build and release the frontend through CloudFront"
date: 2024-01-01
weight: 2
chapter: false
pre: " <b> 5.4.2. </b> "
---

## Objective

Document the repeatable release process for a React frontend: prepare the build variable, validate source, generate static files, synchronize them to S3, refresh CloudFront cache, and smoke-test the production domain.

## Step 1: Prepare the build variable

The frontend reads `frontend/.env.production` at build time. `VITE_API_BASE_URL` is a public URL embedded in JavaScript, not a secret.

With the custom domain and `/api/*` behavior:

```dotenv
VITE_API_BASE_URL=https://cloud-ewallet.com
```

Before attaching the domain, a distribution test can temporarily use:

```dotenv
VITE_API_BASE_URL=https://<CLOUDFRONT_DISTRIBUTION_DOMAIN>
```

Rebuild the frontend whenever this value changes.

## Step 2: Validate and build

In `frontend/`, install dependencies, run ESLint, and build with Vite:

```powershell
cd frontend
npm install
npm run lint
npm run build
```

The `build` script runs TypeScript before Vite writes output to `frontend/dist/`. The repository does not define an automated frontend test script, so linting, building, and smoke testing are the main release checks.

Before upload, inspect `dist/index.html`, `dist/assets/`, and the required static files. Do not edit `dist/` manually because the next build overwrites it.

## Step 3: Synchronize static files to S3

When AWS CLI is configured with the correct IAM identity, synchronize `dist/`:

```powershell
aws s3 sync .\dist\ s3://<FRONTEND_BUCKET>/ --delete
```

Confirm the destination bucket before using `--delete`. With the AWS Console, upload everything inside `dist/` and remove obsolete assets that are no longer referenced.

The bucket should contain `index.html`, `assets/`, images, and other frontend files. Source code and environment files must not appear in S3.

![Frontend static files uploaded to S3](/images/5-Workshop/5.4-Frontend-deployment/5.4.2-Frontend-release/s3-deployed-objects.png)

<p style="text-align: center;"><em>Figure 5.16. The frontend build output uploaded to the production S3 bucket.</em></p>

## Step 4: Create a CloudFront invalidation

After updating S3, invalidate `/*` so edge locations refresh cached content. Use **CloudFront → Distribution → Invalidations → Create invalidation**, or AWS CLI:

```powershell
aws cloudfront create-invalidation `
  --distribution-id <DISTRIBUTION_ID> `
  --paths "/*"
```

![Create a CloudFront invalidation for the frontend release](/images/5-Workshop/5.4-Frontend-deployment/5.4.2-Frontend-release/cloudfront-create-invalidation.png)

<p style="text-align: center;"><em>Figure 5.17. Invalidating all paths after updating the frontend.</em></p>

Wait for **Completed** before evaluating the release. Otherwise, an older `index.html` may reference assets that have already changed.

## Step 5: Post-release validation

Perform smoke tests in this order:

1. Open `https://cloud-ewallet.com` and confirm a valid HTTPS connection.
2. Hard-refresh, open DevTools, and check that no asset returns `403` or `404`.
3. Open React routes directly to validate SPA fallback.
4. Call `/api/*` and confirm that requests reach the backend.
5. Review desktop/mobile rendering and the sign-in workflow.

If a problem appears, compare the local build, S3 object list, CloudFront origins/behaviors, and invalidation status before releasing again.

## Result

The new frontend version is stored in S3, distributed through CloudFront, and available through the custom domain.