export type Status = "QUEUED" | "RUNNING" | "DONE" | "PARTIAL" | "FAILED";

export type Finding = {
  category: string;
  ruleId: string;
  filePath: string;
  line: number;
  snippet: string;
  primary: boolean;
};

export type Permission = {
  truncated: boolean;
  fileCount: number;
  skippedFiles: number;
  domains: string[];
  domainsTruncated: boolean;
  findingsTruncated: boolean;
  primaryCounts: Record<string, number>;
  secondaryFindings: number;
  submodules: string[];
};

export type Analysis = {
  jobId: number;
  owner: string;
  name: string;
  commitSha: string;
  status: Status;
  toolKind: string | null;
  error: string | null;
  findings: Finding[];
  permission: Permission | null;
  analyzedAt: string | null;
  notice: string;
  disputeUrl: string | null;
};

export type Created = { jobId: number; owner: string; name: string; commitSha: string; status: Status };

type ApiResponse<T> = { success: boolean; data: T | null; error: string | null };

export const API_BASE = process.env.NEXT_PUBLIC_API_BASE ?? "http://localhost:8080";

export class ApiError extends Error {
  readonly status: number;
  readonly retryAfter: number | null;

  constructor(message: string, status: number, retryAfter: number | null) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.retryAfter = retryAfter;
  }
}

async function unwrap<T>(res: Response): Promise<T> {
  let body: ApiResponse<T> | null = null;
  try {
    body = (await res.json()) as ApiResponse<T>;
  } catch {
    // 프록시 오류 페이지처럼 JSON 이 아닌 본문
  }
  if (res.ok && body?.success && body.data != null) {
    return body.data;
  }
  const retryAfter = Number(res.headers.get("Retry-After"));
  throw new ApiError(body?.error ?? `서버 응답 오류(${res.status})`, res.status,
    Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter : null);
}

export async function createAnalysis(url: string): Promise<Created> {
  const res = await fetch(`${API_BASE}/api/analyses`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ url }),
  });
  return unwrap<Created>(res);
}

/** 없는 분석이면 null. 그 밖의 실패는 ApiError. */
export async function getAnalysis(id: string): Promise<Analysis | null> {
  const res = await fetch(`${API_BASE}/api/analyses/${encodeURIComponent(id)}`, { cache: "no-store" });
  if (res.status === 404) {
    return null;
  }
  return unwrap<Analysis>(res);
}

export function eventsUrl(jobId: number): string {
  return `${API_BASE}/api/analyses/${jobId}/events`;
}

export function errorMessage(err: unknown): string {
  if (err instanceof ApiError) {
    if (err.status === 429) {
      return err.retryAfter
        ? `요청이 많습니다. ${err.retryAfter}초 뒤 다시 시도해 주세요.`
        : "요청이 많습니다. 잠시 뒤 다시 시도해 주세요.";
    }
    return err.message;
  }
  return "서버에 닿지 못했습니다. 잠시 뒤 다시 시도해 주세요.";
}
