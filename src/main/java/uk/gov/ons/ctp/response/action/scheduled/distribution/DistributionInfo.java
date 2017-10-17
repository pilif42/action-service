package uk.gov.ons.ctp.response.action.scheduled.distribution;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import net.sourceforge.cobertura.CoverageIgnore;
import uk.gov.ons.ctp.common.health.ScheduledHealthInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * info regarding the last action distribution to handlers
 *
 */
@CoverageIgnore
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DistributionInfo extends ScheduledHealthInfo {

  /**
   * the type of instruction
   */
  public enum Instruction {
    REQUEST, CANCEL_REQUEST
  };

  private List<InstructionCount> instructionCounts = new ArrayList<>();

}
