package energy.eddie.s3.config;

import java.util.List;
import javax.annotation.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(@Nullable List<String> allowedOrigins) {}
