package uk.gov.ons.ctp.response.action.scheduled.distribution;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.data.domain.Pageable;
import uk.gov.ons.ctp.common.FixtureHelper;
import uk.gov.ons.ctp.common.distributed.DistributedListManager;
import uk.gov.ons.ctp.response.action.config.ActionDistribution;
import uk.gov.ons.ctp.response.action.config.AppConfig;
import uk.gov.ons.ctp.response.action.domain.model.Action;
import uk.gov.ons.ctp.response.action.domain.model.ActionType;
import uk.gov.ons.ctp.response.action.domain.repository.ActionRepository;
import uk.gov.ons.ctp.response.action.domain.repository.ActionTypeRepository;
import uk.gov.ons.ctp.response.action.representation.ActionDTO.ActionState;
import uk.gov.ons.ctp.response.action.service.ActionProcessingService;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

/**
 * Test the ActionDistributor
 *
 * Important reminder on the standing data held in json files:
 *  - case 3382981d-3df0-464e-9c95-aea7aee80c81 is linked with a SUBMITTED action so expect 1 ActionRequest
 *  - case 3382981d-3df0-464e-9c95-aea7aee80c82 is linked with a CANCEL_SUBMITTED action so expect 1 ActionCancel
 *  - case 3382981d-3df0-464e-9c95-aea7aee80c83 is linked with a SUBMITTED action so expect 1 ActionRequest
 *  - case 3382981d-3df0-464e-9c95-aea7aee80c84 is linked with a CANCEL_SUBMITTED action so expect 1 ActionCancel
 *  - all actions have responseRequired = true
 */
@RunWith(MockitoJUnitRunner.class)
public class ActionDistributorTest {

  private static final int TEN = 10;

  private static final String HOUSEHOLD_INITIAL_CONTACT = "HouseholdInitialContact";
  private static final String HOUSEHOLD_UPLOAD_IAC = "HouseholdUploadIAC";

  private List<ActionType> actionTypes;
  private List<Action> householdInitialContactActions;
  private List<Action> householdUploadIACActions;

  @Spy
  private AppConfig appConfig = new AppConfig();

  @Mock
  private DistributedListManager<BigInteger> actionDistributionListManager;

  @Mock
  private ActionRepository actionRepo;

  @Mock
  private ActionTypeRepository actionTypeRepo;

  @Mock
  private ActionProcessingService actionProcessingService;

  @InjectMocks
  private ActionDistributor actionDistributor;

  /**
   * Initialises Mockito and loads Class Fixtures
   */
  @Before
  public void setUp() throws Exception {
    ActionDistribution actionDistributionConfig = new ActionDistribution();
    actionDistributionConfig.setDelayMilliSeconds(TEN);
    actionDistributionConfig.setRetrievalMax(TEN);
    actionDistributionConfig.setRetrySleepSeconds(TEN);
    appConfig.setActionDistribution(actionDistributionConfig);

    actionTypes = FixtureHelper.loadClassFixtures(ActionType[].class);
    householdInitialContactActions = FixtureHelper.loadClassFixtures(Action[].class, HOUSEHOLD_INITIAL_CONTACT);
    householdUploadIACActions = FixtureHelper.loadClassFixtures(Action[].class, HOUSEHOLD_UPLOAD_IAC);

    MockitoAnnotations.initMocks(this);
  }

  /**
   * Test that when we fail at first hurdle to load ActionTypes, we do not go on to call anything else. In reality, the
   * wakeup method would then be called again after a sleep interval by Spring but we cannot test that here.
   *
   * @throws Exception oops
   */
  @Test
  public void testFailToGetAnyActionType() throws Exception {
    when(actionTypeRepo.findAll()).thenThrow(new RuntimeException("Database access failed"));

    DistributionInfo info = actionDistributor.distribute();
    List<InstructionCount> countList = info.getInstructionCounts();
    assertTrue(countList.isEmpty());

    verify(actionTypeRepo).findAll();

    // Assertions for calls in method retrieveActions
    verify(actionDistributionListManager, times(0)).findList(anyString(),
        any(Boolean.class));
    verify(actionRepo, times(0)).findByActionTypeNameAndStateInAndActionPKNotIn(
        anyString(), anyListOf(ActionState.class), anyListOf(BigInteger.class), any(Pageable.class));
    verify(actionDistributionListManager, times(0)).saveList(anyString(), any(List.class),
        any(Boolean.class));

    // Assertions for calls in actionProcessingService
    verify(actionProcessingService, times(0)).processActionRequest(any(Action.class));
    verify(actionProcessingService, times(0)).processActionCancel(any(Action.class));
  }

  /**
   * We retrieve actionTypes but then exception thrown when retrieving actions.
   *
   * @throws Exception oops
   */
  @Test
  public void testFailToGetAnyAction() throws Exception {
    when(actionTypeRepo.findAll()).thenReturn(actionTypes);
    when(actionRepo.findByActionTypeNameAndStateInAndActionPKNotIn(anyString(), any(List.class),
        any(List.class), any(Pageable.class))).thenThrow(new RuntimeException("Database access failed"));

    DistributionInfo info = actionDistributor.distribute();
    List<InstructionCount> countList = info.getInstructionCounts();
    assertEquals(4, countList.size());
    List<InstructionCount> expectedCountList = new ArrayList<>();
    expectedCountList.add(new InstructionCount(HOUSEHOLD_INITIAL_CONTACT,
        DistributionInfo.Instruction.REQUEST, 0));
    expectedCountList.add(new InstructionCount(HOUSEHOLD_INITIAL_CONTACT,
        DistributionInfo.Instruction.CANCEL_REQUEST, 0));
    expectedCountList.add(new InstructionCount(HOUSEHOLD_UPLOAD_IAC,
        DistributionInfo.Instruction.REQUEST, 0));
    expectedCountList.add(new InstructionCount(HOUSEHOLD_UPLOAD_IAC,
        DistributionInfo.Instruction.CANCEL_REQUEST, 0));
    assertTrue(countList.equals(expectedCountList));

    // Assertions for calls in method retrieveActions
    verify(actionDistributionListManager).findList(eq(HOUSEHOLD_INITIAL_CONTACT), eq(false));
    verify(actionDistributionListManager).findList(eq(HOUSEHOLD_UPLOAD_IAC), eq(false));
    verify(actionRepo, times(1)).findByActionTypeNameAndStateInAndActionPKNotIn(
        eq(HOUSEHOLD_INITIAL_CONTACT), anyListOf(ActionState.class), anyListOf(BigInteger.class), any(Pageable.class));
    verify(actionRepo, times(1)).findByActionTypeNameAndStateInAndActionPKNotIn(
        eq(HOUSEHOLD_UPLOAD_IAC), anyListOf(ActionState.class), anyListOf(BigInteger.class), any(Pageable.class));
    verify(actionDistributionListManager, times(0)).saveList(anyString(), anyList(),
        anyBoolean());

    // Assertions for calls in actionProcessingService
    verify(actionProcessingService, times(0)).processActionRequest(any(Action.class));
    verify(actionProcessingService, times(0)).processActionCancel(any(Action.class));
  }

  /**
   * Happy Path with 2 ActionRequests and 2 ActionCancels for a H case (ie parent case)
   *
   * @throws Exception oops
   */
  @Test
  public void testHappyPathParentCase() throws Exception {
    when(actionTypeRepo.findAll()).thenReturn(actionTypes);
    when(actionRepo.findByActionTypeNameAndStateInAndActionPKNotIn(eq(HOUSEHOLD_INITIAL_CONTACT),
        anyListOf(ActionState.class), anyListOf(BigInteger.class), any(Pageable.class))).thenReturn(
            householdInitialContactActions);
    when(actionRepo.findByActionTypeNameAndStateInAndActionPKNotIn(eq(HOUSEHOLD_UPLOAD_IAC),
        anyListOf(ActionState.class), anyListOf(BigInteger.class), any(Pageable.class))).thenReturn(
        householdUploadIACActions);

    DistributionInfo info = actionDistributor.distribute();
    List<InstructionCount> countList = info.getInstructionCounts();
    assertEquals(4, countList.size());
    List<InstructionCount> expectedCountList = new ArrayList<>();
    expectedCountList.add(new InstructionCount(HOUSEHOLD_INITIAL_CONTACT,
        DistributionInfo.Instruction.REQUEST, 1));
    expectedCountList.add(new InstructionCount(HOUSEHOLD_INITIAL_CONTACT,
        DistributionInfo.Instruction.CANCEL_REQUEST, 1));
    expectedCountList.add(new InstructionCount(HOUSEHOLD_UPLOAD_IAC,
        DistributionInfo.Instruction.REQUEST, 1));
    expectedCountList.add(new InstructionCount(HOUSEHOLD_UPLOAD_IAC,
        DistributionInfo.Instruction.CANCEL_REQUEST, 1));
    assertTrue(countList.equals(expectedCountList));

    verify(actionTypeRepo).findAll();

    // Assertions for calls in method retrieveActions
    verify(actionDistributionListManager).findList(eq(HOUSEHOLD_INITIAL_CONTACT), eq(false));
    verify(actionDistributionListManager).findList(eq(HOUSEHOLD_UPLOAD_IAC), eq(false));
    verify(actionRepo, times(1)).findByActionTypeNameAndStateInAndActionPKNotIn(
        eq(HOUSEHOLD_INITIAL_CONTACT), anyListOf(ActionState.class), anyListOf(BigInteger.class), any(Pageable.class));
    verify(actionRepo, times(1)).findByActionTypeNameAndStateInAndActionPKNotIn(
        eq(HOUSEHOLD_UPLOAD_IAC), anyListOf(ActionState.class), anyListOf(BigInteger.class), any(Pageable.class));
    verify(actionDistributionListManager).saveList(eq(HOUSEHOLD_INITIAL_CONTACT), anyList(), anyBoolean());
    verify(actionDistributionListManager).saveList(eq(HOUSEHOLD_UPLOAD_IAC), anyList(), anyBoolean());

    // Assertions for calls to actionProcessingService & processActionRequest
    ArgumentCaptor<Action> actionCaptorForActionRequest = ArgumentCaptor.forClass(Action.class);
    verify(actionProcessingService, times(2)).processActionRequest(
        actionCaptorForActionRequest.capture());
    List<Action> actionsList = actionCaptorForActionRequest.getAllValues();
    assertEquals(2, actionsList.size());
    List<Action> expectedActionsList = new ArrayList<>();
    expectedActionsList.add(householdInitialContactActions.get(0));
    expectedActionsList.add(householdUploadIACActions.get(0));
    assertTrue(expectedActionsList.equals(actionsList));

    // Assertions for calls to actionProcessingService & processActionCancel
    ArgumentCaptor<Action> actionCaptorForActionCancel = ArgumentCaptor.forClass(Action.class);
    verify(actionProcessingService, times(2)).processActionCancel(
        actionCaptorForActionCancel.capture());
    actionsList = actionCaptorForActionCancel.getAllValues();
    assertEquals(2, actionsList.size());
    expectedActionsList = new ArrayList<>();
    expectedActionsList.add(householdInitialContactActions.get(1));
    expectedActionsList.add(householdUploadIACActions.get(1));
    assertTrue(expectedActionsList.equals(actionsList));
  }

  /**
   * Test with 2 ActionRequests and 2 ActionCancels for a H case (ie parent case) where ActionProcessingService throws
   * an Exception when processActionRequest and when processActionCancel
   *
   * @throws Exception oops
   */
  @Test
  public void testActionProcessingServiceThrowsException() throws Exception {
    when(actionTypeRepo.findAll()).thenReturn(actionTypes);
    when(actionRepo.findByActionTypeNameAndStateInAndActionPKNotIn(eq(HOUSEHOLD_INITIAL_CONTACT),
        anyListOf(ActionState.class), anyListOf(BigInteger.class), any(Pageable.class))).thenReturn(
        householdInitialContactActions);
    when(actionRepo.findByActionTypeNameAndStateInAndActionPKNotIn(eq(HOUSEHOLD_UPLOAD_IAC),
        anyListOf(ActionState.class), anyListOf(BigInteger.class), any(Pageable.class))).thenReturn(
        householdUploadIACActions);
    doThrow(new RuntimeException("Database access failed")).when(actionProcessingService).processActionRequest(
        any(Action.class));
    doThrow(new RuntimeException("Database access failed")).when(actionProcessingService).processActionCancel(
        any(Action.class));

    DistributionInfo info = actionDistributor.distribute();
    List<InstructionCount> countList = info.getInstructionCounts();
    assertEquals(4, countList.size());
    List<InstructionCount> expectedCountList = new ArrayList<>();
    expectedCountList.add(new InstructionCount(HOUSEHOLD_INITIAL_CONTACT,
        DistributionInfo.Instruction.REQUEST, 0));
    expectedCountList.add(new InstructionCount(HOUSEHOLD_INITIAL_CONTACT,
        DistributionInfo.Instruction.CANCEL_REQUEST, 0));
    expectedCountList.add(new InstructionCount(HOUSEHOLD_UPLOAD_IAC,
        DistributionInfo.Instruction.REQUEST, 0));
    expectedCountList.add(new InstructionCount(HOUSEHOLD_UPLOAD_IAC,
        DistributionInfo.Instruction.CANCEL_REQUEST, 0));
    assertTrue(countList.equals(expectedCountList));

    verify(actionTypeRepo).findAll();

    // Assertions for calls in method retrieveActions
    verify(actionDistributionListManager).findList(eq(HOUSEHOLD_INITIAL_CONTACT), eq(false));
    verify(actionDistributionListManager).findList(eq(HOUSEHOLD_UPLOAD_IAC), eq(false));
    verify(actionRepo, times(1)).findByActionTypeNameAndStateInAndActionPKNotIn(
        eq(HOUSEHOLD_INITIAL_CONTACT), anyListOf(ActionState.class), anyListOf(BigInteger.class), any(Pageable.class));
    verify(actionRepo, times(1)).findByActionTypeNameAndStateInAndActionPKNotIn(
        eq(HOUSEHOLD_UPLOAD_IAC), anyListOf(ActionState.class), anyListOf(BigInteger.class), any(Pageable.class));
    verify(actionDistributionListManager).saveList(eq(HOUSEHOLD_INITIAL_CONTACT), anyList(), anyBoolean());
    verify(actionDistributionListManager).saveList(eq(HOUSEHOLD_UPLOAD_IAC), anyList(), anyBoolean());

    // Assertions for calls to actionProcessingService & processActionRequest
    ArgumentCaptor<Action> actionCaptorForActionRequest = ArgumentCaptor.forClass(Action.class);
    verify(actionProcessingService, times(2)).processActionRequest(
        actionCaptorForActionRequest.capture());
    List<Action> actionsList = actionCaptorForActionRequest.getAllValues();
    assertEquals(2, actionsList.size());
    List<Action> expectedActionsList = new ArrayList<>();
    expectedActionsList.add(householdInitialContactActions.get(0));
    expectedActionsList.add(householdUploadIACActions.get(0));
    assertTrue(expectedActionsList.equals(actionsList));

    // Assertions for calls to actionProcessingService & processActionCancel
    ArgumentCaptor<Action> actionCaptorForActionCancel = ArgumentCaptor.forClass(Action.class);
    verify(actionProcessingService, times(2)).processActionCancel(
        actionCaptorForActionCancel.capture());
    actionsList = actionCaptorForActionCancel.getAllValues();
    assertEquals(2, actionsList.size());
    expectedActionsList = new ArrayList<>();
    expectedActionsList.add(householdInitialContactActions.get(1));
    expectedActionsList.add(householdUploadIACActions.get(1));
    assertTrue(expectedActionsList.equals(actionsList));
  }

  /**
   * Test with 2 ActionRequests and 2 ActionCancels for a H case (ie parent case) where ActionProcessingService throws
   * an Exception intermittently when processActionRequest and when processActionCancel
   *    - processActionRequest KO for actionPK = 1 (HOUSEHOLD_INITIAL_CONTACT)
   *    - processActionRequest OK for actionPK = 3 (HOUSEHOLD_UPLOAD_IAC)
   *    - processActionCancel OK for actionPK = 2 (HOUSEHOLD_INITIAL_CONTACT)
   *    - processActionCancel KO for actionPK = 4 (HOUSEHOLD_UPLOAD_IAC)
   *
   * @throws Exception oops
   */
  @Test
  public void testActionProcessingServiceThrowsExceptionIntermittently() throws Exception {
    when(actionTypeRepo.findAll()).thenReturn(actionTypes);
    when(actionRepo.findByActionTypeNameAndStateInAndActionPKNotIn(eq(HOUSEHOLD_INITIAL_CONTACT),
        anyListOf(ActionState.class), anyListOf(BigInteger.class), any(Pageable.class))).thenReturn(
        householdInitialContactActions);
    when(actionRepo.findByActionTypeNameAndStateInAndActionPKNotIn(eq(HOUSEHOLD_UPLOAD_IAC),
        anyListOf(ActionState.class), anyListOf(BigInteger.class), any(Pageable.class))).thenReturn(
        householdUploadIACActions);
    doThrow(new RuntimeException("Database access failed")).when(actionProcessingService).processActionRequest(
        eq(householdInitialContactActions.get(0)));
    doThrow(new RuntimeException("Database access failed")).when(actionProcessingService).processActionCancel(
        eq(householdUploadIACActions.get(1)));

    DistributionInfo info = actionDistributor.distribute();
    List<InstructionCount> countList = info.getInstructionCounts();
    assertEquals(4, countList.size());
    List<InstructionCount> expectedCountList = new ArrayList<>();
    expectedCountList.add(new InstructionCount(HOUSEHOLD_INITIAL_CONTACT,
        DistributionInfo.Instruction.REQUEST, 0));
    expectedCountList.add(new InstructionCount(HOUSEHOLD_INITIAL_CONTACT,
        DistributionInfo.Instruction.CANCEL_REQUEST, 1));
    expectedCountList.add(new InstructionCount(HOUSEHOLD_UPLOAD_IAC,
        DistributionInfo.Instruction.REQUEST, 1));
    expectedCountList.add(new InstructionCount(HOUSEHOLD_UPLOAD_IAC,
        DistributionInfo.Instruction.CANCEL_REQUEST, 0));
    assertTrue(countList.equals(expectedCountList));

    verify(actionTypeRepo).findAll();

    // Assertions for calls in method retrieveActions
    verify(actionDistributionListManager).findList(eq(HOUSEHOLD_INITIAL_CONTACT), eq(false));
    verify(actionDistributionListManager).findList(eq(HOUSEHOLD_UPLOAD_IAC), eq(false));
    verify(actionRepo, times(1)).findByActionTypeNameAndStateInAndActionPKNotIn(
        eq(HOUSEHOLD_INITIAL_CONTACT), anyListOf(ActionState.class), anyListOf(BigInteger.class), any(Pageable.class));
    verify(actionRepo, times(1)).findByActionTypeNameAndStateInAndActionPKNotIn(
        eq(HOUSEHOLD_UPLOAD_IAC), anyListOf(ActionState.class), anyListOf(BigInteger.class), any(Pageable.class));
    verify(actionDistributionListManager).saveList(eq(HOUSEHOLD_INITIAL_CONTACT), anyList(), anyBoolean());
    verify(actionDistributionListManager).saveList(eq(HOUSEHOLD_UPLOAD_IAC), anyList(), anyBoolean());

    // Assertions for calls to actionProcessingService & processActionRequest
    ArgumentCaptor<Action> actionCaptorForActionRequest = ArgumentCaptor.forClass(Action.class);
    verify(actionProcessingService, times(2)).processActionRequest(
        actionCaptorForActionRequest.capture());
    List<Action> actionsList = actionCaptorForActionRequest.getAllValues();
    assertEquals(2, actionsList.size());
    List<Action> expectedActionsList = new ArrayList<>();
    expectedActionsList.add(householdInitialContactActions.get(0));
    expectedActionsList.add(householdUploadIACActions.get(0));
    assertTrue(expectedActionsList.equals(actionsList));

    // Assertions for calls to actionProcessingService & processActionCancel
    ArgumentCaptor<Action> actionCaptorForActionCancel = ArgumentCaptor.forClass(Action.class);
    verify(actionProcessingService, times(2)).processActionCancel(
        actionCaptorForActionCancel.capture());
    actionsList = actionCaptorForActionCancel.getAllValues();
    assertEquals(2, actionsList.size());
    expectedActionsList = new ArrayList<>();
    expectedActionsList.add(householdInitialContactActions.get(1));
    expectedActionsList.add(householdUploadIACActions.get(1));
    assertTrue(expectedActionsList.equals(actionsList));
  }
}
