# Production deployment

Production email is sent through Amazon SES SMTP in `ap-southeast-1`. Resend SMTP
remains an environment-selected rollback provider. Never put populated environment
files or SMTP credentials in Git.

## Migrate from Resend to Amazon SES

1. Confirm the sender/domain identity is verified in SES in `ap-southeast-1`.
2. Confirm the SES account is out of the sandbox in the Singapore region.
3. Create SES-specific SMTP credentials. Do not use an AWS console password, root
   credential, or ordinary AWS access key as an SMTP credential.
4. Back up the current environment file:

   ```bash
   cp /home/ec2-user/ewallet-backend.env \
      /home/ec2-user/ewallet-backend.env.before-ses
   ```

5. Edit `/home/ec2-user/ewallet-backend.env` manually. Set
   `EMAIL_PROVIDER=ses`, add the five `SES_*` values from
   `.env.production.example`, and ensure `MAIL_DEVELOPMENT_LOG_ENABLED=false`.
6. Preserve the existing `RESEND_*` values in the external environment file as
   rollback configuration.
7. Recreate the backend container:

   ```bash
   docker stop ewallet-backend
   docker rm ewallet-backend

   docker run -d \
     --name ewallet-backend \
     --restart unless-stopped \
     --env-file /home/ec2-user/ewallet-backend.env \
     -p 8080:8080 \
     <BACKEND_IMAGE>
   ```

8. Verify startup:

   ```bash
   docker ps
   docker logs --tail 100 ewallet-backend
   curl http://localhost:8080/actuator/health
   ```

9. Test registration email, resend-verification email, forgot-password email,
   and completion of password reset.
10. Check SES Sending Statistics for sends, deliveries, bounces, and complaints.

Only the selected provider is validated at startup. Unsupported provider names or
missing selected-provider fields stop startup without logging credential values.

## Rollback to Resend

Set `EMAIL_PROVIDER=resend` in `/home/ec2-user/ewallet-backend.env`, retain valid
`RESEND_SMTP_*` and `RESEND_MAIL_FROM_ADDRESS` values, then repeat the container
stop/remove/run and verification commands above. No application rebuild is needed.

## CI/CD preparation

Future CI/CD should inject `EMAIL_PROVIDER`, `SES_SMTP_USERNAME`,
`SES_SMTP_PASSWORD`, and `SES_MAIL_FROM_ADDRESS` through AWS Secrets Manager,
Systems Manager Parameter Store, GitHub Actions secrets, or CodePipeline/CodeBuild
environment secrets. Host and port can also be injected. Resend fallback secrets
must remain external to Git. This repository does not implement CI/CD or a secrets
manager integration.
