package uk.gov.ons.ctp.response.action.message.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.support.CorrelationData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.messaging.handler.annotation.Header;
import uk.gov.ons.ctp.response.action.message.ActionInstructionPublisher;
import uk.gov.ons.ctp.response.action.message.instruction.Action;
import uk.gov.ons.ctp.response.action.message.instruction.ActionCancel;
import uk.gov.ons.ctp.response.action.message.instruction.ActionInstruction;
import uk.gov.ons.ctp.response.action.message.instruction.ActionRequest;
import uk.gov.ons.ctp.response.action.message.utility.CorrelationDataUtils;
import uk.gov.ons.ctp.response.action.message.utility.DlqActionInstructionCache;
import uk.gov.ons.ctp.response.action.message.utility.DlqActionInstructionData;

import java.util.UUID;

/**
 * This class is used to publish ActionInstructions to the downstream handlers.
 */
@Slf4j
@MessageEndpoint
public class ActionInstructionPublisherImpl implements ActionInstructionPublisher {

  public static final String HEADER_CORRELATION_DATA_ID = "correlationDataId";

  @Qualifier("actionInstructionRabbitTemplate")
  @Autowired
  private RabbitTemplate rabbitTemplate;

  @Autowired
  private CorrelationDataUtils correlationDataUtils;

  @Autowired
  private DlqActionInstructionCache dlqActionInstructionCache;

  public static final String ACTION = "Action.";
  public static final String BINDING = ".binding";

  public void sendActionInstruction(@Header("HANDLER") String handler, Action action) {
    log.debug("Entering sendActionInstruction with handler {} and action {}", handler, action);
    sendActionInstruction(handler, action, null);
  }

  public void sendActionInstruction(@Header("HANDLER") String handler, Action action, Integer dlqActionInstructionPK) {
    log.debug("Entering sendActionInstruction with handler {} and action {}", handler, action);

    ActionInstruction instruction = new ActionInstruction();
    if (action instanceof ActionRequest) {
      instruction.setActionRequest((ActionRequest)action);
    } else if (action instanceof ActionCancel) {
      instruction.setActionCancel((ActionCancel)action);
    }

    String correlationDataId = UUID.randomUUID().toString();
    DlqActionInstructionData dlqActionInstructionData = correlationDataUtils.provideDlqActionInstructionData(handler,
        action, dlqActionInstructionPK);
    dlqActionInstructionCache.store(correlationDataId, dlqActionInstructionData);

    // Required for correlating publisher confirms to sent messages.
    CorrelationData correlationData = new CorrelationData(correlationDataId);

    // Required for correlating publisher returns to sent messages.
    MessagePostProcessor messagePostProcessor = new MessagePostProcessor() {
      public Message postProcessMessage(Message message) throws AmqpException {
        message.getMessageProperties().setHeader(HEADER_CORRELATION_DATA_ID, correlationDataId);
        return message;
      }
    };

    String routingKey = String.format("%s%s%s", ACTION, handler, BINDING);
    rabbitTemplate.convertAndSend(routingKey, instruction, messagePostProcessor, correlationData);
    log.info("actionInstruction published");
  }
}
