package com.jeffkm.repoinspect.permission;

import static com.jeffkm.repoinspect.permission.Category.FILE_ACCESS;
import static com.jeffkm.repoinspect.permission.Category.INSTALL_SCRIPT;
import static com.jeffkm.repoinspect.permission.Category.NETWORK;
import static com.jeffkm.repoinspect.permission.Category.OBFUSCATION;
import static com.jeffkm.repoinspect.permission.Category.SECRETS;
import static com.jeffkm.repoinspect.permission.Category.SHELL_EXEC;

import java.util.List;

/** 1차 규칙. 판정이 아니라 "신호"다. 규칙을 바꾸면 PermissionScannerTest 에 찾을 것과 찾지 말 것을 같이 추가한다. */
public final class PermissionRules {

  public static final List<Rule> ALL = List.of(
      Rule.of("node.child_process", SHELL_EXEC, "\\bchild_process\\b"),
      Rule.of("py.subprocess", SHELL_EXEC, "\\bsubprocess\\.(run|call|Popen|check_output|check_call)\\b"),
      Rule.of("py.os_system", SHELL_EXEC, "\\bos\\.(system|popen)\\s*\\("),
      Rule.of("java.runtime_exec", SHELL_EXEC, "Runtime\\.getRuntime\\(\\)\\.exec|new\\s+ProcessBuilder\\b"),
      Rule.onlyIn("SKILL.md", "skill.allowed_tools_bash", SHELL_EXEC, "(?i)^allowed-tools:.*\\bBash\\b"),

      Rule.of("path.ssh", FILE_ACCESS, "~/\\.ssh|\\.ssh/id_"),
      Rule.of("path.aws", FILE_ACCESS, "~/\\.aws|\\.aws/credentials"),
      Rule.of("path.home", FILE_ACCESS,
          "os\\.homedir\\(\\)|expanduser\\(\\s*[\"']~|System\\.getProperty\\(\\s*\"user\\.home\""),

      Rule.of("net.http_call", NETWORK,
          "\\bfetch\\s*\\(|\\baxios\\b|\\brequests\\.(get|post|put|delete|request)\\s*\\(|\\burllib\\.request\\b"
              + "|\\bcurl\\s|\\bwget\\s|\\bHttpClient\\b"),

      Rule.of("env.node", SECRETS, "process\\.env\\b"),
      Rule.of("env.python", SECRETS, "os\\.environ\\b|os\\.getenv\\s*\\("),
      Rule.of("env.dotenv", SECRETS, "\\bdotenv\\b|[\"']\\.env[\"']"),

      Rule.onlyIn("package.json", "npm.lifecycle", INSTALL_SCRIPT, "\"(preinstall|install|postinstall)\"\\s*:"),
      Rule.of("pipe.to_shell", INSTALL_SCRIPT, "(curl|wget)[^\\n|]{0,500}\\|\\s*(ba|z)?sh\\b"),

      Rule.of("js.eval", OBFUSCATION, "\\beval\\s*\\(|new\\s+Function\\s*\\("),
      Rule.of("long.base64", OBFUSCATION, "[A-Za-z0-9+/]{200,}={0,2}")
  );

  private PermissionRules() {
  }
}
