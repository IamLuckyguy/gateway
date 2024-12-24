package kr.co.kwt.gateway.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "gateway.rate-limit")
public class RateLimitProperties {

    private RouteConfig defaultConfig = new RouteConfig();
    private Map<String, RouteConfig> routes = new HashMap<>();

    @Setter
    @Getter
    public static class RouteConfig {
        private int capacity = 100;
        private int refillTokens = 1;
        private Duration refillPeriod = Duration.ofSeconds(1);

        private ClientConfig user = new ClientConfig(200, 20);
        private ClientConfig api = new ClientConfig(150, 15);
        private ClientConfig ip = new ClientConfig(50, 5);
    }

    @Setter
    @Getter
    public static class ClientConfig {
        private int capacity;
        private int refillTokens;
        private Duration refillPeriod = Duration.ofSeconds(1);

        public ClientConfig(int capacity, int refillTokens) {
            this.capacity = capacity;
            this.refillTokens = refillTokens;
        }
    }
}