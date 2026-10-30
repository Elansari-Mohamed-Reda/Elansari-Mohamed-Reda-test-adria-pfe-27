package ma.adria.account.mcp;

import java.math.BigDecimal;
import java.util.UUID;
import ma.adria.account.domain.AccountStatus;
import ma.adria.account.service.AccountService;
import ma.adria.account.service.dto.*;
import ma.adria.account.service.exception.InvalidAccountException;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class AccountMcpTools {
    private final AccountService service;
    public AccountMcpTools(AccountService service) { this.service = service; }

    @Tool(name="account_get", description="Consulter un compte bancaire par son UUID.")
    public AccountView get(@ToolParam(description="UUID du compte") String accountId) {
        return service.getAccount(UUID.fromString(accountId));
    }

    @Tool(name="account_list", description="Lister les comptes avec pagination, page >= 0, taille de 1 a 100.")
    public AccountPage list(int page, int size) { return service.getAccounts(page, size); }

    @Tool(name="account_create", description="Creer un compte actif avec reference client, devise ISO et solde initial decimal.")
    public AccountView create(String customerReference, String currency,
            @ToolParam(description="Montant decimal exact, exemple 1000.00") String balance) {
        BigDecimal amount;
        try { amount = new BigDecimal(balance); }
        catch (NumberFormatException exception) { throw new InvalidAccountException("Solde decimal invalide."); }
        return service.createAccount(new CreateAccountCommand(customerReference, currency, amount));
    }

    @Tool(name="account_change_status", description="Bloquer ou reactiver un compte. Statuts autorises ACTIVE et BLOCKED.")
    public AccountView changeStatus(String accountId, AccountStatus status) {
        return service.changeStatus(UUID.fromString(accountId), new ChangeAccountStatusCommand(status));
    }
}
