package ma.adria.transaction.service;
import ma.adria.transaction.client.AccountGateway;
import ma.adria.transaction.service.dto.*;
import org.springframework.stereotype.Service;

@Service
public class TransactionPreparationService {
    private final AccountGateway gateway;
    private final TransactionPolicy policy;
    public TransactionPreparationService(AccountGateway gateway, TransactionPolicy policy) {
        this.gateway=gateway; this.policy=policy;
    }
    public AccountSnapshot validateAgainstCurrentAccount(CreateTransactionCommand command) {
        policy.validateCommand(command);
        AccountSnapshot account=gateway.getAccount(command.accountId());
        policy.validate(command,account);
        return account;
    }
}
