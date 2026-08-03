/** Router thật của ứng dụng. Bảng route nằm ở `routes.tsx` để test dùng lại được (xem Javadoc ở đó). */

import { createBrowserRouter } from 'react-router-dom';

import { routes } from '@/app/routes';

export const router = createBrowserRouter(routes);
