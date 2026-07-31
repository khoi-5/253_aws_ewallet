---
title: "Dọn dẹp tài nguyên"
date: 2024-01-01
weight: 7
chapter: false
pre: " <b> 5.7. </b> "
---

## Các bước dọn dẹp

Việc dọn dẹp được thực hiện theo thứ tự phụ thuộc để tránh xóa nhầm dữ liệu và hạn chế tài nguyên còn sót tiếp tục phát sinh chi phí.

1. **Sao lưu dữ liệu cần thiết:** xuất dữ liệu cần lưu và tạo final snapshot cho Amazon RDS trước khi xóa database.
2. **Dừng tầng backend:** đặt Desired capacity của Auto Scaling Group về `0`, chờ các EC2 backend được terminate, sau đó xóa Auto Scaling Group và Launch Template khi không còn sử dụng.
3. **Dọn dẹp Application Load Balancer:** xóa listener, Application Load Balancer và target group của backend sau khi không còn EC2 target phụ thuộc.
4. **Dọn dẹp AWS WAF và CloudFront:** gỡ Web ACL khỏi distribution, gỡ alternate domain name nếu cần, disable rồi xóa CloudFront distribution; sau đó xóa Web ACL khi không còn resource liên kết.
5. **Dọn dẹp Amazon S3:** xóa toàn bộ object và version trong frontend bucket, sau đó xóa bucket khi CloudFront không còn sử dụng.
6. **Xóa Amazon RDS:** xác nhận final snapshot đã được tạo, sau đó xóa DB instance và DB subnet group không còn cần thiết.
7. **Xóa tài nguyên mạng:** xóa NAT Gateway trước, release Elastic IP không còn sử dụng, rồi xóa Security Group, route table tùy chỉnh, subnet, Internet Gateway và VPC theo thứ tự phụ thuộc.
8. **Dọn dẹp IAM:** gỡ policy và xóa IAM role hoặc IAM user chỉ phục vụ dự án; vô hiệu hóa và xóa SES SMTP credential không còn sử dụng.
9. **Dọn dẹp Amazon SES và DNS:** xóa SES identity, DKIM, MAIL FROM, ACM validation và các DNS record Cloudflare khi domain không còn được dùng cho website hoặc email.
10. **Kiểm tra chi phí:** kiểm tra AWS Billing và Cost Explorer để xác nhận không còn tài nguyên ngoài dự kiến tiếp tục phát sinh phí.

Khi xóa tài nguyên, nhóm kiểm tra chính xác tên, Region và quan hệ phụ thuộc trước mỗi thao tác. CloudFront cần được disable trước khi xóa; snapshot, Elastic IP, NAT Gateway và dữ liệu S3 cũng có thể tiếp tục phát sinh chi phí nếu còn tồn tại.