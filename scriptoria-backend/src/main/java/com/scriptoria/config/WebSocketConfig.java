package com.scriptoria.config;

import com.scriptoria.controller.AnalysisWebSocketHandler;
import jakarta.websocket.server.ServerContainer;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final AnalysisWebSocketHandler analysisWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(analysisWebSocketHandler, "/ws/analyze")
                .setAllowedOriginPatterns("*");
    }

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> webSocketBufferCustomizer() {
        return factory -> factory.addContextCustomizers(context -> {
            Object attribute = context.getServletContext().getAttribute("jakarta.websocket.server.ServerContainer");
            if (attribute instanceof ServerContainer container) {
                container.setDefaultMaxTextMessageBufferSize(2 * 1024 * 1024);   // 2 MB inbound screenplay payload
                container.setDefaultMaxSessionIdleTimeout(10 * 60 * 1000L);      // 10 min for long AI runs
            }
        });
    }
}
