package com.orderhere.GraphQLProxy.controller;

import com.amazonaws.xray.entities.Segment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Controller
@RequestMapping("/graphql")
public class GraphQLProxyController {

    private final WebClient webClient;
    private final Logger logger = LoggerFactory.getLogger(GraphQLProxyController.class);

    @Value("${targetUrl.monolithicService}")
    private String monolithicServiceUrl;

    public GraphQLProxyController(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @PostMapping
    public Mono<ResponseEntity<String>> proxyGraphQLRequest(
            @RequestBody String query,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        return Mono.deferContextual(context -> {
            Segment segment = context.getOrDefault("AWSXRaySegment", null);

            String traceHeader = segment != null
                    ? String.format("Root=%s;Parent=%s;Sampled=1", segment.getTraceId(), segment.getId())
                    : null;

            return webClient.post()
                    .uri(monolithicServiceUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, authHeader != null ? authHeader : "")
                    .header("X-Amzn-Trace-Id", traceHeader != null ? traceHeader : "") // Add trace header
                    .bodyValue(query)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, clientResponse -> {
                        logger.error("Non-200 HTTP Response Status: {}", clientResponse.statusCode());
                        return clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> Mono.error(new RuntimeException("HTTP Error: " + errorBody)));
                    })
                    .bodyToMono(String.class)
                    .flatMap(responseBody -> {
                        if (containsGraphQLErrors(responseBody)) {
                            logger.error("GraphQL Error Detected: {}", responseBody);
                        }
                        return Mono.just(ResponseEntity.ok(responseBody));
                    })
                    .onErrorResume(error -> {
                        logger.error("Unexpected Error: {}", error.getMessage());
                        return Mono.just(ResponseEntity.status(500).body("Internal Server Error: " + error.getMessage()));
                    });
        });
    }

    private boolean containsGraphQLErrors(String responseBody) {
        return responseBody.contains("\"errors\"");
    }
}
