package uk.gov.ons.ctp.response.action.message.utility;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class DlqActionInstructionCache {

  private Map<String, DlqActionInstructionData> theMap;

  public DlqActionInstructionCache() {
    theMap = new HashMap<>();
  }

  public void store(String correlationDataId, DlqActionInstructionData dlqActionInstructionData) {
    log.debug("store correlationDataId {} with {}", correlationDataId, dlqActionInstructionData);
    theMap.put(correlationDataId, dlqActionInstructionData);
  }

  public void remove(String correlationDataId) {
    log.debug("remove data associated with correlationDataId {}", correlationDataId);
    theMap.remove(correlationDataId);
  }

  public DlqActionInstructionData retrieve(String correlationDataId) {
    log.debug("retrieve data associated with correlationDataId {}", correlationDataId);
    return theMap.get(correlationDataId);
  }
}
