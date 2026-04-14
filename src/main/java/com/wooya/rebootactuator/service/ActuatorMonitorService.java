package com.wooya.rebootactuator.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ActuatorMonitorService {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(2);
    private static final String DEFAULT_ACCEPT = "application/json";

    // Java 21 virtual thread executor.
    // I/O 대기 시간이 많은 HTTP 호출은 플랫폼 스레드를 오래 점유할 필요가 없으므로
    // 버추얼 스레드를 사용하면 적은 비용으로 많은 동시 요청을 처리하기 쉽다.
    // 이 프로젝트는 5초마다 약 20개의 actuator를 호출하므로 가벼운 동시성 처리 방식이 유리하다.
    private final ExecutorService httpExecutor = Executors.newVirtualThreadPerTaskExecutor();

    // 5초마다 반복 호출되는 배치이므로 HttpClient/Executor는 재사용한다.
    // 매번 새로 생성하면 연결 풀과 스레드 초기화 비용이 반복되어 불리하다.
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .executor(httpExecutor)
            .version(HttpClient.Version.HTTP_2)
            .build();

    /**
     * 배치에서 5초마다 호출되는 메서드.
     * 약 20개의 actuator URL을 동시에 호출하고, 성공 응답만 모아서 후속 Redis 저장 대상으로 반환한다.
     *
     * - 순차 호출이 아니라 병렬 호출을 사용해서 전체 배치 시간을 줄인다.
     * - 각 요청은 CompletableFuture 기반으로 동시에 실행된다.
     * - 응답은 성공/실패로 나눠서 반환하므로 Redis 저장과 실패 로그 처리를 분리하기 쉽다.
     */
    public BatchMonitorResult getActuatorInfo(List<ActuatorRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return BatchMonitorResult.empty();
        }

        List<CompletableFuture<ActuatorResponse>> futures = new ArrayList<>(requests.size());
        for (ActuatorRequest request : requests) {
            if (request != null) {
                futures.add(sendGet(request));
            }
        }

        if (futures.isEmpty()) {
            return BatchMonitorResult.empty();
        }

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

        List<ActuatorResponse> successResponses = new ArrayList<>(futures.size());
        List<ActuatorResponse> failureResponses = new ArrayList<>();

        for (CompletableFuture<ActuatorResponse> future : futures) {
            ActuatorResponse response = future.join();
            if (response == null) {
                continue;
            }

            if (response.success()) {
                successResponses.add(response);
            }
            else {
                failureResponses.add(response);
            }
        }

        // Redis는 건건이 저장하지 말고, 성공 응답만 모아서 한 번에 저장하는 편이 배치에 유리하다.
        // 예시:
        // monitorRedisService.saveAll(successResponses);

        return new BatchMonitorResult(
                Collections.unmodifiableList(successResponses),
                Collections.unmodifiableList(failureResponses)
        );
    }

    /**
     * URL 1건에 대한 GET 요청을 비동기로 전송한다.
     *
     * sendAsync 사용 이유:
     * - 여러 서버를 동시에 호출해야 하므로 요청별로 기다리지 않고 즉시 다음 요청을 시작할 수 있다.
     * - 배치 전체 시간을 각 요청 시간의 합이 아니라 "가장 느린 요청 + 약간의 오버헤드" 수준으로 줄일 수 있다.
     * - 내부적으로 virtual thread executor를 사용하므로 많은 대기 시간을 저비용으로 처리할 수 있다.
     */
    private CompletableFuture<ActuatorResponse> sendGet(ActuatorRequest request) {
        if (request.url() == null) {
            return CompletableFuture.completedFuture(
                    ActuatorResponse.failure(null, 0, "Request URL is null")
            );
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(request.url())
                .GET()
                .timeout(request.requestTimeout() != null ? request.requestTimeout() : REQUEST_TIMEOUT)
                .header("Accept", DEFAULT_ACCEPT);

        // Host 헤더는 반드시 외부에서 받은 값으로 명시 세팅한다.
        if (StringUtils.hasText(request.host())) {
            requestBuilder.header("Host", request.host());
        }

        if (request.headers() != null && !request.headers().isEmpty()) {
            request.headers().forEach((headerName, headerValue) -> {
                if (StringUtils.hasText(headerName) && StringUtils.hasText(headerValue)) {
                    requestBuilder.header(headerName, headerValue);
                }
            });
        }

        return httpClient.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
                .handle((response, throwable) -> mapResponse(request.url(), response, throwable));
    }

    /**
     * HTTP 응답을 배치 처리용 결과 객체로 변환한다.
     *
     * null, 예외, 비정상 status, empty body 를 모두 여기서 정리해 두면
     * 배치 호출부는 success 여부만 보고 Redis 저장 여부를 결정할 수 있다.
     */
    private ActuatorResponse mapResponse(
            URI requestUrl,
            HttpResponse<String> response,
            Throwable throwable
    ) {
        if (throwable != null) {
            return ActuatorResponse.failure(requestUrl, 0, throwable.getMessage());
        }

        if (response == null) {
            return ActuatorResponse.failure(requestUrl, 0, "Response is null");
        }

        String responseBody = response.body();
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return ActuatorResponse.failure(requestUrl, response.statusCode(), responseBody);
        }

        if (!StringUtils.hasText(responseBody)) {
            return ActuatorResponse.failure(requestUrl, response.statusCode(), "Response body is empty");
        }

        return new ActuatorResponse(
                requestUrl,
                response.statusCode(),
                responseBody,
                true,
                null
        );
    }

    /**
     * 애플리케이션 종료 시 virtual thread executor를 정리한다.
     *
     * 재사용되는 executor이므로 종료 시점에 명시적으로 close 해두는 편이 안전하다.
     */
    @PreDestroy
    public void close() {
        httpExecutor.close();
    }

    public record ActuatorRequest(
            URI url,
            String host,
            Map<String, String> headers,
            Duration requestTimeout
    ) {
        public ActuatorRequest {
            headers = headers == null ? Map.of() : Map.copyOf(headers);
        }
    }

    public record ActuatorResponse(
            URI url,
            int statusCode,
            String body,
            boolean success,
            String errorMessage
    ) {
        public static ActuatorResponse failure(URI url, int statusCode, String errorMessage) {
            return new ActuatorResponse(url, statusCode, null, false, errorMessage);
        }
    }

    public record BatchMonitorResult(
            List<ActuatorResponse> successResponses,
            List<ActuatorResponse> failureResponses
    ) {
        public static BatchMonitorResult empty() {
            return new BatchMonitorResult(List.of(), List.of());
        }
    }
}
