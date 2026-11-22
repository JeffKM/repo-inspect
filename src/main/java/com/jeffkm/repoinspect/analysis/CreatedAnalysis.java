package com.jeffkm.repoinspect.analysis;

public record CreatedAnalysis(long jobId, String owner, String name, String commitSha, String status) {
}
