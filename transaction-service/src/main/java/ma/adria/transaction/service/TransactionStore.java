package ma.adria.transaction.service;
import java.util.*;
import ma.adria.transaction.domain.*;
import ma.adria.transaction.repository.TransactionRepository;
import ma.adria.transaction.service.dto.*;
import ma.adria.transaction.service.exception.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class TransactionStore {
 private final TransactionRepository repository;
 private final JdbcTemplate jdbc;
 public TransactionStore(TransactionRepository repository,JdbcTemplate jdbc) { this.repository=repository;this.jdbc=jdbc; }
 @Transactional
 public UUID begin(SubmitTransactionCommand c) {
  jdbc.queryForObject("select id from bank_transaction_lock where id=1 for update",Long.class);
  var existing=repository.findByRequestId(c.requestId());
  if(existing.isPresent()) {
   var t=existing.get();
   if(!t.getAccountId().equals(c.accountId()) || !Objects.equals(t.getTargetAccountId(),c.targetAccountId())
    || t.getAmount().compareTo(c.amount())!=0 || !t.getCurrency().equals(c.currency())
    || !t.getMerchantCategory().equals(c.merchantCategory()) || !t.getCountry().equals(c.country()))
      throw new TransactionConflictException();
   return t.getId();
  }
  return repository.saveAndFlush(BankTransaction.pending(c.requestId(),c.accountId(),c.targetAccountId(),c.amount(),
    c.currency(),c.merchantCategory(),c.country())).getId();
 }
 @Transactional
 public void reject(UUID id,String reason) {
  var t=repository.lockById(id).orElseThrow(()->new TransactionNotFoundException(id));
  if(t.getStatus()==TransactionStatus.PENDING)t.reject(reason);
 }
}
