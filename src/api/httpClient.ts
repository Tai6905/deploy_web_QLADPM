export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly details?: unknown,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || "").replace(
  /\/$/,
  "",
);

let csrfToken: string | null = null;
let csrfRequest: Promise<void> | null = null;

async function ensureCsrfToken() {
  if (csrfToken) return;
  csrfRequest ??= apiRequest<{ csrfToken: string }>("/api/auth/session")
    .then(() => undefined)
    .finally(() => { csrfRequest = null; });
  await csrfRequest;
}

function readCookie(name: string) {
  const cookie = document.cookie
    .split("; ")
    .find((part) => part.startsWith(`${name}=`));
  return cookie ? decodeURIComponent(cookie.slice(name.length + 1)) : null;
}

export async function apiRequest<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const method = (init.method || "GET").toUpperCase();
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  if (init.body && !headers.has("Content-Type"))
    headers.set("Content-Type", "application/json");

  const isPublicAuthWrite =
    path.endsWith("/auth/login") || path.endsWith("/auth/register");
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method) && !isPublicAuthWrite) {
    await ensureCsrfToken();
    const token = csrfToken || readCookie("XSRF-TOKEN");
    if (token) headers.set("X-XSRF-TOKEN", token);
  }

  let response: Response;
  try {
    response = await fetch(`${apiBaseUrl}${path}`, {
      ...init,
      method,
      headers,
      credentials: "include",
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") throw error;
    throw new ApiError("Không kết nối được máy chủ. Kiểm tra backend và kết nối mạng.", 0);
  }

  if (!response.ok) {
    if (response.status === 401) {
      csrfToken = null;
      window.dispatchEvent(new Event("shopflow:session-expired"));
    }
    const responseText = await response.text();
    let details: unknown = responseText || null;
    try {
      details = responseText ? JSON.parse(responseText) : null;
    } catch {
      // The current auth controller returns plain text for failed logins.
    }
    const message =
      typeof details === "object" && details && "message" in details
        ? String(details.message)
        : typeof details === "object" && details && "detail" in details
          ? String(details.detail)
        : typeof details === "string" && details
          ? details
          : `Yêu cầu API thất bại (${response.status})`;
    throw new ApiError(message, response.status, details);
  }

  if (isPublicAuthWrite || path.endsWith("/auth/logout")) csrfToken = null;
  if (response.status === 204 || response.status === 205) return undefined as T;
  const text = await response.text();
  if (!text.trim()) return undefined as T;
  let result: T;
  try {
    result = JSON.parse(text) as T;
  } catch {
    throw new ApiError("Máy chủ trả về dữ liệu không hợp lệ. Kiểm tra địa chỉ API và proxy.", response.status);
  }
  if (result && typeof result === "object" && "csrfToken" in result && typeof result.csrfToken === "string") {
    csrfToken = result.csrfToken;
  }
  return result;
}
