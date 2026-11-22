package com.jeffkm.repoinspect.common;

import com.jeffkm.repoinspect.github.GitHubException;
import com.jeffkm.repoinspect.github.RateLimitedException;
import com.jeffkm.repoinspect.github.RepoNotFoundException;
import java.time.Clock;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private final Clock clock;

  public GlobalExceptionHandler(Clock clock) {
    this.clock = clock;
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ApiResponse<Void>> badRequest(IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(ApiResponse.fail(e.getMessage()));
  }

  @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
  ResponseEntity<ApiResponse<Void>> invalidBody(Exception e) {
    return ResponseEntity.badRequest().body(ApiResponse.fail("저장소 주소(url)가 필요합니다"));
  }

  @ExceptionHandler(RepoNotFoundException.class)
  ResponseEntity<ApiResponse<Void>> repoNotFound(RepoNotFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiResponse.fail("저장소를 찾을 수 없습니다(공개 저장소만 분석합니다)"));
  }

  @ExceptionHandler(RateLimitedException.class)
  ResponseEntity<ApiResponse<Void>> rateLimited(RateLimitedException e) {
    long seconds = Math.max(1, Duration.between(clock.instant(), e.resetAt()).toSeconds());
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds))
        .body(ApiResponse.fail("GitHub 요청 한도에 걸렸습니다. 잠시 뒤 다시 시도해 주세요"));
  }

  @ExceptionHandler(GitHubException.class)
  ResponseEntity<ApiResponse<Void>> gitHubError(GitHubException e) {
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiResponse.fail("GitHub 응답에 문제가 있습니다"));
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<ApiResponse<Void>> status(ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode()).body(ApiResponse.fail(e.getReason()));
  }
}
