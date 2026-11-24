package com.jeffkm.repoinspect.analysis;

import com.jeffkm.repoinspect.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/analyses")
public class AnalysisController {

  public record CreateRequest(@NotBlank String url) {
  }

  private final AnalysisService service;

  private final AnalysisRateLimiter limiter;

  private final JobEvents events;

  public AnalysisController(AnalysisService service, AnalysisRateLimiter limiter, JobEvents events) {
    this.service = service;
    this.limiter = limiter;
    this.events = events;
  }

  @PostMapping
  public ResponseEntity<ApiResponse<CreatedAnalysis>> create(@Valid @RequestBody CreateRequest request,
      HttpServletRequest http) {
    // 분석 요청만 제한한다. GitHub 호출과 워커 시간을 쓰는 쪽이라서. 결과 조회는 DB 만 읽으므로 막지 않는다
    limiter.check(http.getRemoteAddr()).ifPresent(seconds -> {
      throw new TooManyRequestsException(seconds);
    });
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(service.create(request.url())));
  }

  @GetMapping("/{id}")
  public ApiResponse<AnalysisView> get(@PathVariable long id) {
    return ApiResponse.ok(service.get(id));
  }

  // produces 를 걸지 않는다. 걸면 404·503 JSON 응답을 낼 수 없다
  @GetMapping("/{id}/events")
  public SseEmitter events(@PathVariable long id) {
    return events.subscribe(id, service.status(id))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "지금 지켜보는 사람이 많습니다. 잠시 뒤 새로 고침해 주세요"));
  }
}
