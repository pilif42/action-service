package uk.gov.ons.ctp.response.action.service.impl;

import org.apache.commons.collections.map.MultiValueMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.ons.ctp.common.error.CTPException;
import uk.gov.ons.ctp.common.rest.RestUtility;
import uk.gov.ons.ctp.response.action.config.AppConfig;
import uk.gov.ons.ctp.response.action.config.CollectionExerciseSvc;

import java.util.UUID;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.*;

/**
 * test for CollectionExerciseSvcClient
 */
@RunWith(MockitoJUnitRunner.class)
public class CollectionExerciseClientServiceImplTest {

  private static final String PATH = "/path";
  private static final String HTTP = "http";
  private static final String LOCALHOST = "localhost";

  @InjectMocks
  private CollectionExerciseClientServiceImpl collectionExerciseClientServiceImpl;

  @Mock
  private RestTemplate restTemplate;

  @Mock
  private RestUtility restUtility;

  @Mock
  private AppConfig appConfig;

  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void verifyGetCollectionExerciseCallInvokesRESTEndpoint() throws CTPException {
    CollectionExerciseSvc collectionExerciseSvc = new CollectionExerciseSvc();
    collectionExerciseSvc.setCollectionByCollectionExerciseGetPath("/path");

    UriComponents uriComponents = UriComponentsBuilder.newInstance()
        .scheme(HTTP)
        .host(LOCALHOST)
        .port(80)
        .path(PATH)
        .build();

    //cast to MultiValueMap was required to get mock to work.
    when(restUtility.createUriComponents(anyString(),
        (org.springframework.util.MultiValueMap<String, String>) isNull(MultiValueMap.class), any(UUID.class)))
        .thenReturn(uriComponents);
    when(appConfig.getCollectionExerciseSvc()).thenReturn(collectionExerciseSvc);

    collectionExerciseClientServiceImpl.getCollectionExercise(UUID.fromString("d06c440e-4fad-4ea6-952a-72d9db144f05"));

    verify(restUtility, times(1)).createUriComponents(PATH, null, UUID.fromString(
        "d06c440e-4fad-4ea6-952a-72d9db144f05"));
    verify(restUtility, times(1)).createHttpEntity(null);
  }
}
