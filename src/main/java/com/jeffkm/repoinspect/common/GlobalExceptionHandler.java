package com.jeffkm.repoinspect.common;

import com.jeffkm.repoinspect.github.GitHubException;
import com.jeffkm.repoinspect.github.EmptyRepositoryException;
import com.jeffkm.repoinspect.github.InvalidRepoUrlException;
import com.jeffkm.repoinspect.github.RateLimitedException;
import com.jeffkm.repoinspect.github.RepoNotFoundException;
import jakarta.servlet.ServletException;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private final Clock clock;

  public GlobalExceptionHandler(Clock clock) {
    this.clock = clock;
  }

  @ExceptionHandler(InvalidRepoUrlException.class)
  ResponseEntity<ApiResponse<Void>> badRequest(InvalidRepoUrlException e) {
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

  @ExceptionHandler(EmptyRepositoryException.class)
  ResponseEntity<ApiResponse<Void>> emptyRepository(EmptyRepositoryException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.fail("빈 저장소라 분석할 코드가 없습니다"));
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

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<ApiResponse<Void>> typeMismatch(MethodArgumentTypeMismatchException e) {
    return ResponseEntity.badRequest().body(ApiResponse.fail("요청 형식이 올바르지 않습니다"));
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ApiResponse<Void>> methodNotAllowed(HttpRequestMethodNotSupportedException e) {
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ApiResponse.fail("지원하지 않는 요청 방식입니다"));
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ApiResponse<Void>> unsupportedMediaType(HttpMediaTypeNotSupportedException e) {
    return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(ApiResponse.fail("JSON 형식으로 보내 주세요"));
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<ApiResponse<Void>> status(ResponseStatusException e) {
    HttpStatus resolved = HttpStatus.resolve(e.getStatusCode().value());
    String reason = e.getReason() != null ? e.getReason()
        : resolved != null ? resolved.getReasonPhrase() : "요청을 처리할 수 없습니다";
    return ResponseEntity.status(e.getStatusCode()).body(ApiResponse.fail(reason));
  }

  /** 그 밖의 스프링 MVC 예외(없는 경로, 406, 필수 파라미터 누락 등). 상태 코드는 예외가 가진 값을 따른다. */
  @ExceptionHandler(ServletException.class)
  ResponseEntity<ApiResponse<Void>> mvcError(ServletException e) {
    HttpStatusCode code = e instanceof ErrorResponse er ? er.getStatusCode() : HttpStatus.BAD_REQUEST;
    log.debug("요청 처리 불가: {}", e.toString());
    String message = code.value() == 404 ? "찾을 수 없는 주소입니다" : "요청을 처리할 수 없습니다";
    return ResponseEntity.status(code).body(ApiResponse.fail(message));
  }

  /** 예상 못 한 오류. 내부 메시지는 로그에만 남기고 응답은 고정 문구. */
  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiResponse<Void>> unexpected(Exception e) {
    log.error("처리하지 못한 오류", e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail("서버 오류가 발생했습니다"));
  }
}
