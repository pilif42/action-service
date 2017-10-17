package uk.gov.ons.ctp.response.action.message.utility;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.support.CorrelationData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.ons.ctp.response.action.service.PublisherConfirmsService;

/**
 * Utility class to deal with Publisher Confirms for ActionInstructions
 */
@Slf4j
@Component
public class ActionInstructionConfirmCallback implements RabbitTemplate.ConfirmCallback {

  public final static String DELIVERY_FAILURE_MSG = "Delivery of message to queue failed. Cause is %s.";

  @Autowired
  private PublisherConfirmsService publisherConfirmsService;

  @Autowired
  private DlqActionInstructionCache dlqActionInstructionCache;

  @Override
  public void confirm(CorrelationData correlationData, boolean ack, String cause) {
    log.info("confirming message with ack {} - cause {} - correlationData {}", ack, cause, correlationData);

    String correlationDataId = correlationData.getId();
    DlqActionInstructionData dlqActionInstructionData = dlqActionInstructionCache.retrieve(correlationDataId);
    if (dlqActionInstructionData != null) {
      if (!ack) {
        String errorMsg = String.format(DELIVERY_FAILURE_MSG, cause);
        log.error(errorMsg);

        publisherConfirmsService.persistActionInstruction(correlationDataId, false);
      } else {
        if (dlqActionInstructionData.getMessagePrimaryKey() != null) {
          // we are in REPLAY mode
          publisherConfirmsService.removeDlqActionInstructionFromDatabase(dlqActionInstructionData);
        }
      }

      // we need to remove this correlationData as it has now been dealt with
      dlqActionInstructionCache.remove(correlationDataId);
    } else {
      log.error("Unexpected situation - no correlation data found for correlationDataId {}", correlationDataId);
    }
  }
}
