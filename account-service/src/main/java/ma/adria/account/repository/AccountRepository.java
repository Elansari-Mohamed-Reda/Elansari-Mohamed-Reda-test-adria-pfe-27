package ma.adria.account.repository;

import ma.adria.account.domain.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccountRepository extends JpaRepository<BankAccount, UUID> {
}
