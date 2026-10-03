import type { NextRequest } from "next/server";

import { parseBaseDomains, slugFromHost } from "@/lib/tenant";
import {
  backendBaseUrl,
  downstreamResponseHeaders,
  upstreamPath,
  upstreamRequestHeaders,
} from "@/lib/upstream";

/**
 * Same-origin proxy: browser -> `<slug>.localhost:3000/api/v1/...` -> backend.
 *
 * Local development has no real subdomains at the backend, so the tenant travels in the
 * `X-Tenant-Slug` header, which only the backend's `local` profile honours. The slug always
 * comes from the Host the browser connected to, never from the client. In production the load
 * balancer routes `/api/*` straight to the backend and the backend reads the real Host.
 *
 * Server-side configuration only (never NEXT_PUBLIC_*): SC_BACKEND_URL, SC_TENANT_BASE_DOMAINS.
 */

export const dynamic = "force-dynamic";

/** Matches the backend's multipart limit (2 MB) plus multipart framing. */
const MAX_BODY_BYTES = 2 * 1024 * 1024 + 64 * 1024;

const BACKEND = backendBaseUrl(process.env.SC_BACKEND_URL);
const BASE_DOMAINS = parseBaseDomains(process.env.SC_TENANT_BASE_DOMAINS);

function json(status: number, error: string): Response {
  return Response.json({ error }, { status, headers: { "cache-control": "no-store" } });
}

async function forward(request: NextRequest): Promise<Response> {
  const path = upstreamPath(request.nextUrl.pathname);
  if (path === null) {
    return json(404, "not_found");
  }
  const slug = slugFromHost(request.headers.get("host"), BASE_DOMAINS);

  let body: ArrayBuffer | undefined;
  if (request.method !== "GET" && request.method !== "HEAD") {
    const declared = Number(request.headers.get("content-length") ?? "0");
    if (declared > MAX_BODY_BYTES) {
      return json(413, "upload_too_large");
    }
    body = await request.arrayBuffer();
    if (body.byteLength > MAX_BODY_BYTES) {
      return json(413, "upload_too_large");
    }
  }

  let upstream: Response;
  try {
    upstream = await fetch(BACKEND + path + request.nextUrl.search, {
      method: request.method,
      headers: upstreamRequestHeaders(request.headers, slug),
      body,
      redirect: "manual",
      cache: "no-store",
    });
  } catch {
    return json(502, "backend_unavailable");
  }

  return new Response(upstream.body, {
    status: upstream.status,
    headers: downstreamResponseHeaders(upstream.headers),
  });
}

export const GET = forward;
export const POST = forward;
export const PUT = forward;
export const PATCH = forward;
export const DELETE = forward;
