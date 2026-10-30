package ma.adria.account.web.rest;

import jakarta.validation.Valid;
import ma.adria.account.service.AccountService;
import ma.adria.account.service.dto.AccountPage;
import ma.adria.account.service.dto.AccountView;
import ma.adria.account.service.dto.ChangeAccountStatusCommand;
import ma.adria.account.service.dto.CreateAccountCommand;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
public class AccountRestController {

    private final AccountService service;

    public AccountRestController(AccountService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AccountView> create(@Valid @RequestBody CreateAccountCommand command) {
        AccountView account = service.createAccount(command);
        URI location = URI.create("/api/accounts/" + account.id());
        return ResponseEntity.created(location).body(account);
    }

    @GetMapping("/{accountId}")
    public AccountView get(@PathVariable UUID accountId) {
        return service.getAccount(accountId);
    }

    @GetMapping
    public AccountPage list(@RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "20") int size) {
        return service.getAccounts(page, size);
    }

    @PatchMapping(value = "/{accountId}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AccountView changeStatus(@PathVariable UUID accountId,
                                    @Valid @RequestBody ChangeAccountStatusCommand command) {
        return service.changeStatus(accountId, command);
    }
}
