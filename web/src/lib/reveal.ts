// 분석 대상 저장소의 텍스트는 믿을 수 없어, 방향 제어·폭 없는 문자를 눈에 보이게 바꾼다
const INVISIBLE = /[\u061C\u200B-\u200F\u202A-\u202E\u2060-\u2069\uFEFF]/g;

export function reveal(text: string): string {
  return text.replace(INVISIBLE, (c) => `<U+${c.charCodeAt(0).toString(16).toUpperCase().padStart(4, "0")}>`);
}
