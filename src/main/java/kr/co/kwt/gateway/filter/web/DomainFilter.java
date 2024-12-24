package kr.co.kwt.gateway.filter.web;

import kr.co.kwt.gateway.config.properties.SecurityProperties;
import kr.co.kwt.gateway.exception.InvalidDomainException;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import java.net.URI;

@Component
public class DomainFilter implements WebFilter, Ordered {
    private static final Logger logger = LoggerFactory.getLogger(DomainFilter.class);
    private final SecurityProperties securityProperties;

    public DomainFilter(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        URI uri = request.getURI();
        String host = uri.getHost();

        securityProperties.getAllowedDomains().stream().forEach(System.out::println);

        if (host != null && securityProperties.getAllowedDomains().stream().anyMatch(host::endsWith)) {
            logger.debug("Domain validation passed for host: {}", host);
            return chain.filter(exchange);
        }

        logger.warn("Invalid domain access attempt: {}", host);
        return Mono.error(new InvalidDomainException("Invalid domain: " + host));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}