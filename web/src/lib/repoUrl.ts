// 최종 판단은 서버(RepoRef.parse)가 한다. 여기선 흔한 실수만 요청 전에 알려 준다
const PATTERN = /^(https?:\/\/)?(www\.)?github\.com\/[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+/i;

export function checkRepoUrl(input: string): string | null {
  const s = input.trim();
  if (!s) {
    return "저장소 주소를 넣어 주세요";
  }
  return PATTERN.test(s) ? null : "github.com/소유자/저장소 형태의 주소를 넣어 주세요";
}
