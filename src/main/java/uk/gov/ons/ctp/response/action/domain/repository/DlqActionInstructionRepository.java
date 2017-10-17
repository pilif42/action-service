package uk.gov.ons.ctp.response.action.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.ctp.response.action.domain.model.DlqActionInstruction;

/**
 * JPA Data Repository.
 */
@Repository
@Transactional(readOnly = true)
public interface DlqActionInstructionRepository extends JpaRepository<DlqActionInstruction, Integer> {
}
