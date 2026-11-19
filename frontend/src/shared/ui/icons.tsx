/**
 * Bộ icon của ứng dụng — Lucide, nét 1.5, mặc định 16px.
 *
 * Component gọi `IconX` của file này, KHÔNG import thẳng từ `lucide-react`: một chỗ duy nhất quyết
 * định nét, cỡ mặc định và thuộc tính truy cập, nên đổi bộ icon về sau không phải sửa hàng chục
 * file. Tên `IconX` giữ nguyên từ bộ SVG tự vẽ trước đây (thay ngày 19/11/2026).
 *
 * Mọi icon đều `aria-hidden`: chúng đi kèm chữ, không thay chữ. Nhờ vậy tên truy cập của nút và
 * liên kết vẫn là đúng chữ hiển thị (test tìm theo vai trò + tên không đổi), và trình đọc màn hình
 * không đọc ra những thứ như "graphic". Icon đứng MỘT MÌNH thì phần tử chứa nó phải có `aria-label`.
 */

import {
  ArrowLeft,
  ArrowRight,
  ChartColumn,
  Check,
  ChevronDown,
  ChevronRight,
  CircleAlert,
  CodeXml,
  Copy,
  Download,
  FileCode,
  Folder,
  Inbox,
  Info,
  LogOut,
  MessageSquare,
  Package,
  Play,
  RefreshCw,
  Search,
  Settings,
  ShieldCheck,
  UserCog,
  TriangleAlert,
  Upload,
  X,
} from 'lucide-react';

import type { LucideIcon, LucideProps } from 'lucide-react';

type IconProps = LucideProps;

/** Nét, cỡ mặc định và thuộc tính truy cập — áp cho mọi icon bên dưới. */
function AppIcon({
  icon: Lucide,
  className = 'size-4',
  strokeWidth = 1.5,
  ...props
}: IconProps & { icon: LucideIcon }) {
  return (
    <Lucide
      aria-hidden="true"
      focusable="false"
      strokeWidth={strokeWidth}
      className={`shrink-0 ${className}`}
      {...props}
    />
  );
}

export function IconShield(props: IconProps) {
  return <AppIcon icon={UserCog} {...props} />;
}

/** Logo VSleuth: khiên + dấu tích — "cảnh báo đã được kiểm bằng chứng". */
export function IconLogo(props: IconProps) {
  return <AppIcon icon={ShieldCheck} {...props} />;
}

export function IconArrowLeft(props: IconProps) {
  return <AppIcon icon={ArrowLeft} {...props} />;
}

export function IconArrowRight(props: IconProps) {
  return <AppIcon icon={ArrowRight} {...props} />;
}

export function IconChevronDown(props: IconProps) {
  return <AppIcon icon={ChevronDown} {...props} />;
}

export function IconChevronRight(props: IconProps) {
  return <AppIcon icon={ChevronRight} {...props} />;
}

export function IconSettings(props: IconProps) {
  return <AppIcon icon={Settings} {...props} />;
}

export function IconLogout(props: IconProps) {
  return <AppIcon icon={LogOut} {...props} />;
}

export function IconFolder(props: IconProps) {
  return <AppIcon icon={Folder} {...props} />;
}

export function IconFile(props: IconProps) {
  return <AppIcon icon={FileCode} {...props} />;
}

export function IconUpload(props: IconProps) {
  return <AppIcon icon={Upload} {...props} />;
}

export function IconDownload(props: IconProps) {
  return <AppIcon icon={Download} {...props} />;
}

export function IconPlay(props: IconProps) {
  return <AppIcon icon={Play} {...props} />;
}

export function IconChart(props: IconProps) {
  return <AppIcon icon={ChartColumn} {...props} />;
}

export function IconChat(props: IconProps) {
  return <AppIcon icon={MessageSquare} {...props} />;
}

export function IconAlert(props: IconProps) {
  return <AppIcon icon={TriangleAlert} {...props} />;
}

export function IconError(props: IconProps) {
  return <AppIcon icon={CircleAlert} {...props} />;
}

export function IconInfo(props: IconProps) {
  return <AppIcon icon={Info} {...props} />;
}

export function IconCode(props: IconProps) {
  return <AppIcon icon={CodeXml} {...props} />;
}

export function IconCheck(props: IconProps) {
  return <AppIcon icon={Check} {...props} />;
}

export function IconBox(props: IconProps) {
  return <AppIcon icon={Package} {...props} />;
}

export function IconSearch(props: IconProps) {
  return <AppIcon icon={Search} {...props} />;
}

export function IconInbox(props: IconProps) {
  return <AppIcon icon={Inbox} {...props} />;
}

export function IconRetry(props: IconProps) {
  return <AppIcon icon={RefreshCw} {...props} />;
}

export function IconCopy(props: IconProps) {
  return <AppIcon icon={Copy} {...props} />;
}

export function IconClose(props: IconProps) {
  return <AppIcon icon={X} {...props} />;
}
