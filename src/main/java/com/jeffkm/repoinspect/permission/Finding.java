package com.jeffkm.repoinspect.permission;

/** 권한 명세서의 근거 한 줄. line 은 1부터. */
public record Finding(String category, String ruleId, String filePath, int line, String snippet) {
}
