package uk.gov.ons.ctp.response.action.endpoint;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uk.gov.ons.ctp.common.error.CTPException;
import uk.gov.ons.ctp.common.error.RestExceptionHandler;
import uk.gov.ons.ctp.common.jackson.CustomObjectMapper;
import uk.gov.ons.ctp.response.action.service.PublisherConfirmsService;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.Is.isA;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.ons.ctp.common.MvcHelper.postJson;
import static uk.gov.ons.ctp.common.utility.MockMvcControllerAdviceHelper.mockAdviceFor;
import static uk.gov.ons.ctp.response.action.endpoint.SupportEndpoint.UNEXPECTED_MSG_TYPE;

/**
 * Support Endpoint Unit tests
 */
public class SupportEndpointUnitTest {

  private static final String ACTION_INSTRUCTION = "ActionInstruction";
  private static final String NON_EXISTING_MSG_TYPE = "SomeType";
  private static final String REPLAY_MSG = "replayMessages";
  private static final String RUNTIME_ERROR_MSG = "DB connection KO";

  @InjectMocks
  private SupportEndpoint supportEndpoint;

  @Mock
  private PublisherConfirmsService publisherConfirmsService;

  private MockMvc mockMvc;

  /**
   * Set up of tests
   * @throws Exception exception thrown
   */
  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    this.mockMvc = MockMvcBuilders
        .standaloneSetup(supportEndpoint)
        .setHandlerExceptionResolvers(mockAdviceFor(RestExceptionHandler.class))
        .setMessageConverters(new MappingJackson2HttpMessageConverter(new CustomObjectMapper()))
        .build();
  }

  @Test
  public void replayMsgTypeNotFound() throws Exception {
    ResultActions actions = mockMvc.perform(postJson(String.format("/support/replayMessages/%s", NON_EXISTING_MSG_TYPE), ""));

    actions.andExpect(status().isNotFound());
    actions.andExpect(handler().handlerType(SupportEndpoint.class));
    actions.andExpect(handler().methodName(REPLAY_MSG));
    actions.andExpect(jsonPath("$.error.code", is(CTPException.Fault.RESOURCE_NOT_FOUND.name())));
    actions.andExpect(jsonPath("$.error.message", is(String.format(UNEXPECTED_MSG_TYPE, NON_EXISTING_MSG_TYPE))));
    actions.andExpect(jsonPath("$.error.timestamp", isA(String.class)));

    verify(publisherConfirmsService, never()).replayActionInstruction();
  }

  @Test
  public void replayMsgTypeActionInstruction() throws Exception {
    ResultActions actions = mockMvc.perform(postJson(String.format("/support/replayMessages/%s", ACTION_INSTRUCTION), ""));

    actions.andExpect(status().is2xxSuccessful());
    actions.andExpect(handler().handlerType(SupportEndpoint.class));
    actions.andExpect(handler().methodName(REPLAY_MSG));

    verify(publisherConfirmsService, times(1)).replayActionInstruction();
  }

  @Test
  public void replayMsgTypeActionInstructionButServiceBlowsUp() throws Exception {
    doThrow(new RuntimeException(RUNTIME_ERROR_MSG)).when(publisherConfirmsService).replayActionInstruction();

    ResultActions actions = mockMvc.perform(postJson(String.format("/support/replayMessages/%s", ACTION_INSTRUCTION), ""));

    actions.andExpect(status().is5xxServerError());
    actions.andExpect(handler().handlerType(SupportEndpoint.class));
    actions.andExpect(handler().methodName(REPLAY_MSG));
    actions.andExpect(jsonPath("$.error.code", is(CTPException.Fault.SYSTEM_ERROR.name())));
    actions.andExpect(jsonPath("$.error.message", is(RUNTIME_ERROR_MSG)));
    actions.andExpect(jsonPath("$.error.timestamp", isA(String.class)));


    verify(publisherConfirmsService, times(1)).replayActionInstruction();
  }
}
