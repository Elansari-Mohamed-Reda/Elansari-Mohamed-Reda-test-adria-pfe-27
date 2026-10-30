package ma.adria.transaction.service;
import java.util.UUID;
import ma.adria.transaction.client.*;
import ma.adria.transaction.domain.*;
import ma.adria.transaction.repository.TransactionRepository;
import ma.adria.transaction.service.dto.*;
import ma.adria.transaction.service.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class TransactionExecutor {
 private final TransactionRepository repository;
 private final AccountGateway gateway;
 private final TransactionPolicy policy;
 public TransactionExecutor(TransactionRepository repository,AccountGateway gateway,TransactionPolicy policy) {
  this.repository=repository;this.gateway=gateway;this.policy=policy;
 }
 @Transactional
 public TransactionView execute(UUID id) {
  var t=repository.lockById(id).orElseThrow(()->new TransactionNotFoundException(id));
  if(t.getStatus()==TransactionStatus.COMPLETED)return TransactionView.from(t);
  if(t.getStatus()==TransactionStatus.REJECTED)throw new TransactionRejectedException(t.getFailureReason());
  // Une reponse reseau perdue ne doit pas provoquer un deuxieme debit.
  var receipt=gateway.findMovement(id);
  if(receipt.isEmpty()) {
   var command=new CreateTransactionCommand(t.getAccountId(),t.getAmount(),t.getCurrency(),t.getMerchantCategory(),t.getCountry());
   policy.validate(command,gateway.getAccount(t.getAccountId()));
   if(t.getTargetAccountId()!=null) {
    var target=gateway.getAccount(t.getTargetAccountId());
    if(!"ACTIVE".equals(target.status()) || !t.getCurrency().equals(target.currency()))
       throw new TransactionRejectedException("Compte destinataire inactif ou devise incompatible.");
   }
   var applied=gateway.applyMovement(new MovementRequest(id,t.getTargetAccountId()==null?"DEBIT":"TRANSFER",
      t.getAccountId(),t.getTargetAccountId(),t.getAmount(),t.getCurrency()));
   if(!matches(t,applied))throw new AccountServiceUnavailableException(new IllegalStateException("Confirmation invalide."));
  } else if(!matches(t,receipt.get())) {
   throw new AccountServiceUnavailableException(new IllegalStateException("Confirmation incoherente."));
  }
  t.complete();
  repository.flush();
  return TransactionView.from(t);
 }

 private boolean matches(BankTransaction t,MovementReceipt receipt) {
  return receipt!=null && t.getId().equals(receipt.operationId())
   && t.getAccountId().equals(receipt.sourceAccountId())
   && java.util.Objects.equals(t.getTargetAccountId(),receipt.targetAccountId())
   && receipt.amount()!=null && t.getAmount().compareTo(receipt.amount())==0
   && t.getCurrency().equals(receipt.currency())
   && (t.getTargetAccountId()==null?"DEBIT":"TRANSFER").equals(receipt.type());
 }
}
