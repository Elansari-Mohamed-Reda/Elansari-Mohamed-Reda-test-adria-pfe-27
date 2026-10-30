package ma.adria.transaction.service.dto;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.Instant;
import ma.adria.transaction.domain.BankTransaction;
public record TransactionView(UUID id, UUID accountId, BigDecimal amount, String currency,
                              String merchantCategory, String country, Instant createdAt) {
    public static TransactionView from(BankTransaction t) {
        return new TransactionView(t.getId(),t.getAccountId(),t.getAmount(),t.getCurrency(),
                t.getMerchantCategory(),t.getCountry(),t.getCreatedAt());
    }
}
