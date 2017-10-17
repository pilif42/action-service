package uk.gov.ons.ctp.response.action.scheduled.plan;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import net.sourceforge.cobertura.CoverageIgnore;
import uk.gov.ons.ctp.common.health.ScheduledHealthInfo;
import uk.gov.ons.ctp.response.action.domain.model.ActionPlanJob;

import java.util.ArrayList;
import java.util.List;

/**
 * info regarding the last scheduled execution of action plans for this instance only
 * bear in mind when reading this at runtime that this instance may have skipped plans
 * if they are being executed by another service instance at the same time OR within the
 * last scheduled period.
 */
@CoverageIgnore
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PlanExecutionInfo extends ScheduledHealthInfo {

  private List<ActionPlanJob> executedJobs = new ArrayList<>();
}
