import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { createApi, CSRF_HEADER, readCookie, type ApiDeps } from "../src/lib/api";
import { ApiError } from "../src/lib/errors";

interface Call {
  url: string;
  init: RequestInit;
}

function jsonResponse(status: number, body: unknown): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: body === undefined ? {} : { "content-type": "application/json" },
  });
}

/** A fake backend: scripted responses in order, every call recorded. */
function harness(responses: Response[], cookies = "") {
  const calls: Call[] = [];
  let unauthorized = 0;
  const state = { cookies };
  const deps: ApiDeps = {
    fetch: async (url, init) => {
      calls.push({ url, init });
      const next = responses.shift();
      if (!next) {
        throw new Error(`unexpected request ${init.method} ${url}`);
      }
      return next;
    },
    getCookies: () => state.cookies,
    onUnauthorized: () => {
      unauthorized++;
    },
  };
  return { api: createApi(deps), calls, state, unauthorizedCount: () => unauthorized };
}

function header(call: Call | undefined, name: string): string | undefined {
  return (call?.init.headers as Record<string, string> | undefined)?.[name];
}

describe("readCookie", () => {
  it("finds a cookie among others and decodes it", () => {
    assert.equal(readCookie("a=1; XSRF-TOKEN=abc%3D; b=2", "XSRF-TOKEN"), "abc=");
  });
  it("does not match a cookie whose name merely ends with the name", () => {
    assert.equal(readCookie("NOT-XSRF-TOKEN=evil", "XSRF-TOKEN"), null);
  });
  it("returns null for a missing or empty cookie", () => {
    assert.equal(readCookie("", "XSRF-TOKEN"), null);
    assert.equal(readCookie("XSRF-TOKEN=", "XSRF-TOKEN"), null);
  });
});

describe("CSRF header attachment", () => {
  it("sends the XSRF-TOKEN cookie value as X-XSRF-TOKEN on writes", async () => {
    const h = harness([jsonResponse(201, { id: 1 })], "XSRF-TOKEN=tok-1");
    await h.api.request("/devotees", { method: "POST", json: { fullName: "A" } });
    assert.equal(h.calls.length, 1);
    assert.equal(h.calls[0]?.url, "/api/v1/devotees");
    assert.equal(header(h.calls[0], CSRF_HEADER), "tok-1");
    assert.equal(header(h.calls[0], "Content-Type"), "application/json");
    assert.equal(h.calls[0]?.init.credentials, "same-origin");
  });

  it("never sends the CSRF header on GET", async () => {
    const h = harness([jsonResponse(200, { ok: true })], "XSRF-TOKEN=tok-1");
    await h.api.get("/me");
    assert.equal(header(h.calls[0], CSRF_HEADER), undefined);
  });

  it("fetches a token first when there is no cookie yet", async () => {
    const h = harness([jsonResponse(200, { token: "fresh", headerName: CSRF_HEADER }), jsonResponse(204, undefined)]);
    await h.api.request("/auth/setup", { method: "POST", json: { token: "t", password: "p" } });
    assert.equal(h.calls[0]?.url, "/api/v1/csrf");
    assert.equal(h.calls[0]?.init.method, "GET");
    assert.equal(header(h.calls[1], CSRF_HEADER), "fresh");
  });

  it("retries a 403 write once with a fresh token (rotated after login)", async () => {
    const h = harness(
      [jsonResponse(403, { error: "forbidden" }), jsonResponse(200, { token: "rotated" }), jsonResponse(200, { id: 7 })],
      "XSRF-TOKEN=stale",
    );
    const res = await h.api.request<{ id: number }>("/users/7/deactivate", { method: "POST" });
    assert.deepEqual(res, { id: 7 });
    assert.equal(header(h.calls[0], CSRF_HEADER), "stale");
    assert.equal(h.calls[1]?.url, "/api/v1/csrf");
    assert.equal(header(h.calls[2], CSRF_HEADER), "rotated");
  });

  it("gives up after one retry: a real authorization 403 surfaces as an error", async () => {
    const h = harness(
      [jsonResponse(403, null), jsonResponse(200, { token: "t2" }), jsonResponse(403, { error: "forbidden" })],
      "XSRF-TOKEN=t1",
    );
    await assert.rejects(h.api.request("/devotees/1", { method: "DELETE" }), (e: unknown) => {
      assert.ok(e instanceof ApiError);
      assert.equal(e.status, 403);
      return true;
    });
    assert.equal(h.calls.length, 3);
  });

  it("sends login as a form body", async () => {
    const h = harness([jsonResponse(200, { userId: 1 })], "XSRF-TOKEN=t");
    await h.api.request("/auth/login", { method: "POST", form: { email: "a@b.org", password: "p w&x" }, allowUnauthorized: true });
    assert.equal(header(h.calls[0], "Content-Type"), "application/x-www-form-urlencoded");
    assert.equal(h.calls[0]?.init.body, "email=a%40b.org&password=p+w%26x");
  });
});

describe("401 handling", () => {
  it("calls onUnauthorized and throws", async () => {
    const h = harness([jsonResponse(401, { status: 401, error: "Unauthorized" })]);
    await assert.rejects(h.api.get("/me"), ApiError);
    assert.equal(h.unauthorizedCount(), 1);
  });

  it("does not redirect when the caller handles 401 itself (login form)", async () => {
    const h = harness([jsonResponse(401, { error: "invalid_credentials" })], "XSRF-TOKEN=t");
    await assert.rejects(
      h.api.request("/auth/login", { method: "POST", form: { email: "x", password: "y" }, allowUnauthorized: true }),
      (e: unknown) => e instanceof ApiError && e.code === "invalid_credentials",
    );
    assert.equal(h.unauthorizedCount(), 0);
  });
});

describe("paths", () => {
  it("refuses absolute or protocol-relative paths", async () => {
    const h = harness([]);
    await assert.rejects(h.api.get("//evil.example/x"));
    await assert.rejects(h.api.get("https://evil.example/x"));
    assert.equal(h.calls.length, 0);
  });
});
