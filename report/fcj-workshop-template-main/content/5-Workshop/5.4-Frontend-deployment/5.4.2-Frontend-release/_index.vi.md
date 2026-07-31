---
title: "Build và phát hành frontend qua CloudFront"
date: 2024-01-01
weight: 2
chapter: false
pre: " <b> 5.4.2. </b> "
---

## Mục tiêu

Mô tả quy trình được lặp lại mỗi khi phát hành React frontend mới: chuẩn bị biến build, kiểm tra source, tạo static files, đồng bộ lên S3, làm mới cache CloudFront và smoke test trên domain production.

## Bước 1: Chuẩn bị biến build

Frontend đọc `frontend/.env.production` tại thời điểm build. `VITE_API_BASE_URL` là URL public được nhúng vào JavaScript, không phải secret.

Khi sử dụng custom domain và behavior `/api/*`:

```dotenv
VITE_API_BASE_URL=https://cloud-ewallet.com
```

Nếu kiểm tra distribution trước khi gắn domain, có thể tạm dùng:

```dotenv
VITE_API_BASE_URL=https://<CLOUDFRONT_DISTRIBUTION_DOMAIN>
```

Sau khi đổi giá trị này, frontend phải được build lại.

## Bước 2: Kiểm tra và build frontend

Tại thư mục `frontend/`, cài dependency, kiểm tra ESLint và build bằng Vite:

```powershell
cd frontend
npm install
npm run lint
npm run build
```

Script `build` chạy TypeScript trước khi Vite tạo output trong `frontend/dist/`. Repository hiện không khai báo automated frontend test script, vì vậy lint, build và smoke test là các bước kiểm tra chính trước khi phát hành.

Trước khi upload, nhóm kiểm tra `dist/index.html`, thư mục `dist/assets/` và các static files cần thiết. Không chỉnh trực tiếp nội dung trong `dist/` vì lần build tiếp theo sẽ ghi đè thư mục này.

## Bước 3: Đồng bộ static files lên S3

Nếu máy triển khai đã cài và cấu hình AWS CLI cho đúng IAM identity, nội dung `dist/` được đồng bộ bằng:

```powershell
aws s3 sync .\dist\ s3://<FRONTEND_BUCKET>/ --delete
```

Cần kiểm tra đúng bucket trước khi dùng `--delete`. Nếu triển khai bằng AWS Console, upload toàn bộ nội dung bên trong `dist/` và loại bỏ những asset cũ không còn được tham chiếu.

Kết quả trong bucket gồm `index.html`, thư mục `assets/`, hình ảnh và các file tĩnh phục vụ frontend; source và file môi trường không xuất hiện trong S3.

![Các static files frontend sau khi upload lên S3](/images/5-Workshop/5.4-Frontend-deployment/5.4.2-Frontend-release/s3-deployed-objects.png)

<p style="text-align: center;"><em>Hình 5.16. Nội dung bản build frontend đã được upload lên S3 production bucket.</em></p>

## Bước 4: Tạo CloudFront invalidation

Sau khi S3 được cập nhật, nhóm tạo invalidation cho `/*` để các edge location làm mới nội dung đã cache. Thao tác có thể thực hiện tại **CloudFront → Distribution → Invalidations → Create invalidation**, hoặc bằng AWS CLI:

```powershell
aws cloudfront create-invalidation `
  --distribution-id <DISTRIBUTION_ID> `
  --paths "/*"
```

![Tạo CloudFront invalidation cho bản frontend mới](/images/5-Workshop/5.4-Frontend-deployment/5.4.2-Frontend-release/cloudfront-create-invalidation.png)

<p style="text-align: center;"><em>Hình 5.17. Tạo invalidation cho toàn bộ đường dẫn sau khi cập nhật frontend.</em></p>

Sau khi gửi yêu cầu, cần chờ invalidation chuyển sang trạng thái **Completed** trước khi đánh giá bản phát hành, tránh trường hợp trình duyệt nhận `index.html` cũ tham chiếu đến asset đã thay đổi.

## Bước 5: Kiểm tra sau phát hành

Nhóm thực hiện smoke test theo thứ tự:

1. Mở `https://cloud-ewallet.com` và xác nhận kết nối HTTPS hợp lệ.
2. Hard refresh, mở DevTools và kiểm tra không có asset trả về `403` hoặc `404`.
3. Mở trực tiếp các route React để kiểm tra SPA fallback.
4. Gọi API qua `/api/*` và xác nhận request được chuyển đến backend.
5. Kiểm tra giao diện trên desktop/mobile và workflow đăng nhập.

Nếu phát hiện lỗi, nhóm so sánh kết quả build local, danh sách object S3, CloudFront origin/behavior và trạng thái invalidation trước khi phát hành lại.

## Kết quả

Phiên bản frontend mới được lưu trên S3, phân phối qua CloudFront và truy cập bằng custom domain.