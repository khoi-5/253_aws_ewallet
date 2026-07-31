---
title: "Kiểm tra hệ thống sau triển khai"
date: 2024-01-01
weight: 6
chapter: false
pre: " <b> 5.6. </b> "
---

## Mục tiêu

Kiểm tra hệ thống từ góc nhìn người dùng sau khi triển khai production, xác nhận frontend có thể tải qua domain chính, các chức năng gọi backend hoạt động và Amazon SES gửi được email của ứng dụng.

## Xác nhận hệ thống sẵn sàng

Nhóm chúng em truy cập hệ thống bằng domain production `https://cloud-ewallet.com`. Frontend được phân phối từ Amazon S3 qua CloudFront, còn request `/api/*` được chuyển đến Application Load Balancer và backend trên EC2.

Trạng thái hạ tầng đã được kiểm tra tại [mục 5.5](../5.5-Traffic-security/): target group sử dụng HTTP port `8080` và các EC2 backend ở trạng thái **Healthy**. Vì vậy, phần này không lặp lại ảnh AWS Console hoặc yêu cầu mở riêng endpoint Actuator chỉ để chụp minh chứng.

Sau khi đăng nhập, trang ví hiển thị đúng thông tin tài khoản và số dư hiện tại. Kết quả này đồng thời xác nhận frontend đã gọi được API có xác thực và backend đọc được dữ liệu từ Amazon RDS.

![Trang ví trên môi trường production](/images/5-Workshop/5.6-Validation/production-wallet.png)

<p style="text-align: center;"><em>Hình 5.21. Trang ví của người dùng được tải thành công trên môi trường production.</em></p>

## Kiểm thử chức năng chuyển tiền

Nhóm chúng em thực hiện chuyển tiền giữa hai tài khoản thử nghiệm. Trước khi gửi request, hệ thống tra cứu số điện thoại người nhận, hiển thị tên tài khoản tương ứng và nhận số tiền cùng nội dung giao dịch.

![Nhập thông tin chuyển tiền](/images/5-Workshop/5.6-Validation/transfer-form.png)

<p style="text-align: center;"><em>Hình 5.22. Nhập người nhận, số tiền và nội dung cho giao dịch thử nghiệm.</em></p>

Sau khi xác nhận, giao diện hiển thị thông báo **Transfer successfully**, số dư tài khoản gửi giảm từ `210 USD` xuống `110 USD` và biểu mẫu được đặt lại. Kết quả này cho thấy request đã đi qua CloudFront và ALB đến backend, giao dịch được xử lý và dữ liệu số dư được cập nhật trong RDS.

![Kết quả chuyển tiền thành công](/images/5-Workshop/5.6-Validation/transfer-success.png)

<p style="text-align: center;"><em>Hình 5.23. Giao dịch hoàn tất và số dư tài khoản được cập nhật.</em></p>

## Kiểm thử email qua Amazon SES

Nhóm chúng em sử dụng chức năng quên mật khẩu để kiểm tra luồng gửi email. Backend tạo yêu cầu khôi phục, gửi email bằng Amazon SES và người dùng nhận được thư từ địa chỉ thuộc domain `cloud-ewallet.com`. Đường dẫn trong email trỏ về trang đặt lại mật khẩu của domain production.

![Email khôi phục mật khẩu được gửi qua Amazon SES](/images/5-Workshop/5.6-Validation/ses-password-reset-email.png)

<p style="text-align: center;"><em>Hình 5.24. Email khôi phục mật khẩu được nhận thành công qua Amazon SES.</em></p>

## Kết quả

Các kiểm thử xác nhận ba luồng chính của hệ thống hoạt động trên môi trường production:

- Người dùng truy cập frontend qua HTTPS và đăng nhập để xem thông tin ví.
- Chức năng chuyển tiền gọi backend thành công và cập nhật dữ liệu trong database.
- Backend gửi được email ứng dụng thông qua Amazon SES.

Kết hợp với trạng thái **Healthy** của target group tại mục 5.5, các kết quả trên xác nhận luồng CloudFront/WAF → ALB → Target Group → EC2 → RDS ở tầng ứng dụng và tích hợp Amazon SES hoạt động đúng như cấu hình.