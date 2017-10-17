package uk.gov.ons.ctp.response.action.service.impl;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.ons.ctp.common.FixtureHelper;
import uk.gov.ons.ctp.common.error.CTPException;
import uk.gov.ons.ctp.common.state.StateTransitionManager;
import uk.gov.ons.ctp.response.action.config.AppConfig;
import uk.gov.ons.ctp.response.action.domain.model.Action;
import uk.gov.ons.ctp.response.action.domain.model.OutcomeCategory;
import uk.gov.ons.ctp.response.action.domain.model.OutcomeHandlerId;
import uk.gov.ons.ctp.response.action.domain.repository.ActionRepository;
import uk.gov.ons.ctp.response.action.domain.repository.OutcomeCategoryRepository;
import uk.gov.ons.ctp.response.action.message.feedback.ActionFeedback;
import uk.gov.ons.ctp.response.action.representation.ActionDTO;
import uk.gov.ons.ctp.response.action.representation.ActionDTO.ActionEvent;
import uk.gov.ons.ctp.response.action.representation.ActionDTO.ActionState;
import uk.gov.ons.ctp.response.action.service.CaseSvcClientService;
import uk.gov.ons.ctp.response.casesvc.representation.CategoryDTO;

import java.util.List;
import java.util.UUID;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Tests for FeedbackServiceImpl
 */
@RunWith(MockitoJUnitRunner.class)
public class FeedbackServiceImplTest {

  private static final UUID ACTION_ID_1 = UUID.fromString("774afa97-8c87-4131-923b-b33ccbf72b3e");
  private static final UUID ACTION_ID_2 = UUID.fromString("774afa97-8c87-4131-923b-b33ccbf72b3e");
  
  @Mock
  private CaseSvcClientService caseSvcClientService;

  @Mock
  private ActionRepository actionRepo;

  @Mock
  private OutcomeCategoryRepository outcomeCategoryRepository;

  @Mock
  private StateTransitionManager<ActionState, ActionEvent> actionSvcStateTransitionManager;

  @Mock
  private AppConfig appConfig;

  @InjectMocks
  private FeedbackServiceImpl feedbackService;

  private List<Action> actions;
  private List<ActionFeedback> actionFeedbacks;
  private List<OutcomeCategory> outcomeCategories;

  /**
   * Mockito setup
   */
  @Before
  public void setup() throws Exception {
    actions = FixtureHelper.loadClassFixtures(Action[].class);
    actionFeedbacks = FixtureHelper.loadClassFixtures(ActionFeedback[].class);
    outcomeCategories = FixtureHelper.loadClassFixtures(OutcomeCategory[].class);

    MockitoAnnotations.initMocks(this);
  }

  /**
   * Yep - another test
   * @throws Exception exception thrown
   */
  @Test
  public void testFeedbackAccepted() throws Exception {
    Mockito.when(actionRepo.findById(ACTION_ID_1)).thenReturn(actions.get(0));
    Mockito.when(actionSvcStateTransitionManager.transition(ActionState.PENDING, ActionEvent.REQUEST_ACCEPTED))
        .thenReturn(ActionState.ACTIVE);

    feedbackService.acceptFeedback(actionFeedbacks.get(0));

    // Verify calls made
    verify(actionRepo, times(1)).saveAndFlush(any(Action.class));
    verify(caseSvcClientService, times(0)).createNewCaseEvent(any(Action.class),
            any(CategoryDTO.CategoryName.class));
  }

  /**
   * Yep - another test
   * @throws Exception exception thrown
   */
  @Test
  public void testFeedbackBlankSituationActionCompleted() throws Exception {
    Mockito.when(actionRepo.findById(ACTION_ID_2)).thenReturn(actions.get(2));
    Mockito.when(outcomeCategoryRepository.findOne(new OutcomeHandlerId(ActionDTO.ActionEvent
            .valueOf(actionFeedbacks.get(2).getOutcome().name()), "Printer"))).thenReturn(outcomeCategories.get(0));
    Mockito.when(actionSvcStateTransitionManager.transition(ActionState.ACTIVE, ActionEvent.REQUEST_COMPLETED))
        .thenReturn(ActionState.COMPLETED);

    feedbackService.acceptFeedback(actionFeedbacks.get(2));

    verify(actionRepo, times(1)).saveAndFlush(any(Action.class));
    verify(caseSvcClientService, times(1)).createNewCaseEvent(actions.get(2),
            CategoryDTO.CategoryName.ACTION_COMPLETED);
  }


  /**
   * Yep - another test
   * @throws Exception exception thrown
   */
  @Test
  public void testFeedbackDerelictSituationActionCompleted() throws Exception {
    Mockito.when(actionRepo.findById(ACTION_ID_2)).thenReturn(actions.get(2));
    Mockito.when(outcomeCategoryRepository.findOne(new OutcomeHandlerId(ActionDTO.ActionEvent
            .valueOf(actionFeedbacks.get(3).getOutcome().name()), "Printer"))).thenReturn(outcomeCategories.get(0));
    Mockito.when(actionSvcStateTransitionManager.transition(ActionState.ACTIVE, ActionEvent.REQUEST_COMPLETED))
        .thenReturn(ActionState.COMPLETED);

    feedbackService.acceptFeedback(actionFeedbacks.get(3));

    verify(actionRepo, times(1)).saveAndFlush(any(Action.class));
    verify(caseSvcClientService, times(1)).createNewCaseEvent(actions.get(2),
            CategoryDTO.CategoryName.ACTION_COMPLETED);
  }

  /**
   * Yep - another test
   * @throws Exception exception thrown
   */
  @Test
  public void testFeedbackConfusedSituationActionCompleted() throws Exception {
    Mockito.when(actionRepo.findById(ACTION_ID_2)).thenReturn(actions.get(2));
    Mockito.when(outcomeCategoryRepository.findOne(new OutcomeHandlerId(ActionDTO.ActionEvent.valueOf(
            actionFeedbacks.get(4).getOutcome().name()), "Printer"))).thenReturn(outcomeCategories.get(1));
    Mockito.when(actionSvcStateTransitionManager.transition(ActionState.ACTIVE, ActionEvent.REQUEST_COMPLETED))
        .thenReturn(ActionState.COMPLETED);

    feedbackService.acceptFeedback(actionFeedbacks.get(4));

    verify(actionRepo, times(1)).saveAndFlush(any(Action.class));
    verify(caseSvcClientService, times(1)).createNewCaseEvent(actions.get(2),
            CategoryDTO.CategoryName.ACTION_COMPLETED_DEACTIVATED);
  }
}
