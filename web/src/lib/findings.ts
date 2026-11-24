import type { Finding } from "./api";

// 설계 6-1 표의 범주 순서. 모르는 범주는 맨 뒤
const ORDER = ["SHELL_EXEC", "FILE_ACCESS", "NETWORK", "SECRETS", "INSTALL_SCRIPT", "OBFUSCATION"];

export type FindingGroup = { category: string; primary: Finding[]; secondary: Finding[] };

export function categoryRank(category: string): number {
  const i = ORDER.indexOf(category);
  return i < 0 ? ORDER.length : i;
}

export function groupFindings(findings: Finding[]): FindingGroup[] {
  const groups = new Map<string, FindingGroup>();
  for (const f of findings) {
    const g = groups.get(f.category) ?? { category: f.category, primary: [], secondary: [] };
    (f.primary ? g.primary : g.secondary).push(f);
    groups.set(f.category, g);
  }
  return [...groups.values()].sort((a, b) => categoryRank(a.category) - categoryRank(b.category));
}

/** 분석한 커밋에 고정된 링크라 저장소가 바뀌어도 같은 줄을 가리킨다. */
export function lineUrl(repo: { owner: string; name: string; commitSha: string }, f: Finding): string {
  const path = f.filePath.split("/").map(encodeURIComponent).join("/");
  return `https://github.com/${repo.owner}/${repo.name}/blob/${repo.commitSha}/${path}#L${f.line}`;
}
