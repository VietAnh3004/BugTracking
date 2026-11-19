/**
 * Khối "hoặc → Đăng nhập bằng Google" dưới form đăng nhập/đăng ký. Tự ẩn hoàn toàn khi backend
 * chưa cấu hình Google (không có Client ID) hoặc chưa trả lời — không hiện một nút bấm vào sẽ lỗi.
 */

import { useNavigate } from 'react-router-dom';

import { GoogleSignInButton } from '@/features/auth/components/GoogleSignInButton';
import { useAuthConfig, useGoogleLogin } from '@/features/auth/hooks/useAccount';
import { ApiError } from '@/shared/lib/apiClient';

interface GoogleLoginSectionProps {
  text?: 'signin_with' | 'signup_with';
}

export function GoogleLoginSection({ text = 'signin_with' }: GoogleLoginSectionProps) {
  const config = useAuthConfig();
  const googleLogin = useGoogleLogin();
  const navigate = useNavigate();
  const clientId = config.data?.googleClientId;

  if (clientId === undefined || clientId === '') {
    return null;
  }

  return (
    <div className="mt-5 flex flex-col gap-3">
      <div className="text-ink-muted flex items-center gap-3 text-xs">
        <span className="bg-border h-px flex-1" />
        hoặc
        <span className="bg-border h-px flex-1" />
      </div>
      <GoogleSignInButton
        clientId={clientId}
        text={text}
        onCredential={(credential) =>
          googleLogin.mutate(credential, {
            onSuccess: () => void navigate('/', { replace: true }),
          })
        }
      />
      {googleLogin.isPending ? (
        <p className="text-ink-muted m-0 text-center text-sm" role="status">
          Đang đăng nhập bằng Google…
        </p>
      ) : null}
      {googleLogin.isError ? (
        <p
          className="border-danger/30 bg-danger/10 text-danger m-0 rounded-lg border px-3 py-2 text-sm"
          role="alert"
        >
          {describeGoogleError(googleLogin.error)}
        </p>
      ) : null}
    </div>
  );
}

function describeGoogleError(error: unknown): string {
  if (error instanceof ApiError) {
    return error.problem.detail ?? 'Đăng nhập Google thất bại.';
  }
  return 'Không gọi được backend. Kiểm tra kết nối mạng.';
}
