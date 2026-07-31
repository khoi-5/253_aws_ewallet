---
title: "Cấu hình ALB, CloudFront, DNS và bảo mật mạng"
date: 2024-01-01
weight: 5
chapter: false
pre: " <b> 5.5. </b> "
---

## Mục tiêu

Hoàn thiện đường đi của request từ người dùng đến frontend và backend, đồng thời giới hạn kết nối mạng theo chuỗi CloudFront → Application Load Balancer → EC2 → Amazon RDS.


## AWS WAF tại CloudFront

CloudFront distribution được bảo vệ bằng Web ACL sử dụng AWS Managed Rule Group `AWS-AWSManagedRulesCommonRuleSet` (Core Rule Set, 700 WCU). WAF kiểm tra request HTTP/HTTPS trước khi request đến S3 hoặc ALB origin. Các rule bao phủ User-Agent bất thường, bad bots cơ bản, SSRF đến EC2 metadata, LFI/RFI, restricted extensions, XSS và giới hạn kích thước request. Một số rule đang Block; SizeRestrictions và CrossSiteScripting ở Count để nhóm theo dõi sampled requests trước khi thay đổi hành động. WAF không thay thế JWT, Spring Security hoặc validation của backend.

## Auto Scaling Group và Target Group

ASG sử dụng `Min = 0`, `Desired = 2`, `Max = 2` để duy trì hai EC2 backend ở hai Availability Zone. Request thực tế đi từ ALB qua Target Group đến EC2; ASG chỉ quản lý vòng đời và thay thế instance không khỏe. Khi một target lỗi, ALB ngừng chuyển traffic đến target đó trong khi ASG khôi phục desired capacity. RDS vẫn Single-AZ nên khả năng chịu lỗi này chỉ áp dụng cho tầng application.
## Cấu hình target group

Nhóm chúng em tạo target group cho backend với các thông tin chính:

- Target type: EC2 instance.
- Protocol: HTTP.
- Port: `8080`.
- Health check path: `/actuator/health`.
- Hai EC2 backend do ASG quản lý được đăng ký làm target tại hai Availability Zone.

ALB chỉ chuyển traffic đến EC2 sau khi health check trả kết quả hợp lệ và target chuyển sang trạng thái **Healthy**.

![Target Group của backend ở trạng thái Healthy](/images/5-Workshop/5.5-Traffic-security/target-group-healthy.png)

<p style="text-align: center;"><em>Hình 5.18. Target group sử dụng HTTP port 8080 và EC2 backend đang ở trạng thái Healthy.</em></p>

## Cấu hình Application Load Balancer

Application Load Balancer được triển khai dạng internet-facing trong các public subnet của `ewallet-vpc`. Listener HTTP port `80` chuyển request đến backend target group port `8080`.

Kết nối từ trình duyệt đến CloudFront sử dụng HTTPS. CloudFront kết nối đến ALB origin theo cấu hình hiện tại, sau đó ALB chuyển request đến Spring Boot container trên EC2.
![Thông tin và listener của Application Load Balancer](/images/5-Workshop/5.5-Traffic-security/alb-details-listener.png)

<p style="text-align: center;"><em>Hình 5.19. ALB internet-facing với listener HTTP port 80 chuyển request đến backend target group.</em></p>

## Giới hạn bằng Security Group

Các Security Group được cấu hình theo quan hệ nguồn thay vì mở toàn bộ port trực tiếp ra Internet:

| Tài nguyên | Inbound rule cần thiết | Mục đích |
| --- | --- | --- |
| ALB | HTTP port `80` theo phạm vi public của listener hiện tại | Nhận request API từ CloudFront |
| EC2 backend | TCP `8080` với source là ALB Security Group | Chỉ cho ALB gọi Spring Boot backend |
| Amazon RDS | MySQL `3306` với source là EC2 Security Group | Chỉ cho backend kết nối database |

Trong kiến trúc triển khai, EC2 nằm trong các private subnet và không nhận traffic ứng dụng trực tiếp từ Internet. Port `8080` chỉ nhận từ ALB Security Group; kết nối outbound cần thiết đi qua NAT Gateway trong public subnet.

![Inbound rules của ALB Security Group](/images/5-Workshop/5.5-Traffic-security/alb-security-group-inbound.png)

<p style="text-align: center;"><em>Hình 5.20. ALB Security Group cho phép lưu lượng HTTP port 80 và HTTPS port 443 từ Internet.</em></p>

Inbound rule của EC2 đã được minh chứng tại [mục 5.3.1](../5.3-Backend-deployment/5.3.1-Backend-environment/), còn inbound rule của RDS được trình bày tại [mục 5.2.1](../5.2-Database-deployment/5.2.1-RDS-configuration/), nên không lặp lại các ảnh trong phần này.

## Định tuyến API bằng CloudFront

Distribution có hai origin và hai behavior:

- Default `(*)` sử dụng S3 frontend origin với cache policy `Managed-CachingOptimized`.
- `/api/*` sử dụng `ewallet-alb-origin` với `Managed-CachingDisabled` để response API không bị cache ngoài ý muốn.

CloudFront origin và behavior đã được minh chứng tại [mục 5.4.1](../5.4-Frontend-deployment/5.4.1-S3-CloudFront-configuration/). Phần 5.5 tập trung giải thích vai trò của chúng trong luồng định tuyến tổng thể thay vì chụp lại cùng một cấu hình.

## Cấu hình DNS trên Cloudflare

Cloudflare được dùng để quản lý DNS cho `cloud-ewallet.com`, không thay thế CloudFront trong vai trò CDN. Domain chính và `www` được trỏ đến CloudFront; các record xác minh certificate, DKIM, Custom MAIL FROM/SPF và DMARC được cấu hình theo giá trị do ACM hoặc Amazon SES cung cấp. Các record xác minh AWS và email để ở chế độ **DNS only**. Phần cấu hình SES domain identity, Easy DKIM và SMTP credential được trình bày tại [mục 5.3.1](../5.3-Backend-deployment/5.3.1-Backend-environment/).

## Kiểm tra kết quả

Các minh chứng triển khai hiện có xác nhận:

- `https://cloud-ewallet.com` tải frontend từ S3 thông qua CloudFront.
- Request `/api/*` được CloudFront chuyển đến ALB rồi đến EC2 port `8080`.
- EC2 target trong target group ở trạng thái **Healthy**.
- Backend EC2 không có đường truy cập ứng dụng trực tiếp từ Internet; port `8080` chỉ nhận từ ALB Security Group.
- EC2 chỉ kết nối RDS qua port `3306` theo Security Group source.
- Domain, certificate và các record xác minh SES hoạt động qua Cloudflare DNS.

Các ảnh trong phần này xác minh cấu hình định tuyến, Security Group và trạng thái hoạt động của các target trong hệ thống.


