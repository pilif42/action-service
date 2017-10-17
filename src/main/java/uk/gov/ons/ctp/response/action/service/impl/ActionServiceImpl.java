package uk.gov.ons.ctp.response.action.service.impl;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.cobertura.CoverageIgnore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import uk.gov.ons.ctp.common.error.CTPException;
import uk.gov.ons.ctp.common.state.StateTransitionManager;
import uk.gov.ons.ctp.common.time.DateTimeUtil;
import uk.gov.ons.ctp.response.action.domain.model.Action;
import uk.gov.ons.ctp.response.action.domain.model.ActionType;
import uk.gov.ons.ctp.response.action.domain.repository.ActionRepository;
import uk.gov.ons.ctp.response.action.domain.repository.ActionTypeRepository;
import uk.gov.ons.ctp.response.action.message.feedback.ActionFeedback;
import uk.gov.ons.ctp.response.action.representation.ActionDTO;
import uk.gov.ons.ctp.response.action.representation.ActionDTO.ActionEvent;
import uk.gov.ons.ctp.response.action.representation.ActionDTO.ActionState;
import uk.gov.ons.ctp.response.action.service.ActionService;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An ActionService implementation which encapsulates all business logic
 * operating on the Action entity model.
 */

@Service
@Slf4j
public class ActionServiceImpl implements ActionService {

  private static final int TRANSACTION_TIMEOUT = 30;

  @Autowired
  private ActionRepository actionRepo;

  @Autowired
  private ActionTypeRepository actionTypeRepo;

  @Autowired
  private StateTransitionManager<ActionState, ActionDTO.ActionEvent> actionSvcStateTransitionManager;

  @CoverageIgnore
  @Override
  public List<Action> findAllActionsOrderedByCreatedDateTimeDescending() {
    log.debug("Entering findAllActions");
    return actionRepo.findAllByOrderByCreatedDateTimeDesc();
  }

  @CoverageIgnore
  @Override
  public List<Action> findActionsByTypeAndStateOrderedByCreatedDateTimeDescending(final String actionTypeName,
      final ActionDTO.ActionState state) {
    log.debug("Entering findActionsByTypeAndState with {} {}", actionTypeName, state);
    return actionRepo.findByActionTypeNameAndStateOrderByCreatedDateTimeDesc(actionTypeName, state);
  }

  @CoverageIgnore
  @Override
  public List<Action> findActionsByType(final String actionTypeName) {
    log.debug("Entering findActionsByType with {}", actionTypeName);
    return actionRepo.findByActionTypeNameOrderByCreatedDateTimeDesc(actionTypeName);
  }

  @CoverageIgnore
  @Override
  public List<Action> findActionsByState(final ActionDTO.ActionState state) {
    log.debug("Entering findActionsByState with {}", state);
    return actionRepo.findByStateOrderByCreatedDateTimeDesc(state);
  }

  @CoverageIgnore
  @Override
  public Action findActionByActionPK(final BigInteger actionKey) {
    log.debug("Entering findActionByActionPK with {}", actionKey);
    return actionRepo.findOne(actionKey);
  }

  @CoverageIgnore
  @Override
  public Action findActionById(final UUID actionId) {
    log.debug("Entering findActionById with {}", actionId);
    return actionRepo.findById(actionId);
  }

  @CoverageIgnore
  @Override
  public List<Action> findActionsByCaseId(final UUID caseId) {
    log.debug("Entering findActionsByCaseId with {}", caseId);
    return actionRepo.findByCaseIdOrderByCreatedDateTimeDesc(caseId);
  }

  @Transactional(propagation = Propagation.REQUIRED, readOnly = false, timeout = TRANSACTION_TIMEOUT)
  @Override
  public List<Action> cancelActions(final UUID caseId) throws CTPException {
    log.debug("Entering cancelAction with {}", caseId);

    List<Action> flushedActions = new ArrayList<>();
    List<Action> actions = actionRepo.findByCaseId(caseId);
    for (Action action : actions) {
      if (action.getActionType().getCanCancel()) {
        log.debug("Cancelling action {} of type {}", action.getId(), action.getActionType().getName());
        ActionDTO.ActionState nextState = actionSvcStateTransitionManager.transition(action.getState(),
            ActionEvent.REQUEST_CANCELLED);
        action.setState(nextState);
        action.setUpdatedDateTime(DateTimeUtil.nowUTC());
        actionRepo.saveAndFlush(action);
        flushedActions.add(action);
      }
    }
    return flushedActions;
  }

  @Transactional(propagation = Propagation.REQUIRED, readOnly = false, timeout = TRANSACTION_TIMEOUT)
  @Override
  public Action feedBackAction(ActionFeedback actionFeedback) throws CTPException {
    String actionId = actionFeedback.getActionId();
    log.debug("Entering feedBackAction with actionId {}", actionId);

    Action result = null;
    if (!StringUtils.isEmpty(actionId)) {
      result = actionRepo.findById(UUID.fromString(actionId));
      if (result != null) {
        ActionDTO.ActionEvent event = ActionDTO.ActionEvent.valueOf(actionFeedback.getOutcome().name());
        result.setSituation(actionFeedback.getSituation());
        result.setUpdatedDateTime(DateTimeUtil.nowUTC());
        ActionDTO.ActionState nextState = actionSvcStateTransitionManager.transition(result.getState(), event);
        result.setState(nextState);
        result = actionRepo.saveAndFlush(result);
      }
    }

    return result;
  }

  @Transactional(propagation = Propagation.REQUIRED, readOnly = false, timeout = TRANSACTION_TIMEOUT)
  @Override
  public Action createAction(final Action action) {
    log.debug("Entering createAdhocAction with {}", action);

    // guard against the caller providing an id - we would perform an update otherwise
    action.setActionPK(null);

    // the incoming action has a placeholder action type with the name as provided to the caller but we need the entire
    // action type object for that action type name
    ActionType actionType = actionTypeRepo.findByName(action.getActionType().getName());
    action.setActionType(actionType);

    action.setManuallyCreated(true);
    action.setCreatedDateTime(DateTimeUtil.nowUTC());
    action.setState(ActionDTO.ActionState.SUBMITTED);
    action.setId(UUID.randomUUID());
    return actionRepo.saveAndFlush(action);
  }

  @Transactional(propagation = Propagation.REQUIRED, readOnly = false, timeout = TRANSACTION_TIMEOUT)
  @Override
  public Action updateAction(final Action action) {
    UUID actionId = action.getId();
    log.debug("Entering updateAction with actionId {}", actionId);
    Action existingAction = actionRepo.findById(actionId);
    if (existingAction != null) {
      boolean needsUpdate = false;

      Integer newPriority = action.getPriority();
      log.debug("newPriority = {}", newPriority);
      if (newPriority != null) {
        needsUpdate = true;
        existingAction.setPriority(newPriority);
      }

      String newSituation = action.getSituation();
      log.debug("newSituation = {}", newSituation);
      if (newSituation != null) {
        needsUpdate = true;
        existingAction.setSituation(newSituation);
      }

      if (needsUpdate) {
        existingAction.setUpdatedDateTime(DateTimeUtil.nowUTC());
        log.debug("updating action with {}", existingAction);
        existingAction = actionRepo.saveAndFlush(existingAction);
      }
    }
    return existingAction;
  }

}
