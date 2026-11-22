package com.jeffkm.repoinspect.archive;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 압축 파일을 읽을 때의 상한. 넘으면 앞부분만 분석하고 "일부만 분석"으로 표시한다. */
@ConfigurationProperties("repo-inspect.archive")
public record ArchiveLimits(long maxTotalBytes, int maxFiles, int maxFileBytes) {
}
