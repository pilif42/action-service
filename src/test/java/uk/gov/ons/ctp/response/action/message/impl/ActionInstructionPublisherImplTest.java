package uk.gov.ons.ctp.response.action.message.impl;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.support.CorrelationData;
import uk.gov.ons.ctp.common.FixtureHelper;
import uk.gov.ons.ctp.response.action.message.instruction.Action;
import uk.gov.ons.ctp.response.action.message.instruction.ActionCancel;
import uk.gov.ons.ctp.response.action.message.instruction.ActionInstruction;
import uk.gov.ons.ctp.response.action.message.instruction.ActionRequest;
import uk.gov.ons.ctp.response.action.message.utility.CorrelationDataUtils;
import uk.gov.ons.ctp.response.action.message.utility.DlqActionInstructionCache;

import java.util.List;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static uk.gov.ons.ctp.response.action.message.impl.ActionInstructionPublisherImpl.ACTION;
import static uk.gov.ons.ctp.response.action.message.impl.ActionInstructionPublisherImpl.BINDING;

/**
 * Tests for ActionInstructionPublisherImpl
 */
@RunWith(MockitoJUnitRunner.class)
public class ActionInstructionPublisherImplTest {

  private static final String HANDLER_TEST = "test";

  @Mock
  private RabbitTemplate rabbitTemplate;

  @Mock
  private CorrelationDataUtils correlationDataUtils;

  @Mock
  private DlqActionInstructionCache dlqActionInstructionCache;

  @InjectMocks
  private ActionInstructionPublisherImpl actionInstructionPublisherImpl;

  private List<ActionCancel> actionCancels;
  private List<ActionRequest> actionRequests;

  /**
   * Initialises Mockito and loads Class Fixtures
   */
  @Before
  public void setUp() throws Exception {
    actionCancels = FixtureHelper.loadClassFixtures(ActionCancel[].class);
    actionRequests = FixtureHelper.loadClassFixtures(ActionRequest[].class);

    MockitoAnnotations.initMocks(this);
  }

  /**
   * Build an ActionInstruction with One ActionRequest and send to queue. NOT in replay mode.
   */
  @Test
  public void sendActionInstructionWithOneActionRequest() {
    ActionRequest requestToSend = actionRequests.get(0);

    actionInstructionPublisherImpl.sendActionInstruction(HANDLER_TEST, requestToSend);

    ArgumentCaptor<String> routingKeyCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<ActionInstruction> actionInstructionCaptor = ArgumentCaptor.forClass(ActionInstruction.class);
    ArgumentCaptor<CorrelationData> correlationDataCaptor = ArgumentCaptor.forClass(CorrelationData.class);

    verify(rabbitTemplate, times(1)).convertAndSend(routingKeyCaptor.capture(),
        actionInstructionCaptor.capture(), any(MessagePostProcessor.class), correlationDataCaptor.capture());

    assertEquals(String.format("%s%s%s", ACTION, HANDLER_TEST, BINDING), routingKeyCaptor.getValue());

    ActionInstruction instructionSent = actionInstructionCaptor.getValue();
    assertNull(instructionSent.getActionCancel());
    assertNull(instructionSent.getActionUpdate());
    assertEquals(requestToSend, instructionSent.getActionRequest());

    // TODO verify on the correlationData & correlationDataUtils: correlationDataCaptor.getValue()
  }

  /**
   * Build an DlqActionInstruction with One ActionCancel and send to queue. NOT in replay mode.
   */
  @Test
  public void sendActionInstructionWithOneActionCancel() {
    ActionCancel cancelToSend = actionCancels.get(0);

    actionInstructionPublisherImpl.sendActionInstruction(HANDLER_TEST, cancelToSend);

    ArgumentCaptor<String> routingKeyCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<ActionInstruction> actionInstructionCaptor = ArgumentCaptor.forClass(ActionInstruction.class);
    ArgumentCaptor<CorrelationData> correlationDataCaptor = ArgumentCaptor.forClass(CorrelationData.class);

    verify(rabbitTemplate, times(1)).convertAndSend(routingKeyCaptor.capture(),
        actionInstructionCaptor.capture(), any(MessagePostProcessor.class), correlationDataCaptor.capture());

    assertEquals(String.format("%s%s%s", ACTION, HANDLER_TEST, BINDING), routingKeyCaptor.getValue());

    ActionInstruction instructionSent = actionInstructionCaptor.getValue();
    assertNull(instructionSent.getActionRequest());
    assertNull(instructionSent.getActionUpdate());
    assertEquals(cancelToSend, instructionSent.getActionCancel());

    // TODO verify on the correlationData & correlationDataUtils: correlationDataCaptor.getValue()
  }

  /**
   * Build an DlqActionInstruction with Neither an ActionRequest Nor an ActionCancel and send to queue
   */
  @Test
  public void sendActionInstructionWithNoActionRequestNorCancel() {
    actionInstructionPublisherImpl.sendActionInstruction(HANDLER_TEST, new Action());

    ArgumentCaptor<String> routingKeyCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<ActionInstruction> actionInstructionCaptor = ArgumentCaptor.forClass(ActionInstruction.class);
    ArgumentCaptor<CorrelationData> correlationDataCaptor = ArgumentCaptor.forClass(CorrelationData.class);

    verify(rabbitTemplate, times(1)).convertAndSend(routingKeyCaptor.capture(),
        actionInstructionCaptor.capture(), any(MessagePostProcessor.class), correlationDataCaptor.capture());

    assertEquals(String.format("%s%s%s", ACTION, HANDLER_TEST, BINDING), routingKeyCaptor.getValue());

    ActionInstruction instructionSent = actionInstructionCaptor.getValue();
    assertNull(instructionSent.getActionRequest());
    assertNull(instructionSent.getActionUpdate());
    assertNull(instructionSent.getActionCancel());

    // TODO verify on the correlationData & correlationDataUtils: correlationDataCaptor.getValue()
  }

  // TODO Tests for replay mode, ie dlqActionInstructionPK not null
}
