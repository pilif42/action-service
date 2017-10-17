package uk.gov.ons.ctp.response.action.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import uk.gov.ons.ctp.common.rest.RestUtility;
import uk.gov.ons.ctp.response.action.config.AppConfig;
import uk.gov.ons.ctp.response.action.service.CollectionExerciseClientService;
import uk.gov.ons.ctp.response.collection.exercise.representation.CollectionExerciseDTO;

import java.io.IOException;
import java.util.UUID;

/**
 * Impl of the service that centralizes all REST calls to the Collection
 * Exercise service
 */
@Slf4j
@Service
public class CollectionExerciseClientServiceImpl implements CollectionExerciseClientService {

  @Autowired
  private AppConfig appConfig;

  @Autowired
  private RestTemplate restTemplate;

  @Autowired
  @Qualifier("collectionExerciseSvcClient")
  private RestUtility restUtility;

  @Autowired
  private ObjectMapper objectMapper;

  @Cacheable("collectionExercise")
  @Retryable(value = {RestClientException.class}, maxAttemptsExpression = "#{${retries.maxAttempts}}",
      backoff = @Backoff(delayExpression = "#{${retries.backoff}}"))
  @Override
  public CollectionExerciseDTO getCollectionExercise(UUID collectionExcerciseId) {
    UriComponents uriComponents = restUtility.createUriComponents(
        appConfig.getCollectionExerciseSvc().getCollectionByCollectionExerciseGetPath(), null, collectionExcerciseId);

    HttpEntity<?> httpEntity = restUtility.createHttpEntity(null);

    ResponseEntity<String> responseEntity = restTemplate.exchange(uriComponents.toUri(), HttpMethod.GET, httpEntity,
        String.class);

    CollectionExerciseDTO result = null;
    if (responseEntity != null && responseEntity.getStatusCode().is2xxSuccessful()) {
      String responseBody = responseEntity.getBody();
      try {
        result = objectMapper.readValue(responseBody, CollectionExerciseDTO.class);
      } catch (IOException e) {
        String msg = String.format("cause = %s - message = %s", e.getCause(), e.getMessage());
        log.error(msg);
      }
    }

    log.info("made call to collection Exercise and retrieved {}", result);
    return result;
  }
}
