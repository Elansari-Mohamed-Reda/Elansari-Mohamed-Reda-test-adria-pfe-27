package ma.adria.transaction.repository;

import java.util.UUID;
import ma.adria.transaction.domain.BankTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TransactionRepository extends JpaRepository<BankTransaction, UUID> {
    Page<BankTransaction> findByAccountId(UUID accountId, Pageable pageable);
    java.util.Optional<BankTransaction> findByRequestId(UUID requestId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select t from BankTransaction t where t.id=:id")
    java.util.Optional<BankTransaction> lockById(@org.springframework.data.repository.query.Param("id") UUID id);
    java.util.List<BankTransaction> findTop100ByStatusOrderByCreatedAtAsc(ma.adria.transaction.domain.TransactionStatus status);
    @org.springframework.data.jpa.repository.Query("select t from BankTransaction t where t.accountId=:accountId or t.targetAccountId=:accountId")
    Page<BankTransaction> findRelated(@org.springframework.data.repository.query.Param("accountId") UUID accountId,Pageable pageable);
}
