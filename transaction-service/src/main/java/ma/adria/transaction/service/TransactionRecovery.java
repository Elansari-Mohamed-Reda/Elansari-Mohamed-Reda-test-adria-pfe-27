package ma.adria.transaction.service;
import ma.adria.transaction.domain.TransactionStatus;
import ma.adria.transaction.repository.TransactionRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;
import org.slf4j.LoggerFactory;
@Configuration
@EnableScheduling
@ConditionalOnProperty(name="bank.transaction.recovery.enabled",havingValue="true",matchIfMissing=true)
public class TransactionRecovery {
 private final TransactionRepository repository;
 private final TransactionService service;
 public TransactionRecovery(TransactionRepository repository,TransactionService service) { this.repository=repository;this.service=service; }
 @Scheduled(fixedDelayString="${bank.transaction.recovery.delay-ms:30000}",initialDelayString="${bank.transaction.recovery.delay-ms:30000}")
 public void recover() {
  for(var t:repository.findTop100ByStatusOrderByCreatedAtAsc(TransactionStatus.PENDING)) {
   try { service.retry(t.getId()); }
   catch(RuntimeException failure) { LoggerFactory.getLogger(getClass()).warn("Reprise impossible pour {} : {}",t.getId(),failure.getClass().getSimpleName()); }
  }
 }
}
