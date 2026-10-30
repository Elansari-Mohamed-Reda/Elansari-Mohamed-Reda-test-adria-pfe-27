package ma.adria.transaction.web.rest;
import java.net.URI;
import java.util.UUID;
import jakarta.validation.Valid;
import ma.adria.transaction.domain.TransactionStatus;
import ma.adria.transaction.service.*;
import ma.adria.transaction.service.dto.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/transactions")
public class TransactionRestController {
 private final TransactionService service;
 private final TransactionQueryService queries;
 public TransactionRestController(TransactionService service,TransactionQueryService queries) { this.service=service;this.queries=queries; }
 @PostMapping public ResponseEntity<TransactionView> create(@Valid @RequestBody SubmitTransactionCommand command) {
  var result=service.submit(command);
  return ResponseEntity.status(result.status()==TransactionStatus.PENDING?202:201)
    .location(URI.create("/api/transactions/"+result.id())).body(result);
 }
 @GetMapping("/{id}") public TransactionView get(@PathVariable UUID id) { return queries.getTransaction(id); }
 @GetMapping public TransactionPage list(@RequestParam UUID accountId,
   @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
  return TransactionPage.from(queries.getTransactions(accountId,page,size));
 }
 @PostMapping("/{id}/retry") public ResponseEntity<TransactionView> retry(@PathVariable UUID id) {
  var result=service.retry(id);
  return ResponseEntity.status(result.status()==TransactionStatus.PENDING?202:200).body(result);
 }
}
