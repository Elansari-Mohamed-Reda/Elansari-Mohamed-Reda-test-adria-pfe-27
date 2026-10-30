package ma.adria.transaction.web.graphql;
import java.math.BigDecimal;
import java.util.UUID;
import ma.adria.transaction.service.*;
import ma.adria.transaction.service.dto.*;
import org.springframework.stereotype.Controller;
import org.springframework.graphql.data.method.annotation.*;
@Controller
public class TransactionGraphqlController {
 private final TransactionService service;
 private final TransactionQueryService queries;
 public TransactionGraphqlController(TransactionService service,TransactionQueryService queries) { this.service=service;this.queries=queries; }
 @QueryMapping public TransactionView transaction(@Argument UUID id) { return queries.getTransaction(id); }
 @QueryMapping public TransactionPage transactions(@Argument UUID accountId,@Argument int page,@Argument int size) {
  return TransactionPage.from(queries.getTransactions(accountId,page,size));
 }
 @MutationMapping public TransactionView createTransaction(@Argument SubmitTransactionInput input) { return service.submit(input.command()); }
 @MutationMapping public TransactionView retryTransaction(@Argument UUID id) { return service.retry(id); }
 @SchemaMapping(typeName="Transaction",field="createdAt") public String createdAt(TransactionView transaction) { return transaction.createdAt().toString(); }
 public record SubmitTransactionInput(String requestId,String accountId,String targetAccountId,String amount,
                                      String currency,String merchantCategory,String country) {
  public SubmitTransactionCommand command() {
   return new SubmitTransactionCommand(UUID.fromString(requestId),UUID.fromString(accountId),
    targetAccountId==null?null:UUID.fromString(targetAccountId),new BigDecimal(amount),currency,merchantCategory,country);
  }
 }
}
