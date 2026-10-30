CREATE TABLE bank_transactions (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    merchant_category VARCHAR(64) NOT NULL,
    country VARCHAR(2) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_transactions_positive_amount CHECK (amount > 0)
);
CREATE INDEX idx_transactions_account_created ON bank_transactions(account_id, created_at);
