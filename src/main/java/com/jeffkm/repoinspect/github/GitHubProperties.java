package com.jeffkm.repoinspect.github;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("repo-inspect.github")
public record GitHubProperties(String baseUrl, String token, long maxTarballBytes) {
}
