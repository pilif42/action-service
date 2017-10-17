package uk.gov.ons.ctp.response.action.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import uk.gov.ons.ctp.common.rest.RestUtility;
import uk.gov.ons.ctp.response.action.config.AppConfig;
import uk.gov.ons.ctp.response.action.domain.model.Action;
import uk.gov.ons.ctp.response.action.service.CaseSvcClientService;
import uk.gov.ons.ctp.response.casesvc.representation.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Impl of the service that centralizes all REST calls to the Case service
 *
 */
@Slf4j
@Service
public class CaseSvcClientServiceImpl implements CaseSvcClientService {

  @Autowired
  private AppConfig appConfig;

  @Autowired
  private RestTemplate restTemplate;

  @Autowired
  @Qualifier("caseSvcClient")
  private RestUtility restUtility;

  @Autowired
  private ObjectMapper objectMapper;

  @Retryable(value = {RestClientException.class}, maxAttemptsExpression = "#{${retries.maxAttempts}}",
      backoff = @Backoff(delayExpression = "#{${retries.backoff}}"))
  @Override
  public CaseDetailsDTO getCase(final UUID caseId) {
    UriComponents uriComponents = restUtility.createUriComponents(appConfig.getCaseSvc().getCaseByCaseGetPath(),
        null, caseId);

    HttpEntity<?> httpEntity = restUtility.createHttpEntity(null);

    ResponseEntity<String> responseEntity = restTemplate.exchange(uriComponents.toUri(), HttpMethod.GET, httpEntity,
        String.class);

    CaseDetailsDTO result = null;
    if (responseEntity != null && responseEntity.getStatusCode().is2xxSuccessful()) {
      String responseBody = responseEntity.getBody();
      try {
        result = objectMapper.readValue(responseBody, CaseDetailsDTO.class);
      } catch (IOException e) {
        String msg = String.format("cause = %s - message = %s", e.getCause(), e.getMessage());
        log.error(msg);
      }
    }
    return result;
  }

  @Retryable(value = {RestClientException.class}, maxAttemptsExpression = "#{${retries.maxAttempts}}",
      backoff = @Backoff(delayExpression = "#{${retries.backoff}}"))
  @Override
  public CaseGroupDTO getCaseGroup(final UUID caseGroupId) {
    UriComponents uriComponents = restUtility.createUriComponents(appConfig.getCaseSvc().getCaseGroupPath(), null,
        caseGroupId);

    HttpEntity<?> httpEntity = restUtility.createHttpEntity(null);

    ResponseEntity<String> responseEntity = restTemplate.exchange(uriComponents.toUri(), HttpMethod.GET, httpEntity,
        String.class);

    CaseGroupDTO result = null;
    if (responseEntity != null && responseEntity.getStatusCode().is2xxSuccessful()) {
      String responseBody = responseEntity.getBody();
      try {
        result = objectMapper.readValue(responseBody, CaseGroupDTO.class);
      } catch (IOException e) {
        String msg = String.format("cause = %s - message = %s", e.getCause(), e.getMessage());
        log.error(msg);
      }
    }
    return result;
  }

  @Retryable(value = {RestClientException.class}, maxAttemptsExpression = "#{${retries.maxAttempts}}",
      backoff = @Backoff(delayExpression = "#{${retries.backoff}}"))
  @Override
  public List<CaseEventDTO> getCaseEvents(final UUID caseId) {
    UriComponents uriComponents = restUtility.createUriComponents(appConfig.getCaseSvc().getCaseEventsByCaseGetPath(),
        null, caseId);

    HttpEntity<?> httpEntity = restUtility.createHttpEntity(null);

    ResponseEntity<String> responseEntity = restTemplate.exchange(uriComponents.toUri(), HttpMethod.GET, httpEntity,
        String.class);

    CaseEventDTO[] result = null;
    if (responseEntity != null && responseEntity.getStatusCode().is2xxSuccessful()) {
      String responseBody = responseEntity.getBody();
      try {
        result = objectMapper.readValue(responseBody, CaseEventDTO[].class);
      } catch (IOException e) {
        String msg = String.format("cause = %s - message = %s", e.getCause(), e.getMessage());
        log.error(msg);
      }
    }
    return Arrays.asList(result);
  }

  @Retryable(value = {RestClientException.class}, maxAttemptsExpression = "#{${retries.maxAttempts}}",
      backoff = @Backoff(delayExpression = "#{${retries.backoff}}"))
  @Override
  public CaseDetailsDTO getCaseWithIAC(UUID caseId) {
    MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
    queryParams.add("iac", "true");
    UriComponents uriComponents = restUtility.createUriComponents(appConfig.getCaseSvc().getCaseByCaseGetPath(),
        queryParams, caseId);

    HttpEntity<?> httpEntity = restUtility.createHttpEntity(null);

    ResponseEntity<String> responseEntity = restTemplate.exchange(uriComponents.toUri(), HttpMethod.GET, httpEntity,
        String.class);

    CaseDetailsDTO result = null;
    if (responseEntity != null && responseEntity.getStatusCode().is2xxSuccessful()) {
      String responseBody = responseEntity.getBody();
      try {
        result = objectMapper.readValue(responseBody, CaseDetailsDTO.class);
      } catch (IOException e) {
        String msg = String.format("cause = %s - message = %s", e.getCause(), e.getMessage());
        log.error(msg);
      }
    }
    return result;
  }

  @Retryable(value = {RestClientException.class}, maxAttemptsExpression = "#{${retries.maxAttempts}}",
      backoff = @Backoff(delayExpression = "#{${retries.backoff}}"))
  @Override
  public CaseDetailsDTO getCaseWithIACandCaseEvents(UUID caseId) {
    MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
    queryParams.add("iac", "true");
    queryParams.add("caseevents", "true");
    UriComponents uriComponents = restUtility.createUriComponents(appConfig.getCaseSvc().getCaseByCaseGetPath(),
        queryParams, caseId);

    HttpEntity<?> httpEntity = restUtility.createHttpEntity(null);

    ResponseEntity<String> responseEntity = restTemplate.exchange(uriComponents.toUri(), HttpMethod.GET, httpEntity,
        String.class);

    CaseDetailsDTO result = null;
    if (responseEntity != null && responseEntity.getStatusCode().is2xxSuccessful()) {
      String responseBody = responseEntity.getBody();
      try {
        result = objectMapper.readValue(responseBody, CaseDetailsDTO.class);
      } catch (IOException e) {
        String msg = String.format("cause = %s - message = %s", e.getCause(), e.getMessage());
        log.error(msg);
      }
    }
    return result;
  }

  @Retryable(value = {RestClientException.class}, maxAttemptsExpression = "#{${retries.maxAttempts}}",
      backoff = @Backoff(delayExpression = "#{${retries.backoff}}"))
  @Override
  public CreatedCaseEventDTO createNewCaseEvent(final Action action, CategoryDTO.CategoryName actionCategory) {
    log.debug("posting caseEvent for actionId {} to casesvc for category {} ", action.getId(),
        actionCategory);
    UriComponents uriComponents = restUtility.createUriComponents(appConfig.getCaseSvc().getCaseEventsByCasePostPath(),
        null, action.getCaseId());
    CaseEventCreationRequestDTO caseEventDTO = new CaseEventCreationRequestDTO();
    caseEventDTO.setCategory(actionCategory);
    caseEventDTO.setCreatedBy(action.getCreatedBy());
    caseEventDTO.setSubCategory(action.getActionType().getName());

    if (!StringUtils.isEmpty(action.getSituation())) {
      caseEventDTO.setDescription(String.format("%s (%s)",
          action.getActionType().getDescription(), action.getSituation()));
    } else {
      caseEventDTO.setDescription(action.getActionType().getDescription());
    }

    HttpEntity<CaseEventCreationRequestDTO> httpEntity = restUtility.createHttpEntity(caseEventDTO);
    ResponseEntity<String> responseEntity = restTemplate.exchange(uriComponents.toUri(), HttpMethod.POST, httpEntity,
        String.class);

    CreatedCaseEventDTO result = null;
    if (responseEntity != null && responseEntity.getStatusCode().is2xxSuccessful()) {
      String responseBody = responseEntity.getBody();
      try {
        result = objectMapper.readValue(responseBody, CreatedCaseEventDTO.class);
      } catch (IOException e) {
        String msg = String.format("cause = %s - message = %s", e.getCause(), e.getMessage());
        log.error(msg);
      }
    }
    return result;
  }
}
