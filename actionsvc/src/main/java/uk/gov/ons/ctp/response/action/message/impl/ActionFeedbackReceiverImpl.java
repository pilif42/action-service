package uk.gov.ons.ctp.response.action.message.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;

import lombok.extern.slf4j.Slf4j;
import uk.gov.ons.ctp.common.error.CTPException;
import uk.gov.ons.ctp.response.action.message.ActionFeedbackReceiver;
import uk.gov.ons.ctp.response.action.message.feedback.ActionFeedback;
import uk.gov.ons.ctp.response.action.service.FeedbackService;

/**
 * The entry point for inbound feedback messages from SpringIntegration. See the
 * integration-context.xml
 *
 * This is just an annotated class that acts as the initial receiver - the work
 * is done in the feedbackservice, but having this class in this package keeps
 * spring integration related entry/exit points in one logical location
 */
@MessageEndpoint
@Slf4j
public class ActionFeedbackReceiverImpl implements ActionFeedbackReceiver {

  @Autowired
  private FeedbackService feedbackService;

  @Override
  @ServiceActivator(inputChannel = "actionFeedbackTransformed", adviceChain = "actionFeedbackRetryAdvice")
  public void acceptFeedback(ActionFeedback feedback) throws CTPException {
    log.debug("processing action feedback {} for action id {}", feedback.getOutcome(), feedback.getActionId());
    feedbackService.acceptFeedback(feedback);
  }
}
