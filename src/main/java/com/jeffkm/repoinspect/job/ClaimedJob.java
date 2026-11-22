package com.jeffkm.repoinspect.job;

import com.jeffkm.repoinspect.github.RepoRef;

/** 워커가 꺼낸 작업. attempts 는 이번 시도를 포함한 횟수. */
public record ClaimedJob(long id, RepoRef repo, String sha, int attempts) {
}
