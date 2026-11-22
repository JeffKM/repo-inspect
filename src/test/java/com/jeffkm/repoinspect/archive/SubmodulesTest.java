package com.jeffkm.repoinspect.archive;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SubmodulesTest {

  @Test
  void 루트_gitmodules_의_경로를_모은다() {
    String gitmodules = "[submodule \"lib\"]\n\tpath = vendor/lib\n\turl = https://github.com/x/lib\n"
        + "[submodule \"b\"]\n  path=third_party/b \n";

    assertThat(Submodules.paths(List.of(new SourceFile(".gitmodules", gitmodules))))
        .containsExactly("vendor/lib", "third_party/b");
  }

  @Test
  void gitmodules_가_없거나_하위_폴더에_있으면_비어_있다() {
    assertThat(Submodules.paths(List.of(new SourceFile("x/.gitmodules", "\tpath = a\n")))).isEmpty();
    assertThat(Submodules.paths(List.of())).isEmpty();
  }
}
