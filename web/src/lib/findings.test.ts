import { describe, expect, it } from "vitest";
import type { Finding } from "./api";
import { groupFindings, lineUrl } from "./findings";

const f = (category: string, primary: boolean, line = 1): Finding =>
  ({ category, ruleId: "r", filePath: "a.js", line, snippet: "x", primary });

describe("groupFindings", () => {
  it("정해진 범주 순서로, 빈 범주는 빼고, 주요와 참고를 나눈다", () => {
    const groups = groupFindings([f("NETWORK", true), f("SHELL_EXEC", false), f("SHELL_EXEC", true), f("NEW_ONE", true)]);
    expect(groups.map((g) => g.category)).toEqual(["SHELL_EXEC", "NETWORK", "NEW_ONE"]);
    expect(groups[0].primary).toHaveLength(1);
    expect(groups[0].secondary).toHaveLength(1);
  });
});

describe("lineUrl", () => {
  it("커밋에 고정된 GitHub 줄 링크, 경로는 조각마다 인코딩", () => {
    const finding = { ...f("NETWORK", true, 12), filePath: "dir/my file.md" };
    expect(lineUrl({ owner: "a", name: "b", commitSha: "abc" }, finding))
      .toBe("https://github.com/a/b/blob/abc/dir/my%20file.md#L12");
  });
});
