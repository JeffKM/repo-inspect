import type { Analysis, Finding } from "@/lib/api";
import { categoryRank, groupFindings, lineUrl } from "@/lib/findings";
import { categoryLabel, formatKst, isTerminal, statusLabel, toolKindLabels } from "@/lib/labels";
import { JobProgress } from "./JobProgress";

function FindingItems({ a, items }: { a: Analysis; items: Finding[] }) {
  return (
    <ul className="space-y-2">
      {items.map((f, i) => (
        <li key={`${f.filePath}:${f.line}:${f.ruleId}:${i}`} className="rounded border border-zinc-200 p-3">
          <div className="flex flex-wrap justify-between gap-2 text-sm">
            <a href={lineUrl(a, f)} className="font-mono break-all hover:underline">
              {f.filePath}:{f.line}
            </a>
            <span className="text-zinc-500">{f.ruleId}</span>
          </div>
          <pre className="mt-2 overflow-x-auto rounded bg-zinc-50 p-2 text-xs break-all whitespace-pre-wrap">
            <code>{f.snippet}</code>
          </pre>
        </li>
      ))}
    </ul>
  );
}

export function AnalysisResult({ a }: { a: Analysis }) {
  const repoUrl = `https://github.com/${a.owner}/${a.name}`;
  const p = a.permission;
  const kinds = toolKindLabels(a.toolKind);
  const groups = groupFindings(a.findings);
  const counts = p ? Object.entries(p.primaryCounts).sort(([x], [y]) => categoryRank(x) - categoryRank(y)) : [];

  return (
    <article className="space-y-8">
      <header className="space-y-2">
        <h1 className="text-2xl font-bold break-all">
          <a href={repoUrl} className="hover:underline">
            {a.owner}/{a.name}
          </a>
        </h1>
        <p className="text-sm text-zinc-600">
          커밋{" "}
          <a href={`${repoUrl}/tree/${a.commitSha}`} className="font-mono hover:underline">
            {a.commitSha.slice(0, 7)}
          </a>
          {a.analyzedAt && <> · {formatKst(a.analyzedAt)} 분석</>} · {statusLabel(a.status)}
        </p>
        {kinds.length > 0 && (
          <ul className="flex flex-wrap gap-2">
            {kinds.map((k) => (
              <li key={k} className="rounded bg-zinc-100 px-2 py-0.5 text-sm">
                {k}
              </li>
            ))}
          </ul>
        )}
      </header>

      <p className="rounded border border-amber-200 bg-amber-50 p-3 text-sm">{a.notice}</p>

      {!isTerminal(a.status) && <JobProgress jobId={a.jobId} initialStatus={a.status} />}

      {a.status === "FAILED" && (
        <p className="rounded border border-zinc-300 p-3 text-sm">
          분석하지 못했습니다{a.error ? `: ${a.error}` : ""}. 30분 뒤 같은 주소로 다시 요청하면 다시 분석합니다.
        </p>
      )}

      {p && (
        <section className="space-y-3">
          <h2 className="text-lg font-semibold">요약</h2>
          {counts.length === 0 ? (
            <p className="text-sm">본 코드·지시문·설정에서 찾은 신호가 없습니다.</p>
          ) : (
            <ul className="grid grid-cols-2 gap-2 sm:grid-cols-3">
              {counts.map(([c, n]) => (
                <li key={c} className="rounded border border-zinc-200 p-3">
                  <div className="text-sm text-zinc-600">{categoryLabel(c)}</div>
                  <div className="text-xl font-semibold">{n}</div>
                </li>
              ))}
            </ul>
          )}
          <ul className="list-disc space-y-1 pl-5 text-sm text-zinc-600">
            <li>
              읽은 파일 {p.fileCount}개{p.skippedFiles > 0 && `, 크기·바이너리로 건너뛴 파일 ${p.skippedFiles}개`}
            </li>
            {p.truncated && <li>저장소가 커서 앞부분만 분석했습니다.</li>}
            {p.findingsTruncated && <li>근거가 많아 일부만 저장했습니다.</li>}
            {p.secondaryFindings > 0 && (
              <li>테스트·예제·문서 폴더에서 나온 참고 근거 {p.secondaryFindings}개는 범주별 목록 아래에 접어 두었습니다.</li>
            )}
            {p.submodules.length > 0 && <li>하위 모듈은 분석하지 않았습니다: {p.submodules.join(", ")}</li>}
          </ul>
        </section>
      )}

      {p && p.domains.length > 0 && (
        <section className="space-y-2">
          <h2 className="text-lg font-semibold">
            부르는 도메인 {p.domains.length}개{p.domainsTruncated && " (일부)"}
          </h2>
          <ul className="flex flex-wrap gap-2 font-mono text-sm">
            {p.domains.map((d) => (
              <li key={d} className="rounded bg-zinc-100 px-2 py-0.5">
                {d}
              </li>
            ))}
          </ul>
        </section>
      )}

      {groups.map((g) => (
        <section key={g.category} className="space-y-2">
          <h2 className="text-lg font-semibold">
            {categoryLabel(g.category)} <span className="text-zinc-500">{g.primary.length}</span>
          </h2>
          {g.primary.length > 0 && <FindingItems a={a} items={g.primary} />}
          {g.secondary.length > 0 && (
            <details className="text-sm">
              <summary className="cursor-pointer text-zinc-600">참고 근거 {g.secondary.length}개</summary>
              <div className="mt-2">
                <FindingItems a={a} items={g.secondary} />
              </div>
            </details>
          )}
        </section>
      ))}

      {a.disputeUrl && (
        <footer className="border-t border-zinc-200 pt-4 text-sm text-zinc-600">
          저장소 주인이신가요? 결과에 이의가 있으면{" "}
          <a href={a.disputeUrl} className="underline">
            알려 주세요
          </a>
          .
        </footer>
      )}
    </article>
  );
}
