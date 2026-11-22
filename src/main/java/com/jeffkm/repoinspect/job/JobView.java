package com.jeffkm.repoinspect.job;

import java.time.Instant;

/** 조회용. permissionJson 은 권한 명세서 결과(jsonb)를 문자열로, 아직 없으면 null. finishedAt 은 DONE·FAILED 로 끝난 시각. */
public record JobView(long id, String owner, String name, String commitSha, String status,
    String toolKind, String error, String permissionJson, Instant finishedAt) {
}
