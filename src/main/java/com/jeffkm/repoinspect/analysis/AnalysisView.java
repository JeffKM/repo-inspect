package com.jeffkm.repoinspect.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.jeffkm.repoinspect.permission.Finding;
import java.time.Instant;
import java.util.List;

/**
 * permission: 권한 명세서 요약(truncated, fileCount, skippedFiles, domains, primaryCounts, secondaryFindings 등). 끝나기 전이면 null.
 * analyzedAt: 분석이 끝난 시각(DONE·PARTIAL 일 때만). notice: 결과를 단정하지 않는다는 고정 안내. disputeUrl: 저장소 주인용 이의 제기 창구.
 */
public record AnalysisView(long jobId, String owner, String name, String commitSha, String status, String toolKind,
    String error, List<Finding> findings, JsonNode permission, Instant analyzedAt, String notice, String disputeUrl) {
}
