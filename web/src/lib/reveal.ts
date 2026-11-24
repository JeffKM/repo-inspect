// 분석 대상 저장소의 텍스트는 믿을 수 없어, 방향 제어·폭 없는 문자를 눈에 보이게 바꾼다
const INVISIBLE = /[؜​-‏‪-‮⁠-⁩﻿]/g;

export function reveal(text: string): string {
  return text.replace(INVISIBLE, (c) => `<U+${c.charCodeAt(0).toString(16).toUpperCase().padStart(4, "0")}>`);
}
