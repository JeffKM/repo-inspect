package com.jeffkm.repoinspect.permission;

import static com.jeffkm.repoinspect.permission.Category.FILE_ACCESS;
import static com.jeffkm.repoinspect.permission.Category.INSTALL_SCRIPT;
import static com.jeffkm.repoinspect.permission.Category.NETWORK;
import static com.jeffkm.repoinspect.permission.Category.OBFUSCATION;
import static com.jeffkm.repoinspect.permission.Category.SECRETS;
import static com.jeffkm.repoinspect.permission.Category.SHELL_EXEC;

import static com.jeffkm.repoinspect.permission.FileRole.Kind.CODE;
import static com.jeffkm.repoinspect.permission.FileRole.Kind.CONFIG;
import static com.jeffkm.repoinspect.permission.FileRole.Kind.DOC;
import static com.jeffkm.repoinspect.permission.FileRole.Kind.INSTRUCTION;

import java.util.List;

/** 1차 규칙. 판정이 아니라 "신호"다. 규칙을 바꾸면 PermissionScannerTest 에 찾을 것과 찾지 말 것을 같이 추가한다. */
public final class PermissionRules {

  // 코드 문법 규칙은 코드에만 건다. 설명문의 "eval (" 나 "fetch (" 가 걸리지 않게
  public static final List<Rule> ALL = List.of(
      Rule.of("node.child_process", SHELL_EXEC, "\\bchild_process\\b", CODE),
      Rule.of("py.subprocess", SHELL_EXEC, "\\bsubprocess\\.(run|call|Popen|check_output|check_call)\\b", CODE),
      Rule.of("py.os_system", SHELL_EXEC, "\\bos\\.(system|popen)\\s*\\(", CODE),
      Rule.of("java.runtime_exec", SHELL_EXEC, "Runtime\\.getRuntime\\(\\)\\.exec|new\\s+ProcessBuilder\\b", CODE),

      Rule.of("path.ssh", FILE_ACCESS, "~/\\.ssh|\\.ssh/id_", CODE, INSTRUCTION, CONFIG),
      Rule.of("path.aws", FILE_ACCESS, "~/\\.aws|\\.aws/credentials", CODE, INSTRUCTION, CONFIG),
      Rule.of("path.home", FILE_ACCESS,
          "os\\.homedir\\(\\)|expanduser\\(\\s*[\"']~|System\\.getProperty\\(\\s*\"user\\.home\"", CODE),

      Rule.of("net.http_call", NETWORK,
          "\\bfetch\\s*\\(|\\baxios\\b|\\brequests\\.(get|post|put|delete|request)\\s*\\(|\\burllib\\.request\\b"
              + "|\\bcurl\\s|\\bwget\\s|\\bHttpClient\\b", CODE, INSTRUCTION, CONFIG),

      Rule.of("env.node", SECRETS, "process\\.env\\b", CODE),
      Rule.of("env.python", SECRETS, "os\\.environ\\b|os\\.getenv\\s*\\(", CODE),
      Rule.of("env.dotenv", SECRETS, "\\bdotenv\\b|[\"']\\.env[\"']", CODE),

      Rule.onlyIn("package.json", "npm.lifecycle", INSTALL_SCRIPT, "\"(preinstall|install|postinstall)\"\\s*:"),
      Rule.of("pipe.to_shell", INSTALL_SCRIPT,
          "(curl|wget)[^\\n|]{0,500}\\|\\s*(sudo\\s+(-[A-Za-z]+\\s+(\\w+\\s+)?){0,3})?(ba|z)?sh\\b",
          CODE, INSTRUCTION, CONFIG, DOC),
      Rule.of("pipe.shell_subst", INSTALL_SCRIPT,
          "(?:\\b(?:ba|z)?sh\\s+(?:<\\(|-c\\s+[\"']?\\$\\()|\\beval\\s+[\"']?\\$\\(|(?:\\bsource|(?:^|\\s)\\.)\\s+<\\()"
              + "\\s*(?:curl|wget)\\b",
          CODE, INSTRUCTION, CONFIG, DOC),

      Rule.of("js.eval", OBFUSCATION, "\\beval\\s*\\(|new\\s+Function\\s*\\(", CODE),
      // 이미지 data URI 와 노트북 출력 이미지는 소음이라 뺀다. 실행 코드를 담는 data:text/javascript 같은 것은 그대로 잡는다
      Rule.of("long.base64", OBFUSCATION,
          "(?<![A-Za-z0-9+/])(?<!data:image/(?:png|jpeg|gif|webp|svg\\+xml);base64,)(?<!\"image/(?:png|jpeg)\": \")"
              + "[A-Za-z0-9+/]{200,}={0,2}", CODE, INSTRUCTION)
  );

  private PermissionRules() {
  }
}
