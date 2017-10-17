package uk.gov.ons.ctp.response.action.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.sourceforge.cobertura.CoverageIgnore;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.io.Serializable;

/**
 * Domain model object.
 */
@CoverageIgnore
@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "dlq_actioninstruction", schema = "action")
public class DlqActionInstruction implements Serializable {

  private static final long serialVersionUID = -8303976639716007779L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY, generator = "dlq_actioninstructionseq_gen")
  @GenericGenerator(name = "dlq_actioninstructionseq_gen", strategy = "org.hibernate.id.enhanced.SequenceStyleGenerator",
      parameters = {
          @org.hibernate.annotations.Parameter(name = "sequence_name", value = "action.dlq_actioninstructionseq"),
          @org.hibernate.annotations.Parameter(name = "increment_size", value = "1")
      })
  @Column(name = "actioninstructionPK")
  private int actionInstructionPK;

  @Column(name = "handler")
  private String handler;

  @Column(name = "message")
  private String message;
}
