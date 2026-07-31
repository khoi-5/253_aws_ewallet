---
title: "Khởi tạo database"
date: 2024-01-01
weight: 2
chapter: false
pre: " <b> 5.2.2. </b> "
---

## Mục tiêu

Khởi tạo schema, tài khoản quản trị ban đầu và danh mục dịch vụ cho Cloud E-Wallet sau khi Amazon RDS MySQL đã sẵn sàng. RDS vẫn nằm trong private subnet và các script được thực thi từ EC2 backend thông qua kết nối nội bộ.

## Phạm vi thực hiện

Quy trình được chia giữa hai môi trường:

| Môi trường | Công việc |
| --- | --- |
| Máy local | Kiểm tra script SQL và chuẩn bị bản admin seed riêng không đưa vào Git |
| EC2 production | Kết nối đến RDS và chạy các script khởi tạo |

Nhóm không chuyển RDS sang public để import dữ liệu. Security Group của RDS chỉ cho phép kết nối MySQL TCP `3306` từ Security Group của EC2 backend.

## Chuẩn bị các script

Các script production được lưu trong `database/rds/` và chạy theo thứ tự:

| Thứ tự | Script | Nội dung |
| --- | --- | --- |
| 1 | `001_schema.sql` | Tạo các bảng, khóa, index, ràng buộc và quan hệ |
| 2 | `002_admin_template.sql` | Tạo tài khoản và hồ sơ quản trị ban đầu |
| 3 | `003_services_seed.sql` | Thêm năm dịch vụ mặc định của ứng dụng |

Thứ tự trên cần được giữ nguyên vì admin seed và service seed phụ thuộc vào các bảng được tạo bởi `001_schema.sql`.

Schema sử dụng `utf8mb4` và gồm bảy bảng chính:

- `users`
- `account_tokens`
- `user_profiles`
- `admin_profiles`
- `wallets`
- `services`
- `transactions`

## Chuẩn bị admin seed an toàn

`002_admin_template.sql` chỉ chứa placeholder và được theo dõi trong repository. Nhóm tạo bản riêng:

```text
database/rds/002_admin_local.sql
```

Trong bản này, nhóm điền số điện thoại, họ tên và BCrypt password hash của tài khoản quản trị. Mật khẩu dạng rõ không được ghi trong script.

Repository có rule:

```gitignore
database/rds/*_local.sql
```

Do đó `002_admin_local.sql` không được commit lên Git. Nội dung file, thông tin quản trị và password hash cũng không được đưa vào ảnh hoặc báo cáo.

## Chuyển script lên EC2

Nhóm sử dụng SFTP qua kết nối SSH để chuyển ba script cần thiết vào thư mục:

```text
/home/ec2-user/sql/
```

Trên EC2, kiểm tra file trước khi thực thi:

```bash
ls -l /home/ec2-user/sql
```

Các lệnh từ phần này được chạy trong terminal SSH của EC2 production, không chạy trên máy local.

## Kết nối Amazon RDS MySQL

Từ EC2, sử dụng MySQL client để kết nối RDS:

```bash
mysql \
  -h <DB_ENDPOINT> \
  -P 3306 \
  -u <DB_USERNAME> \
  -p \
  <DB_NAME>
```

`<DB_ENDPOINT>`, `<DB_USERNAME>` và `<DB_NAME>` là placeholder. Tham số `-p` yêu cầu nhập password tương tác, tránh ghi password trực tiếp trong câu lệnh và shell history.

Sau khi đăng nhập, nhóm kiểm tra đúng database đích:

```sql
SELECT DATABASE();
SHOW TABLES;
```

Nếu database không đúng, dừng lại và kiểm tra cấu hình trước khi chạy script.

## Khởi tạo schema và dữ liệu

Trong MySQL client trên EC2, nhóm chạy:

```sql
SOURCE /home/ec2-user/sql/001_schema.sql;
SOURCE /home/ec2-user/sql/002_admin_local.sql;
SOURCE /home/ec2-user/sql/003_services_seed.sql;
```

Vai trò của từng script:

- `001_schema.sql` tạo cấu trúc database bằng `CREATE TABLE IF NOT EXISTS`.
- `002_admin_local.sql` tạo admin trong transaction và chỉ được dùng cho lần khởi tạo ban đầu.
- `003_services_seed.sql` sử dụng ID ổn định và `ON DUPLICATE KEY UPDATE`, giúp tránh tạo trùng danh mục dịch vụ khi chạy lại.

Trước khi chạy admin seed, cần xác nhận database chưa có tài khoản quản trị để tránh vi phạm unique constraint hoặc tạo dữ liệu ngoài ý muốn.

## Kiểm tra kết quả

Sau khi hoàn tất, nhóm sử dụng các truy vấn không hiển thị dữ liệu nhạy cảm:

```sql
SHOW TABLES;

SELECT role, status, COUNT(*) AS total
FROM users
GROUP BY role, status;

SELECT id, name, price, is_active
FROM services
ORDER BY id;
```

Kết quả cần đạt:

- Có đủ bảy bảng chính.
- Có tài khoản quản trị ở trạng thái active.
- Có năm dịch vụ mặc định.
- Không xuất hiện lỗi khóa ngoại, unique constraint hoặc charset.

Không truy vấn hoặc chụp số điện thoại, email, password hash và token. Sau khi kiểm tra, thoát MySQL bằng `EXIT;`.


## Kết quả

Amazon RDS MySQL có đầy đủ schema, tài khoản quản trị ban đầu và danh mục dịch vụ. Toàn bộ thao tác được thực hiện từ EC2 mà không mở database trực tiếp ra Internet.
