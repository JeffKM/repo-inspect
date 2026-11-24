package com.jeffkm.repoinspect.common;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 웹 화면(다른 출처)이 브라우저에서 API 를 직접 부른다. 웹 서버를 거치면 IP 별 요청 제한이 웹 서버 IP 하나로 묶이기 때문이다.
 * 설정한 출처만 연다. 비어 있으면 열지 않는다.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  private final String[] origins;

  public WebConfig(@Value("${repo-inspect.web-origins:}") String[] origins) {
    this.origins = Arrays.stream(origins).map(String::strip).filter(s -> !s.isEmpty()).toArray(String[]::new);
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    if (origins.length == 0) {
      return;
    }
    registry.addMapping("/api/**")
        .allowedOrigins(origins)
        .allowedMethods("GET", "POST")
        .allowedHeaders("Content-Type")
        .exposedHeaders("Retry-After")
        .maxAge(600);
  }
}
