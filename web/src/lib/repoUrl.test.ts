import { describe, expect, it } from "vitest";
import { checkRepoUrl } from "./repoUrl";

describe("checkRepoUrl", () => {
  it.each([
    "https://github.com/anthropics/skills",
    "github.com/a/b",
    "https://www.github.com/a/b.git",
    "https://github.com/a/b/tree/main",
  ])("받는다: %s", (u) => {
    expect(checkRepoUrl(u)).toBeNull();
  });

  it("비었으면 안내", () => {
    expect(checkRepoUrl("   ")).toBe("저장소 주소를 넣어 주세요");
  });

  it.each(["https://gitlab.com/a/b", "https://github.com/a", "a/b"])("거절: %s", (u) => {
    expect(checkRepoUrl(u)).toBe("github.com/소유자/저장소 형태의 주소를 넣어 주세요");
  });
});
