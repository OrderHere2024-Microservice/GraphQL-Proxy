package com.orderhere.GraphQLProxy.config;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorder;
import com.amazonaws.xray.entities.Segment;
import com.amazonaws.xray.strategy.IgnoreErrorContextMissingStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.util.context.Context;

@Configuration
public class WebConfig {

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    @Bean
    public WebFilter xRayTracingFilter() {
        String appName = "orderhere-graphql-proxy-" + activeProfile;

        return (ServerWebExchange exchange, WebFilterChain chain) -> {
            Segment segment = AWSXRay.beginSegment(appName);
            segment.putAnnotation("method", exchange.getRequest().getMethod().name());
            segment.putAnnotation("path", exchange.getRequest().getURI().getPath());

            return chain.filter(exchange)
                    .contextWrite(Context.of("AWSXRaySegment", segment))
                    .doFinally(signalType -> {
                        if (AWSXRay.getCurrentSegmentOptional().isPresent()) {
                            AWSXRay.endSegment();
                        }
                    });
        };
    }

    @Bean
    public AWSXRayRecorder configureXRayRecorder() {
        AWSXRayRecorder recorder = AWSXRay.getGlobalRecorder();
        recorder.setContextMissingStrategy(new IgnoreErrorContextMissingStrategy());
        return recorder;
    }
}
