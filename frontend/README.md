# frontend

SPA tĩnh: quản lý dự án, danh sách cảnh báo, evidence explorer, chat với AI Agent và human review.

## Deploy độc lập

Frontend không phụ thuộc build-time vào backend. URL backend đọc **lúc chạy** qua
`window.__APP_CONFIG__` (xem `src/shared/config/runtime.ts`), nên **một image dùng được cho mọi
môi trường** — chỉ đổi biến `API_BASE_URL` của container.

Chi tiết và lý do: [ADR 0002](../docs/adr/0002-frontend-cau-hinh-luc-chay.md).

## Cấu trúc

```
src/
├── app/          Providers, routing, layout
├── features/     Tổ chức theo TÍNH NĂNG, không theo loại file
│   └── system/   { components/  hooks/ }
└── shared/
    ├── ui/       Thuần trình bày — không gọi API, không biết nghiệp vụ
    ├── lib/      apiClient, kiểu sinh từ OpenAPI
    └── config/   Đọc cấu hình runtime
```

Quy ước:

- Logic lấy dữ liệu nằm trong custom hook, không nằm trong JSX.
- Không component nào gọi `fetch` trực tiếp — dùng `apiClient`.
- Mọi trạng thái bất đồng bộ phải render đủ loading / error / empty.
- Component vượt ~150 dòng là tín hiệu cần tách.

## Chạy local

```bash
npm install
npm run dev        # http://localhost:5173
```

Dev gọi thẳng sang `http://localhost:8080`, **không qua proxy**. Đây là chủ ý: proxy sẽ che mất
mọi lỗi CORS cho tới lúc deploy tách domain.

## Kiểm tra chất lượng

```bash
npm run lint
npm run format:check
npm run build      # bao gồm kiểm tra kiểu
```

## Sinh lại kiểu dữ liệu API

```bash
npm run api:generate   # cần core-backend đang chạy
```

Kiểu dữ liệu API sinh từ OpenAPI spec của backend nên hai bên không thể lệch nhau mà không bị
phát hiện lúc biên dịch. Vì vậy TypeScript giữ ở 5.9: `openapi-typescript` 7.x yêu cầu peer
`typescript@^5.x`.
