package com.jeffkm.repoinspect.permission;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.jeffkm.repoinspect.archive.SourceFile;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** 파일 이름과 의존성으로 AI 도구 종류를 판별한다. 한 저장소가 여러 종류일 수 있다(플러그인 안의 skill, hook). */
public final class ToolKindDetector {

  // 패키지 이름이 mcp 나 fastmcp 인 의존성 한 줄. mcp-server, mcp_x, fastmcp-helper 는 제외
  private static final Pattern REQ_MCP =
      Pattern.compile("(?i)^\\s*(fast)?mcp(\\[[^\\]]*])?(\\s|[<>=~!;@#,]|$)");
  private static final Pattern TABLE_HEADER = Pattern.compile("^\\s*\\[\\[?\\s*([^\\]]*?)\\s*]]?\\s*(#.*)?$");
  private static final Pattern ARRAY_START = Pattern.compile("^\\s*([^=\\s]+)\\s*=\\s*\\[");
  // 배열 안의 따옴표 문자열, 주석, 배열 끝을 앞에서부터 하나씩 읽는다
  private static final Pattern ARRAY_TOKEN = Pattern.compile("\"([^\"]*)\"|'([^']*)'|#.*|]");
  private static final Pattern POETRY_DEPS = Pattern.compile("tool\\.poetry\\.((group\\.[^.]+\\.)?dependencies|dev-dependencies)");
  private static final Pattern POETRY_MCP = Pattern.compile("(?i)^\\s*(fast)?mcp\\s*=");
  private static final Pattern REQUIREMENTS = Pattern.compile("requirements([-_.][A-Za-z0-9_.-]+)?\\.txt");
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final List<String> NPM_DEP_FIELDS =
      List.of("dependencies", "devDependencies", "peerDependencies", "optionalDependencies");
  private static final List<String> NPM_MCP = List.of("@modelcontextprotocol/sdk", "fastmcp");

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
      if (base.equals("hooks.json") || (base.equals("settings.json") && readJson(f.content()).path("hooks").isObject())) {
        kinds.add(ToolKind.HOOK);
      }
      if (base.equals("package.json") && npmHasMcp(f.content())) {
        kinds.add(ToolKind.MCP_SERVER);
      }
      if (isRequirements(path, base) && f.content().lines().anyMatch(l -> REQ_MCP.matcher(l).find())) {
        kinds.add(ToolKind.MCP_SERVER);
      }
      if (base.equals("pyproject.toml") && pyprojectHasMcp(f.content())) {
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

  private static boolean isRequirements(String path, String base) {
    return REQUIREMENTS.matcher(base).matches() || (path.contains("requirements/") && base.endsWith(".txt"));
  }

  // 문자열 포함으로 보면 설명문의 패키지 이름도 걸린다. 의존성 필드의 키만 본다
  private static boolean npmHasMcp(String content) {
    JsonNode root = readJson(content);
    for (String field : NPM_DEP_FIELDS) {
      for (String name : NPM_MCP) {
        if (root.path(field).has(name)) {
          return true;
        }
      }
    }
    return false;
  }

  private static JsonNode readJson(String content) {
    try {
      JsonNode node = JSON.readTree(content.startsWith("\uFEFF") ? content.substring(1) : content);
      return node == null ? MissingNode.getInstance() : node;
    } catch (JsonProcessingException e) {
      return MissingNode.getInstance(); // 깨진 JSON 은 판별 신호가 없는 것으로 본다
    }
  }

  // shortcut: 한 줄에 여러 테이블이나 인라인 테이블 표기는 다루지 않음, 필요해지면 TOML 파서 도입
  private static boolean pyprojectHasMcp(String content) {
    String table = "";
    boolean inArray = false;
    for (String line : content.lines().toList()) {
      String rest = line;
      if (!inArray) {
        var header = TABLE_HEADER.matcher(line);
        if (header.matches()) {
          table = header.group(1);
          continue;
        }
        var start = ARRAY_START.matcher(line);
        boolean depTable = table.isEmpty() || table.equals("project");
        boolean depArray = start.find() && ((depTable && start.group(1).equals("dependencies"))
            || table.equals("project.optional-dependencies")
            || table.equals("dependency-groups")
            || (table.equals("tool.uv") && start.group(1).equals("dev-dependencies")));
        if (depArray) {
          inArray = true;
          rest = line.substring(start.end());
        } else {
          if (POETRY_DEPS.matcher(table).matches() && POETRY_MCP.matcher(line).find()) {
            return true;
          }
          continue;
        }
      }
      var token = ARRAY_TOKEN.matcher(rest);
      while (token.find()) {
        if (token.group().equals("]")) {
          inArray = false;
          break;
        }
        String quoted = token.group(1) != null ? token.group(1) : token.group(2);
        if (quoted != null && REQ_MCP.matcher(quoted).find()) {
          return true;
        }
      }
    }
    return false;
  }

  public static String label(Set<ToolKind> kinds) {
    return kinds.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
  }
}
