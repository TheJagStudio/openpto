package gov.openpto.gateway.status;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /gateway/status} (public): health of every downstream as seen from the gateway. */
@RestController
public class GatewayStatusController {

    private final GatewayStatusService statusService;

    public GatewayStatusController(GatewayStatusService statusService) {
        this.statusService = statusService;
    }

    @GetMapping("/gateway/status")
    ResponseEntity<GatewayStatusService.StatusResponse> status() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(statusService.check());
    }
}
