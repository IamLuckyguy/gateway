package kr.co.kwt.gateway.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Setter
@Getter
@ConfigurationProperties(prefix = "gateway.security")
@Component
public class SecurityProperties {
    private List<String> allowedDomains;
    private List<String> publicPaths;
}