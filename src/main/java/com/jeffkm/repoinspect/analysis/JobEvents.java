package com.jeffkm.repoinspect.analysis;

import com.jeffkm.repoinspect.job.JobRepository;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 분석 진행 상태 SSE. 구독마다 DB 를 부르지 않고, 정해진 주기에 구독 중인 작업 전체의 상태를 쿼리 한 번으로 읽어 바뀐 것만 보낸다.
 * 끝난 상태(DONE·PARTIAL·FAILED)를 보내면 스트림을 닫는다. 구독자 수에 상한을 둬 메모리를 묶는다(넘으면 화면이 폴링으로 바꾼다).
 */
@Component
public class JobEvents {

  static final Set<String> TERMINAL = Set.of("DONE", "PARTIAL", "FAILED");

  private final JobRepository jobs;
  private final long timeoutMs;
  private final int maxSubscribers;
  private final Map<Long, Set<Subscription>> subs = new ConcurrentHashMap<>();
  private final AtomicInteger count = new AtomicInteger();

  public JobEvents(JobRepository jobs,
      @Value("${repo-inspect.events.timeout-seconds:600}") long timeoutSeconds,
      @Value("${repo-inspect.events.max-subscribers:500}") int maxSubscribers) {
    this.jobs = jobs;
    this.timeoutMs = timeoutSeconds * 1000;
    this.maxSubscribers = maxSubscribers;
  }

  /** 지금 상태를 바로 한 번 보낸다. 이미 끝난 작업이면 그 자리에서 닫는다. 구독자가 상한이면 비어 있는 값. */
  public Optional<SseEmitter> subscribe(long jobId, String currentStatus) {
    if (count.incrementAndGet() > maxSubscribers) {
      count.decrementAndGet();
      return Optional.empty();
    }
    SseEmitter emitter = new SseEmitter(timeoutMs);
    Subscription sub = new Subscription(jobId, emitter);
    emitter.onCompletion(() -> remove(sub));
    emitter.onTimeout(sub::close);
    emitter.onError(e -> remove(sub));
    subs.compute(jobId, (k, set) -> {
      Set<Subscription> s = set != null ? set : ConcurrentHashMap.newKeySet();
      s.add(sub);
      return s;
    });
    sub.offer(currentStatus);
    return Optional.of(emitter);
  }

  @Scheduled(fixedDelayString = "${repo-inspect.events.poll-ms:1000}")
  public void push() {
    if (subs.isEmpty()) {
      return;
    }
    Map<Long, String> statuses = jobs.findStatuses(subs.keySet());
    subs.forEach((jobId, set) -> {
      String status = statuses.get(jobId);
      for (Subscription sub : set) {
        if (status == null) {
          sub.close();
        } else {
          sub.offer(status);
        }
      }
    });
  }

  int subscriberCount() {
    return count.get();
  }

  // 같은 구독을 여러 경로(완료 콜백, 오류, 직접 닫기)에서 불러도 한 번만 센다
  private void remove(Subscription sub) {
    AtomicBoolean removed = new AtomicBoolean();
    subs.computeIfPresent(sub.jobId, (k, set) -> {
      if (set.remove(sub)) {
        removed.set(true);
      }
      return set.isEmpty() ? null : set;
    });
    if (removed.get()) {
      count.decrementAndGet();
    }
  }

  private final class Subscription {

    final long jobId;
    final SseEmitter emitter;
    private String last;
    private boolean closed;

    Subscription(long jobId, SseEmitter emitter) {
      this.jobId = jobId;
      this.emitter = emitter;
    }

    // 요청 스레드(첫 전송)와 스케줄 스레드(푸시)가 동시에 보낼 수 있어 구독 단위로 묶는다
    synchronized void offer(String status) {
      if (closed) {
        return;
      }
      if (!status.equals(last)) {
        try {
          emitter.send(SseEmitter.event().name("status").data(Map.of("status", status), MediaType.APPLICATION_JSON));
          last = status;
        } catch (IOException | IllegalStateException e) {
          // 브라우저가 떠났다. 정리는 여기서 하고 스트림 마무리는 Spring 에 맡긴다
          closed = true;
          remove(this);
          return;
        }
      }
      if (TERMINAL.contains(status)) {
        close();
      }
    }

    synchronized void close() {
      if (!closed) {
        closed = true;
        emitter.complete();
      }
      remove(this);
    }
  }
}
