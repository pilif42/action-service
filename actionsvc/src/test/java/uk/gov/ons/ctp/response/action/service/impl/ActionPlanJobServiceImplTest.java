package uk.gov.ons.ctp.response.action.service.impl;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.cloud.sleuth.Tracer;
import uk.gov.ons.ctp.common.FixtureHelper;
import uk.gov.ons.ctp.common.distributed.DistributedLockManager;
import uk.gov.ons.ctp.common.time.DateTimeUtil;
import uk.gov.ons.ctp.response.action.config.AppConfig;
import uk.gov.ons.ctp.response.action.config.PlanExecution;
import uk.gov.ons.ctp.response.action.domain.model.ActionCase;
import uk.gov.ons.ctp.response.action.domain.model.ActionPlan;
import uk.gov.ons.ctp.response.action.domain.model.ActionPlanJob;
import uk.gov.ons.ctp.response.action.domain.repository.ActionCaseRepository;
import uk.gov.ons.ctp.response.action.domain.repository.ActionPlanJobRepository;
import uk.gov.ons.ctp.response.action.domain.repository.ActionPlanRepository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Tests for the ActionPlanJobServiceImpl
 */
@RunWith(MockitoJUnitRunner.class)
public class ActionPlanJobServiceImplTest {

  @Mock
  Tracer tracer;

  @Mock
  private DistributedLockManager actionPlanExecutionLockManager;

  @Spy
  private AppConfig appConfig = new AppConfig();

  @Mock
  private ActionPlanRepository actionPlanRepo;

  @Mock
  private ActionCaseRepository actionCaseRepo;

  @Mock
  private ActionPlanJobRepository actionPlanJobRepo;

  @InjectMocks
  private ActionPlanJobServiceImpl actionPlanJobServiceImpl;

  /**
   * Before the test
   */
  @Before
  public void setup() throws Exception {
    PlanExecution planExecution = new PlanExecution();
    planExecution.setDelayMilliSeconds(5000L);
    appConfig.setPlanExecution(planExecution);
    MockitoAnnotations.initMocks(this);

    Mockito.when(actionPlanExecutionLockManager.lock(any(String.class))).thenReturn(true);
  }

  /**
   * Test the service method called by the endpoint where exec is forced ie the service should disregard last exec times
   * @throws Exception oops
   */
  @Test
  public void testCreateAndExecuteActionPlanJobForcedExecutionBlueSky() throws Exception {
    // load fixtures
    List<ActionPlan> actionPlans = FixtureHelper.loadClassFixtures(ActionPlan[].class);
    List<ActionPlanJob> actionPlanJobs = FixtureHelper.loadClassFixtures(ActionPlanJob[].class);
    List<ActionCase> actionCases = FixtureHelper.loadClassFixtures(ActionCase[].class);

    // wire up mock responses
    Mockito.when(actionPlanRepo.findOne(1)).thenReturn(actionPlans.get(0));
    Mockito.when(actionCaseRepo.countByActionPlanFK(1)).thenReturn(new Long(actionCases.size()));
    Mockito.when(actionPlanJobRepo.save(actionPlanJobs.get(0))).thenReturn(actionPlanJobs.get(0));
    Mockito.when(actionCaseRepo.createActions(1)).thenReturn(Boolean.TRUE);

    // let it roll
    ActionPlanJob executedJob = actionPlanJobServiceImpl.createAndExecuteActionPlanJob(actionPlanJobs.get(0));

    // assert the right calls were made
    verify(actionPlanRepo).findOne(1);
    verify(actionCaseRepo).countByActionPlanFK(1);

    ArgumentCaptor <ActionPlanJob> actionPlanJob = ArgumentCaptor.forClass(ActionPlanJob.class);
    verify(actionPlanJobRepo).save(actionPlanJob.capture());
    ActionPlanJob savedJob = actionPlanJob.getValue();
    assertEquals(actionPlanJobs.get(0), savedJob);

    verify(actionCaseRepo).createActions(1);

    Assert.assertNotNull(executedJob);
  }

  /**
   * Test that the endpoint forced exec method gracefully handles the failure to lock an action plan
   * @throws Exception oops
   */
  @Test
  public void testCreateAndExecuteActionPlanJobForcedExecutionFailedLock() throws Exception {
  
    // set up mock hazelcast with a lock that will fail
    Mockito.when(actionPlanExecutionLockManager.lock(any(String.class))).thenReturn(false);

    // load fixtures
    List<ActionPlan> actionPlans = FixtureHelper.loadClassFixtures(ActionPlan[].class);
    List<ActionPlanJob> actionPlanJobs = FixtureHelper.loadClassFixtures(ActionPlanJob[].class);
  
    // wire up mock responses
    Mockito.when(actionPlanRepo.findOne(1)).thenReturn(actionPlans.get(0));
    
    // let it roll
    ActionPlanJob executedJob = actionPlanJobServiceImpl.createAndExecuteActionPlanJob(actionPlanJobs.get(0));
  
    // assert the right calls were made
    verify(actionPlanRepo).findOne(1);
    verify(actionCaseRepo, times(0)).countByActionPlanFK(1);
    verify(actionPlanJobRepo, times(0)).save(actionPlanJobs.get(0));
    verify(actionCaseRepo, times(0)).createActions(1);
    Assert.assertNull(executedJob);
  }

  /**
   * Test the endpoint forced exec method handles no open cases for an action plan gracefully
   * @throws Exception oops
   */
  @Test
  public void testCreateAndExecuteActionPlanJobForcedExecutionNoCases() throws Exception {

    // load fixtures
    List<ActionPlan> actionPlans = FixtureHelper.loadClassFixtures(ActionPlan[].class);
    List<ActionPlanJob> actionPlanJobs = FixtureHelper.loadClassFixtures(ActionPlanJob[].class);
    List<ActionCase> actionCases = new ArrayList<>();

    // wire up mock responses
    Mockito.when(actionPlanRepo.findOne(1)).thenReturn(actionPlans.get(0));
    Mockito.when(actionCaseRepo.countByActionPlanFK(1)).thenReturn(new Long(actionCases.size()));

    //let it roll
    ActionPlanJob executedJob = actionPlanJobServiceImpl.createAndExecuteActionPlanJob(actionPlanJobs.get(0));

    // assert the right calls were made
    verify(actionPlanRepo).findOne(1);
    verify(actionCaseRepo).countByActionPlanFK(1);
    verify(actionPlanJobRepo, times(0)).save(actionPlanJobs.get(0));
    verify(actionCaseRepo, times(0)).createActions(1);
  
    Assert.assertNotNull(executedJob);
  }

  /**
   * Test that the service method that execs ALL plans works when all plans require running due to expired last run times
   * @throws Exception oops
   */
  @Test
  public void testCreateAndExecuteActionPlanJobUnForcedExecutionPlanDoesRun() throws Exception {

    // load fixtures
    List<ActionPlan> actionPlans = FixtureHelper.loadClassFixtures(ActionPlan[].class);

    // set fixture actionplans to have run 10s ago
    Timestamp now = DateTimeUtil.nowUTC();
    Timestamp lastExecutionTime = new Timestamp(now.getTime() - 10000);
    actionPlans.forEach(actionPlan->actionPlan.setLastRunDateTime(lastExecutionTime));

    List<ActionPlanJob> actionPlanJobs = FixtureHelper.loadClassFixtures(ActionPlanJob[].class);

    // wire up mock responses
    Mockito.when(actionPlanRepo.findAll()).thenReturn(actionPlans);
    Mockito.when(actionPlanRepo.findOne(1)).thenReturn(actionPlans.get(0));
    Mockito.when(actionPlanRepo.findOne(2)).thenReturn(actionPlans.get(1));
    Mockito.when(actionCaseRepo.countByActionPlanFK(1)).thenReturn(1L);
    Mockito.when(actionCaseRepo.countByActionPlanFK(2)).thenReturn(1L);
    Mockito.when(actionPlanJobRepo.save(any(ActionPlanJob.class))).thenReturn(actionPlanJobs.get(0));
    Mockito.when(actionCaseRepo.createActions(1)).thenReturn(Boolean.TRUE);


    // let it roll
    List<ActionPlanJob> executedJobs = actionPlanJobServiceImpl.createAndExecuteAllActionPlanJobs();

    // assert the right calls were made
    verify(actionPlanRepo, times(1)).findAll();
    verify(actionPlanRepo, times(1)).findOne(1);
    verify(actionPlanRepo, times(1)).findOne(2);
    verify(actionCaseRepo, times(1)).countByActionPlanFK(1);
    verify(actionCaseRepo, times(1)).countByActionPlanFK(2);
    verify(actionPlanJobRepo, times(2)).save(any(ActionPlanJob.class));
    verify(actionCaseRepo, times(2)).createActions(any(Integer.class));

    Assert.assertTrue(executedJobs.size() > 0);
  }

  /**
   * Test that the service method that execs ALL plans works when all plans require running due to expired last run times
   * @throws Exception oops
   */
  @Test
  public void testCreateAndExecuteActionPlanJobUnForcedExecutionPlanDoesNotRun() throws Exception {

    // load fixtures
    List<ActionPlan> actionPlans = FixtureHelper.loadClassFixtures(ActionPlan[].class);

    // set fixture actionplans to have run 1s ago
    Timestamp now = DateTimeUtil.nowUTC();
    Timestamp lastExecutionTime = new Timestamp(now.getTime() - 1000);
    actionPlans.forEach(actionPlan->actionPlan.setLastRunDateTime(lastExecutionTime));

    // wire up mock responses
    Mockito.when(actionPlanRepo.findAll()).thenReturn(actionPlans);
    Mockito.when(actionPlanRepo.findOne(1)).thenReturn(actionPlans.get(0));
    Mockito.when(actionPlanRepo.findOne(2)).thenReturn(actionPlans.get(1));

    // let it roll
    List<ActionPlanJob> executedJobs = actionPlanJobServiceImpl.createAndExecuteAllActionPlanJobs();

    // assert the right calls were made
    verify(actionPlanRepo, times(1)).findAll();
    verify(actionPlanRepo, times(1)).findOne(1);
    verify(actionPlanRepo, times(1)).findOne(2);
    verify(actionCaseRepo, times(0)).findByActionPlanId(1);
    verify(actionCaseRepo, times(0)).findByActionPlanId(2);
    verify(actionPlanJobRepo, times(0)).save(any(ActionPlanJob.class));
    verify(actionCaseRepo, times(0)).createActions(any(Integer.class));

    Assert.assertFalse(executedJobs.size() > 0);
  }
}
