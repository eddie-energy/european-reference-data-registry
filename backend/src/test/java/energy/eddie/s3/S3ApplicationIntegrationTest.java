package energy.eddie.s3;

import energy.eddie.s3.config.PostgresTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class S3ApplicationIntegrationTest {

    @Test
    void contextLoads() {
    }
}
