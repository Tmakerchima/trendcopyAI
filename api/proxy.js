const hopByHopHeaders = new Set([
  "connection",
  "keep-alive",
  "proxy-authenticate",
  "proxy-authorization",
  "te",
  "trailer",
  "transfer-encoding",
  "upgrade",
  "content-encoding",
  "content-length",
]);

function backendBaseUrl() {
  const value = process.env.BACKEND_URL || process.env.RAILWAY_BACKEND_URL;
  if (!value) {
    throw new Error("Missing BACKEND_URL. Set it to the Railway backend URL.");
  }
  return value.replace(/\/+$/, "");
}

function normalizePath(value) {
  if (Array.isArray(value)) {
    return value.join("/");
  }
  return String(value || "").replace(/^\/+/, "");
}

function targetUrl(request) {
  const url = new URL(request.url, `https://${request.headers.host || "localhost"}`);
  const proxiedPath = normalizePath(url.searchParams.get("path"));
  url.searchParams.delete("path");
  const suffix = url.searchParams.toString();
  return `${backendBaseUrl()}/api/${proxiedPath}${suffix ? `?${suffix}` : ""}`;
}

function copyRequestHeaders(headers) {
  const copied = {};
  for (const [key, value] of Object.entries(headers)) {
    const normalized = key.toLowerCase();
    if (!hopByHopHeaders.has(normalized) && normalized !== "host") {
      copied[key] = value;
    }
  }
  return copied;
}

function copyResponseHeaders(headers) {
  const copied = {};
  headers.forEach((value, key) => {
    if (!hopByHopHeaders.has(key.toLowerCase()) && key.toLowerCase() !== "set-cookie") {
      copied[key] = value;
    }
  });
  return copied;
}

export default async function handler(request, response) {
  const hasBody = !["GET", "HEAD"].includes(request.method);
  const upstream = await fetch(targetUrl(request), {
    method: request.method,
    headers: copyRequestHeaders(request.headers),
    body: hasBody ? request : undefined,
    redirect: "manual",
    duplex: hasBody ? "half" : undefined,
  });

  response.status(upstream.status);
  for (const [key, value] of Object.entries(copyResponseHeaders(upstream.headers))) {
    response.setHeader(key, value);
  }
  if (typeof upstream.headers.getSetCookie === "function") {
    const cookies = upstream.headers.getSetCookie();
    if (cookies.length) response.setHeader("set-cookie", cookies);
  } else {
    const cookie = upstream.headers.get("set-cookie");
    if (cookie) response.setHeader("set-cookie", cookie);
  }

  response.send(Buffer.from(await upstream.arrayBuffer()));
}
