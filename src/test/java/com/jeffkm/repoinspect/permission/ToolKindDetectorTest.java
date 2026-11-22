package com.jeffkm.repoinspect.permission;

import static org.assertj.core.api.Assertions.assertThat;

import com.jeffkm.repoinspect.archive.SourceFile;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ToolKindDetectorTest {

  static Set<ToolKind> detect(SourceFile... files) {
    return ToolKindDetector.detect(List.of(files));
  }

  @Test
  void SKILL_md_가_있으면_skill() {
    assertThat(detect(new SourceFile("skills/review/SKILL.md", "---\nname: review\n---")))
        .containsExactly(ToolKind.SKILL);
  }

  @Test
  void hooks_json_이나_hooks_항목이_든_settings_json_이면_hook() {
    assertThat(detect(new SourceFile("hooks/hooks.json", "{}"))).containsExactly(ToolKind.HOOK);
    assertThat(detect(new SourceFile(".claude/settings.json", "{\"hooks\": {}}"))).containsExactly(ToolKind.HOOK);
    assertThat(detect(new SourceFile(".claude/settings.json", "{\"model\": \"x\"}"))).containsExactly(ToolKind.GENERIC);
  }

  @Test
  void MCP_SDK_의존성이면_mcp_서버() {
    assertThat(detect(new SourceFile("package.json", "{\"dependencies\": {\"@modelcontextprotocol/sdk\": \"^1.0.0\"}}")))
        .containsExactly(ToolKind.MCP_SERVER);
    assertThat(detect(new SourceFile("requirements.txt", "httpx\nmcp>=1.2\n"))).containsExactly(ToolKind.MCP_SERVER);
    assertThat(detect(new SourceFile("pyproject.toml", "dependencies = [\"mcp[cli]>=1.2\"]"))).containsExactly(ToolKind.MCP_SERVER);
  }

  @Test
  void 이름만_비슷한_파이썬_패키지는_mcp_서버가_아니다() {
    assertThat(detect(new SourceFile("requirements.txt", "fastmcp-helper==0.1\nmcp-tools-x==1\n")))
        .containsExactly(ToolKind.GENERIC);
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "mcp ; python_version>=\"3.10\"",
      "mcp[cli]; extra==\"x\"",
      "mcp # pin",
      "mcp @ git+https://github.com/x/y",
      "MCP>=1"})
  void requirements_에서_환경_마커나_주석이_붙은_mcp_도_mcp_서버(String line) {
    assertThat(detect(new SourceFile("requirements.txt", "httpx\n" + line + "\n"))).containsExactly(ToolKind.MCP_SERVER);
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "[project]\ndependencies = [\n  \"httpx\",\n  \"mcp>=1.2\",\n]",
      "[project.optional-dependencies]\nserver = [\"mcp[cli]\"]",
      "[tool.poetry.dependencies]\npython = \"^3.10\"\nmcp = \"^1.0\""})
  void pyproject_의_의존성_선언에_있는_mcp_는_mcp_서버(String toml) {
    assertThat(detect(new SourceFile("pyproject.toml", toml))).containsExactly(ToolKind.MCP_SERVER);
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "[project]\nname = \"mcp\"\ndescription = \"Tools for mcp\"\nkeywords = [\"mcp\", \"ai\"]\ndependencies = [\"httpx\"]",
      "[project.scripts]\nmcp = \"pkg:main\""})
  void pyproject_의_의존성_밖에_나온_mcp_는_mcp_서버가_아니다(String toml) {
    assertThat(detect(new SourceFile("pyproject.toml", toml))).containsExactly(ToolKind.GENERIC);
  }

  @Test
  void requirements_주석의_mcp_는_mcp_서버가_아니다() {
    assertThat(detect(new SourceFile("requirements.txt", "# requires mcp\nhttpx\n"))).containsExactly(ToolKind.GENERIC);
  }

  @Test
  void 플러그인_매니페스트면_plugin() {
    assertThat(detect(new SourceFile(".claude-plugin/plugin.json", "{}"))).containsExactly(ToolKind.PLUGIN);
    assertThat(detect(new SourceFile("plugins/x/.claude-plugin/plugin.json", "{}"))).containsExactly(ToolKind.PLUGIN);
  }

  @Test
  void 여러_종류가_섞이면_모두_표시하고_라벨은_이름순() {
    Set<ToolKind> kinds = detect(
        new SourceFile(".claude-plugin/plugin.json", "{}"),
        new SourceFile("skills/a/SKILL.md", "x"),
        new SourceFile("hooks/hooks.json", "{}"));
    assertThat(ToolKindDetector.label(kinds)).isEqualTo("HOOK,PLUGIN,SKILL");
  }

  @Test
  void 아무_신호도_없으면_generic() {
    assertThat(detect(new SourceFile("src/main.go", "package main"))).containsExactly(ToolKind.GENERIC);
  }
}
