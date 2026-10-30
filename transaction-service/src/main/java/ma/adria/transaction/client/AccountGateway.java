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

    public java.util.Optional<MovementReceipt> findMovement(UUID id) {
        return breakers.create("accountService").run(()->{
            try { return java.util.Optional.of(client.findMovement(id)); }
            catch(FeignException.NotFound missing) { return java.util.Optional.empty(); }
        },failure->{ throw new AccountServiceUnavailableException(failure); });
    }
    public MovementReceipt applyMovement(MovementRequest command) {
        return breakers.create("accountService").run(()->{
            try { return client.applyMovement(command); }
            catch(FeignException failure) {
                if(failure.status()==400 || failure.status()==404 || failure.status()==422)
                    throw new TransactionRejectedException("Mouvement refuse : compte, devise ou solde invalide.");
                throw failure;
            }
        },failure->{
            Throwable cause=failure;
            while((cause instanceof java.util.concurrent.ExecutionException || cause instanceof java.util.concurrent.CompletionException)
                    && cause.getCause()!=null)cause=cause.getCause();
            if(cause instanceof TransactionRejectedException rejected)throw rejected;
            throw new AccountServiceUnavailableException(cause);
        });
    }
}
