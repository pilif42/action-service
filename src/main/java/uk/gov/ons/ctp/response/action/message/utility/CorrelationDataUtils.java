package uk.gov.ons.ctp.response.action.message.utility;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import uk.gov.ons.ctp.response.action.message.instruction.Action;

import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;

/**
 * Utility class to provide correlation data required by Publisher Confirms
 */
@Component
public class CorrelationDataUtils {

  @Qualifier("actionInstructionMarshaller")
  @Autowired
  private org.springframework.oxm.jaxb.Jaxb2Marshaller actionInstructionMarshaller;

  /**
   * To build data that will be required if a REPLAY is necessary
   *
   * @param handler the handler which determines onto which queue Actions are published
   * @param action the published Action
   * @param dlqActionInstructionPK the dlqActionInstructionPK only NON null in REPLAY mode
   * @return the DlqActionInstructionData
   */
  public DlqActionInstructionData provideDlqActionInstructionData(String handler, Action action, Integer dlqActionInstructionPK) {
    return DlqActionInstructionData.builder()
        .handler(handler)
        .message(marshalAction(action).toString())
        .messagePrimaryKey(dlqActionInstructionPK)
        .build();
  }

  /**
   * To marshal an Action
   *
   * @param action the object to marshal
   * @return the stringbuffer containing the marshalled object
   */
  private StringBuffer marshalAction(Action action) {
    StringWriter outWriter = new StringWriter();
    StreamResult result = new StreamResult(outWriter);
    actionInstructionMarshaller.marshal(action, result);
    return outWriter.getBuffer();
  }
}









