package uk.gov.ons.ctp.response.action.service;

import org.springframework.web.client.RestClientException;
import uk.gov.ons.response.survey.representation.SurveyDTO;

/**
 * Service responsible for making client calls to the Survey service
 */
public interface SurveySvcClientService {

  /**
   * Get survey details by survey UUID.
   *
   * @param surveyId UUID String for which to request details.
   * @return the survey details.
   * @throws RestClientException something went wrong making http call.
   *
   */
  SurveyDTO requestDetailsForSurvey(String surveyId) throws RestClientException;
}
