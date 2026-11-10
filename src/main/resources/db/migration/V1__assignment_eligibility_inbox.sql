CREATE TABLE courier_assignment_eligibility (
    command_event_id VARCHAR(36) PRIMARY KEY,
    operation_id VARCHAR(36) NOT NULL UNIQUE,
    shipment_id VARCHAR(36) NOT NULL,
    courier_id VARCHAR(36) NOT NULL,
    result_event_id VARCHAR(36) NOT NULL UNIQUE,
    occurred_at TIMESTAMP(6) NOT NULL,
    approved BOOLEAN NOT NULL,
    publication_status VARCHAR(12) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    available_at TIMESTAMP(6),
    lease_token VARCHAR(36)
);

CREATE INDEX idx_courier_eligibility_due
    ON courier_assignment_eligibility(publication_status, available_at, occurred_at);
