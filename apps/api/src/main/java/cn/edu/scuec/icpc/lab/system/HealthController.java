package cn.edu.scuec.icpc.lab.system;

import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final String version;

    public HealthController(@Value("${app.version:0.1.0}") String version) {
        this.version = version;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("UP", "icpc-lab-api", version, Instant.now());
    }

    public record HealthResponse(String status, String service, String version, Instant time) {
    }
}