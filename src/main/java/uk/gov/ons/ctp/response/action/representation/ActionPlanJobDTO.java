package uk.gov.ons.ctp.response.action.representation;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Date;
import java.util.UUID;

/**
 * Domain model object for representation.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class ActionPlanJobDTO {

  private static final int CREATED_BY_MAX = 50;
  private static final int CREATED_BY_MIN = 2;

  /**
   * enum for action plan job state
   */
  public enum ActionPlanJobState {
    SUBMITTED, STARTED, COMPLETED, FAILED;
  }

  private UUID id;
  private UUID actionPlanId;

  @NotNull
  @Size(min = CREATED_BY_MIN, max = CREATED_BY_MAX)
  private String createdBy;
  private String state;

  private Date createdDateTime;
  private Date updatedDateTime;
}
