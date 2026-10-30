package ma.adria.transaction.mcp;
import java.util.UUID;
import java.math.BigDecimal;
import ma.adria.transaction.service.*;
import ma.adria.transaction.service.dto.*;
import org.springframework.stereotype.Component;
import org.springframework.ai.tool.annotation.*;
@Component
public class TransactionMcpTools {
 private final TransactionService service;
 private final TransactionQueryService queries;
 public TransactionMcpTools(TransactionService service,TransactionQueryService queries) { this.service=service;this.queries=queries; }
 @Tool(name="transaction_get",description="Consulter une transaction et son etat par UUID.")
 public TransactionView get(String id) { return queries.getTransaction(UUID.fromString(id)); }
 @Tool(name="transaction_list",description="Lister les transactions source ou destinataire d'un compte, page >=0, taille de 1 a 100.")
 public TransactionPage list(String accountId,int page,int size) { return TransactionPage.from(queries.getTransactions(UUID.fromString(accountId),page,size)); }
 @Tool(name="transaction_submit",description="Effectuer un paiement ou un virement. Reutiliser le meme requestId pour une relance du meme ordre. Montant decimal exact. Etat PENDING en cas de panne.")
 public TransactionView submit(String requestId,String accountId,
   @ToolParam(required=false,description="UUID destinataire pour un virement, absent pour un paiement") String targetAccountId,
   String amount,String currency,String merchantCategory,String country) {
  return service.submit(new SubmitTransactionCommand(UUID.fromString(requestId),UUID.fromString(accountId),
   targetAccountId==null?null:UUID.fromString(targetAccountId),new BigDecimal(amount),currency,merchantCategory,country));
 }
 @Tool(name="transaction_retry",description="Reprendre une transaction PENDING sans creer un nouvel ordre ni doubler le debit.")
 public TransactionView retry(String id) { return service.retry(UUID.fromString(id)); }
}
