package ma.adria.transaction.service.dto;
import java.math.BigDecimal;
import java.util.UUID;
public record AccountSnapshot(UUID id, String status, String currency, BigDecimal balance) {}
