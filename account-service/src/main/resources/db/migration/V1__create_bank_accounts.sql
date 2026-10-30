CREATE TABLE bank_accounts (
    id UUID PRIMARY KEY,
    customer_reference VARCHAR(120) NOT NULL,
    status VARCHAR(16) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    balance NUMERIC(19, 4) NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT ck_bank_accounts_status CHECK (status IN ('ACTIVE', 'BLOCKED')),
    CONSTRAINT ck_bank_accounts_balance CHECK (balance >= 0)
);
