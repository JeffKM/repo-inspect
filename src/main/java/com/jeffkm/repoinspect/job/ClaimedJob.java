package com.jeffkm.repoinspect.job;

import com.jeffkm.repoinspect.github.RepoRef;

/** 워커가 꺼낸 작업. attempts 는 이번 시도를 포함한 누적 횟수(펜싱 값), roundAttempts 는 실패 후 다시 돈 회차 안에서의 횟수. */
public record ClaimedJob(long id, RepoRef repo, String sha, int attempts, int roundAttempts) {
}
