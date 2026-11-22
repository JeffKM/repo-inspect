package com.jeffkm.repoinspect.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.jeffkm.repoinspect.permission.Finding;
import java.util.List;

/** permission: 권한 명세서 요약(truncated, fileCount, skippedFiles, domains, primaryCounts, secondaryFindings 등). 끝나기 전이면 null. */
public record AnalysisView(long jobId, String owner, String name, String commitSha, String status, String toolKind,
    String error, List<Finding> findings, JsonNode permission) {
}
