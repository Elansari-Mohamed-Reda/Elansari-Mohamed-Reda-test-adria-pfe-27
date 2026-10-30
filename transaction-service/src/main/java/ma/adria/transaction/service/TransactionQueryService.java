package ma.adria.transaction.service;
import jakarta.validation.constraints.*;
import java.util.UUID;
import ma.adria.transaction.repository.TransactionRepository;
import ma.adria.transaction.domain.BankTransaction;
import ma.adria.transaction.service.dto.TransactionView;
import ma.adria.transaction.service.exception.TransactionNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.data.domain.*;

@Service
@Validated
@Transactional(readOnly=true)
public class TransactionQueryService {
    private final TransactionRepository repository;
    public TransactionQueryService(TransactionRepository repository) { this.repository=repository; }
    public TransactionView getTransaction(@NotNull UUID id) {
        return TransactionView.from(repository.findById(id).orElseThrow(()->new TransactionNotFoundException(id)));
    }
    public Page<TransactionView> getTransactions(@NotNull UUID accountId,@Min(0) int page,@Min(1) @Max(100) int size) {
        return repository.findRelated(accountId,PageRequest.of(page,size,
                Sort.by(Sort.Order.desc("createdAt"),Sort.Order.desc("id")))).map(TransactionView::from);
    }
}
