import { describe, expect, it } from "vitest";
import { reveal } from "./reveal";

describe("reveal", () => {
  it("방향 제어 문자를 보이는 표식으로 바꾼다", () => {
    expect(reveal("a\u202Eb")).toBe("a<U+202E>b");
  });

  it("여러 종류의 보이지 않는 문자를 모두 바꾼다", () => {
    expect(reveal("x\u200By\u2066z\uFEFF")).toBe("x<U+200B>y<U+2066>z<U+FEFF>");
  });

  it("일반 한글·영문·탭·공백은 그대로 둔다", () => {
    const s = "const 이름 = 1;\t // hello world";
    expect(reveal(s)).toBe(s);
  });
});
