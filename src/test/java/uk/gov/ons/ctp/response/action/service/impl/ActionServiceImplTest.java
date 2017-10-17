package uk.gov.ons.ctp.response.action.service.impl;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.ons.ctp.common.FixtureHelper;
import uk.gov.ons.ctp.common.state.StateTransitionManager;
import uk.gov.ons.ctp.response.action.domain.model.Action;
import uk.gov.ons.ctp.response.action.domain.model.ActionType;
import uk.gov.ons.ctp.response.action.domain.repository.ActionRepository;
import uk.gov.ons.ctp.response.action.domain.repository.ActionTypeRepository;
import uk.gov.ons.ctp.response.action.message.feedback.ActionFeedback;
import uk.gov.ons.ctp.response.action.representation.ActionDTO;
import uk.gov.ons.ctp.response.action.representation.ActionDTO.ActionEvent;
import uk.gov.ons.ctp.response.action.representation.ActionDTO.ActionState;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertThat;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for ActionServiceImpl
 */
@RunWith(MockitoJUnitRunner.class)
public class ActionServiceImplTest {

  private static final UUID ACTION_CASEID = UUID.fromString("7fac359e-645b-487e-bb02-70536eae51d4");
  private static final UUID ACTION_ID_0 = UUID.fromString("774afa97-8c87-4131-923b-b33ccbf72b3e");
  private static final UUID ACTION_ID_3 = UUID.fromString("774afa97-8c87-4131-923b-b33ccbf72bd9");
  private static final String ACTION_TYPENAME = "HouseholdInitialContact";

  @InjectMocks
  private ActionServiceImpl actionServiceImpl;

  @Mock
  private ActionRepository actionRepo;

  @Mock
  private ActionTypeRepository actionTypeRepo;

  @Mock
  private StateTransitionManager<ActionState, ActionDTO.ActionEvent> actionSvcStateTransitionManager;

  private List<Action> actions;
  private List<ActionFeedback> actionFeedback;
  private List<ActionType> actionType;

  /**
   * Initialises Mockito and loads Class Fixtures
   * @throws Exception exception thrown
   */
  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    actions = FixtureHelper.loadClassFixtures(Action[].class);
    actionFeedback = FixtureHelper.loadClassFixtures(ActionFeedback[].class);
    actionType = FixtureHelper.loadClassFixtures(ActionType[].class);
  }

  @Test
  public void cancelActionsForACaseAndVerifyActionsAreUpdatedAndFlushedActionsReturned() throws Exception {
    when(actionRepo.findByCaseId(ACTION_CASEID)).thenReturn(actions);
    when(actionSvcStateTransitionManager.transition(ActionState.PENDING, ActionEvent.REQUEST_CANCELLED))
        .thenReturn(ActionState.CANCELLED);
    when(actionSvcStateTransitionManager.transition(ActionState.SUBMITTED, ActionEvent.REQUEST_CANCELLED))
        .thenReturn(ActionState.CANCELLED);
    when(actionSvcStateTransitionManager.transition(ActionState.ACTIVE, ActionEvent.REQUEST_CANCELLED))
        .thenReturn(ActionState.CANCELLED);

    List<Action> flushedActions = actionServiceImpl.cancelActions(ACTION_CASEID);

    for (Action action : actions) {
      if (action.getActionType().getCanCancel()) {
        assertThat(action.getState(), is(ActionState.CANCELLED));
      } else {
        assertThat(action.getState(), is(not(ActionState.CANCELLED)));
      }
    }
    List<Action> originalActions = FixtureHelper.loadClassFixtures(Action[].class);

    verify(actionRepo, times(1)).findByCaseId(ACTION_CASEID);
    verify(actionSvcStateTransitionManager, times(1)).transition(
            originalActions.get(0).getState(), ActionEvent.REQUEST_CANCELLED);
    verify(actionSvcStateTransitionManager, times(1)).transition(
            originalActions.get(1).getState(), ActionEvent.REQUEST_CANCELLED);
    verify(actionRepo, times(1)).saveAndFlush(actions.get(0));
    verify(actionRepo, times(1)).saveAndFlush(actions.get(1));

    assertThat(flushedActions, containsInAnyOrder(actions.get(0), actions.get(1)));
  }

  @Test
  public void feedbackActionVerifyActionAreUpdatedAndStateHasChanged() throws Exception {
    when(actionRepo.findById(ACTION_ID_0)).thenReturn(actions.get(0));
    when(actionSvcStateTransitionManager.transition(ActionState.PENDING, ActionEvent.REQUEST_COMPLETED))
        .thenReturn(ActionState.COMPLETED);
    when(actionRepo.saveAndFlush(any())).then(returnsFirstArg());

    actionServiceImpl.feedBackAction(actionFeedback.get(0));

    ActionDTO.ActionEvent event = ActionDTO.ActionEvent.valueOf(actionFeedback.get(0).getOutcome().name());
    Action originalAction = FixtureHelper.loadClassFixtures(Action[].class).get(0);

    verify(actionRepo, times(1)).findById(ACTION_ID_0);
    verify(actionRepo, times(1)).saveAndFlush(actions.get(0));
    verify(actionSvcStateTransitionManager, times(1)).transition(originalAction.getState(),
            event);

  }

  @Test
  public void whenFeedbackActionNotFoundVerifySaveIsntCalled() throws Exception {
    actionServiceImpl.feedBackAction(actionFeedback.get(0));

    verify(actionRepo, times(1)).findById(any());
    verify(actionRepo, times(0)).saveAndFlush(any());
    verify(actionSvcStateTransitionManager, times(0)).transition(any(), any());
  }

  @Test
  public void whenFeedbackActionWithNullActionIdVerifySaveIsntCalled() throws Exception {
    ActionFeedback actionFeedbackWithNullActionId = new ActionFeedback();
    actionServiceImpl.feedBackAction(actionFeedbackWithNullActionId);

    verify(actionRepo, times(0)).findById(any());
    verify(actionRepo, times(0)).saveAndFlush(any());
    verify(actionSvcStateTransitionManager, times(0)).transition(any(), any());
  }

  @Test
  public void checkCreateActionIsSaved() throws Exception {
    when(actionTypeRepo.findByName(ACTION_TYPENAME)).thenReturn(actionType.get(0));
    actionServiceImpl.createAction(actions.get(0));
    verify(actionRepo, times(1)).saveAndFlush(actions.get(0));
  }

  @Test
  public void testUpdateActionCallsSaveEvent() throws Exception {
    Action action = actions.get(0);
    when(actionRepo.findById(ACTION_ID_0)).thenReturn(action);
    when(actionRepo.saveAndFlush(any())).then(returnsFirstArg());

    actionServiceImpl.updateAction(action);

    verify(actionRepo, times(1)).saveAndFlush(any());
  }

  @Test
  public void testUpdateActionNoActionFound() throws Exception {
    Action existingAction = actionServiceImpl.updateAction(actions.get(0));

    verify(actionRepo, times(0)).saveAndFlush(any());
    assertThat(existingAction, is(nullValue()));
  }

  @Test
  public void testUpdateActionNoUpdate() throws Exception {
    when(actionRepo.findById(ACTION_ID_3)).thenReturn(actions.get(3));
    Action existingAction = actionServiceImpl.updateAction(actions.get(3));

    verify(actionRepo, times(0)).saveAndFlush(any());
    assertThat(existingAction, is(actions.get(3)));
  }
}
