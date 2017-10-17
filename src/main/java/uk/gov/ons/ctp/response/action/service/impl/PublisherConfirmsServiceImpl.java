package uk.gov.ons.ctp.response.action.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import uk.gov.ons.ctp.response.action.domain.model.DlqActionInstruction;
import uk.gov.ons.ctp.response.action.domain.repository.DlqActionInstructionRepository;
import uk.gov.ons.ctp.response.action.message.ActionInstructionPublisher;
import uk.gov.ons.ctp.response.action.message.instruction.Action;
import uk.gov.ons.ctp.response.action.message.utility.DlqActionInstructionCache;
import uk.gov.ons.ctp.response.action.message.utility.DlqActionInstructionData;
import uk.gov.ons.ctp.response.action.service.PublisherConfirmsService;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.util.List;

@Slf4j
@Service
public class PublisherConfirmsServiceImpl implements PublisherConfirmsService {

  public static final String UNEXPECTED_SITUATION_ERRRO_MSG =
      "Unexpected situation. Replay was triggered manually but RabbitMQ queue has been deleted.";

  @Autowired
  private DlqActionInstructionCache dlqActionInstructionCache;

  @Autowired
  private DlqActionInstructionRepository dlqActionInstructionRepository;


  @Qualifier("actionInstructionMarshaller")
  @Autowired
  private org.springframework.oxm.jaxb.Jaxb2Marshaller actionInstructionMarshaller;

  @Autowired
  private ActionInstructionPublisher actionInstructionPublisher;

  @Override
  public void persistActionInstruction(String correlationDataId, boolean msgReturned) {
    log.info("entering persist with correlationDataId {} - msgReturned {}", correlationDataId, msgReturned);

    // Pause below is required to prevent exception 'Row was updated or deleted by another transaction'
    try {
      Thread.sleep(3000);
    } catch (InterruptedException e) {
    }

    DlqActionInstructionData correlationData = dlqActionInstructionCache.retrieve(correlationDataId);

    if (correlationData.getMessagePrimaryKey() != null) {
      if (msgReturned) {
        /**
         * scenario where the queue has been deleted. This should never happen because, at this stage, there has
         * already been a manual intervention to solve the RabbitMQ config and it is considered correct for a
         * replayActionInstruction.
         *
         * We throw a RuntimeException here to ensure that ActionInstructionConfirmCallback.confirm is NOT played as
         * ack would be at true because the message reached the Exchange and we would remove the message from the DB.
         * This would be incorrect as the message has NOT reached the RabbitMQ queue.
         */
        dlqActionInstructionCache.remove(correlationDataId);

        log.error(UNEXPECTED_SITUATION_ERRRO_MSG);
        throw new RuntimeException(UNEXPECTED_SITUATION_ERRRO_MSG);
      } else {
        // we do nothing because we are trying to replay an ActionInstruction which is already stored
        // in DB (table dlq_actioninstruction).
      }
    } else {
      storeFailedActionInstructionToDB(correlationData);
    }
  }

  @Override public void replayActionInstruction() {
    List<DlqActionInstruction> actionInstructionsToReplay = dlqActionInstructionRepository.findAll();

    for (DlqActionInstruction dlqActionInstruction : actionInstructionsToReplay) {
      String handler = dlqActionInstruction.getHandler();
      String xmlMessage = dlqActionInstruction.getMessage();
      actionInstructionPublisher.sendActionInstruction(handler, prepareActionInstruction(xmlMessage),
          dlqActionInstruction.getActionInstructionPK());
    }
  }

  @Override
  public void removeDlqActionInstructionFromDatabase(DlqActionInstructionData correlationData) {
    Integer primaryKeyValue = correlationData.getMessagePrimaryKey();
    DlqActionInstruction actionInstruction = dlqActionInstructionRepository.findOne(primaryKeyValue);
    if (actionInstruction != null) {
      dlqActionInstructionRepository.delete(primaryKeyValue);
      log.info("actionInstruction with primary key {} now deleted", primaryKeyValue);
    } else {
      log.error("unexpected situation. No actionInstruction found with primary key {}", primaryKeyValue);
    }
  }

  /**
   * To transform a marshalled ActionInstruction into an ActionInstruction object that will be published to queue
   *
   * @param xmlMessage a marshalled ActionInstruction message
   * @return an ActionInstruction object
   */
  private Action prepareActionInstruction (String xmlMessage) {
    Source xmlInput = new StreamSource(new StringReader(xmlMessage));
    return (Action)actionInstructionMarshaller.unmarshal(xmlInput);
  }

  /**
   * This utility method stores failed ActionInstruction messages into the database
   *
   * @param correlationData the data required to store in DB
   */
  private void storeFailedActionInstructionToDB(DlqActionInstructionData correlationData) {
    String handler = correlationData.getHandler();
    String message = correlationData.getMessage();

    if (!StringUtils.isEmpty(handler) && !StringUtils.isEmpty(message)) {
      DlqActionInstruction dlqActionInstruction = new DlqActionInstruction();
      dlqActionInstruction.setHandler(handler);
      dlqActionInstruction.setMessage(message);
      dlqActionInstructionRepository.saveAndFlush(dlqActionInstruction);
      log.info("dlqActionInstruction msg now stored in db - ready for replay");
    } else {
      log.error("Unexpected situation. handler {} - message {}", handler, message);
    }
  }
}
