---
title: "Build và triển khai backend lên EC2"
date: 2024-01-01
weight: 2
chapter: false
pre: " <b> 5.3.2. </b> "
---

## Mục tiêu

Kiểm tra source Spring Boot, tạo Docker image và chạy backend container trên EC2 production bằng file môi trường đã chuẩn bị tại mục 5.3.1.

## Phạm vi thực hiện

| Môi trường | Công việc |
| --- | --- |
| Máy local Windows | Chạy Maven test/package, build và push Docker image lên Docker Hub |
| EC2 production | Pull Docker image, chạy container bằng file môi trường production và kiểm tra health |
| AWS Console/domain production | Kiểm tra ALB target, API và email Amazon SES |

Các lệnh PowerShell dùng để kiểm tra source được chạy trên máy local. Các lệnh `docker run`, `docker ps`, `docker logs` và `curl` kiểm tra production được chạy trong phiên SSH đến từng EC2 qua kênh quản trị private. Cùng image, file môi trường và cấu hình khởi động được áp dụng cho hai instance của ASG; Target Group chỉ đưa instance qua health check vào phục vụ.

## Kiểm tra backend trên máy local

Từ repository, nhóm chạy:

```powershell
cd backend
.\mvnw.cmd test
.\mvnw.cmd package -DskipTests
```

Chỉ tiếp tục khi test và package thành công. File JAR được tạo trong `backend/target/`.

Dockerfile của dự án sử dụng multi-stage build với Maven và Java 17 ở build stage, sau đó tạo runtime image sử dụng Eclipse Temurin Java 17 JRE. Ứng dụng chạy bằng user không phải root và expose port `8080`.

## Build và đẩy Docker image lên Docker Hub

Tại thư mục `backend/`, nhóm đăng nhập Docker Hub, tạo image và đẩy đúng phiên bản lên registry:

```powershell
docker login
docker build -t <BACKEND_IMAGE> .
docker image ls
docker push <BACKEND_IMAGE>
```

`<BACKEND_IMAGE>` là tên đầy đủ kèm tag của image được triển khai, theo dạng `<DOCKER_HUB_USERNAME>/<IMAGE_NAME>:<IMAGE_TAG>`. Báo cáo sử dụng placeholder vì repository không lưu bằng chứng xác nhận chính xác tên và tag của phiên bản production. Không đưa mật khẩu hoặc access token Docker Hub vào source và tài liệu.

## Chuẩn bị trước khi chạy container trên EC2

Trên từng EC2 production, nhóm kéo đúng image đã đẩy lên Docker Hub, sau đó xác nhận image và file môi trường đã sẵn sàng:

```bash
docker pull <BACKEND_IMAGE>
docker image ls
ls -l /home/ec2-user/ewallet-backend.env
```

Nếu Docker Hub repository ở chế độ private, cần chạy `docker login` trên EC2 bằng credential có phạm vi tối thiểu trước khi `docker pull`. Repository public không yêu cầu bước đăng nhập để pull image.

Không sử dụng `cat` để hiển thị file môi trường. File này chứa RDS credential, JWT secret và SES SMTP credential, được truyền vào container tại runtime thay vì ghi trực tiếp trong Docker image.

## Chạy backend container

Nếu container cũ đang tồn tại, nhóm kiểm tra trạng thái và log gần nhất:

```bash
docker ps
docker logs --tail 100 ewallet-backend
```

Sau đó dừng và xóa container cũ:

```bash
docker stop ewallet-backend
docker rm ewallet-backend
```

Chạy backend production bằng:

```bash
docker run -d \
  --name ewallet-backend \
  --restart unless-stopped \
  --env-file /home/ec2-user/ewallet-backend.env \
  -p 8080:8080 \
  <BACKEND_IMAGE>
```

Các tham số chính:

| Tham số | Mục đích |
| --- | --- |
| `-d` | Chạy container ở chế độ nền |
| `--name ewallet-backend` | Đặt tên cố định để kiểm tra và quản lý container |
| `--restart unless-stopped` | Tự khởi động lại khi Docker hoặc EC2 restart, trừ khi container được chủ động dừng |
| `--env-file` | Nạp cấu hình production từ file bên ngoài image |
| `-p 8080:8080` | Publish port Spring Boot từ container ra EC2 |

File `docker-compose.yml` ở root chỉ phục vụ MySQL local, không được dùng cho production sử dụng Amazon RDS.

## Kiểm tra trên EC2 production

Sau khi chạy container, nhóm kiểm tra:

```bash
docker ps
docker logs --tail 100 ewallet-backend
curl http://localhost:8080/actuator/health
```

Kết quả health check mong đợi:

```json
{"status":"UP"}
```

Container cần ở trạng thái `Up` và publish port `8080`. Log không được có lỗi kết nối RDS, cấu hình JWT hoặc Amazon SES.

## Kiểm tra qua hạ tầng AWS

Sau khi health check trên EC2 thành công, nhóm thực hiện:

1. Kiểm tra EC2 target trong target group chuyển sang **Healthy**.
2. Gọi API qua `https://cloud-ewallet.com/api/*` để xác nhận CloudFront và ALB định tuyến đúng.
3. Thử đăng ký, gửi lại email xác minh và quên mật khẩu.
4. Kiểm tra Amazon SES Sending Statistics sau khi gửi email.

Backend EC2 không nhận traffic ứng dụng trực tiếp từ Internet; Security Group chỉ cho phép port `8080` từ ALB Security Group.


## Kết quả

Spring Boot backend chạy trong Docker container trên EC2, kết nối Amazon RDS và gửi email qua Amazon SES. Container được kiểm tra bằng Actuator và phục vụ API thông qua Application Load Balancer.
