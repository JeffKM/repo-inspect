package com.jeffkm.repoinspect.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RepoRefTest {

  @ParameterizedTest
  @ValueSource(strings = {
      "https://github.com/acme/tool",
      "http://github.com/acme/tool",
      "github.com/acme/tool",
      "https://www.github.com/acme/tool",
      "https://github.com/acme/tool.git",
      "https://github.com/acme/tool/",
      "https://github.com/acme/tool/tree/main/src",
      "https://github.com/acme/tool?tab=readme-ov-file",
      "https://github.com/acme/tool#install",
      "  https://github.com/acme/tool  "
  })
  void 여러_모양의_주소에서_소유자와_이름을_꺼낸다(String input) {
    assertThat(RepoRef.parse(input)).isEqualTo(new RepoRef("acme", "tool"));
  }

  @Test
  void 점과_밑줄과_하이픈이_든_이름도_받는다() {
    assertThat(RepoRef.parse("github.com/a_b-c/x.y-z_1")).isEqualTo(new RepoRef("a_b-c", "x.y-z_1"));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "",
      "https://gitlab.com/acme/tool",
      "https://github.com/acme",
      "https://github.com/",
      "https://github.com/acme/..",
      "https://github.com/ac me/tool",
      "https://evil.com/github.com/acme/tool",
      "https://github.com@evil.com/acme/tool",
      "github.com.evil.com/acme/tool",
      "https://github.com/../tool",
      "github.com/acme/.git",
      "github.com/%2e%2e/tool",
      "github.com/acme\\tool",
      "github.com//acme/tool"
  })
  void 잘못된_주소는_거절한다(String input) {
    assertThatThrownBy(() -> RepoRef.parse(input)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void 이름이_100자를_넘으면_거절한다() {
    assertThatThrownBy(() -> RepoRef.parse("github.com/acme/" + "a".repeat(101)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void null_은_거절한다() {
    assertThatThrownBy(() -> RepoRef.parse(null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void 생성자로_만든_검증되지_않은_값도_거절한다() {
    assertThatThrownBy(() -> new RepoRef("..", "x")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RepoRef("acme", null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RepoRef("ac me", "tool")).isInstanceOf(IllegalArgumentException.class);
  }
}
