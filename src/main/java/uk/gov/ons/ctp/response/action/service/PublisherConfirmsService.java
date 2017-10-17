package uk.gov.ons.ctp.response.action.service;

import uk.gov.ons.ctp.response.action.message.utility.DlqActionInstructionData;

/**
 * The service dedicated to actions required when a published message fails to reach a RabbitMQ queue. This occurs
 * when an exchange or a queue is badly configured/deleted.
 */
public interface PublisherConfirmsService {
  /**
   * To store messages that failed publishing. This is used when a message does not reach the queue because the exchange
   * is incorrectly set up, the queue has been deleted or?
   *
   * @param correlationDataId the correlation data id that will enable us to get detailed data about the failed message
   * @param msgReturned true if the message was returned (ie case where the queue was deleted)
   */
  void persistActionInstruction(String correlationDataId, boolean msgReturned);

  /**
   * To replay ActionInstruction messages that are currently stored in the database in the table dlq_actioninstruction
   */
  void replayActionInstruction();

  /**
   * To remove from the database ActionInstruction messages that have been successfully published on a RabbitMQ queue
   *
   * @param correlationData the data required to identify successful messages
   */
  void removeDlqActionInstructionFromDatabase(DlqActionInstructionData correlationData);
}
