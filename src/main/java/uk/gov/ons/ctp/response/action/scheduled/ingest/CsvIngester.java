package uk.gov.ons.ctp.response.action.scheduled.ingest;

import liquibase.util.csv.CSVReader;
import liquibase.util.csv.opencsv.bean.ColumnPositionMappingStrategy;
import liquibase.util.csv.opencsv.bean.CsvToBean;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import uk.gov.ons.ctp.response.action.config.AppConfig;
import uk.gov.ons.ctp.response.action.domain.model.Action.ActionPriority;
import uk.gov.ons.ctp.response.action.message.ActionInstructionPublisher;
import uk.gov.ons.ctp.response.action.message.instruction.ActionCancel;
import uk.gov.ons.ctp.response.action.message.instruction.ActionRequest;
import uk.gov.ons.ctp.response.action.message.instruction.Priority;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.io.File;
import java.io.FileReader;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The ingester is configured from the spring integration xml. It is called, by
 * virtue of the @ServiceActivator reference, whenever a candidate CSV file is
 * found in the ingest folder. The CSV file is a superset of all of the fields
 * in the ActionRequest/ActionCancel/ActionUpdate(tbd) messages which can be
 * sent to handlers, as well as the handler name and the action type that the
 * handler will be sent. The CSV file may contain actions for multiple handlers,
 * and even multiple action types for each handler. The generation of the CSV
 * file is outside the remit of the action service.
 *
 */
@MessageEndpoint
@Slf4j
public class CsvIngester extends CsvToBean<CsvLine> {

  private static final String CHANNEL = "csvIngest";

  private static final String REQUEST_INSTRUCTION = "Request";
  private static final String CANCEL_INSTRUCTION = "Cancel";

  private static final String REASON = "Cancelled by Response Management CSV Ingest";

  private static final String HANDLER = "handler";
  private static final String ACTION_TYPE = "actionType";
  private static final String ACTION_PLAN = "actionPlan";
  private static final String QUESTION_SET = "questionSet";
  private static final String INSTRUCTION_TYPE = "instructionType";
  private static final String ADDRESS_TYPE = "addressType";
  private static final String ESTAB_TYPE = "estabType";
  private static final String LOCALITY = "locality";
  private static final String ORGANISATION_NAME = "organisationName";
  private static final String CATEGORY = "category";
  private static final String LINE1 = "line1";
  private static final String LINE2 = "line2";
  private static final String TOWN_NAME = "townName";
  private static final String POSTCODE = "postcode";
  private static final String LADCODE = "ladCode";
  private static final String LATITUDE = "latitude";
  private static final String LONGITUDE = "longitude";
  private static final String UPRN = "uprn";
  private static final String CASE_ID = "caseId";
  private static final String CASE_REF = "caseRef";
  private static final String PRIORITY = "priority";
  private static final String IAC = "iac";
  private static final String EVENTS = "events";
  private static final String TITLE = "title";
  private static final String FORENAME = "forename";
  private static final String SURNAME = "surname";
  private static final String EMAIL = "emailAddress";
  private static final String TELEPHONE = "telephoneNumber";

  private static final String[] COLUMNS = new String[] {HANDLER, ACTION_TYPE, INSTRUCTION_TYPE, ADDRESS_TYPE,
      ESTAB_TYPE, LOCALITY, ORGANISATION_NAME, CATEGORY, LINE1, LINE2, TOWN_NAME, POSTCODE, LADCODE, LATITUDE,
          LONGITUDE, UPRN, CASE_ID, CASE_REF, PRIORITY, IAC, EVENTS, ACTION_PLAN, QUESTION_SET, TITLE, FORENAME,
          SURNAME, EMAIL, TELEPHONE};

  /**
   * Inner class to encapsulate the request and cancel data as they do not have
   * common parentage
   *
   */
  @Data
  private class InstructionBucket {
    private List<ActionRequest> actionRequests = new ArrayList<>();
    private List<ActionCancel> actionCancels = new ArrayList<>();
  }

  @Autowired
  private AppConfig appConfig;

  @Autowired
  private ActionInstructionPublisher actionInstructionPublisher;

  private ColumnPositionMappingStrategy<CsvLine> columnPositionMappingStrategy;

  /**
   * Lazy create a reusable validator
   *
   * @return the cached validator
   */
  @Cacheable(cacheNames = "csvIngestValidator")
  private Validator getValidator() {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    return factory.getValidator();
  }

  /**
   * Create this ingester
   */
  public CsvIngester() {
    columnPositionMappingStrategy = new ColumnPositionMappingStrategy<>();
    columnPositionMappingStrategy.setType(CsvLine.class);
    columnPositionMappingStrategy.setColumnMapping(COLUMNS);
  }

  /**
   * take the provided CSV file, found by spring integration and sent to the
   * inputChannel, and ingest it, creating pseudo actions
   *
   * @param csvFile the file to ingest
   */
  @ServiceActivator(inputChannel = CHANNEL)
  public void ingest(File csvFile) {
    log.debug("INGESTED {}", csvFile.toString());
    CSVReader reader = null;
    Map<String, InstructionBucket> handlerInstructionBuckets = new HashMap<>();

    try {
      reader = new CSVReader(new FileReader(csvFile));
      String[] nextLine = null;
      int lineNum = 0;
      try {
        while ((nextLine = reader.readNext()) != null) {
          if (lineNum++ > 0) {
            CsvLine csvLine = (CsvLine) processLine(columnPositionMappingStrategy, nextLine);
            Optional<String> namesOfInvalidColumns = validateLine(csvLine);
            if (namesOfInvalidColumns.isPresent()) {
              log.error("Problem parsing {} due to {} - entire ingest aborted", Arrays.toString(nextLine),
                  namesOfInvalidColumns.get());
              csvFile.renameTo(
                  new File(csvFile.getPath() + ".error_LINE_" + lineNum + "_COLUMN_" + namesOfInvalidColumns.get()));
              return;
            }
            // first - which handler is it for?
            String handler = csvLine.getHandler();

            // get the handlers bucket
            InstructionBucket handlerInstructionBucket = getHandlerBucket(handlerInstructionBuckets, handler);

            // parse the line
            if (csvLine.getInstructionType().equals(REQUEST_INSTRUCTION)) {
              // store the request in the handlers bucket
              handlerInstructionBucket.getActionRequests().add(buildRequest(csvLine));

            } else if (csvLine.getInstructionType().equals(CANCEL_INSTRUCTION)) {
              // store the cancel in the handlers bucket
              handlerInstructionBucket.getActionCancels().add(buildCancel());
            }
          }
        }
      } catch (Exception e) {
        log.error("Problem parsing {} - entire ingest aborted", nextLine);
        log.error("Stack trace: ", e);
        csvFile.renameTo(new File(csvFile.getPath() + ".fix_line_" + lineNum));
        return;
      } finally {
        reader.close();
      }

      reader.close();
      csvFile.delete();

      // all lines parsed successfully - now send out bucket contents
      publishBuckets(handlerInstructionBuckets);
    } catch (Exception e) {
      log.error("Problem reading ingest file {}", csvFile.getPath());
      log.error("Stack trace: ", e);
    }
  }

  /**
   * get the bucket of instructions for this handler from the store, create and
   * store if not already stored
   *
   * @param handlerInstructionBuckets the bucket store keyed by handler
   * @param handler get the bucket for this handler
   * @return the bucket
   */
  private InstructionBucket getHandlerBucket(Map<String, InstructionBucket> handlerInstructionBuckets, String handler) {
    InstructionBucket handlerInstructionBucket = handlerInstructionBuckets.get(handler);
    if (handlerInstructionBucket == null) {
      handlerInstructionBucket = new InstructionBucket();
      handlerInstructionBuckets.put(handler, handlerInstructionBucket);
    }
    return handlerInstructionBucket;

  }

  /**
   * validate the csv line and return the optional concatenated list of fields
   * failing validation
   *
   * @param csvLine the line
   * @return the errored column names separated by '_'
   */
  private Optional<String> validateLine(CsvLine csvLine) {
    Set<ConstraintViolation<CsvLine>> violations = getValidator().validate(csvLine);
    String invalidColumns = violations.stream().map(v -> v.getPropertyPath().toString())
        .collect(Collectors.joining("_"));
    return (invalidColumns.length() == 0) ? Optional.empty() : Optional.ofNullable(invalidColumns);
  }

  /**
   * build an ActionCancel from a line in the csv
   *
   * @return the built cancel
   */
  private ActionCancel buildCancel() {
    return ActionCancel.builder()
        .withActionId(UUID.randomUUID().toString())
        .withReason(REASON)
        .build();
  }

  /**
   * build an ActionRequest from a line in the csv
   *
   * @param csvLine the line
   * @return the built request
   */
  private ActionRequest buildRequest(CsvLine csvLine) {
    return ActionRequest.builder()
        .withActionId(UUID.randomUUID().toString())
        .withActionType(csvLine.getActionType())
        .withActionPlan(csvLine.getActionPlan())
        .withQuestionSet(csvLine.getQuestionSet())
        .withResponseRequired(false)
        .withContact()
        .withTitle(csvLine.getTitle())
        .withForename(csvLine.getForename())
        .withSurname(csvLine.getSurname())
        .withPhoneNumber(csvLine.getTelephoneNumber())
        .withEmailAddress(csvLine.getEmailAddress())
        .end()
        .withAddress()
        .withCategory(csvLine.getCategory())
        .withEstabType(csvLine.getEstabType())
        .withLatitude(new BigDecimal(csvLine.getLatitude()))
        .withLongitude(new BigDecimal(csvLine.getLongitude()))
        .withLine1(csvLine.getLine1())
        .withLine2(csvLine.getLine2())
        .withLocality(csvLine.getLocality())
        .withOrganisationName(csvLine.getOrganisationName())
        .withPostcode(csvLine.getPostcode())
        .withLadCode(csvLine.getLadCode())
        .withTownName(csvLine.getTownName())
        .withType(csvLine.getAddressType())
        .end()
        .withCaseId(csvLine.getCaseId())
        .withIac(csvLine.getIac())
        .withPriority(
            Priority.fromValue(ActionPriority.valueOf(Integer.parseInt(csvLine.getPriority())).getName()))
        .withCaseRef(csvLine.getCaseRef())
        .withEvents()
        .withEvents(csvLine.getEvents().split("\\|"))
        .end()
        .build();
  }

  /**
   * Takes the map of buckets for all handlers, and splits into messages
   *
   * @param buckets the map of buckets keyed by handler
   */
  private void publishBuckets(Map<String, InstructionBucket> buckets) {
    buckets.forEach((handler, handlerInstructionBucket) -> {
      for (ActionRequest actionRequest : handlerInstructionBucket.actionRequests) {
        actionInstructionPublisher.sendActionInstruction(handler, actionRequest);
      }

      for (ActionCancel actionCancel :handlerInstructionBucket.actionCancels) {
        actionInstructionPublisher.sendActionInstruction(handler, actionCancel);
      }
    });
  }
}
