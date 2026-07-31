---
title: "Build and deploy the backend to EC2"
date: 2024-01-01
weight: 2
chapter: false
pre: " <b> 5.3.2. </b> "
---

## Objective

Validate the Spring Boot source, create a Docker image, and run the backend container on production EC2 with the environment file prepared in Section 5.3.1.

## Execution scope

| Environment | Work |
| --- | --- |
| Local Windows machine | Run Maven tests/package, build the Docker image, and push it to Docker Hub |
| Production EC2 | Pull the Docker image, run the container with production configuration, and check health |
| AWS Console and production domain | Verify ALB targets, API routing, and Amazon SES email |

PowerShell source checks run locally. Production `docker run`, `docker ps`, `docker logs`, and `curl` commands run through an SSH session reached through the private management path on each EC2 instance. Both ASG instances use the same image, environment file, and start command; the Target Group serves only instances that pass health checks.

## Validate the backend locally

From the repository:

```powershell
cd backend
.\mvnw.cmd test
.\mvnw.cmd package -DskipTests
```

Continue only after both commands succeed. The packaged JAR is created in `backend/target/`.

The project Dockerfile uses a multi-stage build with Maven and Java 17, followed by an Eclipse Temurin Java 17 JRE runtime image. The application runs as a non-root user and exposes port `8080`.

## Build and push the Docker image to Docker Hub

In `backend/`, sign in to Docker Hub, build the image, and push the selected release to the registry:

```powershell
docker login
docker build -t <BACKEND_IMAGE> .
docker image ls
docker push <BACKEND_IMAGE>
```

`<BACKEND_IMAGE>` is the fully qualified image name and tag in the form `<DOCKER_HUB_USERNAME>/<IMAGE_NAME>:<IMAGE_TAG>`. The report uses a placeholder because the repository does not contain authoritative evidence for the exact production image name and tag. Docker Hub passwords and access tokens must not be placed in source code or documentation.

## Prepare EC2

On each production EC2 instance, pull the same image that was pushed to Docker Hub, then confirm that the image and external environment file are available:

```bash
docker pull <BACKEND_IMAGE>
docker image ls
ls -l /home/ec2-user/ewallet-backend.env
```

If the Docker Hub repository is private, run `docker login` on EC2 with a least-privilege credential before `docker pull`. A public repository does not require authentication to pull the image.

Do not use `cat` to display the environment file. It contains RDS credentials, the JWT secret, and SES SMTP credentials. These values enter the container at runtime instead of being embedded in the image.

## Run the backend container

If an older container exists, inspect its status and recent logs:

```bash
docker ps
docker logs --tail 100 ewallet-backend
```

Then stop and remove it:

```bash
docker stop ewallet-backend
docker rm ewallet-backend
```

Run the production backend:

```bash
docker run -d \
  --name ewallet-backend \
  --restart unless-stopped \
  --env-file /home/ec2-user/ewallet-backend.env \
  -p 8080:8080 \
  <BACKEND_IMAGE>
```

| Option | Purpose |
| --- | --- |
| `-d` | Run in the background |
| `--name ewallet-backend` | Use a stable name for inspection and management |
| `--restart unless-stopped` | Restart after Docker or EC2 restarts unless deliberately stopped |
| `--env-file` | Load production settings from outside the image |
| `-p 8080:8080` | Publish the Spring Boot container port on EC2 |

The root `docker-compose.yml` is only for local MySQL and is not used in production with Amazon RDS.

## Validate on production EC2

After startup, run:

```bash
docker ps
docker logs --tail 100 ewallet-backend
curl http://localhost:8080/actuator/health
```

Expected health response:

```json
{"status":"UP"}
```

The container must be `Up` and publish port `8080`. Logs must not show RDS connectivity, JWT configuration, or Amazon SES errors.

## Validate through AWS infrastructure

After the local EC2 health check succeeds:

1. Confirm that both EC2 targets become **Healthy** in the Target Group.
2. Call `https://cloud-ewallet.com/api/*` to verify CloudFront and ALB routing.
3. Test registration, verification-email resend, and password reset.
4. Review Amazon SES Sending Statistics after email delivery.

Backend EC2 does not accept application traffic directly from the Internet; its security group accepts port `8080` only from the ALB security group.

## Result

The Spring Boot backend runs in Docker on EC2, connects to Amazon RDS, and sends email through Amazon SES. Actuator confirms container health, and the API is served through the Application Load Balancer.
