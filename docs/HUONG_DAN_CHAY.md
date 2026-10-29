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

Cấu hình chia 2 tầng: `application.yml` giữ **mặc định chung** (đã commit), `application-local.yml` chỉ ghi đè **phần riêng của máy bạn** (không commit).

| File | Nội dung | Commit? |
|---|---|---|
| `src/main/resources/application.yml` | Mặc định chung: CSDL, JPA (`validate`), Thymeleaf, upload, cổng **8088**, `app.shop`, `app.ghn`, `app.shipping`, `app.admin` | có |
| `src/main/resources/application-local.yml` | Chỉ những gì khác giữa các máy: tài khoản CSDL, mật khẩu ADMIN, token GHN | **không** |
| `src/main/resources/application-local.example.yml` | File mẫu để sao chép | có |
| `.env.example` | Danh sách biến môi trường tương ứng (dùng thay file local cũng được) | có |

Tạo `application-local.yml` từ file mẫu rồi điền – thực ra **chỉ cần 2 mục**:

```yaml
spring:
  datasource:
    username: test           # tài khoản SQL Server đã dùng ở bước 2
    password: "123456"
app:
  admin:
    password: "Admin@123"    # mật khẩu tài khoản quản trị tạo tự động ở lần chạy đầu
```

Mọi giá trị trong `application.yml` đều đọc được từ biến môi trường, nên máy khác không cần file local:

| Biến môi trường | Mặc định | Ý nghĩa |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | `localhost,1433` / `sa` / trống | Kết nối CSDL `BadminStore` |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | `admin@badstore.local` / trống | Tài khoản quản trị tạo tự động (bỏ trống ⇒ không tạo) |
| `UPLOAD_DIR` | `uploads` | Thư mục lưu ảnh sản phẩm do admin tải lên |
| `SHOP_NAME`, `SHOP_ADDRESS`, `SHOP_HOTLINE`, `SHOP_EMAIL` | BadStore… | Thông tin in trên phiếu bán tại quầy + chân trang |
| `GHN_ENABLED`, `GHN_MOCK`, `GHN_MOCK_STATUS` | `true` / `true` / `delivering` | Bật tích hợp GHN và chế độ ngoại tuyến: `mock=true` dùng **dữ liệu GHN lưu sẵn** (địa chỉ đầy đủ, không cần mạng/token; phí ship + mã vận đơn là số giả) |
| `GHN_TOKEN` | trống | Token GHN – **chỉ cần token là lấy được địa chỉ** Tỉnh → Huyện → Xã (khi `GHN_MOCK=false`) |
| `GHN_SHOP_ID` | trống | Mã shop GHN – chỉ cần khi **tính phí/tạo vận đơn** GHN |
| `GHN_BASE_URL` | online-gateway | `https://online-gateway.ghn.vn/shiip/public-api` (đổi sang `dev-online-gateway` khi dùng môi trường thử nghiệm GHN) |
| `GHN_FROM_DISTRICT_ID`, `GHN_FROM_WARD_CODE`, `GHN_FROM_PHONE` | trống / trống / `0900000000` | Kho lấy hàng của shop (cần khi tính phí/tạo vận đơn) |
| `GHN_SYNC_ENABLED` | `false` | Scheduler tự đồng bộ trạng thái vận đơn định kỳ |

> **Lấy địa chỉ thật từ GHN** (chỉ cần token): đặt `GHN_MOCK=false` + `GHN_TOKEN=<token của shop>`
> ⇒ sổ địa chỉ và bước thanh toán có dropdown **Tỉnh → Huyện → Xã** lấy trực tiếp từ GHN.
> Chưa khai báo `GHN_SHOP_ID` / `GHN_FROM_DISTRICT_ID` thì phí ship vẫn dùng mức mặc định 30.000 ₫.
>
> Khi **chưa có token** vẫn demo được đầy đủ: đặt `app.ghn.enabled=true` + `app.ghn.mock=true`
> ⇒ dropdown **Tỉnh–Huyện–Xã vẫn đầy đủ 63 tỉnh/thành** (dữ liệu GHN lưu sẵn trong
> `src/main/resources/ghn/*.json`, mã thật nên đổi sang GHN thật không phải nhập lại địa chỉ),
> chỉ có phí vận chuyển, mã vận đơn `GHNMOCK…` và trạng thái giao hàng là số giả.
> Cần cập nhật lại dữ liệu địa chỉ: chạy `docs/ghn-fetch-address.mjs` (Node 18+) với biến môi trường
> `GHN_TOKEN` rồi chép 3 file JSON vào `src/main/resources/ghn/`.
> GHN tắt hoặc lỗi mạng thì trang địa chỉ/thanh toán **không vỡ**: form tự chuyển về nhập tay, phí ship dùng mức 30.000 ₫.

## 4. Chạy ứng dụng

```bat
.\mvnw spring-boot:run
```

hoặc đóng gói rồi chạy:

```bat
.\mvnw -B -DskipTests package
java -jar target\demo-0.0.1-SNAPSHOT.jar
```

Mở http://localhost:8088 (đổi cổng bằng `--server.port=...`)

> Profile `local` (đọc `application-local.yml`) được nạp **tự động** nhờ `spring.profiles.default=local`,
> nên không cần thêm tham số; nếu muốn chỉ rõ thì dùng `-Dspring-boot.run.profiles=local`
> hoặc `--spring.profiles.active=local`.

> Muốn demo phần **GHN** thì thêm cờ khi chạy (không cần token thật):
> `--app.ghn.enabled=true --app.ghn.mock=true --app.ghn.from-district-id=1489 --app.ghn.mock-status=delivered`
> (`1489` = Quận Hoàn Kiếm, Hà Nội – mã thật của GHN; muốn gọi API thật thì đặt `--app.ghn.mock=false --app.ghn.token=<token>`)
> (với `spring-boot:run` thì để trong `-Dspring-boot.run.arguments="..."`; nếu cổng 8088 đang bận, thêm `--server.port=8089`)

| Vai trò | Đăng nhập |
|---|---|
| Quản trị | `admin@badstore.local` / `Admin@123` (tạo tự động khi khởi động lần đầu) |
| Khách hàng | tự đăng ký ở `/dang-ky` |

## 5. Chạy test

```bat
.\mvnw -B clean test
```

Hiện tại: **280 test – 0 lỗi** (unit test Service bằng JUnit 5 + Mockito, test tích hợp MockMvc chạy trên CSDL thật). Trong đó 44 test của 2 chức năng làm thêm: **bán hàng tại quầy** (10 test) và **tích hợp GHN** (34 test: địa chỉ, phí vận chuyển, vận đơn, đồng bộ trạng thái).

## 6. Các chức năng chính để demo

**Khách hàng**
- Trang chủ (slider Swiper, hiệu ứng AOS), danh mục: tìm theo tên + lọc hãng/loại/khoảng giá, 5 kiểu sắp xếp, phân trang.
- Chi tiết sản phẩm: chọn biến thể (màu × trọng lượng × cỡ cán), giá gạch ngang, tồn kho theo biến thể, nút **So sánh**, khối **Đánh giá** (điểm trung bình + bình luận).
- **So sánh sản phẩm** `/so-sanh`: thêm tối đa 4 cây, bảng so sánh chiều dài – điểm cân bằng – độ cứng thân – mức căng dây – chất liệu – giá – tồn kho.
- **Đánh giá sản phẩm** (chỉ khách đã mua và đã nhận hàng, mỗi lượt mua 1 lần) – gửi bằng SweetAlert2 xác nhận rồi hiện flash.
- Giỏ hàng → thanh toán (COD / chuyển khoản), sổ địa chỉ, voucher, phí ship 30.000 ₫ (miễn phí từ 5.000.000 ₫). Khi bật GHN: sổ địa chỉ và bước thanh toán có **dropdown Tỉnh → Huyện → Xã đầy đủ 63 tỉnh/thành** (mã thật của GHN, chọn tới đâu tạm tính phí tới đó), vẫn nhập tay được nếu GHN tắt/lỗi.
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
8. **GHN là tuỳ chọn**: token GHN chỉ nằm ở server (trình duyệt gọi `/api/ghn/*` nội bộ), mã Tỉnh/Huyện/Xã khách gửi lên được **kiểm tra lại với GHN** (mã sai hoặc lệch tên địa danh bị bỏ, không lưu) nên không thể sửa mã để ăn phí ship rẻ; phí ship luôn tính lại ở server khi đặt hàng.

## 8. Ghi chú kỹ thuật

- `spring.jpa.hibernate.ddl-auto=validate` và `spring.jpa.open-in-view=false`: muốn đổi schema phải sửa `docs/BadStore.sql` (đã thống nhất với GV), các truy vấn hiển thị đều dùng fetch join.
- Thymeleaf 3.1 chặn truy cập static `T(com.example...)` trong template ⇒ các nhãn hiển thị được tính ở entity/DTO.
- Thư mục `uploads/` chứa ảnh do admin tải lên (đã nằm trong `.gitignore`).
