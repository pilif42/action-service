package uk.gov.ons.ctp.response.action.endpoint;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.ons.ctp.common.endpoint.CTPEndpoint;
import uk.gov.ons.ctp.common.error.CTPException;
import uk.gov.ons.ctp.response.action.message.instruction.ActionInstruction;
import uk.gov.ons.ctp.response.action.service.PublisherConfirmsService;

/**
 * The REST endpoint controller for ActionSvc Support tasks
 */
@RestController
@RequestMapping(value = "/support", produces = "application/json")
@Slf4j
public class SupportEndpoint implements CTPEndpoint {

  public static final String UNEXPECTED_MSG_TYPE = "Unexpected message type: %s";

  @Autowired
  private PublisherConfirmsService publisherConfirmsService;

  /**
   * To replay messages that are stored in the database because they failed reaching a RabbitMQ queue when initially
   * published.
   *
   * @return the response 204 if all ok
   * @throws CTPException something went wrong
   */
  @RequestMapping(value = "/replayMessages/{msgType}", method = RequestMethod.POST)
  public ResponseEntity<?> replayMessages(@PathVariable("msgType") final String msgType) throws CTPException {
    log.info("Entering replayMessages with msgType {}", msgType);
    if (msgType.equalsIgnoreCase(ActionInstruction.class.getSimpleName())) {
      publisherConfirmsService.replayActionInstruction();
    } else {
      throw new CTPException(CTPException.Fault.RESOURCE_NOT_FOUND, String.format(UNEXPECTED_MSG_TYPE, msgType));
    }
    return ResponseEntity.noContent().build();
  }
}
