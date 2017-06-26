package uk.gov.ons.ctp.response.action.message;

import uk.gov.ons.ctp.common.error.CTPException;
import uk.gov.ons.ctp.response.casesvc.message.notification.CaseNotifications;

/**
 * Interface for the receipt of case lifecycle notification messages from the
 * Spring Integration queue
 */
public interface CaseNotificationReceiver {

  /**
   * Will be called with the unmarshalled JMS message sent from the case service
   * on life cycle event
   *
   * @param caseNotifications unmarshalled XML Java object graph
   * @throws CTPException if action state transition error
   */
  void acceptNotification(CaseNotifications caseNotifications) throws CTPException;
}
