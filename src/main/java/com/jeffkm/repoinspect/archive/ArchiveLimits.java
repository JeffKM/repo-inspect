package com.jeffkm.repoinspect.archive;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 압축 파일을 읽을 때의 상한. 넘으면 앞부분만 분석하고 "일부만 분석"으로 표시한다. */
@Validated
@ConfigurationProperties("repo-inspect.archive")
public record ArchiveLimits(@Positive long maxTotalBytes, @Positive int maxFiles, @Positive int maxFileBytes) {
}
