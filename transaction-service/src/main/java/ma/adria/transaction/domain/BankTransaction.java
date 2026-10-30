package ma.adria.transaction.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bank_transactions")
public class BankTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    @Digits(integer = 15, fraction = 4)
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @NotBlank
    @Pattern(regexp = "[A-Z]{3}")
    @Column(nullable = false, length = 3)
    private String currency;

    @NotBlank
    @Size(max = 64)
    @Column(name = "merchant_category", nullable = false, length = 64)
    private String merchantCategory;

    @NotBlank
    @Pattern(regexp = "[A-Z]{2}")
    @Column(nullable = false, length = 2)
    private String country;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected BankTransaction() {}

    public BankTransaction(UUID accountId, BigDecimal amount, String currency,
                           String merchantCategory, String country) {
        this.accountId = accountId;
        this.amount = amount;
        this.currency = currency;
        this.merchantCategory = merchantCategory;
        this.country = country;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }

    public UUID getAccountId() { return accountId; }

    public BigDecimal getAmount() { return amount; }

    public String getCurrency() { return currency; }

    public String getMerchantCategory() { return merchantCategory; }

    public String getCountry() { return country; }

    public Instant getCreatedAt() { return createdAt; }
}
