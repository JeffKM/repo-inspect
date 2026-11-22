package com.jeffkm.repoinspect.github;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** maxConcurrent: 동시에 기다리는 GitHub 호출 수 상한. acquireTimeoutMs: 자리가 날 때까지 기다리는 시간. */
@ConfigurationProperties("repo-inspect.github")
public record GitHubProperties(String baseUrl, String token, long maxTarballBytes,
    @DefaultValue("8") int maxConcurrent, @DefaultValue("2000") long acquireTimeoutMs) {
}
