package ma.adria.account.web.rest;
import java.util.UUID;
import jakarta.validation.Valid;
import ma.adria.account.service.AccountMovementService;
import ma.adria.account.service.dto.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/internal/movements")
public class AccountMovementController {
 private final AccountMovementService service;
 public AccountMovementController(AccountMovementService service) { this.service=service; }
 @PostMapping public MovementView apply(@Valid @RequestBody MovementCommand command) { return service.apply(command); }
 @GetMapping("/{id}") public MovementView get(@PathVariable UUID id) { return service.get(id); }
}
