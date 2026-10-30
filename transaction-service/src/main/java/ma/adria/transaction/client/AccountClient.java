package ma.adria.transaction.client;
import java.util.UUID;
import ma.adria.transaction.service.dto.AccountSnapshot;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
@FeignClient(name="account-service",url="${bank.account-service.url:}")
public interface AccountClient {
    @GetMapping("/api/accounts/{accountId}")
    AccountSnapshot getAccount(@PathVariable("accountId") UUID accountId);
    @GetMapping("/internal/movements/{id}")
    MovementReceipt findMovement(@PathVariable("id") UUID id);
    @PostMapping("/internal/movements")
    MovementReceipt applyMovement(@RequestBody MovementRequest command);
}
