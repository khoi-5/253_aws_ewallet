# Cloud-based E-wallet - Project Requirements

> **Current implementation note (2026-07-28):** This began as an early requirements and design document. Later wording such as “proposed,” “future,” production Docker Compose, ECR, or App Runner is historical/legacy guidance. The current-state notes below and [PROJECT_STATUS.md](PROJECT_STATUS.md) are authoritative for deployed status.
>
> ✅ The application currently includes simulated deposit/top-up, wallet-to-wallet transfer, service payment, and transaction history. Deposit is not a future feature.
>
> ✅ Current application deployment: Users reach Amazon CloudFront over HTTPS, protected by AWS WAF. CloudFront serves the React build from Amazon S3 and routes `/api/*` to an internet-facing ALB → Target Group → two Dockerized Spring Boot EC2 instances across two Availability Zones. ASG uses Min 0 / Desired 2 / Max 2. The backend uses Single-AZ Amazon RDS MySQL and Amazon SES SMTP. Cloudflare is limited to DNS and AWS/SES verification records.
>
> ✅ Production uses manual `docker run`, environment file `/home/ec2-user/ewallet-backend.env`, and port mapping `8080:8080`. The exact deployed image tag is not authoritative in repository source. Docker Compose is local-only; automated CI/CD is not used.
>
> ✅ Completed application scope includes JWT authentication and logout; registration email verification and resend; forgot/reset password; profile management; wallet balance, deposit, transfer, authenticated receiver-name lookup, service payment, and transaction history; plus the admin dashboard, user management, transaction management, and service management.
>
> ✅ The active responsive frontend is `frontend/`; obsolete `frontend_new/` and `frontend_new1/` migration directories were removed. Receiver names appear in a read-only input-style field that disappears immediately when the phone changes. Deposit sender is shown as `Bank Card`, service banners use the actual service name, visible demo labels were removed, mobile/tablet/desktop behavior was improved, and mobile welcome-heading line spacing was corrected.
>
> ✅ The provider-migration repository validation passed 72 backend tests with no failures. Local, frontend build-time, and production runtime environment files remain separate for safety; documentation contains variable names and placeholders only.
>
> ⚠️ Target availability boundary: ALB and ASG improve application-tier availability across two Availability Zones, but one NAT Gateway remains an outbound dependency and RDS is Single-AZ, so the design is not end-to-end highly available. Backend EC2 instances are placed in private application subnets and accept port 8080 only from the ALB Security Group.

## 1. Giới thiệu dự án

Cloud-based E-wallet là một hệ thống ví điện tử mô phỏng được xây dựng dưới dạng web application. Hệ thống cho phép người dùng đăng ký tài khoản bằng số điện thoại, tự động tạo ví, nhận số dư ban đầu, nạp tiền mô phỏng, chuyển tiền giữa các ví, thanh toán dịch vụ ảo và xem lịch sử giao dịch.

Dự án được thiết kế theo hướng dễ mở rộng về sau. Ban đầu hệ thống tập trung vào chức năng của người dùng ví điện tử. Tuy nhiên, database và backend được chuẩn bị sẵn để sau này có thể bổ sung chức năng quản trị dành cho admin như quản lý người dùng, ví, giao dịch và dịch vụ thanh toán.

---

## 2. Mục tiêu dự án

Mục tiêu chính của dự án là xây dựng một hệ thống e-wallet mô phỏng với các chức năng cơ bản:

* Đăng ký tài khoản bằng số điện thoại.
* Đăng nhập tài khoản.
* Mỗi tài khoản người dùng có đúng một ví.
* Khi đăng ký thành công, ví được tạo tự động với số dư ban đầu là 10 đồng.
* Người dùng có thể nạp tiền mô phỏng.
* Người dùng có thể chuyển tiền sang ví khác thông qua số điện thoại.
* Người dùng có thể thanh toán dịch vụ ảo.
* Người dùng có thể xem lịch sử giao dịch.
* Hệ thống có phân quyền `user` và `admin`.
* Backend được thiết kế để sau này dễ mở rộng thêm trang quản trị admin.
* Database được thiết kế rõ ràng, có khả năng mở rộng.
* Hệ thống có thể chạy local trong giai đoạn đầu và triển khai lên cloud sau này.

---

## 3. Công nghệ sử dụng

## 3.1 Frontend

Frontend sử dụng:

```text
React
TypeScript
Axios
React Router
Zustand
Zod
```

Vai trò:

* React dùng để xây dựng giao diện người dùng.
* TypeScript giúp kiểm soát kiểu dữ liệu và giảm lỗi khi phát triển.
* Axios dùng để gọi API từ frontend đến backend.
* React Router dùng để điều hướng giữa các trang.
* Zustand dùng để quản lý state như thông tin đăng nhập, user, token, số dư ví.
* Zod dùng để validate dữ liệu form như số điện thoại, mật khẩu, số tiền nạp, số tiền chuyển.

---

## 3.2 Backend

Backend sử dụng:

```text
Java Spring Boot
Spring Web
Spring Data JPA
Spring Security
Bean Validation
MySQL Driver
```

Vai trò:

* Spring Boot dùng để xây dựng REST API.
* Spring Web dùng để tạo controller và endpoint.
* Spring Data JPA dùng để làm việc với MySQL thông qua repository/entity.
* Spring Security dùng cho đăng nhập, xác thực và phân quyền user/admin.
* Bean Validation dùng để validate dữ liệu đầu vào.
* `@Transactional` dùng để đảm bảo các giao dịch liên quan đến tiền được xử lý an toàn.

Backend ban đầu là **một backend duy nhất**, không tách riêng user backend và admin backend. Thay vào đó, backend chia route và controller rõ ràng:

```text
/api/auth/**
/api/user/**
/api/admin/**
```

---

## 3.3 Database

Database sử dụng:

```text
MySQL
```

Trong giai đoạn phát triển local, MySQL nên chạy bằng Docker để dễ cài đặt, dễ reset và dễ tái tạo môi trường.

Sau này khi deploy lên AWS, database có thể chuyển sang:

```text
Amazon RDS for MySQL
```

---

## 4. Kiến trúc tổng quan

Kiến trúc hệ thống ban đầu:

```text
React TypeScript Frontend
        |
        | Axios / HTTP Request
        v
Spring Boot Backend
        |
        | Spring Data JPA
        v
MySQL Database
```

Kiến trúc có admin trong tương lai:

```text
User Frontend / User Pages
          |
          v
Spring Boot Backend
          |
          |-- /api/auth/**
          |-- /api/user/**
          |-- /api/admin/**
          |
          v
MySQL Database
```

Có thể dùng một frontend React duy nhất, trong đó route user và admin được tách riêng:

```text
/login
/register
/dashboard
/deposit
/transfer
/payment
/history

/admin
/admin/users
/admin/wallets
/admin/transactions
/admin/services
```

---

## 5. Môi trường phát triển local

Giai đoạn đầu nên chạy local như sau:

```text
Frontend React:      http://localhost:5173
Spring Boot API:     http://localhost:8080
MySQL Docker:        localhost:3307
phpMyAdmin Docker:   http://localhost:8081
```

Database chạy bằng Docker, còn frontend và backend chạy trực tiếp trên máy local để dễ code và debug.

---

## 6. Cấu trúc thư mục đề xuất

## 6.1 Cấu trúc tổng thể

```text
cloud-ewallet/
├── frontend/
├── backend/
├── database/
│   └── schema.sql
├── docker-compose.yml
└── README.md
```

---

## 6.2 Cấu trúc frontend

```text
frontend/
└── src/
    ├── apis/
    │   ├── authApi.ts
    │   ├── walletApi.ts
    │   ├── serviceApi.ts
    │   └── transactionApi.ts
    ├── components/
    ├── hooks/
    ├── layout/
    ├── lib/
    ├── pages/
    │   ├── LoginPage.tsx
    │   ├── RegisterPage.tsx
    │   ├── DashboardPage.tsx
    │   ├── DepositPage.tsx
    │   ├── TransferPage.tsx
    │   ├── PaymentPage.tsx
    │   ├── HistoryPage.tsx
    │   └── admin/
    │       ├── AdminDashboardPage.tsx
    │       ├── AdminUsersPage.tsx
    │       ├── AdminWalletsPage.tsx
    │       ├── AdminTransactionsPage.tsx
    │       └── AdminServicesPage.tsx
    ├── schema/
    │   ├── authSchema.ts
    │   └── walletSchema.ts
    ├── store/
    │   ├── authStore.ts
    │   └── walletStore.ts
    ├── types/
    │   ├── user.ts
    │   ├── wallet.ts
    │   ├── transaction.ts
    │   └── service.ts
    ├── App.tsx
    └── main.tsx
```

---

## 6.3 Cấu trúc backend Spring Boot

```text
backend/
└── src/main/java/com/example/ewallet/
    ├── EwalletApplication.java
    ├── controller/
    │   ├── auth/
    │   │   └── AuthController.java
    │   ├── user/
    │   │   ├── UserWalletController.java
    │   │   ├── UserTransactionController.java
    │   │   └── UserPaymentController.java
    │   └── admin/
    │       ├── AdminUserController.java
    │       ├── AdminWalletController.java
    │       ├── AdminTransactionController.java
    │       └── AdminServiceController.java
    ├── service/
    │   ├── AuthService.java
    │   ├── WalletService.java
    │   ├── TransactionService.java
    │   ├── PaymentService.java
    │   └── AdminManagementService.java
    ├── repository/
    │   ├── UserRepository.java
    │   ├── UserProfileRepository.java
    │   ├── AdminProfileRepository.java
    │   ├── WalletRepository.java
    │   ├── PaymentServiceRepository.java
    │   └── WalletTransactionRepository.java
    ├── entity/
    │   ├── User.java
    │   ├── UserProfile.java
    │   ├── AdminProfile.java
    │   ├── Wallet.java
    │   ├── PaymentService.java
    │   └── WalletTransaction.java
    ├── dto/
    │   ├── request/
    │   │   ├── RegisterRequest.java
    │   │   ├── LoginRequest.java
    │   │   ├── DepositRequest.java
    │   │   ├── TransferRequest.java
    │   │   └── PaymentRequest.java
    │   └── response/
    │       ├── AuthResponse.java
    │       ├── WalletResponse.java
    │       ├── TransactionResponse.java
    │       └── ApiResponse.java
    ├── config/
    │   ├── SecurityConfig.java
    │   ├── CorsConfig.java
    │   └── JwtConfig.java
    └── exception/
        ├── ApiException.java
        └── GlobalExceptionHandler.java
```

Ghi chú:

Không nên đặt entity là `Transaction`, vì dễ trùng tên với khái niệm transaction trong Spring/database. Nên đặt là:

```text
WalletTransaction
```

Tương tự, bảng `services` có thể map sang entity:

```text
PaymentService
```

để tránh trùng với tầng `service`.

---

# 7. Yêu cầu chức năng

## 7.1 Đăng ký tài khoản

Người dùng có thể đăng ký tài khoản bằng số điện thoại, mật khẩu và họ tên.

Yêu cầu:

* Số điện thoại gồm đúng 10 chữ số.
* Số điện thoại bắt đầu bằng số 0.
* Mỗi số điện thoại chỉ được đăng ký một lần.
* Mật khẩu không được để trống.
* Mật khẩu nên có tối thiểu 6 ký tự.
* Mật khẩu phải được mã hóa trước khi lưu vào database.
* Khi đăng ký thành công, hệ thống tự động tạo:

  * Một dòng trong bảng `users`.
  * Một dòng trong bảng `user_profiles`.
  * Một dòng trong bảng `wallets`.
* Ví mới có số dư ban đầu là 10 đồng.
* Người dùng đăng ký mặc định có role là `user`.

Regex kiểm tra số điện thoại:

```text
^0[0-9]{9}$
```

Ví dụ số điện thoại hợp lệ:

```text
0912345678
0987654321
0901234567
```

Ví dụ số điện thoại không hợp lệ:

```text
912345678
1234567890
09123
09123456789
```

---

## 7.2 Đăng nhập

Người dùng đăng nhập bằng số điện thoại và mật khẩu.

Yêu cầu:

* Backend kiểm tra tài khoản có tồn tại hay không.
* Backend kiểm tra mật khẩu có đúng hay không.
* Backend kiểm tra tài khoản có bị khóa hay không.
* Nếu hợp lệ, backend trả về thông tin user và token.
* Token dùng để gọi các API cần đăng nhập.
* Nếu role là `user`, người dùng được truy cập các API `/api/user/**`.
* Nếu role là `admin`, admin được truy cập các API `/api/admin/**`.

---

## 7.3 Đăng xuất

Frontend xử lý đăng xuất bằng cách:

* Xóa token khỏi local storage hoặc state.
* Xóa thông tin user trong Zustand store.
* Điều hướng người dùng về trang đăng nhập.

---

## 7.4 Xem số dư ví

Người dùng có thể xem số dư ví hiện tại.

Yêu cầu:

* Người dùng phải đăng nhập.
* Backend lấy ví theo user đang đăng nhập.
* Backend trả về số dư hiện tại.
* Frontend hiển thị số dư trên Dashboard.

---

## 7.5 Nạp tiền mô phỏng

Chức năng nạp tiền chỉ là mô phỏng, không tích hợp ngân hàng hoặc cổng thanh toán thật.

Yêu cầu:

* Người dùng nhập số tiền muốn nạp.
* Số tiền phải lớn hơn 0.
* Số tiền không được vượt quá giới hạn hệ thống.
* Backend cộng tiền vào ví của người dùng.
* Backend ghi lại giao dịch với type là `deposit`.
* Backend lưu số dư trước và sau giao dịch.
* Frontend cập nhật số dư sau khi nạp tiền thành công.

Giới hạn đề xuất:

```text
amount > 0
amount <= 10000000
```

Ví dụ:

```text
Số dư ban đầu: 10
Nạp mô phỏng: 100
Số dư sau khi nạp: 110
```

---

## 7.6 Chuyển tiền giữa các ví

Người dùng có thể chuyển tiền sang người dùng khác thông qua số điện thoại.

Yêu cầu:

* Người gửi phải đăng nhập.
* Người gửi nhập số điện thoại người nhận.
* Người gửi nhập số tiền cần chuyển.
* Số điện thoại người nhận phải hợp lệ.
* Người nhận phải tồn tại trong hệ thống.
* Người gửi không được chuyển tiền cho chính mình.
* Số tiền chuyển phải lớn hơn 0.
* Số dư người gửi phải đủ.
* Backend trừ tiền ví người gửi.
* Backend cộng tiền ví người nhận.
* Backend ghi lại giao dịch với type là `transfer`.
* Backend lưu số dư trước và sau giao dịch của người gửi.
* Toàn bộ xử lý chuyển tiền phải nằm trong database transaction.

Ví dụ:

```text
User A có 110 đồng.
User B có 10 đồng.

User A chuyển cho User B 5 đồng.

Sau giao dịch:
User A còn 105 đồng.
User B có 15 đồng.
```

Các lỗi cần xử lý:

* Số điện thoại người nhận không hợp lệ.
* Người nhận không tồn tại.
* Người gửi chuyển tiền cho chính mình.
* Số tiền không hợp lệ.
* Số dư không đủ.
* Tài khoản bị khóa.

---

## 7.7 Thanh toán dịch vụ ảo

Người dùng có thể dùng ví để thanh toán các dịch vụ ảo được tạo sẵn.

Dịch vụ ảo đề xuất:

```text
Mua thẻ điện thoại - 10 đồng
Thanh toán tiền điện - 50 đồng
Thanh toán tiền nước - 30 đồng
Mua gói Internet - 100 đồng
Nạp game - 20 đồng
```

Yêu cầu:

* Người dùng phải đăng nhập.
* Frontend hiển thị danh sách dịch vụ ảo.
* Người dùng chọn dịch vụ muốn thanh toán.
* Backend kiểm tra dịch vụ có tồn tại hay không.
* Backend kiểm tra dịch vụ có đang hoạt động hay không.
* Backend kiểm tra số dư ví.
* Nếu số dư đủ, backend trừ tiền trong ví.
* Backend ghi giao dịch với type là `payment`.
* Backend lưu số dư trước và sau giao dịch.
* Toàn bộ xử lý thanh toán phải nằm trong database transaction.

Ví dụ:

```text
User có 110 đồng.
User chọn dịch vụ "Mua thẻ điện thoại" giá 10 đồng.

Sau thanh toán:
Số dư còn 100 đồng.
Lịch sử giao dịch ghi nhận payment - Mua thẻ điện thoại - 10 đồng.
```

---

## 7.8 Xem lịch sử giao dịch

Người dùng có thể xem lịch sử giao dịch liên quan đến ví của mình.

Các loại giao dịch:

```text
deposit
transfer
payment
```

Yêu cầu:

* Người dùng phải đăng nhập.
* Backend lấy ví của user đang đăng nhập.
* Backend lấy các giao dịch mà ví đó là người gửi hoặc người nhận.
* Giao dịch mới nhất hiển thị trước.
* Frontend hiển thị thông tin giao dịch rõ ràng.

Thông tin hiển thị:

* Mã giao dịch.
* Loại giao dịch.
* Số tiền.
* Người gửi.
* Người nhận.
* Dịch vụ thanh toán nếu có.
* Số dư trước giao dịch.
* Số dư sau giao dịch.
* Trạng thái.
* Mô tả.
* Thời gian tạo giao dịch.

Ý nghĩa từng loại giao dịch:

```text
deposit:
sender_wallet_id   = NULL
receiver_wallet_id = ví người nạp
service_id         = NULL

transfer:
sender_wallet_id   = ví người gửi
receiver_wallet_id = ví người nhận
service_id         = NULL

payment:
sender_wallet_id   = ví người thanh toán
receiver_wallet_id = NULL
service_id         = dịch vụ được thanh toán
```

---

# 8. Chức năng admin đã triển khai

Các chức năng admin mô tả trong phần này đã được triển khai trong giao diện responsive và backend hiện tại. Những endpoint ghi là “dự kiến” bên dưới được giữ như lịch sử thiết kế ban đầu; danh sách endpoint thực tế nằm trong source controller và tài liệu trạng thái hiện hành.

## 8.1 Quản lý người dùng

Admin có thể:

* Xem danh sách người dùng.
* Xem thông tin chi tiết người dùng.
* Khóa hoặc mở khóa tài khoản.
* Lọc người dùng theo trạng thái.
* Tìm người dùng theo số điện thoại.

API dự kiến:

```text
GET   /api/admin/users
GET   /api/admin/users/{id}
PATCH /api/admin/users/{id}/status
```

---

## 8.2 Quản lý ví

Admin có thể:

* Xem danh sách ví.
* Xem số dư của từng ví.
* Tìm ví theo số điện thoại người dùng.
* Xem ví nào có số dư cao/thấp.

API dự kiến:

```text
GET /api/admin/wallets
GET /api/admin/wallets/{id}
```

Ghi chú:

Admin không nên sửa số dư trực tiếp bằng cách update bảng `wallets`. Nếu sau này cần điều chỉnh số dư, nên tạo loại giao dịch mới như `adjustment` để lưu lịch sử.

---

## 8.3 Quản lý giao dịch

Admin có thể:

* Xem toàn bộ giao dịch trong hệ thống.
* Lọc giao dịch theo loại.
* Lọc giao dịch theo ngày.
* Lọc giao dịch theo số điện thoại.
* Xem chi tiết giao dịch.

API dự kiến:

```text
GET /api/admin/transactions
GET /api/admin/transactions/{id}
```

---

## 8.4 Quản lý dịch vụ ảo

Admin có thể:

* Xem danh sách dịch vụ.
* Thêm dịch vụ mới.
* Cập nhật tên, giá, mô tả dịch vụ.
* Bật/tắt dịch vụ.

API dự kiến:

```text
GET    /api/admin/services
POST   /api/admin/services
PATCH  /api/admin/services/{id}
PATCH  /api/admin/services/{id}/status
```

---

# 9. API Specification

## 9.1 Auth API

### Đăng ký

```text
POST /api/auth/register
```

Request:

```json
{
  "phone": "0912345678",
  "password": "123456",
  "fullName": "Nguyen Van A"
}
```

Response:

```json
{
  "message": "Register successfully",
  "user": {
    "id": 1,
    "phone": "0912345678",
    "fullName": "Nguyen Van A",
    "role": "user",
    "status": "active"
  },
  "wallet": {
    "balance": 10
  }
}
```

---

### Đăng nhập

```text
POST /api/auth/login
```

Request:

```json
{
  "phone": "0912345678",
  "password": "123456"
}
```

Response:

```json
{
  "message": "Login successfully",
  "token": "jwt_token_here",
  "user": {
    "id": 1,
    "phone": "0912345678",
    "fullName": "Nguyen Van A",
    "role": "user",
    "status": "active"
  }
}
```

---

## 9.2 User Wallet API

### Xem số dư

```text
GET /api/user/wallet/balance
```

Response:

```json
{
  "balance": 110
}
```

---

### Nạp tiền mô phỏng

```text
POST /api/user/wallet/deposit
```

Request:

```json
{
  "amount": 100
}
```

Response:

```json
{
  "message": "Deposit successfully",
  "balance": 110
}
```

---

### Chuyển tiền

```text
POST /api/user/wallet/transfer
```

Request:

```json
{
  "receiverPhone": "0987654321",
  "amount": 5
}
```

Response:

```json
{
  "message": "Transfer successfully",
  "balance": 105
}
```

---

### Thanh toán dịch vụ ảo

```text
POST /api/user/wallet/payment
```

Request:

```json
{
  "serviceId": 1
}
```

Response:

```json
{
  "message": "Payment successfully",
  "balance": 100
}
```

---

## 9.3 Service API

### Lấy danh sách dịch vụ ảo

```text
GET /api/user/services
```

Response:

```json
[
  {
    "id": 1,
    "name": "Mua thẻ điện thoại",
    "price": 10,
    "description": "Thanh toán mô phỏng dịch vụ thẻ điện thoại",
    "isActive": true
  },
  {
    "id": 2,
    "name": "Thanh toán tiền điện",
    "price": 50,
    "description": "Thanh toán mô phỏng hóa đơn điện",
    "isActive": true
  }
]
```

---

## 9.4 Transaction API

### Xem lịch sử giao dịch của user

```text
GET /api/user/transactions
```

Response:

```json
[
  {
    "id": 1,
    "transactionCode": "TXN202606010001",
    "type": "deposit",
    "senderPhone": null,
    "receiverPhone": "0912345678",
    "serviceName": null,
    "amount": 100,
    "balanceBefore": 10,
    "balanceAfter": 110,
    "status": "success",
    "description": "Mock deposit to wallet",
    "createdAt": "2026-06-01 09:00:00"
  },
  {
    "id": 2,
    "transactionCode": "TXN202606010002",
    "type": "transfer",
    "senderPhone": "0912345678",
    "receiverPhone": "0987654321",
    "serviceName": null,
    "amount": 5,
    "balanceBefore": 110,
    "balanceAfter": 105,
    "status": "success",
    "description": "Transfer money",
    "createdAt": "2026-06-01 10:30:00"
  }
]
```

---

# 10. Database Design

## 10.1 Tổng quan bảng

Database gồm các bảng chính:

```text
users
account_tokens
user_profiles
admin_profiles
wallets
services
transactions
```

Quan hệ tổng quát:

```text
users
  ├── account_tokens
  ├── user_profiles
  │       └── wallets
  └── admin_profiles

wallets
  └── transactions

services
  └── transactions
```

---

## 10.2 Bảng users

Bảng `users` là bảng tài khoản chung cho cả user và admin.

Các trường chính:

* `id`: khóa chính.
* `phone`: số điện thoại đăng nhập, duy nhất.
* `password`: mật khẩu đã mã hóa.
* `role`: vai trò tài khoản, gồm `user` hoặc `admin`.
* `status`: trạng thái tài khoản, gồm `active` hoặc `blocked`.
* `created_at`: thời gian tạo.
* `updated_at`: thời gian cập nhật.

Ý nghĩa:

```text
role = user  → tài khoản người dùng ví điện tử
role = admin → tài khoản quản trị hệ thống
```

---

### Bảng account_tokens

`account_tokens` lưu **hash** của token cho hai loại
`EMAIL_VERIFICATION` và `PASSWORD_RESET`; không lưu token dạng rõ. Trường
`expires_at` giới hạn thời gian hợp lệ và `used_at` đánh dấu token đã sử dụng
một lần. Cleanup token hết hạn bằng Spring scheduling hoặc AWS Lambda chưa
được triển khai.

---

## 10.3 Bảng user_profiles

Bảng `user_profiles` lưu thông tin riêng của người dùng ví điện tử.

Các trường chính:

* `user_id`: khóa chính, đồng thời là khóa ngoại đến `users.id`.
* `full_name`: họ tên người dùng.
* `date_of_birth`: ngày sinh.
* `address`: địa chỉ.
* `created_at`: thời gian tạo.
* `updated_at`: thời gian cập nhật.

Chỉ user thường mới có dòng trong bảng `user_profiles`.

---

## 10.4 Bảng admin_profiles

Bảng `admin_profiles` lưu thông tin riêng của admin.

Các trường chính:

* `user_id`: khóa chính, đồng thời là khóa ngoại đến `users.id`.
* `full_name`: họ tên admin.
* `position`: vị trí hoặc chức vụ.
* `created_at`: thời gian tạo.
* `updated_at`: thời gian cập nhật.

Admin không có ví, nên không có dòng trong bảng `wallets`.

---

## 10.5 Bảng wallets

Bảng `wallets` lưu ví của người dùng.

Các trường chính:

* `id`: khóa chính.
* `user_id`: khóa ngoại đến `user_profiles.user_id`.
* `balance`: số dư ví.
* `created_at`: thời gian tạo.
* `updated_at`: thời gian cập nhật.

Ràng buộc:

* Mỗi user thường chỉ có một ví.
* Admin không có ví.
* Số dư không được âm.
* Số dư mặc định khi tạo ví là 10 đồng.

---

## 10.6 Bảng services

Bảng `services` lưu danh sách dịch vụ ảo để thanh toán.

Các trường chính:

* `id`: khóa chính.
* `name`: tên dịch vụ.
* `price`: giá dịch vụ.
* `description`: mô tả.
* `is_active`: trạng thái hoạt động.
* `created_at`: thời gian tạo.
* `updated_at`: thời gian cập nhật.

---

## 10.7 Bảng transactions

Bảng `transactions` lưu toàn bộ lịch sử giao dịch.

Các trường chính:

* `id`: khóa chính.
* `transaction_code`: mã giao dịch duy nhất.
* `sender_wallet_id`: ví gửi tiền, có thể null.
* `receiver_wallet_id`: ví nhận tiền, có thể null.
* `service_id`: dịch vụ thanh toán, có thể null.
* `amount`: số tiền giao dịch.
* `balance_before`: số dư trước giao dịch của người thực hiện giao dịch.
* `balance_after`: số dư sau giao dịch của người thực hiện giao dịch.
* `type`: loại giao dịch.
* `status`: trạng thái giao dịch.
* `description`: mô tả.
* `created_by`: user tạo giao dịch.
* `created_at`: thời gian tạo giao dịch.

Các loại giao dịch:

```text
deposit
transfer
payment
```

Ý nghĩa:

* `deposit`: nạp tiền mô phỏng.
* `transfer`: chuyển tiền giữa các ví.
* `payment`: thanh toán dịch vụ ảo.

---

# 11. Business Rules

## 11.1 Quy tắc tài khoản

* Một số điện thoại chỉ được đăng ký một tài khoản.
* Số điện thoại phải có đúng 10 chữ số.
* Số điện thoại phải bắt đầu bằng số 0.
* Mật khẩu phải được mã hóa trước khi lưu.
* Người dùng phải đăng nhập trước khi thực hiện chức năng liên quan đến ví.
* Tài khoản bị khóa không được thực hiện giao dịch.
* Admin và user dùng chung bảng `users`, phân biệt bằng `role`.

---

## 11.2 Quy tắc ví

* Một user thường chỉ có một ví.
* Admin không có ví.
* Ví được tạo tự động khi user đăng ký thành công.
* Số dư ban đầu của ví là 10 đồng.
* Số dư ví không được âm.

---

## 11.3 Quy tắc nạp tiền

* Nạp tiền chỉ là mô phỏng.
* Số tiền nạp phải lớn hơn 0.
* Số tiền nạp không được vượt quá giới hạn hệ thống.
* Khi nạp thành công, số dư ví tăng lên.
* Mỗi lần nạp phải được ghi vào bảng `transactions`.

---

## 11.4 Quy tắc chuyển tiền

* Người gửi và người nhận phải là hai tài khoản khác nhau.
* Người nhận phải tồn tại.
* Số tiền chuyển phải lớn hơn 0.
* Số dư người gửi phải đủ.
* Khi chuyển thành công, số dư người gửi giảm và số dư người nhận tăng.
* Giao dịch chuyển tiền phải được xử lý bằng database transaction.
* Giao dịch chuyển tiền phải được ghi vào bảng `transactions`.

---

## 11.5 Quy tắc thanh toán

* Dịch vụ thanh toán phải tồn tại.
* Dịch vụ thanh toán phải đang hoạt động.
* Số dư người dùng phải đủ để thanh toán.
* Khi thanh toán thành công, số dư ví giảm.
* Giao dịch thanh toán phải được ghi vào bảng `transactions`.

---

## 11.6 Quy tắc admin

* Admin có quyền truy cập API `/api/admin/**`.
* User thường không được truy cập API `/api/admin/**`.
* Admin có thể quản lý user, ví, giao dịch và dịch vụ trong tương lai.
* Admin không nên sửa trực tiếp số dư ví. Nếu cần điều chỉnh số dư, nên tạo giao dịch riêng để lưu lịch sử.

---

# 12. Yêu cầu phi chức năng

## 12.1 Tính dễ sử dụng

* Giao diện đơn giản, dễ hiểu.
* Người dùng có thể thao tác các chức năng chính trong vài bước.
* Thông báo lỗi cần rõ ràng.

---

## 12.2 Tính đúng đắn dữ liệu

* Không cho phép số dư âm.
* Không cho phép chuyển tiền khi số dư không đủ.
* Không cho phép tạo nhiều ví cho một user.
* Các thao tác tiền phải dùng database transaction.
* Lịch sử giao dịch phải được lưu đầy đủ.

---

## 12.3 Tính bảo mật cơ bản

* Mật khẩu phải được hash.
* Không trả mật khẩu về frontend.
* Kiểm tra dữ liệu đầu vào ở cả frontend và backend.
* Người dùng chỉ được xem ví và lịch sử giao dịch của chính mình.
* API admin phải kiểm tra role.
* Token không được hard-code.

---

## 12.4 Tính mở rộng

* Backend tách controller user/admin rõ ràng.
* Database có role để phân quyền.
* Có bảng `admin_profiles` để mở rộng admin.
* Có bảng `services` để mở rộng dịch vụ thanh toán.
* Có bảng `transactions` để lưu lịch sử giao dịch.
* Có thể chuyển database từ MySQL Docker sang Amazon RDS.
* Có thể deploy backend bằng container trên EC2, ECS Fargate hoặc App Runner.

---

# 13. Giao diện đề xuất

## 13.1 Trang đăng ký

Chức năng:

* Nhập họ tên.
* Nhập số điện thoại.
* Nhập mật khẩu.
* Validate dữ liệu bằng Zod.
* Gửi request đăng ký.
* Hiển thị thông báo thành công hoặc lỗi.

---

## 13.2 Trang đăng nhập

Chức năng:

* Nhập số điện thoại.
* Nhập mật khẩu.
* Gửi request đăng nhập.
* Lưu token và thông tin user.
* Điều hướng theo role:

  * `user` → Dashboard người dùng.
  * `admin` → Dashboard admin.

---

## 13.3 Trang Dashboard người dùng

Chức năng:

* Hiển thị thông tin người dùng.
* Hiển thị số dư ví.
* Điều hướng đến nạp tiền, chuyển tiền, thanh toán và lịch sử.

---

## 13.4 Trang nạp tiền

Chức năng:

* Nhập số tiền muốn nạp.
* Có thể có nút chọn nhanh: 10, 50, 100, 500.
* Gửi request nạp tiền.
* Hiển thị số dư mới.

---

## 13.5 Trang chuyển tiền

Chức năng:

* Nhập số điện thoại người nhận.
* Nhập số tiền muốn chuyển.
* Gửi request chuyển tiền.
* Hiển thị kết quả giao dịch.

---

## 13.6 Trang thanh toán dịch vụ

Chức năng:

* Hiển thị danh sách dịch vụ ảo.
* Hiển thị tên dịch vụ, giá tiền, mô tả.
* Người dùng chọn dịch vụ để thanh toán.
* Hiển thị kết quả thanh toán.

---

## 13.7 Trang lịch sử giao dịch

Chức năng:

* Hiển thị danh sách giao dịch.
* Hiển thị mã giao dịch, loại giao dịch, số tiền, trạng thái và thời gian.
* Phân biệt giao dịch nạp tiền, chuyển tiền và thanh toán.
* Có thể hiển thị số điện thoại người gửi/người nhận hoặc tên dịch vụ.

---

## 13.8 Trang admin dự kiến

Các trang admin có thể bổ sung sau:

```text
/admin
/admin/users
/admin/wallets
/admin/transactions
/admin/services
```

Chức năng dự kiến:

* Quản lý người dùng.
* Quản lý ví.
* Quản lý giao dịch.
* Quản lý dịch vụ ảo.
* Xem thống kê cơ bản.

---

# 14. Luồng xử lý chính

## 14.1 Luồng đăng ký

```text
Người dùng nhập thông tin đăng ký
→ Frontend validate bằng Zod
→ Gửi request đến backend
→ Backend kiểm tra số điện thoại
→ Backend hash password
→ Backend tạo user với role = user
→ Backend tạo user_profile
→ Backend tạo wallet với balance = 10
→ Backend trả response thành công
→ Frontend chuyển sang trang đăng nhập hoặc dashboard
```

---

## 14.2 Luồng đăng nhập

```text
Người dùng nhập số điện thoại và mật khẩu
→ Frontend gửi request login
→ Backend tìm user theo số điện thoại
→ Backend kiểm tra mật khẩu
→ Backend kiểm tra status
→ Backend tạo token
→ Backend trả token và thông tin user
→ Frontend lưu token
→ Frontend điều hướng theo role
```

---

## 14.3 Luồng nạp tiền mô phỏng

```text
Người dùng nhập số tiền nạp
→ Frontend validate số tiền
→ Gửi request đến backend
→ Backend kiểm tra token
→ Backend tìm ví user
→ Backend kiểm tra amount hợp lệ
→ Backend bắt đầu database transaction
→ Backend cộng tiền vào ví
→ Backend ghi transaction type = deposit
→ Backend commit transaction
→ Backend trả số dư mới
→ Frontend cập nhật giao diện
```

---

## 14.4 Luồng chuyển tiền

```text
Người dùng nhập số điện thoại người nhận và số tiền
→ Frontend validate dữ liệu
→ Gửi request đến backend
→ Backend kiểm tra token
→ Backend tìm ví người gửi
→ Backend tìm người nhận theo số điện thoại
→ Backend kiểm tra không chuyển cho chính mình
→ Backend kiểm tra số dư
→ Backend bắt đầu database transaction
→ Backend trừ tiền ví người gửi
→ Backend cộng tiền ví người nhận
→ Backend ghi transaction type = transfer
→ Backend commit transaction
→ Backend trả kết quả thành công
→ Frontend cập nhật số dư và thông báo
```

---

## 14.5 Luồng thanh toán dịch vụ ảo

```text
Người dùng chọn dịch vụ cần thanh toán
→ Frontend gửi serviceId đến backend
→ Backend kiểm tra token
→ Backend tìm ví người dùng
→ Backend tìm dịch vụ
→ Backend kiểm tra dịch vụ đang hoạt động
→ Backend kiểm tra số dư
→ Backend bắt đầu database transaction
→ Backend trừ tiền ví người dùng
→ Backend ghi transaction type = payment
→ Backend commit transaction
→ Backend trả kết quả thành công
→ Frontend cập nhật số dư và thông báo
```

---

## 14.6 Luồng xem lịch sử giao dịch

```text
Người dùng mở trang lịch sử
→ Frontend gửi request đến backend
→ Backend kiểm tra token
→ Backend tìm ví của user
→ Backend lấy giao dịch có sender_wallet_id hoặc receiver_wallet_id là ví của user
→ Backend sắp xếp theo thời gian mới nhất
→ Backend trả danh sách giao dịch
→ Frontend hiển thị lịch sử
```

---

# 15. Triển khai hiện tại và định hướng sau này

## 15.0 Trạng thái production hiện tại

Kiến trúc đang hoạt động:

```text
User → Amazon CloudFront + AWS WAF
          |-- Default (*) → Amazon S3 React frontend
          `-- /api/* → internet-facing ALB
                         → Target Group (:8080, /actuator/health)
                         → 2 EC2 chạy Spring Boot/Docker tại 2 AZ
                           ASG Min 0 / Desired 2 / Max 2
                         → RDS MySQL Single-AZ trong private subnet
                         → Amazon SES SMTP (STARTTLS :587)
```

CloudFront nhận HTTPS từ người dùng; Cloudflare chỉ quản lý DNS và các record xác minh AWS/SES, không nằm trong application request flow như proxy hoặc CDN. Web ACL của AWS WAF dùng `AWS-AWSManagedRulesCommonRuleSet` (700 WCU); một số rule Block, còn SizeRestrictions và CrossSiteScripting ở Count để theo dõi.

Trong mô hình mục tiêu, ALB internet-facing liên kết với hai public subnet ở hai Availability Zone và forward qua Target Group đến các EC2 healthy. ASG quản lý vòng đời hai EC2 private với `Min = 0`, `Desired = 2`, `Max = 2`; request không đi qua ASG. EC2 chỉ nhận port `8080` từ ALB Security Group và chủ động kết nối outbound qua một NAT Gateway trong public subnet rồi đến Internet Gateway. RDS ở private database subnet, chỉ nhận `3306` từ EC2 Security Group và vẫn là Single-AZ, vì vậy kiến trúc không được xem là HA end-to-end.

Backend chạy trong Docker với file môi trường ngoài repository `/home/ec2-user/ewallet-backend.env` và ánh xạ `8080:8080`. Frontend được build từ `frontend/`, upload lên S3 và phân phối qua CloudFront. Amazon SES SMTP xử lý verification, resend verification, forgot-password và reset-password bằng authentication và STARTTLS port `587`.

Các file môi trường local, Vite build-time và EC2 runtime được giữ riêng. File chứa giá trị thật không được commit; tài liệu và template chỉ chứa tên biến hoặc placeholder.
## 15.1 Giai đoạn local

```text
Frontend React chạy local
Spring Boot backend chạy local
MySQL chạy Docker
phpMyAdmin chạy Docker
```

---

## 15.2 Giai đoạn Docker hóa

Sau khi code ổn định:

```text
Backend Spring Boot đóng Docker image
Frontend React build thành static files
Database vẫn dùng MySQL Docker để test local
```

---

## 15.3 Giai đoạn deploy AWS đơn giản

Cách dễ nhất:

```text
Frontend: S3 + CloudFront hoặc chạy chung web server
Backend: EC2 + Docker Compose
Database: Amazon RDS MySQL
```

---

## 15.4 Giai đoạn deploy AWS container tốt hơn

Cách cloud/container hơn:

```text
Frontend: S3 + CloudFront
Backend: ECS Fargate hoặc App Runner
Database: Amazon RDS MySQL
Image Registry: Amazon ECR
```

---

## 15.5 Cấu hình cần chuẩn bị từ đầu

Không hard-code cấu hình trong code.

Frontend dùng biến môi trường:

```text
VITE_API_BASE_URL=http://localhost:8080
```

Backend dùng biến môi trường:

```text
SPRING_PROFILES_ACTIVE=local hoặc prod
DB_URL=jdbc:mysql://host:port/ewallet_db
DB_USERNAME=ewallet_user
DB_PASSWORD=********
JWT_SECRET=********
JWT_EXPIRATION=3600
FRONTEND_BASE_URL=https://cloud-ewallet.com
CORS_ALLOWED_ORIGINS=https://cloud-ewallet.com
MAIL_DEVELOPMENT_LOG_ENABLED=false
EMAIL_VERIFICATION_MINUTES=1440
PASSWORD_RESET_MINUTES=30
EMAIL_PROVIDER=ses
SES_SMTP_HOST=email-smtp.ap-southeast-1.amazonaws.com
SES_SMTP_PORT=587
SES_SMTP_USERNAME=REPLACE_WITH_SES_SMTP_USERNAME
SES_SMTP_PASSWORD=REPLACE_WITH_SES_SMTP_PASSWORD
SES_MAIL_FROM_ADDRESS=noreply@cloud-ewallet.com
```

Khi deploy lên AWS, chỉ cần đổi biến môi trường, không cần sửa code nghiệp vụ.
Production tiếp tục đọc các tên biến này từ
`/home/ec2-user/ewallet-backend.env`; không di chuyển hoặc đổi tên runtime file
trong quá trình dọn dẹp repository. `VITE_API_BASE_URL` là cấu hình public được
đóng gói khi Vite build và không được chứa secret.

---

# 16. Phạm vi dự án

## 16.1 Trong phạm vi

Dự án bao gồm:

* Đăng ký tài khoản.
* Đăng nhập.
* Phân quyền user/admin.
* Mỗi user có một ví.
* Ví có số dư ban đầu là 10 đồng.
* Nạp tiền mô phỏng.
* Chuyển tiền giữa các ví.
* Thanh toán dịch vụ ảo.
* Xem lịch sử giao dịch.
* Database MySQL.
* Backend Spring Boot.
* Frontend React TypeScript.
* MySQL chạy Docker trong giai đoạn local.
* Thiết kế sẵn để mở rộng admin.

---

## 16.2 Ngoài phạm vi

Dự án không bao gồm trong giai đoạn đầu:

* Tích hợp ngân hàng thật.
* Tích hợp cổng thanh toán thật.
* OTP thật.
* SMS thật.
* KYC người dùng.
* QR payment thật.
* Hoàn tiền tự động.
* Merchant thật.
* Microservices.
* Kubernetes production.
* Hệ thống chống gian lận nâng cao.

---

# 17. Kết luận

Cloud-based E-wallet là một hệ thống ví điện tử mô phỏng phù hợp để thực hành xây dựng web application theo mô hình frontend, backend và database. Dự án sử dụng React TypeScript cho frontend, Spring Boot cho backend và MySQL cho database.

Thiết kế hiện tại đã hoàn thành chức năng người dùng gồm đăng ký, JWT login/logout, xác minh và gửi lại email xác minh, quên/đặt lại mật khẩu, profile, số dư, nạp tiền mô phỏng, chuyển tiền với tra cứu tên người nhận, thanh toán dịch vụ và lịch sử giao dịch. Dashboard admin, quản lý user, giao dịch và dịch vụ cũng đã được triển khai.

Hệ thống hiện phân phối frontend từ Amazon S3 qua CloudFront được bảo vệ bằng AWS WAF. Request `/api/*` đi qua ALB và Target Group đến hai EC2 tại hai Availability Zone do ASG quản lý. Backend kết nối RDS MySQL Single-AZ và gửi transactional email qua Amazon SES SMTP STARTTLS port 587. Application tier có khả năng chịu lỗi tốt hơn, nhưng RDS Single-AZ vẫn là giới hạn availability; việc triển khai phiên bản ứng dụng hiện còn thủ công.
