package uk.gov.ons.ctp.response.action.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import uk.gov.ons.ctp.common.error.CTPException;
import uk.gov.ons.ctp.common.state.StateTransitionManager;
import uk.gov.ons.ctp.common.time.DateTimeUtil;
import uk.gov.ons.ctp.response.action.domain.model.Action;
import uk.gov.ons.ctp.response.action.domain.model.ActionPlan;
import uk.gov.ons.ctp.response.action.domain.model.ActionType;
import uk.gov.ons.ctp.response.action.domain.repository.ActionPlanRepository;
import uk.gov.ons.ctp.response.action.domain.repository.ActionRepository;
import uk.gov.ons.ctp.response.action.message.ActionInstructionPublisher;
import uk.gov.ons.ctp.response.action.message.instruction.*;
import uk.gov.ons.ctp.response.action.representation.ActionDTO;
import uk.gov.ons.ctp.response.action.service.*;
import uk.gov.ons.ctp.response.casesvc.representation.CaseDetailsDTO;
import uk.gov.ons.ctp.response.casesvc.representation.CaseEventDTO;
import uk.gov.ons.ctp.response.casesvc.representation.CaseGroupDTO;
import uk.gov.ons.ctp.response.casesvc.representation.CategoryDTO;
import uk.gov.ons.ctp.response.collection.exercise.representation.CollectionExerciseDTO;
import uk.gov.ons.ctp.response.party.representation.Attributes;
import uk.gov.ons.ctp.response.party.representation.PartyDTO;
import uk.gov.ons.ctp.response.sample.representation.SampleUnitDTO;
import uk.gov.ons.response.survey.representation.SurveyDTO;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class ActionProcessingServiceImpl implements ActionProcessingService {

  public static final String CANCELLATION_REASON = "Action cancelled by Response Management";
  private static final String DATE_FORMAT_IN_REMINDER_EMAIL = "dd/MM/yyyy";

  @Autowired
  private CaseSvcClientService caseSvcClientService;

  @Autowired
  private CollectionExerciseClientService collectionExerciseClientService;

  @Autowired
  private PartySvcClientService partySvcClientService;

  @Autowired
  private SurveySvcClientService surveySvcClientService;

  @Autowired
  private ActionRepository actionRepo;

  @Autowired
  private ActionPlanRepository actionPlanRepo;

  @Autowired
  private ActionInstructionPublisher actionInstructionPublisher;

  @Autowired
  private StateTransitionManager<ActionDTO.ActionState, ActionDTO.ActionEvent> actionSvcStateTransitionManager;

  /**
   * Deal with a single action - the transaction boundary is here.
   *
   * The processing requires numerous calls to Case service, to write to our own action table and to publish to queue.
   *
   * @param action the action to deal with
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = false, rollbackFor = Exception.class)
  public void processActionRequest(final Action action) throws CTPException {
    log.debug("processing actionRequest with actionid {} caseid {} actionplanFK {}", action.getId(),
        action.getCaseId(), action.getActionPlanFK());

    ActionType actionType = action.getActionType();
    if (valid(actionType)) {
      ActionDTO.ActionEvent event = actionType.getResponseRequired() ?
          ActionDTO.ActionEvent.REQUEST_DISTRIBUTED : ActionDTO.ActionEvent.REQUEST_COMPLETED;

      transitionAction(action, event);

      // advise casesvc to create a corresponding caseevent for our action
      caseSvcClientService.createNewCaseEvent(action, CategoryDTO.CategoryName.ACTION_CREATED);

      ActionRequest actionRequest = prepareActionRequest(action);
      if (actionRequest != null) {
        String handler = actionType.getHandler();
        actionInstructionPublisher.sendActionInstruction(handler, actionRequest);
      }
    } else {
      log.error("Unexpected situation. actionType is not defined for action with actionid {}", action.getId());
    }
  }

  /**
   * Deal with a single action cancel - the transaction boundary is here
   *
   * @param action the action to deal with
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = false, rollbackFor = Exception.class)
  public void processActionCancel(final Action action) throws CTPException {
    log.info("processing action cancel for actionid {} caseid {} actionplanFK {}", action.getId(),
        action.getCaseId(), action.getActionPlanFK());

    transitionAction(action, ActionDTO.ActionEvent.CANCELLATION_DISTRIBUTED);

    // advise casesvc to create a corresponding caseevent for our action
    caseSvcClientService.createNewCaseEvent(action, CategoryDTO.CategoryName.ACTION_CANCELLATION_CREATED);

    ActionCancel actionCancel = prepareActionCancel(action);
    if (actionCancel != null) {
      String handler = action.getActionType().getHandler();
      actionInstructionPublisher.sendActionInstruction(handler, actionCancel);
    }

  }

  /**
   * Take an action and using it, fetch further info from Case service in a number of rest calls, in order to create
   * the ActionRequest
   *
   * @param action It all starts with the Action
   * @return The ActionRequest created from the Action and the other info from CaseSvc
   */
  private ActionRequest prepareActionRequest(final Action action) {
    UUID caseId = action.getCaseId();
    log.debug("constructing ActionRequest to publish to downstream handler for action {} and case id {}",
        action.getActionPK(), caseId);

    ActionPlan actionPlan = (action.getActionPlanFK() == null) ? null
        : actionPlanRepo.findOne(action.getActionPlanFK());

    CaseDetailsDTO caseDTO = caseSvcClientService.getCaseWithIACandCaseEvents(caseId);
    String sampleUnitTypeStr = caseDTO.getSampleUnitType();
    if (validate(sampleUnitTypeStr)) {
      SampleUnitDTO.SampleUnitType sampleUnitType = SampleUnitDTO.SampleUnitType.valueOf(sampleUnitTypeStr);
      PartyDTO parentParty;
      PartyDTO childParty = null;
      if (sampleUnitType.isParent()) {
        parentParty = partySvcClientService.getParty(sampleUnitTypeStr, caseDTO.getPartyId());
        log.debug("parentParty retrieved is {}", parentParty);
      } else {
        childParty = partySvcClientService.getParty(sampleUnitTypeStr, caseDTO.getPartyId());
        log.debug("childParty retrieved is {}", childParty);

        UUID associatedParentPartyID = caseDTO.getCaseGroup().getPartyId();
        // For BRES, child sampleUnitTypeStr is BI. parent will thus be B.
        parentParty = partySvcClientService.getParty(sampleUnitTypeStr.substring(0, 1), associatedParentPartyID);
      }

      List<CaseEventDTO> caseEventDTOs = caseDTO.getCaseEvents();

      return createActionRequest(action, actionPlan, caseDTO, parentParty, childParty, caseEventDTOs);
    } else {
      return null;
    }
  }

  /**
   * Take an action and using it, fetch further info from Case service in a
   * number of rest calls, in order to create the ActionRequest
   *
   * @param action It all starts wih the Action
   * @return The ActionRequest created from the Action and the other info from
   *         CaseSvc
   */
  private ActionCancel prepareActionCancel(final Action action) {
    log.debug("constructing ActionCancel to publish to downstream handler for action id {} and case id {}",
        action.getActionPK(), action.getCaseId());
    ActionCancel actionCancel = new ActionCancel();
    actionCancel.setActionId(action.getId().toString());
    actionCancel.setResponseRequired(true);
    actionCancel.setReason(CANCELLATION_REASON);
    return actionCancel;
  }

  /**
   * Given the business objects passed, create, populate and return an
   * ActionRequest
   *
   * @param action the persistent Action obj from the db
   * @param actionPlan the persistent ActionPlan obj from the db
   * @param caseDTO the Case representation from the CaseSvc
   * @param parentParty the parent Party from the PartySvc (in BRES, it is a B)
   * @param childParty the BI Party from the PartySvc (in BRES, it is a C)
   * @param caseEventDTOs the list of CaseEvent representations from the CaseSvc
   * @return the shiney new Action Request
   */
  private ActionRequest createActionRequest(final Action action, final ActionPlan actionPlan,
      final CaseDetailsDTO caseDTO,
      final PartyDTO parentParty,
      final PartyDTO childParty,
      final List<CaseEventDTO> caseEventDTOs) {
    ActionRequest actionRequest = new ActionRequest();
    actionRequest.setActionId(action.getId().toString());
    actionRequest.setActionPlan((actionPlan == null) ? null : actionPlan.getName());
    actionRequest.setActionType(action.getActionType().getName());
    actionRequest.setResponseRequired(action.getActionType().getResponseRequired());
    actionRequest.setCaseId(action.getCaseId().toString());

    CaseGroupDTO caseGroupDTO = caseDTO.getCaseGroup();
    UUID collectionExerciseId = caseGroupDTO.getCollectionExerciseId();
    CollectionExerciseDTO collectionExercise = collectionExerciseClientService.getCollectionExercise(
        collectionExerciseId);
    actionRequest.setExerciseRef(collectionExercise.getExerciseRef());

    ActionContact actionContact = new ActionContact();
    Attributes businessUnitAttributes = parentParty.getAttributes();
    actionContact.setRuName(businessUnitAttributes.getName());
    String tradStyle1 = businessUnitAttributes.getTradstyle1();
    String tradStyle2 = businessUnitAttributes.getTradstyle2();
    String tradStyle3 = businessUnitAttributes.getTradstyle3();
    StringBuffer tradStyle = new StringBuffer();
    if (!StringUtils.isEmpty(tradStyle1)) {
      tradStyle.append(String.format("%s ", tradStyle1));
    }
    if (!StringUtils.isEmpty(tradStyle2)) {
      tradStyle.append(String.format("%s ", tradStyle2));
    }
    if (!StringUtils.isEmpty(tradStyle3)) {
      tradStyle.append(tradStyle3);
    }
    actionContact.setTradingStyle(tradStyle.toString().trim());
    if (childParty != null) {
      Attributes biPartyAttributes = childParty.getAttributes();
      actionContact.setForename(biPartyAttributes.getFirstName());
      actionContact.setSurname(biPartyAttributes.getLastName());
      actionContact.setEmailAddress(biPartyAttributes.getEmailAddress());
    }
    actionRequest.setContact(actionContact);

    ActionEvent actionEvent = new ActionEvent();
    caseEventDTOs.forEach((caseEventDTO) -> actionEvent.getEvents().add(formatCaseEvent(caseEventDTO)));
    actionRequest.setEvents(actionEvent);

    actionRequest.setIac(caseDTO.getIac());
    actionRequest.setPriority(Priority.fromValue(Action.ActionPriority.valueOf(action.getPriority()).getName()));

    ActionAddress actionAddress = new ActionAddress();
    actionAddress.setSampleUnitRef(caseGroupDTO.getSampleUnitRef());
    actionRequest.setAddress(actionAddress);

    String surveyId = collectionExercise.getSurveyId();
    SurveyDTO surveyDTO = surveySvcClientService.requestDetailsForSurvey(surveyId);
    actionRequest.setSurveyName(surveyDTO.getLongName());
    actionRequest.setSurveyRef(surveyDTO.getSurveyRef());

    Date scheduledEndDateTime = collectionExercise.getScheduledEndDateTime();
    if (scheduledEndDateTime != null) {
      DateFormat df = new SimpleDateFormat(DATE_FORMAT_IN_REMINDER_EMAIL);
      actionRequest.setReturnByDate(df.format(scheduledEndDateTime));
    }

    return actionRequest;
  }

  /**
   * Formats a CaseEvent as a string that can added to the ActionRequest
   *
   * @param caseEventDTO the DTO to be formatted
   * @return the pretty one liner
   */
  private String formatCaseEvent(final CaseEventDTO caseEventDTO) {
    return String.format("%s : %s : %s : %s", caseEventDTO.getCategory(), caseEventDTO.getSubCategory(),
        caseEventDTO.getCreatedBy(), caseEventDTO.getDescription());
  }

  /**
   * To validate the sampleUnitTypeStr versus SampleSvc-Api
   *
   * @param sampleUnitTypeStr the string value for sampleUnitType
   * @return true if sampleUnitTypeStr is known to us
   */
  private boolean validate(String sampleUnitTypeStr) {
    boolean result = false;
    try {
      SampleUnitDTO.SampleUnitType sampleUnitType = SampleUnitDTO.SampleUnitType.valueOf(sampleUnitTypeStr);
      if (sampleUnitType.isParent()) {
        result = true;
      } else {
        String childSampleUnitTypeStr = sampleUnitTypeStr.substring(0, 1);
        SampleUnitDTO.SampleUnitType.valueOf(childSampleUnitTypeStr);
        result = true;
      }
    } catch (IllegalArgumentException e) {
      log.error("Unexpected scenario. Error message is {}. Cause is {}", e.getMessage(), e.getCause());
    }

    return result;
  }

  /**
   * Change the action status in db to indicate we have sent this action downstream, and clear previous situation
   * (in the scenario where the action has prev. failed)
   *
   * @param action the action to change and persist
   * @param event the event to transition the action with
   * @throws CTPException if action state transition error
   */
  private void transitionAction(final Action action, final ActionDTO.ActionEvent event) throws CTPException {
    ActionDTO.ActionState nextState = actionSvcStateTransitionManager.transition(action.getState(), event);
    action.setState(nextState);
    action.setSituation(null);
    action.setUpdatedDateTime(DateTimeUtil.nowUTC());
    actionRepo.saveAndFlush(action);
  }

  /**
   * To validate an ActionType
   *
   * @param actionType the ActionType to validate
   * @return true if valid
   */
  private boolean valid(ActionType actionType) {
    return (actionType != null) && (actionType.getResponseRequired() != null);
  }
}
