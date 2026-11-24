import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, createAnalysis, errorMessage, getAnalysis } from "./api";

const json = (status: number, body: unknown, headers: Record<string, string> = {}) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json", ...headers } });

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("createAnalysis", () => {
  it("성공이면 data 를 돌려준다", async () => {
    const created = { jobId: 7, owner: "a", name: "b", commitSha: "c", status: "QUEUED" };
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json(202, { success: true, data: created, error: null })));
    await expect(createAnalysis("https://github.com/a/b")).resolves.toEqual(created);
  });

  it("429 는 Retry-After 를 담은 ApiError", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(
      json(429, { success: false, data: null, error: "요청이 너무 많습니다" }, { "Retry-After": "42" })));
    const err = await createAnalysis("https://github.com/a/b").catch((e: unknown) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).status).toBe(429);
    expect((err as ApiError).retryAfter).toBe(42);
  });

  it("본문이 JSON 이 아니면 상태 코드로 안내", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("<html>", { status: 502 })));
    await expect(createAnalysis("https://github.com/a/b")).rejects.toThrow("서버 응답 오류(502)");
  });
});

describe("getAnalysis", () => {
  it("404 면 null", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(
      json(404, { success: false, data: null, error: "분석을 찾을 수 없습니다" })));
    await expect(getAnalysis("9")).resolves.toBeNull();
  });
});

describe("errorMessage", () => {
  it("429 는 기다릴 시간을 알려 준다", () => {
    expect(errorMessage(new ApiError("x", 429, 30))).toBe("요청이 많습니다. 30초 뒤 다시 시도해 주세요.");
    expect(errorMessage(new ApiError("x", 429, null))).toBe("요청이 많습니다. 잠시 뒤 다시 시도해 주세요.");
  });

  it("그 밖의 ApiError 는 서버 안내 그대로", () => {
    expect(errorMessage(new ApiError("빈 저장소라 분석할 코드가 없습니다", 409, null)))
      .toBe("빈 저장소라 분석할 코드가 없습니다");
  });

  it("네트워크 오류", () => {
    expect(errorMessage(new TypeError("fetch failed"))).toBe("서버에 닿지 못했습니다. 잠시 뒤 다시 시도해 주세요.");
  });
});
