package ma.adria.transaction.service.dto;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.Instant;
import ma.adria.transaction.domain.*;
public record TransactionView(UUID id,UUID accountId,BigDecimal amount,String currency,String merchantCategory,
 String country,Instant createdAt,UUID requestId,UUID targetAccountId,TransactionStatus status,String failureReason) {
 public static TransactionView from(BankTransaction t) {
  return new TransactionView(t.getId(),t.getAccountId(),t.getAmount(),t.getCurrency(),t.getMerchantCategory(),
    t.getCountry(),t.getCreatedAt(),t.getRequestId(),t.getTargetAccountId(),t.getStatus(),t.getFailureReason());
 }
}
