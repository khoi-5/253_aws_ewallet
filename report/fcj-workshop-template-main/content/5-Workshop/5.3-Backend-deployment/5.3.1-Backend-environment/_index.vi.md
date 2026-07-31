---
title: "Cài đặt và cấu hình môi trường backend"
date: 2024-01-01
weight: 1
chapter: false
pre: " <b> 5.3.1. </b> "
---

## Mục tiêu

Chuẩn bị môi trường chạy Spring Boot backend trên Amazon EC2, cài đặt Docker, cấu hình kết nối đến Amazon RDS và Amazon SES, đồng thời kiểm tra trạng thái tài nguyên sau khi hoàn tất.

## Quy trình tổng quát

Nhóm chúng em thực hiện theo bốn giai đoạn:

1. Tạo EC2 trong VPC và subnet đã chuẩn bị, sau đó gắn IAM role cùng Security Group phù hợp.
2. Mở phiên SSH qua kênh quản trị có quyền truy cập private subnet, cài đặt Docker và kiểm tra Docker Engine.
3. Chuẩn bị Amazon SES tại Region Singapore và tạo SMTP credential riêng cho backend.
4. Tạo file biến môi trường trên EC2 để container kết nối RDS, SES và domain frontend mà không đưa secret vào source.

## Chuẩn bị Amazon EC2

Trong kiến trúc triển khai, các EC2 backend sử dụng Amazon Linux 2023 và instance type `t3.micro`. Launch Template được ASG dùng để duy trì hai instance trong hai private subnet tại hai Availability Zone, với `Min = 0`, `Desired = 2`, `Max = 2`. Các instance dùng cùng cấu hình nền; kết nối outbound đi qua NAT Gateway trong public subnet và Internet Gateway.

![Auto Scaling Group quản lý hai EC2 backend](/images/5-Workshop/5.3-Backend-deployment/5.3.1-Backend-environment/asg-capacity-instances.png)

<p style="text-align: center;"><em>Hình 5.6. Auto Scaling Group duy trì hai EC2 backend Healthy trên hai Availability Zone.</em></p>

IAM role `CloudEwalletEC2Role` được gắn cho instance để ứng dụng hoặc công cụ trên EC2 sử dụng quyền AWS cần thiết mà không lưu access key dài hạn trên máy chủ. Instance yêu cầu IMDSv2 nhằm tăng mức bảo vệ khi truy cập Instance Metadata Service.

Security Group của EC2 được cấu hình theo nguyên tắc:

- Port backend `8080` chỉ nhận traffic từ Security Group của Application Load Balancer.
- Không mở trực tiếp port `8080` cho `0.0.0.0/0`.
- Cho phép outbound cần thiết qua NAT Gateway để tải package và kết nối Amazon SES SMTP; kết nối RDS vẫn đi trong VPC.


![Thông tin EC2 backend sau khi cấu hình](image.png)

<p style="text-align: center;"><em>Hình 5.7. EC2 backend dùng IAM role và IMDSv2 trong cấu hình nền.</em></p>

![Inbound rules của EC2 Security Group](/images/5-Workshop/5.3-Backend-deployment/5.3.1-Backend-environment/ec2-security-group-inbound.png)

<p style="text-align: center;"><em>Hình 5.8. EC2 Security Group chỉ cho phép traffic backend port 8080 từ ALB Security Group.</em></p>

## Cài đặt Docker

Sau khi EC2 ở trạng thái **Running**, nhóm mở phiên SSH qua kênh quản trị có quyền truy cập private subnet và cài Docker trên Amazon Linux 2023. NAT Gateway chỉ phục vụ kết nối outbound do EC2 khởi tạo, không cung cấp đường SSH inbound. Các lệnh chính gồm:

```bash
sudo dnf update -y
sudo dnf install -y docker
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user
```

Sau khi thêm `ec2-user` vào group `docker`, cần đăng xuất rồi kết nối lại để quyền group có hiệu lực. Docker được kiểm tra bằng:

```bash
docker version
```

Kết quả xác nhận Docker Client và Docker Engine đã hoạt động trên kiến trúc `linux/amd64`, sẵn sàng chạy Spring Boot container.

![Kết quả kiểm tra Docker trên EC2](/images/5-Workshop/5.3-Backend-deployment/5.3.1-Backend-environment/docker-version.png)

<p style="text-align: center;"><em>Hình 5.9. Docker Client và Docker Engine đã được cài đặt trên EC2.</em></p>

## Chuẩn bị Amazon SES

Backend sử dụng Amazon SES SMTP để gửi email xác minh tài khoản và đặt lại mật khẩu. Nhóm cấu hình SES tại Singapore (`ap-southeast-1`) để đồng nhất với Region triển khai chính:

1. Tạo và xác minh domain identity `cloud-ewallet.com`.
2. Bật Easy DKIM và thêm các record SES cung cấp vào Cloudflare DNS; các record email không bật proxy.
3. Tạo SMTP credential riêng cho ứng dụng bằng IAM user `ses-smtp-user-cloud-ewallet`.
4. Sử dụng SMTP endpoint `email-smtp.ap-southeast-1.amazonaws.com`, port `587` và STARTTLS.
5. Yêu cầu production access để gửi email đến người nhận chưa xác minh.

Tài khoản SES hiện có trạng thái **Healthy**, hạn mức `50.000` email trong 24 giờ và tốc độ tối đa `14` email mỗi giây. Đây là hạn mức gửi tối đa được AWS phê duyệt, không phải số email miễn phí.

![Trạng thái và hạn mức Amazon SES](/images/5-Workshop/5.3-Backend-deployment/5.3.1-Backend-environment/ses-account-dashboard.png)

<p style="text-align: center;"><em>Hình 5.10. Amazon SES tại Region Singapore ở trạng thái Healthy với hạn mức gửi đã được phê duyệt.</em></p>

## Chuẩn bị biến môi trường backend

Trên EC2, nhóm tạo file `/home/ec2-user/ewallet-backend.env` và giới hạn quyền đọc:

```bash
chmod 600 /home/ec2-user/ewallet-backend.env
```

File thật chứa cấu hình RDS, JWT, frontend/CORS và SES. Báo cáo chỉ sử dụng placeholder:

```dotenv
SPRING_PROFILES_ACTIVE=prod

DB_URL=jdbc:mysql://<DB_ENDPOINT>:3306/<DB_NAME>?useUnicode=true&characterEncoding=UTF-8&useSSL=true&requireSSL=true&serverTimezone=UTC
DB_USERNAME=<DB_USERNAME>
DB_PASSWORD=<DB_PASSWORD>

JWT_SECRET=<STRONG_RANDOM_SECRET_AT_LEAST_32_BYTES>
JWT_EXPIRATION=3600

FRONTEND_BASE_URL=https://cloud-ewallet.com
CORS_ALLOWED_ORIGINS=https://cloud-ewallet.com

MAIL_DEVELOPMENT_LOG_ENABLED=false
EMAIL_VERIFICATION_MINUTES=1440
PASSWORD_RESET_MINUTES=30
EMAIL_PROVIDER=ses

SES_SMTP_HOST=email-smtp.ap-southeast-1.amazonaws.com
SES_SMTP_PORT=587
SES_SMTP_USERNAME=<SES_SMTP_USERNAME>
SES_SMTP_PASSWORD=<SES_SMTP_PASSWORD>
SES_MAIL_FROM_ADDRESS=noreply@cloud-ewallet.com
```

Credential thật không được commit vào Git, ghi trong báo cáo hoặc hiển thị trong ảnh chụp màn hình.

## Kiểm tra kết quả

Sau khi hoàn tất, nhóm chúng em xác nhận:

- Hai EC2 thuộc đúng VPC và các private subnet tại hai Availability Zone.
- IAM role và IMDSv2 được cấu hình cho instance.
- Docker Client và Docker Engine hoạt động bình thường.
- Security Group cho phép EC2 kết nối RDS qua port `3306`; outbound qua NAT Gateway hỗ trợ kết nối SES SMTP port `587`.
- SES ở trạng thái Healthy và đã có production sending quota.
- File môi trường được đặt quyền `600`, chỉ owner được đọc/ghi và không xuất hiện trong repository.

Quy trình build image, chạy container và kiểm tra backend được thực hiện ở mục 5.3.2.
