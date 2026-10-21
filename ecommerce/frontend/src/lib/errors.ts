import type { ErrorResponse, FieldViolation } from "./types";

/** An error answered by the API (gateway or service), in the backend's ErrorResponse format. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly violations: FieldViolation[];
  readonly correlationId?: string;

  constructor(status: number, body: Partial<ErrorResponse> | null) {
    super(body?.message || `HTTP ${status}`);
    this.name = "ApiError";
    this.status = status;
    this.code = body?.code ?? "UNKNOWN";
    this.violations = body?.violations ?? [];
    this.correlationId = body?.correlationId;
  }

  /** Validation message of one request field, e.g. "shippingAddress.phoneNumber". */
  fieldError(field: string): string | undefined {
    return this.violations.find((violation) => violation.field === field)?.message;
  }
}

/**
 * Message shown to the user. Business-rule messages (422, 409) come from the services and are
 * specific ("Only 3 unit(s) of ... are available"); they are shown as they are. Other statuses get
 * a generic Vietnamese explanation.
 */
export function errorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return "Không kết nối được tới máy chủ. Vui lòng thử lại.";
  }
  switch (error.status) {
    case 400:
      return error.violations.length > 0
        ? "Vui lòng kiểm tra lại các trường được đánh dấu."
        : `Yêu cầu không hợp lệ: ${error.message}`;
    case 401:
      return "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.";
    case 403:
      return "Bạn không có quyền thực hiện thao tác này.";
    case 404:
      return "Không tìm thấy dữ liệu yêu cầu.";
    case 409:
    case 422:
      return error.message;
    case 429:
      return "Bạn thao tác quá nhanh, vui lòng thử lại sau ít phút.";
    case 503:
    case 504:
      return "Dịch vụ tạm thời gián đoạn, vui lòng thử lại sau.";
    default:
      return "Đã có lỗi xảy ra, vui lòng thử lại.";
  }
}
