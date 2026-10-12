// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2

let csrf = null;
/** 身份改变清空CSRF；不保存密码或分享凭据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function resetApi() {
  csrf = null;
}
/** 同源写入带CSRF，不自动重试写操作，网络中断明示结果未知。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export async function api(path, options = {}) {
  const method = options.method || "GET";
  const write = !["GET", "HEAD"].includes(method);
  if (write && !csrf) {
    const r = await fetch("/api/auth/csrf", {
      credentials: "same-origin",
      cache: "no-store",
    });
    if (!r.ok)
      throw Object.assign(new Error("UNAUTHENTICATED"), { status: r.status });
    csrf = await r.json();
  }
  const headers = { ...options.headers };
  if (write) headers[csrf.header] = csrf.token;
  let body = options.body;
  if (body != null && !(body instanceof FormData)) {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify(body);
  }
  let r;
  try {
    r = await fetch("/api" + path, {
      ...options,
      method,
      headers,
      body,
      credentials: "same-origin",
      cache: "no-store",
    });
  } catch {
    throw Object.assign(new Error(write ? "RESULT_UNKNOWN" : "NETWORK_ERROR"), {
      status: 0,
    });
  }
  if (!r.ok) {
    let b;
    try {
      b = await r.json();
    } catch {
      b = { code: "HTTP_ERROR" };
    }
    // CSRF runs before authentication; an expired session can initially return403.
    // Read identity to distinguish expiry from a real denial; never replay the write.
    if (r.status === 403 && b.code === "FORBIDDEN") {
      let expired = false;
      try {
        const identity = await fetch("/api/auth/me", {
          credentials: "same-origin",
          cache: "no-store",
        });
        expired = identity.status === 401;
      } catch {
        /* Original request was rejected; preserve its error if the probe fails. */
      }
      if (expired) {
        resetApi();
        throw Object.assign(new Error("UNAUTHENTICATED"), { status: 401 });
      }
    }
    throw Object.assign(new Error(b.code || "HTTP_ERROR"), {
      status: r.status,
      row: b.row,
    });
  }
  if (r.status === 204) return null;
  return r.json();
}
/** 导出先HEAD鉴权再交给浏览器原生下载，不自动重放失败请求。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export async function download(path, name) {
  const r = await fetch("/api" + path, {
    method: "HEAD",
    credentials: "same-origin",
    cache: "no-store",
  });
  if (!r.ok) {
    throw Object.assign(
      new Error(
        r.status === 401
          ? "UNAUTHENTICATED"
          : r.status === 403
            ? "FORBIDDEN"
            : r.status === 409
              ? "DOWNLOAD_LOCKED"
              : "DOWNLOAD_FAILED",
      ),
      { status: r.status },
    );
  }
  const a = document.createElement("a");
  a.href = "/api" + path;
  a.download = name;
  a.rel = "noopener";
  document.body.appendChild(a);
  a.click();
  a.remove();
}
