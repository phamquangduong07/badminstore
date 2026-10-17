/* =====================================================================
   BadStore - PATCH cho mức KHÁ (chạy SAU BadStore.sql)
   Bổ sung: đánh giá sản phẩm
   (So sánh sản phẩm dùng session -> không cần bảng)
   ===================================================================== */
USE BadminStore;
GO

/* ---------- Đánh giá sản phẩm ----------
   Mỗi dòng đơn hàng (order_item) chỉ được đánh giá 1 lần => đảm bảo "đã mua".
   Service kiểm tra thêm: đơn của user đó đang ở trạng thái DELIVERED. */
CREATE TABLE product_reviews (
    id            INT IDENTITY(1,1) PRIMARY KEY,
    product_id    INT           NOT NULL,
    user_id       INT           NOT NULL,
    order_item_id INT           NOT NULL,
    rating        TINYINT       NOT NULL,
    comment       NVARCHAR(1000) NULL,
    status        VARCHAR(20)   NOT NULL CONSTRAINT DF_reviews_status DEFAULT 'VISIBLE',
    created_at    DATETIME2     NOT NULL CONSTRAINT DF_reviews_created DEFAULT SYSDATETIME(),
    updated_at    DATETIME2     NOT NULL CONSTRAINT DF_reviews_updated DEFAULT SYSDATETIME(),
    CONSTRAINT FK_reviews_products    FOREIGN KEY (product_id)    REFERENCES products(id),
    CONSTRAINT FK_reviews_users       FOREIGN KEY (user_id)       REFERENCES users(id),
    CONSTRAINT FK_reviews_order_items FOREIGN KEY (order_item_id) REFERENCES order_items(id),
    CONSTRAINT UQ_reviews_order_item  UNIQUE (order_item_id),
    CONSTRAINT CK_reviews_rating      CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT CK_reviews_status      CHECK (status IN ('VISIBLE','HIDDEN'))
);
GO
CREATE INDEX IX_reviews_product ON product_reviews(product_id, status, created_at DESC);
GO
