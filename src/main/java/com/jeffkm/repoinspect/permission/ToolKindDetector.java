package com.jeffkm.repoinspect.permission;

import com.jeffkm.repoinspect.archive.SourceFile;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** 파일 이름과 의존성으로 AI 도구 종류를 판별한다. 한 저장소가 여러 종류일 수 있다(플러그인 안의 skill, hook). */
public final class ToolKindDetector {

  // requirements.txt, pyproject.toml 의 "mcp" 패키지. fastmcp, mcp-tools 같은 다른 이름은 제외
  private static final Pattern PY_MCP =
      Pattern.compile("(?m)(^|[\"'\\s])mcp(\\[[^\\]]*])?\\s*([<>=~!]|[\"',]|$)");

  private ToolKindDetector() {
  }

  public static Set<ToolKind> detect(List<SourceFile> files) {
    EnumSet<ToolKind> kinds = EnumSet.noneOf(ToolKind.class);
    for (SourceFile f : files) {
      String path = f.path();
      String base = path.substring(path.lastIndexOf('/') + 1);
      if (base.equals("SKILL.md")) {
        kinds.add(ToolKind.SKILL);
      }
      if (base.equals("hooks.json") || (base.equals("settings.json") && f.content().contains("\"hooks\""))) {
        kinds.add(ToolKind.HOOK);
      }
      if (base.equals("package.json") && f.content().contains("@modelcontextprotocol/sdk")) {
        kinds.add(ToolKind.MCP_SERVER);
      }
      if ((base.equals("requirements.txt") || base.equals("pyproject.toml")) && PY_MCP.matcher(f.content()).find()) {
        kinds.add(ToolKind.MCP_SERVER);
      }
      if (path.equals(".claude-plugin/plugin.json") || path.endsWith("/.claude-plugin/plugin.json")) {
        kinds.add(ToolKind.PLUGIN);
      }
    }
    if (kinds.isEmpty()) {
      kinds.add(ToolKind.GENERIC);
    }
    return kinds;
  }

  public static String label(Set<ToolKind> kinds) {
    return kinds.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
  }
}
