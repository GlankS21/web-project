import { ButtonLink, EmptyState } from "@/components/ui";

export default function NotFound() {
  return (
    <div className="container-page py-16">
      <EmptyState title="Không tìm thấy trang hoặc dữ liệu bạn yêu cầu">
        <p className="text-sm text-slate-500">Đường dẫn có thể đã thay đổi hoặc sản phẩm không còn được bán.</p>
        <ButtonLink href="/">Về trang chủ</ButtonLink>
      </EmptyState>
    </div>
  );
}
