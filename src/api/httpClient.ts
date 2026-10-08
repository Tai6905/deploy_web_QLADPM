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
    const token = readCookie("XSRF-TOKEN");
    if (token) headers.set("X-XSRF-TOKEN", token);
  }

  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...init,
    method,
    headers,
    credentials: "include",
  });

  if (!response.ok) {
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
        : typeof details === "string" && details
          ? details
          : `Yêu cầu API thất bại (${response.status})`;
    throw new ApiError(message, response.status, details);
  }

  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}
