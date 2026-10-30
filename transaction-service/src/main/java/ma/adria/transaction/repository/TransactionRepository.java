package ma.adria.transaction.repository;

import java.util.UUID;
import ma.adria.transaction.domain.BankTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TransactionRepository extends JpaRepository<BankTransaction, UUID> {
    Page<BankTransaction> findByAccountId(UUID accountId, Pageable pageable);
}
