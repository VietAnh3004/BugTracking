/**
 * Nút "Đăng nhập bằng Google" của Google Identity Services (GIS).
 *
 * Luồng: GIS tự mở cửa sổ chọn tài khoản rồi trả về một ID token (`credential`) — một JWT do
 * Google ký. Frontend KHÔNG đọc nội dung token đó; nó chuyển nguyên cho backend, nơi chữ ký,
 * `aud` (đúng Client ID của ứng dụng này) và hạn được kiểm. Tin vào token ở phía trình duyệt là
 * tin vào thứ người dùng sửa được.
 *
 * Script của Google chỉ được nạp khi backend có cấu hình Client ID: máy chưa bật Google thì không
 * có request nào ra `accounts.google.com`.
 *
 * Nút do chính Google vẽ (`renderButton`) chứ không tự vẽ: đó là điều kiện trong hướng dẫn thương
 * hiệu của Google, và nút tự vẽ không mở được luồng One Tap / FedCM của GIS.
 */

import { useEffect, useRef, useState } from 'react';

const GIS_SRC = 'https://accounts.google.com/gsi/client';

interface CredentialResponse {
  credential: string;
}

interface GoogleIdentityApi {
  accounts: {
    id: {
      initialize: (config: {
        client_id: string;
        callback: (response: CredentialResponse) => void;
        ux_mode?: 'popup' | 'redirect';
      }) => void;
      renderButton: (parent: HTMLElement, options: Record<string, string | number>) => void;
    };
  };
}

declare global {
  interface Window {
    google?: GoogleIdentityApi;
  }
}

let scriptPromise: Promise<void> | null = null;

/** Nạp script GIS đúng MỘT lần cho cả ứng dụng, dù có bao nhiêu nút. */
function loadGis(): Promise<void> {
  if (window.google !== undefined) {
    return Promise.resolve();
  }
  scriptPromise ??= new Promise<void>((resolve, reject) => {
    const script = document.createElement('script');
    script.src = GIS_SRC;
    script.async = true;
    script.onload = () => resolve();
    script.onerror = () => {
      scriptPromise = null;
      reject(new Error('Không tải được Google Identity Services'));
    };
    document.head.appendChild(script);
  });
  return scriptPromise;
}

interface GoogleSignInButtonProps {
  clientId: string;
  onCredential: (credential: string) => void;
  /** "Đăng nhập bằng Google" hay "Đăng ký bằng Google". */
  text?: 'signin_with' | 'signup_with';
}

export function GoogleSignInButton({
  clientId,
  onCredential,
  text = 'signin_with',
}: GoogleSignInButtonProps) {
  const container = useRef<HTMLDivElement>(null);
  const [loadFailed, setLoadFailed] = useState(false);
  // Callback đổi mỗi lần render; GIS chỉ nhận callback lúc initialize. Giữ bản mới nhất trong ref
  // để không phải initialize lại (và vẽ lại nút) sau mỗi lần render.
  const callback = useRef(onCredential);
  useEffect(() => {
    callback.current = onCredential;
  }, [onCredential]);

  useEffect(() => {
    let cancelled = false;
    loadGis()
      .then(() => {
        const google = window.google;
        if (cancelled || google === undefined || container.current === null) {
          return;
        }
        google.accounts.id.initialize({
          client_id: clientId,
          callback: (response) => callback.current(response.credential),
          ux_mode: 'popup',
        });
        google.accounts.id.renderButton(container.current, {
          type: 'standard',
          theme: 'outline',
          size: 'large',
          text,
          shape: 'rectangular',
          logo_alignment: 'center',
          width: container.current.offsetWidth || 320,
          locale: 'vi',
        });
      })
      .catch(() => {
        if (!cancelled) {
          setLoadFailed(true);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [clientId, text]);

  if (loadFailed) {
    return (
      <p className="text-ink-muted m-0 text-center text-xs">
        Không tải được nút đăng nhập Google (kiểm tra kết nối mạng hoặc trình chặn quảng cáo).
      </p>
    );
  }
  return <div ref={container} className="flex min-h-11 w-full justify-center" />;
}
