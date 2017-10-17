package uk.gov.ons.ctp.response.action.message;

import org.springframework.messaging.handler.annotation.Header;
import uk.gov.ons.ctp.response.action.message.instruction.Action;

/**
 * Interface to publish ActionInstructions to downstream handlers
 */
public interface ActionInstructionPublisher {

  /**
   * This implementation is responsible for publishing ActionInstructions in the default scenario.
   *
   * @param handler the handler that the outbound flow should send to - taken directly from the Actions ActionType
   * @param action the action to publish
   */
  void sendActionInstruction(String handler, Action action);

  /**
   * This implementation is responsible for publishing ActionInstructions in the REPLAY scenario. REPLAY is invoked when
   * messages have been stored in the database because they failed publishing onto a queue. We have solved the RabbitMQ
   * configuration and we are now ready to replay them.
   *
   * @param handler the handler that the outbound flow should send to - taken directly from the Actions ActionType
   * @param action the action to publish
   * @param dlqActionInstructionPK the dlqActionInstructionPK when are in REPLAY mode
   */
  void sendActionInstruction(@Header("HANDLER") String handler, Action action, Integer dlqActionInstructionPK);
}
