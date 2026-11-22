package com.jeffkm.repoinspect.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.jeffkm.repoinspect.archive.ArchiveLimits;
import com.jeffkm.repoinspect.github.GitHubProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PropertiesValidationTest {

  @EnableConfigurationProperties({GitHubProperties.class, ArchiveLimits.class})
  static class Config {
  }

  final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
      .withUserConfiguration(Config.class)
      .withPropertyValues(
          "repo-inspect.github.base-url=https://api.github.com",
          "repo-inspect.github.max-tarball-bytes=1000",
          "repo-inspect.archive.max-total-bytes=1000",
          "repo-inspect.archive.max-files=10",
          "repo-inspect.archive.max-file-bytes=100");

  @Test
  void 정상_설정이면_뜬다() {
    runner.run(ctx -> assertThat(ctx).hasNotFailed());
  }

  @Test
  void 동시_호출_상한이_0이면_뜨지_않는다() {
    runner.withPropertyValues("repo-inspect.github.max-concurrent=0").run(ctx -> assertThat(ctx).hasFailed());
  }

  @Test
  void GitHub_주소가_비어_있으면_뜨지_않는다() {
    runner.withPropertyValues("repo-inspect.github.base-url=").run(ctx -> assertThat(ctx).hasFailed());
  }

  @Test
  void 파일_수_상한이_음수면_뜨지_않는다() {
    runner.withPropertyValues("repo-inspect.archive.max-files=-1").run(ctx -> assertThat(ctx).hasFailed());
  }
}
