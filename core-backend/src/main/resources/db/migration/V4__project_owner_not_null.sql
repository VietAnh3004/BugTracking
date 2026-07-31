-- Backfill project.owner_id (nullable từ V3) rồi ép NOT NULL, đúng lúc ProjectService bắt buộc
-- nhận ownerId ở mọi lối vào (tuần 5-7, bước gán chủ sở hữu) — xem CLAUDE.md/ke-hoach-20-tuan.md
-- §G để biết vì sao việc này tách khỏi V3 thay vì làm chung một bước.

-- Hàng project tạo trước khi có auth (tuần 3-4, dữ liệu test/dev qua TestFixtures) không có chủ.
-- Tạo một tài khoản mồ côi VÔ HIỆU HOÁ, password_hash không phải định dạng BCrypt hợp lệ (không
-- đăng nhập được dù có cố tình đoán), enabled=false chặn thêm một lớp nữa. Không có credential
-- thật nào bị commit.
DO $$
DECLARE orphan UUID;
BEGIN
    IF EXISTS (SELECT 1 FROM project WHERE owner_id IS NULL) THEN
        INSERT INTO app_user (id, email, password_hash, display_name, role, enabled)
        VALUES (gen_random_uuid(), 'orphan@invalid.local', '!', 'Du lieu truoc khi co tai khoan', 'USER', false)
        RETURNING id INTO orphan;
        UPDATE project SET owner_id = orphan WHERE owner_id IS NULL;
    END IF;
END $$;

-- ddl-auto: validate KHÔNG kiểm tra nullability — để nullable ở DB sau bước này là mở cửa cho
-- một bug service tạo ra project vô chủ mà không ai biết cho tới khi có người truy vấn kỳ lạ.
ALTER TABLE project ALTER COLUMN owner_id SET NOT NULL;
