package com.jeffkm.repoinspect.job;

/** 조회용. permissionJson 은 권한 명세서 결과(jsonb)를 문자열로. 아직 없으면 null. */
public record JobView(long id, String owner, String name, String commitSha, String status,
    String toolKind, String error, String permissionJson) {
}
