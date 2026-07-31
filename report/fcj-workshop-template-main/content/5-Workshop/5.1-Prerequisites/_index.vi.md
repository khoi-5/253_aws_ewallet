---
title: "Chuẩn bị môi trường triển khai"
date: 2024-01-01
weight: 1
chapter: false
pre: " <b> 5.1. </b> "
---

## Mục tiêu

Chuẩn bị đầy đủ công cụ, source, tài khoản và cấu hình trước khi tạo hoặc cập nhật tài nguyên AWS.

## Công cụ

| Thành phần | Kiểm tra | Yêu cầu |
| --- | --- | --- |
| Java 17 | `java -version` | Build Spring Boot |
| Node.js/npm | `node --version`, `npm --version` | Build React/Vite |
| Docker | `docker --version` | Build và chạy backend image |
| Git | `git --version` | Quản lý source |
| AWS account | Đăng nhập Console | Phân quyền phù hợp cho S3, CloudFront, EC2, ALB, RDS và SES |
| Cloudflare | Kiểm tra zone | Quản lý `cloud-ewallet.com` và các record xác minh Amazon SES |




## Phân quyền IAM

Dự án sử dụng hai IAM user với mục đích riêng biệt, tránh sử dụng root account cho các công việc triển khai hằng ngày:

| IAM user | Hình thức truy cập | Vai trò trong dự án |
| --- | --- | --- |
| `khoi_admin` | Đăng nhập AWS Management Console | Quản lý các tài nguyên S3, CloudFront, EC2, Application Load Balancer, RDS, SES và CloudWatch. User nhận `AdministratorAccess` thông qua `admin-group` trong môi trường thực tập. |
| `ses-smtp-user-cloud-ewallet` | Programmatic access bằng SES SMTP credentials; không dùng để đăng nhập Console | Cho phép Spring Boot backend xác thực với Amazon SES SMTP và gửi email xác minh tài khoản hoặc đặt lại mật khẩu. User này không dùng để build hoặc quản trị hạ tầng. |

`khoi_admin` có phạm vi quyền rộng để phục vụ quá trình thực hành và triển khai nhiều dịch vụ AWS. Với môi trường production dài hạn, quyền quản trị nên được thu hẹp theo nguyên tắc đặc quyền tối thiểu và tài khoản đăng nhập Console nên bật MFA. Credential của `ses-smtp-user-cloud-ewallet` chỉ được lưu trong file môi trường trên EC2, không đưa vào source, ảnh chụp hoặc báo cáo.

![Danh sách IAM user của dự án](/images/5-Workshop/5.1-Prerequisites/IAM_USER.png)

## Chuẩn bị cấu hình môi trường

Trước khi build và triển khai, nhóm chúng em tách cấu hình của frontend và backend thành hai phần.

### Frontend

Frontend đọc file `frontend/.env.production` khi chạy lệnh build. File này khai báo `VITE_API_BASE_URL`, tức địa chỉ API mà ứng dụng React sẽ gọi sau khi được đưa lên S3 và CloudFront. Vì biến của Vite được nhúng vào mã JavaScript khi build, file này không được chứa mật khẩu hoặc secret.

Có thể cấu hình frontend theo một trong hai cách sau:

```dotenv
# Cách 1: Dùng domain mặc định của CloudFront
# Thay <CLOUDFRONT_DISTRIBUTION_DOMAIN> bằng domain riêng của distribution
VITE_API_BASE_URL=https://<CLOUDFRONT_DISTRIBUTION_DOMAIN>

# Ví dụ định dạng: https://dxxxxxxxxxxxxx.cloudfront.net
```

```dotenv
# Cách 2: Dùng tên miền tùy chỉnh đã trỏ đến CloudFront
VITE_API_BASE_URL=https://cloud-ewallet.com
```

Mỗi CloudFront distribution có một domain riêng nên cần lấy đúng giá trị trong AWS Console; domain này là định danh công khai, không phải password hoặc secret. Dự án của nhóm sử dụng `https://cloud-ewallet.com`. Chỉ giữ một giá trị `VITE_API_BASE_URL` đang áp dụng trong file thật. Sau khi thay đổi biến, frontend phải được build và upload lại lên S3; nếu CloudFront còn cache phiên bản cũ thì tạo invalidation cho `/*`.

### Backend

Backend trên EC2 đọc file `/home/ec2-user/ewallet-backend.env` khi Docker container khởi động. Dưới đây là cấu hình theo đúng tên biến của dự án; các thông tin bí mật được thay bằng placeholder:

```dotenv
SPRING_PROFILES_ACTIVE=prod

DB_URL=jdbc:mysql://<RDS_ENDPOINT>:3306/<DB_NAME>?useUnicode=true&characterEncoding=UTF-8&useSSL=true&requireSSL=true&serverTimezone=UTC
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

Các biến được chia theo mục đích:

| Nhóm cấu hình | Biến sử dụng | Mục đích |
| --- | --- | --- |
| Cơ sở dữ liệu | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Kết nối backend với Amazon RDS MySQL |
| Xác thực | `JWT_SECRET` | Ký và xác minh access token |
| Domain và CORS | `FRONTEND_BASE_URL`, `CORS_ALLOWED_ORIGINS` | Tạo đường dẫn gửi cho người dùng và chỉ cho phép frontend hợp lệ gọi API |
| Dịch vụ email | `EMAIL_PROVIDER=ses`, các biến `SES_SMTP_*` và `SES_MAIL_FROM_ADDRESS` | Kết nối Amazon SES SMTP để gửi email xác minh và đặt lại mật khẩu |

File môi trường thật chỉ được lưu trên máy triển khai và không được commit lên Git. Trong source và báo cáo, nhóm chỉ sử dụng file mẫu `.env.production.example` với các giá trị thay thế như `<DB_ENDPOINT>`, `<JWT_SECRET>` hoặc `<SES_SMTP_PASSWORD>`.

## Kiểm tra trước khi triển khai

- Source có đủ ba thư mục `frontend/`, `backend/` và `database/`.
- `frontend/.env.production` trỏ đến đúng API production và không chứa secret.
- `.env.production.example` chỉ chứa placeholder, không chứa credential thật.
- File `/home/ec2-user/ewallet-backend.env` đã được tạo trực tiếp trên EC2 và được giới hạn quyền truy cập.
- Tài khoản AWS có các quyền cần thiết theo nguyên tắc đặc quyền tối thiểu.
- Nhóm đã thống nhất Region Singapore (`ap-southeast-1`), quy tắc đặt tên và danh sách tài nguyên cần triển khai.

## Thiết lập VPC

Nhóm chúng em tạo VPC riêng `ewallet-vpc` tại Region Singapore (`ap-southeast-1`) với IPv4 CIDR `10.0.0.0/16`. VPC riêng giúp tách tài nguyên của dự án khỏi default VPC và kiểm soát rõ luồng mạng giữa Application Load Balancer, EC2 backend và Amazon RDS.

VPC được chia thành bốn subnet trên hai Availability Zone:

| Availability Zone | Public subnet | Private subnet |
| --- | --- | --- |
| `ap-southeast-1a` | `ewallet-subnet-public1-ap-southeast-1a` | `ewallet-subnet-private1-ap-southeast-1a` |
| `ap-southeast-1b` | `ewallet-subnet-public2-ap-southeast-1b` | `ewallet-subnet-private2-ap-southeast-1b` |

Hai public subnet dành cho Application Load Balancer internet-facing và NAT Gateway. Hai private subnet bố trí các EC2 backend do Auto Scaling Group quản lý, đồng thời được đưa vào DB subnet group của Amazon RDS. Internet Gateway được gắn với VPC; NAT Gateway trong một public subnet cung cấp kết nối outbound cho EC2 private.

Hai private subnet sử dụng route table riêng và không có đường inbound trực tiếp từ Internet. RDS không bật public access; DB subnet group bao phủ cả hai Availability Zone, còn DB instance Single-AZ chỉ nhận kết nối từ Security Group của backend trên port `3306`. VPC cũng bật **DNS resolution** và **DNS hostnames** để các tài nguyên phân giải được endpoint và hostname cần thiết.

![Resource map của VPC Cloud E-Wallet](/images/5-Workshop/5.1-Prerequisites/vpc-resource-map.png)

<p style="text-align: center;"><em>Hình 5.2. Resource map của VPC Cloud E-Wallet trên hai Availability Zone.</em></p>

Resource map thể hiện đầy đủ VPC, bốn subnet, các route table và Internet Gateway nên được sử dụng làm hình tổng quan cho phần này. Các cấu hình chi tiết của DB subnet group, Security Group và Application Load Balancer được trình bày trong các bước triển khai tương ứng.

## Tài nguyên compute và bảo vệ edge

CloudFront distribution được associate với AWS WAF Web ACL sử dụng `AWS-AWSManagedRulesCommonRuleSet` (700 WCU). Một số rule đang Block; SizeRestrictions và CrossSiteScripting được giữ ở Count để theo dõi trước khi block. Kiến trúc triển khai sử dụng Launch Template và Auto Scaling Group với `Min = 0`, `Desired = 2`, `Max = 2`, bố trí hai EC2 trong hai private subnet thuộc hai Availability Zone. EC2 private dùng NAT Gateway trong public subnet và Internet Gateway cho kết nối outbound khi cần.
## Kết quả mong đợi

Mọi thành viên hiểu cùng quy trình và không cần chia sẻ secret qua source hoặc báo cáo.
