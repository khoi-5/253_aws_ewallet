---
title: "Workshop"
date: 2024-01-01
weight: 5
chapter: false
pre: " <b> 5. </b> "
---

Phần này trình bày kiến trúc triển khai Cloud E-Wallet trên AWS và quy trình nhóm chúng em đã thực hiện. Nội dung được sắp xếp theo quan hệ phụ thuộc giữa database, backend, frontend, định tuyến, bảo mật và kiểm thử để người đọc có thể theo dõi toàn bộ luồng triển khai từ hạ tầng đến ứng dụng.

![Kiến trúc triển khai Cloud E-Wallet trên AWS](/images/5-Workshop/5.1-Prerequisites/architecture.png)

<p style="text-align: center;"><em>Hình 5.1. Kiến trúc triển khai Cloud E-Wallet trên AWS.</em></p>

Trong kiến trúc này, người dùng gửi HTTPS request đến CloudFront distribution được bảo vệ bằng AWS WAF. Default behavior phân phối React frontend từ Amazon S3, còn `/api/*` đi qua ALB internet-facing và Target Group đến hai EC2 trong các private subnet thuộc hai Availability Zone. Auto Scaling Group với cấu hình `Min 0 / Desired 2 / Max 2` quản lý các instance này. Backend kết nối RDS MySQL Single-AZ thông qua mạng private và sử dụng Amazon SES. CloudWatch hỗ trợ giám sát, KMS hỗ trợ quản lý khóa mã hóa, còn một NAT Gateway trong public subnet cung cấp kết nối Internet outbound cho các EC2 private. Cloudflare chỉ quản lý DNS cùng các record xác minh AWS/SES.

Cloud E-Wallet sử dụng số dư mô phỏng, không xử lý tiền thật, không kết nối cổng thanh toán và không gửi dữ liệu thẻ đến backend.

## Trình tự thực hiện

| Bước | Nội dung chính | Kết quả mong đợi |
| --- | --- | --- |
| [5.1. Chuẩn bị môi trường](5.1-Prerequisites/) | Kiểm tra công cụ và source; xác định vai trò hai IAM user; cấu hình frontend, backend, RDS, JWT, CORS và Amazon SES | Môi trường triển khai sẵn sàng, đúng Region và không để lộ secret |
| [5.2. Triển khai database](5.2-Database-deployment/) | Cấu hình RDS private, sau đó khởi tạo schema và dữ liệu nền | Database production sẵn sàng cho backend |
| [5.3. Triển khai backend](5.3-Backend-deployment/) | Chuẩn bị EC2/Docker/SES, build image và phát hành Spring Boot container | Backend kết nối RDS, gửi email và phục vụ qua ALB |
| [5.4. Triển khai frontend](5.4-Frontend-deployment/) | Cấu hình S3/CloudFront, build React và phát hành static files | Frontend được phân phối qua HTTPS bằng CloudFront hoặc custom domain |
| [5.5. Cấu hình định tuyến và bảo mật](5.5-Traffic-security/) | Cấu hình WAF, target group, ALB, ASG, CloudFront behavior `/api/*`, Cloudflare DNS và Security Group | Traffic đi theo chuỗi CloudFront/WAF → ALB → Target Group → EC2; EC2 và RDS không bị mở trực tiếp không cần thiết |
| [5.6. Kiểm tra sau triển khai](5.6-Validation/) | Xác nhận trạng thái backend, đăng nhập, thông tin ví, chuyển tiền và email khôi phục qua Amazon SES | Các luồng production chính hoạt động end-to-end |
| [5.7. Dọn dẹp tài nguyên](5.7-Cleanup/) | Sao lưu dữ liệu cần thiết, kiểm tra phụ thuộc rồi dừng hoặc xóa tài nguyên không còn sử dụng | Hạn chế chi phí phát sinh sau khi kết thúc demo |
