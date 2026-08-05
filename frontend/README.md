# frontend

SPA tĩnh: đăng nhập, quản lý dự án, tải mã nguồn lên, duyệt cây file và xem mã nguồn theo dòng,
xem danh sách cảnh báo đã chuẩn hoá của một lần phân tích. Evidence Explorer và chat với AI Agent
sẽ thêm ở các tuần sau.

## Deploy độc lập

Frontend không phụ thuộc build-time vào backend. URL backend đọc **lúc chạy** qua
`window.__APP_CONFIG__` (xem `src/shared/config/runtime.ts`), nên **một image dùng được cho mọi
môi trường** — chỉ đổi biến `API_BASE_URL` của container.

Chi tiết và lý do: [ADR 0002](../docs/adr/0002-frontend-cau-hinh-luc-chay.md).

## Cấu trúc

```
src/
├── app/            Routing (routes.tsx), providers, layout, error boundary
├── features/       Tổ chức theo TÍNH NĂNG, không theo loại file
│   ├── auth/       { api/ components/ context/ hooks/ pages/ }
│   ├── projects/   { components/ hooks/ pages/ }
│   ├── snapshots/  { api/ components/ hooks/ layouts/ lib/ pages/ }
│   ├── warnings/   { api/ components/ hooks/ pages/ }
│   └── system/     { components/ hooks/ }
├── shared/
│   ├── ui/         Thuần trình bày — không gọi API, không biết nghiệp vụ
│   ├── lib/        apiClient, uploadClient, tokenStorage, queryClient, kiểu sinh từ OpenAPI
│   └── config/     Đọc cấu hình runtime
└── test/           Setup và helper dùng chung cho test
```

Quy ước:

- Logic lấy dữ liệu nằm trong custom hook, không nằm trong JSX.
- Không component nào gọi `fetch` trực tiếp — dùng `apiClient`. Ngoại lệ **duy nhất** là
  `uploadClient` (dùng `XMLHttpRequest` vì `fetch` không có sự kiện tiến trình upload), và nó phải
  dùng lại helper export từ `apiClient` thay vì chép logic.
- Mọi trạng thái bất đồng bộ phải render đủ loading / error / empty.
- Component vượt ~150 dòng là tín hiệu cần tách.
- **Không `dangerouslySetInnerHTML` cho dữ liệu bắt nguồn từ file upload** — source viewer render
  nội dung do người dùng tải lên, đây là bề mặt XSS thật (ADR 0006).

## Định tuyến và xác thực

Dùng data router (`createBrowserRouter`) nhưng **không dùng `loader`/`action`**: mọi request đi qua
`apiClient` + TanStack Query, thêm `loader` là dựng tầng cache thứ hai chạy song song. Lý do đầy đủ
ở đầu `src/app/routes.tsx`.

Access token giữ trong bộ nhớ, refresh token trong `localStorage` (`shared/lib/tokenStorage.ts` là
nơi duy nhất chạm `localStorage`). `apiClient` tự làm mới token khi gặp 401, gom mọi lời gọi refresh
đang bay về **một** promise — không phải tối ưu mà là bắt buộc, vì backend phát hiện tái sử dụng
refresh token và sẽ thu hồi cả phiên nếu bị gọi nhiều lần với cùng token.

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
npm run typecheck
npm test
npm run build      # bao gồm kiểm tra kiểu
```

Test dùng **happy-dom**, không phải jsdom: trên máy phát triển Windows, nạp jsdom mất ~11 giây mỗi
lần khiến mọi worker của Vitest vượt ngưỡng khởi động 60 giây và toàn bộ test đỏ với thông điệp
không hề nhắc tới jsdom.

`vite.config.ts` còn đặt `maxWorkers: '50%'`, và đó **không phải tinh chỉnh hiệu năng**: mặc định
Vitest mở ~(số core − 1) tiến trình, đủ nhiều để tất cả cùng vượt ngưỡng 60 giây và cho ra **đúng
thông điệp lỗi mà happy-dom đã dựng lên để tránh**. Cách phân biệt hai nguyên nhân: chạy lại với
`npx vitest run --maxWorkers=2` — xanh nghĩa là lỗi tải, vẫn đỏ nghĩa là lỗi environment. Chi tiết
và số đo trong `CLAUDE.md` ở gốc repo.

## Sinh lại kiểu dữ liệu API

```bash
npm run api:generate        # đọc ../docs/api/openapi.json đã commit — KHÔNG cần backend chạy
npm run api:generate:live   # đọc từ backend đang chạy, dùng khi phát triển
```

Trước khi push **bắt buộc** chạy `npm run api:generate` (bản đọc từ file đã commit): CI chạy lệnh
này rồi `git diff --exit-code`, nên spec và kiểu TypeScript không thể lệch nhau mà không bị phát
hiện. Vì vậy TypeScript giữ ở 5.9 — `openapi-typescript` 7.x yêu cầu peer `typescript@^5.x`.
