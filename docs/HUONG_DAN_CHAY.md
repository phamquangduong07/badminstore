# Hướng dẫn chạy dự án BadStore

> Dự án: website bán vợt cầu lông (đồ án tốt nghiệp – mức KHÁ).
> Stack: Java 21 + Spring Boot 4.1.1 (Maven) + Spring Data JPA + Spring Security (BCrypt) + Thymeleaf + Bootstrap 5 + SQL Server.

## 1. Cần có trước khi chạy

| Thành phần | Ghi chú |
|---|---|
| JDK 21 | `java -version` phải ra 21.x |
| SQL Server Express | mặc định `localhost,1433`, tài khoản SQL `test`/`123456` (hoặc đổi trong `application-local.yml`) |
| `sqlcmd` | để chạy các file `.sql` |
| Không cần cài Maven | dùng `mvnw`/`mvnw.cmd` kèm trong project |

## 2. Tạo CSDL (chạy đúng thứ tự)

```bat
sqlcmd -S localhost,1433 -U test -P 123456 -C -I -f 65001 -d master      -i docs\BadStore.sql
sqlcmd -S localhost,1433 -U test -P 123456 -C -I -f 65001 -d BadminStore -i docs\BadStore_patch_kha.sql
sqlcmd -S localhost,1433 -U test -P 123456 -C -I -f 65001 -d BadminStore -i docs\seed_san_pham.sql
sqlcmd -S localhost,1433 -U test -P 123456 -C -I -f 65001 -d BadminStore -i docs\seed_san_pham_bo_sung.sql
```

- `BadStore.sql` là script gốc: tạo database `BadminStore` + toàn bộ bảng (không sửa file này).
- `BadStore_patch_kha.sql` chỉ thêm bảng `product_reviews` (CSDL demo hiện đã có bảng này).
- 2 file `seed_*.sql` là **script dữ liệu** (không đổi cấu trúc): lần lượt nạp **15 + 5 = 20 cây vợt**, 6 hãng, ~48 biến thể. Chạy lại nhiều lần vẫn ra đúng kết quả đó.
- Ảnh sản phẩm là SVG tự vẽ trong `src/main/resources/static/images/rackets/` (không dùng ảnh của hãng).

## 3. Cấu hình

`src/main/resources/application-local.yml` (file này **không commit** – chỉ có file mẫu `application-local.example.yml`):

```yaml
spring:
  datasource:
    url: jdbc:sqlserver://localhost:1433;databaseName=BadminStore;encrypt=true;trustServerCertificate=true
    username: test
    password: 123456
app:
  upload-dir: uploads        # thư mục lưu ảnh sản phẩm do admin tải lên
  ghn:                       # tuỳ chọn: tích hợp GHN. Không khai báo ⇒ app chạy bình thường, phí ship 30.000 ₫
    enabled: false           # bật/tắt toàn bộ phần GHN (địa chỉ + phí + vận đơn)
    mock: true               # true = dùng dữ liệu giả (chỉ có tác dụng khi enabled=true, không cần mạng/token)
    token: ${GHN_TOKEN:}     # token GHN thật – KHÔNG commit, đặt qua biến môi trường
    shop-id: ${GHN_SHOP_ID:}
    from-district-id: 1442   # quận của shop để GHN tính phí
    from-ward-code: "20308"
    mock-status: delivered   # trạng thái GHN giả trả về: picked|storing|delivering|delivered|returned
    sync-enabled: false      # bật scheduler tự đồng bộ trạng thái vận đơn (mặc định tắt)
```

| Biến môi trường | Ý nghĩa |
|---|---|
| `GHN_TOKEN` / `GHN_SHOP_ID` | Token + mã shop GHN (chỉ cần khi `app.ghn.mock=false`) |
| `GHN_BASE_URL` | Mặc định `https://dev-online-gateway.ghn.vn/shiip/public-api` (môi trường thử nghiệm của GHN) |

> Khi **chưa có token** vẫn demo được đầy đủ: đặt `app.ghn.enabled=true` + `app.ghn.mock=true`
> ⇒ dropdown Tỉnh–Huyện–Xã, phí vận chuyển, mã vận đơn `GHNMOCK…` và đồng bộ trạng thái đều chạy bằng dữ liệu giả.
> GHN tắt hoặc lỗi mạng thì trang địa chỉ/thanh toán **không vỡ**: form tự chuyển về nhập tay, phí ship dùng mức 30.000 ₫.

## 4. Chạy ứng dụng

```bat
.\mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

hoặc đóng gói rồi chạy:

```bat
.\mvnw -B -DskipTests package
java -jar target\demo-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

Mở http://localhost:8080

> Muốn demo phần **GHN** thì thêm cờ khi chạy (không cần token thật):
> `--app.ghn.enabled=true --app.ghn.mock=true --app.ghn.from-district-id=1442 --app.ghn.mock-status=delivered`
> (với `spring-boot:run` thì để trong `-Dspring-boot.run.arguments="..."`; nếu cổng 8080 đang bận, thêm `--server.port=8081`)

| Vai trò | Đăng nhập |
|---|---|
| Quản trị | `admin@badstore.local` / `Admin@123` (tạo tự động khi khởi động lần đầu) |
| Khách hàng | tự đăng ký ở `/dang-ky` |

## 5. Chạy test

```bat
.\mvnw -B clean test
```

Hiện tại: **225 test – 0 lỗi** (unit test Service bằng JUnit 5 + Mockito, test tích hợp MockMvc chạy trên CSDL thật). Trong đó 47 test của 2 chức năng làm thêm: **bán hàng tại quầy** và **tích hợp GHN**.

## 6. Các chức năng chính để demo

**Khách hàng**
- Trang chủ (slider Swiper, hiệu ứng AOS), danh mục: tìm theo tên + lọc hãng/loại/khoảng giá, 5 kiểu sắp xếp, phân trang.
- Chi tiết sản phẩm: chọn biến thể (màu × trọng lượng × cỡ cán), giá gạch ngang, tồn kho theo biến thể, nút **So sánh**, khối **Đánh giá** (điểm trung bình + bình luận).
- **So sánh sản phẩm** `/so-sanh`: thêm tối đa 4 cây, bảng so sánh chiều dài – điểm cân bằng – độ cứng thân – mức căng dây – chất liệu – giá – tồn kho.
- **Đánh giá sản phẩm** (chỉ khách đã mua và đã nhận hàng, mỗi lượt mua 1 lần) – gửi bằng SweetAlert2 xác nhận rồi hiện flash.
- Giỏ hàng → thanh toán (COD / chuyển khoản), sổ địa chỉ, voucher, phí ship 30.000 ₫ (miễn phí từ 5.000.000 ₫). Khi bật GHN: sổ địa chỉ và bước thanh toán có **dropdown Tỉnh → Huyện → Xã**, phí ship lấy theo GHN (tạm tính ngay khi chọn), vẫn nhập tay được nếu GHN tắt/lỗi.
- "Đơn hàng của tôi": timeline trạng thái, tự huỷ khi chờ xác nhận, yêu cầu hoàn trả khi đã giao.

**Quản trị** (`/admin`)
- Dashboard Chart.js: doanh thu theo ngày/7 ngày qua, đơn theo trạng thái, top sản phẩm.
- Đơn hàng: lọc/tìm kiếm, đổi trạng thái theo luồng `PENDING → CONFIRMED (trừ kho) → SHIPPING → DELIVERED`, huỷ/hoàn trả (hoàn kho + trả lượt voucher), xác nhận thanh toán, **tạo đơn thủ công** cho khách tại quầy, xem lịch sử trạng thái.
- Sản phẩm: thêm/sửa, xoá mềm, quản lý **biến thể** (giá – tồn kho – SKU), tải **ảnh** (≤ 2 MB, kiểm tra đuôi + MIME) và đặt ảnh chính, **cập nhật giá hàng loạt** (giảm % / bỏ giảm giá) cho nhiều biến thể.
- Voucher: thêm/sửa, bật – ngừng áp dụng (không xoá để giữ liên kết đơn hàng).
- **Bán hàng tại quầy (POS)** `/admin/ban-hang`: quét mã SKU (hoặc gõ tên) → thêm vào hoá đơn, khách lẻ hoặc khách quen theo SĐT (tự gắn vào tài khoản khách), chọn voucher, thanh toán tiền mặt (nhập tiền khách đưa ⇒ tự tính tiền thối) hoặc chuyển khoản, bấm **Thu tiền & in phiếu** ⇒ đơn `MANUAL` xác nhận ngay (trừ kho trong 1 transaction) rồi mở **phiếu bán hàng** in được (`Ctrl+P`, đã có `@media print`). Bán thiếu tồn kho hay tiền khách đưa không đủ thì hoá đơn **giữ nguyên** và báo rõ lý do để thu lại.
- **Vận chuyển GHN** ở trang chi tiết đơn: xem phí dự kiến + khối lượng, **Đẩy đơn sang GHN** (lấy mã vận đơn, lưu vào đơn), **Đồng bộ trạng thái GHN** (đã giao ⇒ đơn `DELIVERED` + COD chuyển `PAID`; hoàn hàng ⇒ `RETURNED`, nếu đơn chưa giao thì `CANCELLED`), có link tra cứu vận đơn.

## 7. Quy tắc nghiệp vụ đáng lưu ý khi bảo vệ

1. **Xác nhận đơn = trừ kho** bằng câu UPDATE có điều kiện `stock_quantity >= :qty` (atomic), thiếu hàng thì rollback và báo rõ SKU thiếu; cờ `stock_deducted` luôn khớp `order_status` (ràng buộc `CK_orders_stock`).
2. **Huỷ/hoàn trả** cộng lại kho đúng một lần theo cờ `stock_deducted`, trả lượt voucher, đơn đã thanh toán khi hoàn trả chuyển payment sang `REFUNDED`.
3. Mỗi lần đổi trạng thái là 1 transaction và **luôn ghi `order_status_histories`**.
4. Giá/tổng tiền **luôn tính lại ở server** từ DB, không tin dữ liệu từ form.
5. Không có bảng promotion: giảm giá chỉ bằng **voucher** (theo tổng hoá đơn) và cặp cột `original_price → price` của biến thể.
6. Mọi thao tác ghi CSDL đều có **xác nhận SweetAlert2 trước** + **flash message sau** (Post-Redirect-Get).
7. **Bán tại quầy** dùng lại đúng luồng đơn `MANUAL` (không viết luồng trừ kho riêng): đơn được xác nhận ngay ⇒ trừ kho; giá, voucher, tiền thối **tính ở server** từ CSDL, trình duyệt chỉ hiển thị tạm.
8. **GHN là tuỳ chọn**: token GHN chỉ nằm ở server (trình duyệt gọi `/api/ghn/*` nội bộ), mã Tỉnh/Huyện/Xã khách gửi lên được **kiểm tra lại với GHN** (mã sai bị bỏ, không lưu) nên không thể sửa mã để ăn phí ship rẻ; phí ship luôn tính lại ở server khi đặt hàng.

## 8. Ghi chú kỹ thuật

- `spring.jpa.hibernate.ddl-auto=validate` và `spring.jpa.open-in-view=false`: muốn đổi schema phải sửa `docs/BadStore.sql` (đã thống nhất với GV), các truy vấn hiển thị đều dùng fetch join.
- Thymeleaf 3.1 chặn truy cập static `T(com.example...)` trong template ⇒ các nhãn hiển thị được tính ở entity/DTO.
- Thư mục `uploads/` chứa ảnh do admin tải lên (đã nằm trong `.gitignore`).
