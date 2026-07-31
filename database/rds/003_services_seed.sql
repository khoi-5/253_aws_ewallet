SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- Controlled production catalog seed. Run after 001_schema.sql and 002_admin_template.sql.
-- Stable IDs make the script safe to retry without creating duplicate catalog rows.
INSERT INTO services (id, name, price, description, is_active)
VALUES
    (1, 'Mua thẻ điện thoại', 10.00, 'Thanh toán mô phỏng dịch vụ thẻ điện thoại', TRUE),
    (2, 'Thanh toán tiền điện', 50.00, 'Thanh toán mô phỏng hóa đơn điện', TRUE),
    (3, 'Thanh toán tiền nước', 30.00, 'Thanh toán mô phỏng hóa đơn nước', TRUE),
    (4, 'Mua gói Internet', 100.00, 'Thanh toán mô phỏng gói Internet', TRUE),
    (5, 'Nạp game', 20.00, 'Thanh toán mô phỏng dịch vụ nạp game', TRUE)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    price = VALUES(price),
    description = VALUES(description),
    is_active = VALUES(is_active);
