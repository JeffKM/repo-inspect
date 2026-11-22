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

@RestController
@RequestMapping("/api/analyses")
public class AnalysisController {

  public record CreateRequest(@NotBlank String url) {
  }

  private final AnalysisService service;

  private final AnalysisRateLimiter limiter;

  public AnalysisController(AnalysisService service, AnalysisRateLimiter limiter) {
    this.service = service;
    this.limiter = limiter;
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
}
