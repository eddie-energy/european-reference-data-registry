package energy.eddie.s3.controllers;

import energy.eddie.s3.generated.api.PingApi;
import energy.eddie.s3.generated.model.Pong;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PingController implements PingApi {

    @Override
    public ResponseEntity<Pong> getPing() {
        var pong = new Pong()
                .message("pong")
                .timestamp(Instant.now());
        return ResponseEntity.ok(pong);
    }
}
