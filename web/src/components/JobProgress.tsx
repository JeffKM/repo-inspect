"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { eventsUrl, getAnalysis, type Status } from "@/lib/api";
import { isTerminal, statusLabel } from "@/lib/labels";

const POLL_MS = 3000;

/** SSE 로 상태를 듣다가 끝나면 서버 컴포넌트를 새로 그린다. SSE 가 끊기거나 503 이면 폴링으로 바꾼다. */
export function JobProgress({ jobId, initialStatus }: { jobId: number; initialStatus: Status }) {
  const router = useRouter();
  const [status, setStatus] = useState<Status>(initialStatus);

  useEffect(() => {
    let finished = false;
    let poll: ReturnType<typeof setInterval> | null = null;

    const finish = () => {
      if (!finished) {
        finished = true;
        router.refresh();
      }
    };

    const startPolling = () => {
      if (poll) {
        return;
      }
      poll = setInterval(async () => {
        try {
          const a = await getAnalysis(String(jobId));
          if (a) {
            setStatus(a.status);
            if (isTerminal(a.status)) {
              finish();
            }
          }
        } catch {
          // 다음 주기에 다시 묻는다
        }
      }, POLL_MS);
    };

    const source = new EventSource(eventsUrl(jobId));
    source.addEventListener("status", (e) => {
      const next = (JSON.parse((e as MessageEvent<string>).data) as { status: Status }).status;
      setStatus(next);
      if (isTerminal(next)) {
        source.close();
        finish();
      }
    });
    source.onerror = () => {
      source.close();
      if (!finished) {
        startPolling();
      }
    };

    return () => {
      source.close();
      if (poll) {
        clearInterval(poll);
      }
    };
  }, [jobId, router]);

  return (
    <p role="status" aria-live="polite" className="rounded border border-zinc-200 p-3 text-sm">
      {statusLabel(status)}… 끝나면 이 화면에 결과가 나옵니다.
    </p>
  );
}
