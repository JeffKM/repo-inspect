package com.jeffkm.repoinspect.analysis;

import com.jeffkm.repoinspect.common.ApiResponse;
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

  public AnalysisController(AnalysisService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<ApiResponse<CreatedAnalysis>> create(@Valid @RequestBody CreateRequest request) {
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(service.create(request.url())));
  }

  @GetMapping("/{id}")
  public ApiResponse<AnalysisView> get(@PathVariable long id) {
    return ApiResponse.ok(service.get(id));
  }
}
