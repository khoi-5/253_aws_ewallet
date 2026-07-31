---
title: "Cài đặt và cấu hình Amazon RDS MySQL"
date: 2024-01-01
weight: 1
chapter: false
pre: " <b> 5.2.1. </b> "
---

## Mục tiêu

Phần này trình bày kết quả cấu hình Amazon RDS MySQL cho môi trường production của Cloud E-Wallet. Database được đặt trong private subnet và chỉ nhận kết nối MySQL từ backend thông qua Security Group.

## Cấu hình Security Group cho RDS

Nhóm chúng em tạo Security Group `RDS-Ewallet-SG` trong `ewallet-vpc`. Inbound rule chỉ cho phép giao thức MySQL/Aurora, TCP port `3306`, với source là Security Group của EC2 backend. Nhờ sử dụng Security Group làm source thay vì địa chỉ IP cố định, chỉ các EC2 được gắn đúng Security Group backend mới có thể kết nối database.

Port `3306` không được mở cho `0.0.0.0/0`, vì vậy RDS không nhận kết nối MySQL trực tiếp từ Internet.

![Inbound rule của Security Group RDS](/images/5-Workshop/5.2-Database-deployment/5.2.1-RDS-configuration/rds-security-group-inbound.png)

<p style="text-align: center;"><em>Hình 5.3. Inbound rule chỉ cho phép Security Group của backend kết nối RDS qua port 3306.</em></p>

## Cấu hình DB subnet group

DB subnet group `ewallet-rds-subnet-group` gồm hai private subnet thuộc hai Availability Zone `ap-southeast-1a` và `ap-southeast-1b`. Cách bố trí này đáp ứng yêu cầu mạng của Amazon RDS và tách database khỏi các public subnet đang chứa thành phần nhận traffic từ bên ngoài.

![DB subnet group của Cloud E-Wallet](/images/5-Workshop/5.2-Database-deployment/5.2.1-RDS-configuration/rds-subnet-group.png)

<p style="text-align: center;"><em>Hình 5.4. DB subnet group sử dụng hai private subnet trên hai Availability Zone.</em></p>

## Kết quả tạo RDS MySQL
![Trạng thái Amazon RDS MySQL của Cloud E-Wallet](image.png)

<p style="text-align: center;"><em>Hình 5.5. RDS MySQL `ewallet-db` sử dụng instance class `db.t4g.micro` và ở trạng thái Available.</em></p>
RDS instance của dự án có DB identifier `ewallet-db`, sử dụng MySQL Community và instance class `db.t4g.micro` tại Region Singapore. Sau khi khởi tạo, database chuyển sang trạng thái **Available**.

Amazon RDS không bật chế độ truy cập công khai và database không được triển khai trong public subnet. Backend kết nối đến RDS bằng endpoint nội bộ, port `3306` và credential được lưu trong file `/home/ec2-user/ewallet-backend.env`. Endpoint, username và password thật không được đưa vào source hoặc báo cáo.

Cấu hình kết nối trong tài liệu chỉ sử dụng placeholder:

```text
DB_URL=jdbc:mysql://<DB_ENDPOINT>:3306/<DB_NAME>
DB_USERNAME=<DB_USERNAME>
DB_PASSWORD=<DB_PASSWORD>
```

## Kiểm tra kết quả

Sau khi hoàn tất cấu hình, nhóm chúng em xác nhận:

- RDS instance ở trạng thái **Available**.
- Database sử dụng đúng VPC và DB subnet group chứa hai private subnet.
- RDS không có đường truy cập công khai từ Internet.
- Inbound port `3306` chỉ nhận source từ Security Group của EC2 backend.
- Endpoint và credential thật chỉ được lưu trong cấu hình runtime trên EC2.

Schema và dữ liệu ban đầu được khởi tạo ở mục 5.2.2 sau khi backend EC2 có thể kết nối đến RDS.
