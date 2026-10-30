package ma.adria.transaction.client;
import java.util.UUID;
import feign.FeignException;
import ma.adria.transaction.service.dto.AccountSnapshot;
import ma.adria.transaction.service.exception.*;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

@Component
public class AccountGateway {
    private final AccountClient client;
    private final CircuitBreakerFactory<?,?> breakers;
    public AccountGateway(AccountClient client, CircuitBreakerFactory<?,?> breakers) {
        this.client=client; this.breakers=breakers;
    }
    public AccountSnapshot getAccount(UUID id) {
        return breakers.create("accountService").run(()->{
            try { return client.getAccount(id); }
            catch (FeignException.NotFound exception) {
                throw new TransactionRejectedException("Compte introuvable : "+id);
            }
        }, failure->{
            Throwable cause=failure;
            while ((cause instanceof java.util.concurrent.ExecutionException
                    || cause instanceof java.util.concurrent.CompletionException) && cause.getCause()!=null) {
                cause=cause.getCause();
            }
            if(cause instanceof TransactionRejectedException rejected) throw rejected;
            throw new AccountServiceUnavailableException(cause);
        });
    }
}
