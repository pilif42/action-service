package uk.gov.ons.ctp.response.action.service;

import uk.gov.ons.ctp.common.error.CTPException;
import uk.gov.ons.ctp.response.action.domain.model.Action;

/**
 * The service to go from Action to ActionRequests, ActionCancels. It then publishes them to downstream services
 * (handlers).
 *
 * It enriches Actions with case, questionnaire, address, caseevent details, etc. It then updates its own action table
 * to change the action state to PENDING, posts a new CaseEvent to the Case Service, and constructs an outbound
 * ActionRequest/ActionCancel instance.
 */
public interface ActionProcessingService {
  /**
   * To produce an ActionRequest and publish it to the relevant Handler.
   *
   * @param action the Action
   * @throws CTPException
   */
  void processActionRequest(Action action) throws CTPException;

  /**
   * To produce an ActionCancel and publish it to the relevant Handler.
   *
   * @param action the Action
   * @throws CTPException
   */
  void processActionCancel(Action action) throws CTPException;
}
