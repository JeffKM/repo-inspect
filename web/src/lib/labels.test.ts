import { describe, expect, it } from "vitest";
import { categoryLabel, formatKst, isTerminal, statusLabel, toolKindLabels } from "./labels";

describe("labels", () => {
  it("범주 이름, 모르는 범주는 그대로", () => {
    expect(categoryLabel("SHELL_EXEC")).toBe("셸 실행");
    expect(categoryLabel("NEW_ONE")).toBe("NEW_ONE");
  });

  it("도구 종류는 쉼표로 여럿", () => {
    expect(toolKindLabels("MCP_SERVER,SKILL")).toEqual(["MCP 서버", "스킬"]);
    expect(toolKindLabels(null)).toEqual([]);
  });

  it("끝난 상태", () => {
    expect(isTerminal("DONE")).toBe(true);
    expect(isTerminal("PARTIAL")).toBe(true);
    expect(isTerminal("FAILED")).toBe(true);
    expect(isTerminal("RUNNING")).toBe(false);
    expect(isTerminal("QUEUED")).toBe(false);
  });

  it("상태 이름", () => {
    expect(statusLabel("QUEUED")).toBe("대기 중");
    expect(statusLabel("PARTIAL")).toBe("일부만 완료");
  });

  it("시각은 한국 시간", () => {
    const s = formatKst("2026-11-24T08:22:45Z");
    expect(s).toContain("2026");
    expect(s).toContain("5:22");
  });
});
