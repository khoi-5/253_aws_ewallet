---
title: "Cài đặt và cấu hình S3, CloudFront"
date: 2024-01-01
weight: 1
chapter: false
pre: " <b> 5.4.1. </b> "
---

## Mục tiêu

Chuẩn bị hạ tầng phân phối React frontend bằng Amazon S3 và Amazon CloudFront. Phần này tóm tắt những cấu hình chính đã thực hiện và sử dụng trạng thái tài nguyên sau khi cấu hình làm minh chứng.

## Quy trình tổng quát

Nhóm chúng em thực hiện theo ba giai đoạn:

1. Tạo S3 bucket dành riêng cho static files của frontend và giữ bucket ở trạng thái private.
2. Tạo CloudFront distribution, cấu hình S3 làm origin frontend và sử dụng Origin Access Control để CloudFront truy cập bucket.
3. Gắn custom domain, chứng chỉ TLS và các behavior cần thiết để phân phối frontend qua HTTPS và chuyển `/api/*` đến backend.

## Cấu hình Amazon S3

Bucket `khoi-ewallet-frontend-2026` chỉ lưu kết quả build trong `frontend/dist/`, bao gồm `index.html`, thư mục `assets/` và các tài nguyên tĩnh. Source code, `node_modules` và file `.env.production` không được upload lên bucket.

S3 bucket được giữ private bằng **Block Public Access**. Người dùng không truy cập object trực tiếp từ S3; CloudFront đọc object thông qua Origin Access Control và bucket policy giới hạn theo distribution.
![Block Public Access của S3 frontend bucket](/images/5-Workshop/5.4-Frontend-deployment/5.4.1-S3-CloudFront-configuration/s3-block-public-access.png)

<p style="text-align: center;"><em>Hình 5.11. Block Public Access được bật cho S3 frontend bucket.</em></p>

## Cấu hình CloudFront distribution

CloudFront distribution `ewallet-frontend` được dùng làm điểm truy cập HTTPS cho toàn bộ ứng dụng. Cấu hình chính gồm:

- Default root object là `index.html`.
- Custom domain gồm `cloud-ewallet.com` và `www.cloud-ewallet.com`.
- Custom SSL certificate được gắn cho domain của dự án.
- Viewer sử dụng HTTPS và security policy TLS hiện hành của distribution.
- S3 là origin phục vụ React frontend.
- Application Load Balancer là origin phục vụ backend API.

![Cấu hình tổng quan CloudFront distribution](/images/5-Workshop/5.4-Frontend-deployment/5.4.1-S3-CloudFront-configuration/cloudfront-general.png)

<p style="text-align: center;"><em>Hình 5.12. Cấu hình domain, chứng chỉ TLS và default root object của CloudFront.</em></p>

## Cấu hình origin

Distribution sử dụng hai origin:

| Origin | Loại | Vai trò |
| --- | --- | --- |
| S3 frontend bucket | Amazon S3 | Phân phối `index.html` và static assets của React |
| `ewallet-alb-origin` | Elastic Load Balancing | Chuyển request API đến Application Load Balancer |

S3 origin sử dụng Origin Access Control thay vì mở public bucket. ALB origin chỉ xử lý nhánh API; cấu hình mạng và Security Group của ALB được trình bày chi tiết tại mục 5.5.

![Danh sách origin của CloudFront](/images/5-Workshop/5.4-Frontend-deployment/5.4.1-S3-CloudFront-configuration/cloudfront-origins.png)

<p style="text-align: center;"><em>Hình 5.13. S3 frontend origin và Application Load Balancer origin trong CloudFront.</em></p>

## Cấu hình behavior

CloudFront sử dụng hai behavior theo thứ tự ưu tiên:

- `/api/*` chuyển đến `ewallet-alb-origin`, tắt cache bằng `Managed-CachingDisabled` và chuyển HTTP sang HTTPS ở phía viewer.
- Default `(*)` chuyển đến S3 frontend origin và sử dụng `Managed-CachingOptimized` để tăng hiệu quả phân phối static files.

Việc tách behavior cho phép frontend và API dùng chung domain `cloud-ewallet.com`, đồng thời tránh CORS phức tạp do trình duyệt chỉ giao tiếp với một domain public.

![Các behavior của CloudFront distribution](/images/5-Workshop/5.4-Frontend-deployment/5.4.1-S3-CloudFront-configuration/cloudfront-behaviors.png)

<p style="text-align: center;"><em>Hình 5.14. Behavior `/api/*` đến ALB và default behavior đến S3 frontend.</em></p>

Đối với React Router, nếu truy cập trực tiếp một route trả về `403` hoặc `404`, cần cấu hình custom error response trả `/index.html` với HTTP status `200`, sau đó kiểm tra lại các route trên domain production.
Custom domain, các DNS record của CloudFront, ACM và Amazon SES được trình bày tại [mục 5.5](../../5.5-Traffic-security/).

## Gắn AWS WAF với CloudFront

Web ACL sử dụng `AWS-AWSManagedRulesCommonRuleSet` (700 WCU) được associate với distribution để kiểm tra request trước S3 và ALB origin. Các rule đã đánh giá phù hợp dùng Block; SizeRestrictions và CrossSiteScripting được để Count để theo dõi sampled requests trước khi block. WAF không thay thế cơ chế xác thực JWT, Spring Security hoặc validation phía backend.
![AWS WAF Web ACL và AWS Managed Rules](/images/5-Workshop/5.4-Frontend-deployment/5.4.1-S3-CloudFront-configuration/waf-common-rule-set.png)

<p style="text-align: center;"><em>Hình 5.15. Web ACL của CloudFront sử dụng AWS Managed Rules Common Rule Set để kiểm tra request.</em></p>

## Kiểm tra kết quả

Sau khi hoàn tất, nhóm chúng em xác nhận:

- CloudFront distribution sử dụng đúng custom domain và chứng chỉ TLS.
- Default behavior phân phối frontend từ S3.
- Behavior `/api/*` chuyển request đến ALB và không cache response API.
- S3 bucket không cần public access để người dùng tải frontend.
- Trang chủ được mở qua HTTPS bằng domain production.

Quy trình build và phát hành một phiên bản frontend mới được trình bày ở mục 5.4.2.

