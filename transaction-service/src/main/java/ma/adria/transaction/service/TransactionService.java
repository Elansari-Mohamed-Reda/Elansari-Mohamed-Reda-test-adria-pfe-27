package ma.adria.transaction.service;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import ma.adria.transaction.service.dto.*;
import ma.adria.transaction.service.exception.*;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
@Service
@Validated
public class TransactionService {
 private final TransactionStore store;
 private final TransactionExecutor executor;
 private final TransactionQueryService queries;
 private final TransactionPolicy policy;
 public TransactionService(TransactionStore store,TransactionExecutor executor,TransactionQueryService queries,TransactionPolicy policy) {
  this.store=store;this.executor=executor;this.queries=queries;this.policy=policy;
 }
 public TransactionView submit(@NotNull @Valid SubmitTransactionCommand command) {
  policy.validateCommand(command.basic());
  if(command.accountId().equals(command.targetAccountId()))throw new TransactionRejectedException("Comptes source et destinataire identiques.");
  return retry(store.begin(command));
 }
 public TransactionView retry(@NotNull UUID id) {
  try { return executor.execute(id); }
  catch(TransactionRejectedException rejected) { store.reject(id,rejected.getMessage());throw rejected; }
  catch(AccountServiceUnavailableException | DataAccessException unavailable) {
   return queries.getTransaction(id);
  }
 }
}
