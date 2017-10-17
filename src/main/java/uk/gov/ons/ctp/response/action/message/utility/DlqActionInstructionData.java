package uk.gov.ons.ctp.response.action.message.utility;

import lombok.Builder;
import lombok.Data;
import org.springframework.amqp.rabbit.support.CorrelationData;

@Builder
@Data
public class DlqActionInstructionData {
  private Integer messagePrimaryKey;  // Not null ONLY when we replay dlq messages from DB
  private String handler;
  private String message;
}