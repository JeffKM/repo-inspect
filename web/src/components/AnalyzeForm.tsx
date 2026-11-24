"use client";

import { useRouter } from "next/navigation";
import { type FormEvent, useState } from "react";
import { createAnalysis, errorMessage } from "@/lib/api";
import { checkRepoUrl } from "@/lib/repoUrl";

export function AnalyzeForm() {
  const router = useRouter();
  const [url, setUrl] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const problem = checkRepoUrl(url);
    if (problem) {
      setError(problem);
      return;
    }
    setPending(true);
    setError(null);
    try {
      const created = await createAnalysis(url.trim());
      router.push(`/a/${created.jobId}`);
    } catch (err) {
      setError(errorMessage(err));
      setPending(false);
    }
  }

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-2">
      <label htmlFor="repo-url" className="block text-sm font-medium">
        GitHub 저장소 주소
      </label>
      <div className="flex flex-col gap-2 sm:flex-row">
        <input
          id="repo-url"
          type="url"
          inputMode="url"
          autoComplete="off"
          placeholder="https://github.com/owner/repo"
          value={url}
          onChange={(e) => setUrl(e.target.value)}
          aria-invalid={error !== null}
          aria-describedby={error ? "repo-url-error" : undefined}
          className="min-w-0 flex-1 rounded border border-zinc-300 px-3 py-2 focus:outline-2 focus:outline-zinc-900"
        />
        <button
          type="submit"
          disabled={pending}
          className="rounded bg-zinc-900 px-4 py-2 whitespace-nowrap text-white disabled:opacity-50"
        >
          {pending ? "요청 중…" : "까 보기"}
        </button>
      </div>
      {error && (
        <p id="repo-url-error" role="alert" className="text-sm text-red-700">
          {error}
        </p>
      )}
    </form>
  );
}
