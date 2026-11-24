import type { Status } from "./api";

const CATEGORY: Record<string, string> = {
  SHELL_EXEC: "셸 실행",
  FILE_ACCESS: "파일 접근",
  NETWORK: "외부 전송",
  SECRETS: "환경 변수·비밀",
  INSTALL_SCRIPT: "설치 스크립트",
  OBFUSCATION: "난독화",
};

const TOOL_KIND: Record<string, string> = {
  SKILL: "스킬",
  HOOK: "훅",
  MCP_SERVER: "MCP 서버",
  PLUGIN: "플러그인",
  GENERIC: "일반 저장소",
};

const STATUS: Record<Status, string> = {
  QUEUED: "대기 중",
  RUNNING: "분석 중",
  DONE: "완료",
  PARTIAL: "일부만 완료",
  FAILED: "실패",
};

const KST = new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short", timeZone: "Asia/Seoul" });

export const categoryLabel = (c: string): string => CATEGORY[c] ?? c;

export const toolKindLabels = (raw: string | null): string[] =>
  raw ? raw.split(",").filter(Boolean).map((k) => TOOL_KIND[k] ?? k) : [];

export const statusLabel = (s: Status): string => STATUS[s] ?? s;

export const isTerminal = (s: Status): boolean => s === "DONE" || s === "PARTIAL" || s === "FAILED";

export const formatKst = (iso: string): string => KST.format(new Date(iso));
