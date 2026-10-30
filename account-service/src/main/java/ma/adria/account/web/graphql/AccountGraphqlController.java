package ma.adria.account.web.graphql;

import java.math.BigDecimal;
import java.util.UUID;
import ma.adria.account.domain.AccountStatus;
import ma.adria.account.service.AccountService;
import ma.adria.account.service.dto.*;
import ma.adria.account.service.exception.InvalidAccountException;
import org.springframework.stereotype.Controller;
import org.springframework.graphql.data.method.annotation.*;

@Controller
public class AccountGraphqlController {
    private final AccountService service;
    public AccountGraphqlController(AccountService service) { this.service = service; }

    @QueryMapping
    public AccountView account(@Argument UUID id) { return service.getAccount(id); }

    @QueryMapping
    public AccountPage accounts(@Argument int page, @Argument int size) {
        return service.getAccounts(page, size);
    }

    @MutationMapping
    public AccountView createAccount(@Argument CreateAccountInput input) {
        BigDecimal balance;
        try { balance = new BigDecimal(input.balance()); }
        catch (NumberFormatException exception) {
            throw new InvalidAccountException("Le solde doit etre un nombre decimal.");
        }
        return service.createAccount(new CreateAccountCommand(input.customerReference(), input.currency(), balance));
    }

    @MutationMapping
    public AccountView changeAccountStatus(@Argument UUID id, @Argument AccountStatus status) {
        return service.changeStatus(id, new ChangeAccountStatusCommand(status));
    }

    public record CreateAccountInput(String customerReference, String currency, String balance) {}
}
