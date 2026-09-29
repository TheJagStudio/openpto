package gov.openpto.fee.repository;

import gov.openpto.fee.model.SavedQuoteEntity;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedQuoteRepository extends JpaRepository<SavedQuoteEntity, UUID> {
}
