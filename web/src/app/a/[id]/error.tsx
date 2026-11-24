"use client";

export default function AnalysisError({ reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <div className="space-y-3">
      <p>결과를 불러오지 못했습니다. 잠시 뒤 다시 시도해 주세요.</p>
      <button type="button" onClick={reset} className="rounded border border-zinc-300 px-3 py-1">
        다시 시도
      </button>
    </div>
  );
}
