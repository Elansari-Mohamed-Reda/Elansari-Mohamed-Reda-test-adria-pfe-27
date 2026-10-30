package ma.adria.account.service;
import java.util.*;
import java.math.*;
import java.time.Instant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.adria.account.domain.*;
import ma.adria.account.repository.AccountRepository;
import ma.adria.account.service.dto.*;
import ma.adria.account.service.exception.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class AccountMovementService {
 private final AccountRepository accounts;
 private final JdbcTemplate jdbc;
 private final ObjectMapper mapper;
 public AccountMovementService(AccountRepository accounts,JdbcTemplate jdbc,ObjectMapper mapper) {
  this.accounts=accounts;this.jdbc=jdbc;this.mapper=mapper;
 }
 @Transactional(readOnly=true)
 public MovementView get(@NotNull UUID operationId) {
  return find(operationId).orElseThrow(()->new AccountNotFoundException(operationId));
 }
 private Optional<MovementView> find(UUID id) {
  var rows=jdbc.queryForList("select result_json from bank_movements where operation_id=?",String.class,id);
  if(rows.isEmpty())return Optional.empty();
  try { return Optional.of(mapper.readValue(rows.get(0),MovementView.class)); }
  catch(Exception exception) { throw new IllegalStateException("Mouvement illisible.",exception); }
 }
 @Transactional
 public MovementView apply(@NotNull @Valid MovementCommand command) {
  // Verrou persistant commun : deduplication et mouvements serialises entre instances.
  jdbc.queryForObject("select id from bank_movement_lock where id=1 for update",Long.class);
  var existing=find(command.operationId());
  if(existing.isPresent()) {
   String fingerprint=jdbc.queryForObject("select fingerprint from bank_movements where operation_id=?",String.class,command.operationId());
   if(!command.fingerprint().equals(fingerprint))throw new MovementConflictException();
   return existing.get();
  }
  boolean debit=command.type()!=MovementCommand.Type.CREDIT;
  boolean credit=command.type()!=MovementCommand.Type.DEBIT;
  if((debit && command.sourceAccountId()==null) || (!debit && command.sourceAccountId()!=null)
    ||(credit && command.targetAccountId()==null) ||(!credit && command.targetAccountId()!=null))
   throw new InvalidAccountException("References de comptes incompatibles avec le mouvement.");
  if(debit && credit && command.sourceAccountId().equals(command.targetAccountId()))
   throw new InvalidAccountException("Les comptes du virement doivent etre distincts.");
  BigDecimal amount;
  try {
   int scale=Currency.getInstance(command.currency()).getDefaultFractionDigits();
   if(scale<0 || scale>4)throw new IllegalArgumentException();
   amount=command.amount().setScale(scale,RoundingMode.UNNECESSARY);
  } catch(IllegalArgumentException | ArithmeticException exception) {
   throw new InvalidAccountException("Devise ou precision invalide.");
  }
  BankAccount source=debit?load(command.sourceAccountId(),command.currency()):null;
  BankAccount target=credit?load(command.targetAccountId(),command.currency()):null;
  if(source!=null && source.getBalance().compareTo(amount)<0)throw new InvalidAccountException("Solde insuffisant.");
  if(target!=null && target.getBalance().add(amount).compareTo(new BigDecimal("999999999999999.9999"))>0)
   throw new InvalidAccountException("Solde maximal depasse.");
  if(source!=null)source.debit(amount);
  if(target!=null)target.credit(amount);
  accounts.flush();
  var view=new MovementView(command.operationId(),command.sourceAccountId(),command.targetAccountId(),amount,
    command.currency(),command.type(),source==null?null:source.getBalance(),target==null?null:target.getBalance());
  try {
   jdbc.update("insert into bank_movements(operation_id,fingerprint,result_json,created_at) values(?,?,?,?)",
    command.operationId(),command.fingerprint(),mapper.writeValueAsString(view),java.sql.Timestamp.from(Instant.now()));
  } catch(com.fasterxml.jackson.core.JsonProcessingException exception) { throw new IllegalStateException(exception); }
  return view;
 }
 private BankAccount load(UUID id,String currency) {
  var account=accounts.findById(id).orElseThrow(()->new AccountNotFoundException(id));
  if(account.getStatus()!=AccountStatus.ACTIVE)throw new InvalidAccountException("Le compte doit etre actif.");
  if(!currency.equals(account.getCurrency()))throw new InvalidAccountException("Devises incompatibles.");
  return account;
 }
}
