package uk.gov.ons.ctp.response.action.config;

import lombok.Data;
import net.sourceforge.cobertura.CoverageIgnore;
import uk.gov.ons.ctp.common.rest.RestUtilityConfig;

/**
 * App config POJO for collection exercise service access - host location and endpoint
 * locations
 *
 */
@CoverageIgnore
@Data
public class CollectionExerciseSvc {
  private RestUtilityConfig connectionConfig;
  private String collectionByCollectionExerciseGetPath;
}
