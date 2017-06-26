package uk.gov.ons.ctp.response.action.message;

import uk.gov.ons.ctp.common.error.CTPException;
import uk.gov.ons.ctp.response.action.message.feedback.ActionFeedback;

/**
 * Interface for the receipt of feedback messages from the Spring Integration
 * inbound message queue
 */
public interface ActionFeedbackReceiver {

  /**
   * impl will be called with the deserialised AMQ message sent from downstream
   * handlers
   *
   * @param feedback the java representation of the AMQ message body
   * @throws CTPException CTPException thrown
   */
  void acceptFeedback(ActionFeedback feedback) throws CTPException;

}
