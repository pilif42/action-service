package uk.gov.ons.ctp.response.action.service.impl;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.ons.ctp.common.FixtureHelper;
import uk.gov.ons.ctp.response.action.domain.model.DlqActionInstruction;
import uk.gov.ons.ctp.response.action.domain.repository.DlqActionInstructionRepository;
import uk.gov.ons.ctp.response.action.message.ActionInstructionPublisher;
import uk.gov.ons.ctp.response.action.message.instruction.ActionRequest;
import uk.gov.ons.ctp.response.action.message.utility.CorrelationDataUtils;
import uk.gov.ons.ctp.response.action.message.utility.DlqActionInstructionCache;
import uk.gov.ons.ctp.response.action.message.utility.DlqActionInstructionData;

import javax.xml.transform.Source;
import java.util.ArrayList;
import java.util.List;

import static junit.framework.TestCase.fail;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;
import static uk.gov.ons.ctp.response.action.service.impl.PublisherConfirmsServiceImpl.UNEXPECTED_SITUATION_ERRRO_MSG;

@RunWith(MockitoJUnitRunner.class)
public class PublisherConfirmsServiceImplTest {

  private static final int ONE = 1;
  private static final int TWO = 2;

  private static final String HANDLER_1 = "actionexporter";
  private static final String HANDLER_2 = "notifygw";
  private static final String MESSAGE_1 = "some xml msg";
  private static final String MESSAGE_2 = "other xml msg";

  @Mock
  private DlqActionInstructionCache dlqActionInstructionCache;

  @Mock
  private DlqActionInstructionRepository dlqActionInstructionRepository;

  @Mock
  private org.springframework.oxm.jaxb.Jaxb2Marshaller actionInstructionMarshaller;

  @Mock
  private ActionInstructionPublisher actionInstructionPublisher;


  @InjectMocks
  private PublisherConfirmsServiceImpl publisherConfirmsService;

  private List<ActionRequest> actionRequests;

  /**
   * Initialises Mockito and loads Class Fixtures
   */
  @Before
  public void setUp() throws Exception {
    actionRequests = FixtureHelper.loadClassFixtures(ActionRequest[].class);

    MockitoAnnotations.initMocks(this);
  }

  // TODO tests for persistActionInstruction & replayActionInstruction

  @Test
  public void testReplayWhenNoMsgFound() {
    List<DlqActionInstruction> emptyList = new ArrayList<>();
    when(dlqActionInstructionRepository.findAll()).thenReturn(emptyList);

    publisherConfirmsService.replayActionInstruction();

    verify(actionInstructionPublisher, never()).sendActionInstruction(anyString(), any(uk.gov.ons.ctp.response.action.message.instruction.Action.class), anyInt());
  }

  @Test
  public void testReplayWhenActionInstructionMsgsFound() throws Exception {
    // Mocking section
    List<DlqActionInstruction> msgList = new ArrayList<>();
    msgList.add(DlqActionInstruction.builder().actionInstructionPK(ONE).handler(HANDLER_1).message(MESSAGE_1).build());
    msgList.add(DlqActionInstruction.builder().actionInstructionPK(TWO).handler(HANDLER_2).message(MESSAGE_2).build());
    when(dlqActionInstructionRepository.findAll()).thenReturn(msgList);

    uk.gov.ons.ctp.response.action.message.instruction.Action publishedAction = actionRequests.get(0);
    when(actionInstructionMarshaller.unmarshal(any(Source.class))).thenReturn(publishedAction);

    // Trigger the test
    publisherConfirmsService.replayActionInstruction();

    // Verify section
    ArgumentCaptor<String> handlerArgument = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<uk.gov.ons.ctp.response.action.message.instruction.Action> actionArgumentCaptor =
        ArgumentCaptor.forClass(uk.gov.ons.ctp.response.action.message.instruction.Action.class);
    ArgumentCaptor<Integer> dlqActionInstructionPKArgument = ArgumentCaptor.forClass(Integer.class);
    verify(actionInstructionPublisher, times(2)).sendActionInstruction(handlerArgument.capture(),
        actionArgumentCaptor.capture(), dlqActionInstructionPKArgument.capture());

    List<String> handlersList = handlerArgument.getAllValues();
    assertEquals(2, handlersList.size());
    assertThat(handlersList, containsInAnyOrder(HANDLER_1, HANDLER_2));

    List<uk.gov.ons.ctp.response.action.message.instruction.Action> actionsList =  actionArgumentCaptor.getAllValues();
    assertEquals(2, actionsList.size());
    assertThat(actionsList, containsInAnyOrder(publishedAction, publishedAction));

    List<Integer> dlqActionInstructionPKList = dlqActionInstructionPKArgument.getAllValues();
    assertEquals(2, dlqActionInstructionPKList.size());
    assertThat(dlqActionInstructionPKList, containsInAnyOrder(ONE, TWO));
  }

  @Test
  public void removeFromDatabaseNoDlqActionInstructionFound() {
    DlqActionInstructionData dlqActionInstructionData = DlqActionInstructionData.builder().build();
    publisherConfirmsService.removeDlqActionInstructionFromDatabase(dlqActionInstructionData);

    verify(dlqActionInstructionRepository, times(1)).findOne(any(Integer.class));
    verify(dlqActionInstructionRepository, never()).delete(any(Integer.class));
  }

  @Test
  public void removeFromDatabaseDlqActionInstructionFound() {
    DlqActionInstruction actionInstruction = DlqActionInstruction.builder()
          .handler(HANDLER_1)
          .message(MESSAGE_1)
          .build();
    when(dlqActionInstructionRepository.findOne(ONE)).thenReturn(actionInstruction);

    DlqActionInstructionData dlqActionInstructionData = DlqActionInstructionData.builder().messagePrimaryKey(ONE).build();
    publisherConfirmsService.removeDlqActionInstructionFromDatabase(dlqActionInstructionData);

    verify(dlqActionInstructionRepository, times(1)).findOne(any(Integer.class));
    verify(dlqActionInstructionRepository, times(1)).delete(eq(ONE));
  }
}
