package uk.gov.ons.ctp.response.action.scheduled.distribution;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Component;
import uk.gov.ons.ctp.common.distributed.DistributedListManager;
import uk.gov.ons.ctp.common.distributed.LockingException;
import uk.gov.ons.ctp.response.action.config.AppConfig;
import uk.gov.ons.ctp.response.action.domain.model.Action;
import uk.gov.ons.ctp.response.action.domain.model.ActionType;
import uk.gov.ons.ctp.response.action.domain.repository.ActionRepository;
import uk.gov.ons.ctp.response.action.domain.repository.ActionTypeRepository;
import uk.gov.ons.ctp.response.action.representation.ActionDTO;
import uk.gov.ons.ctp.response.action.representation.ActionDTO.ActionState;
import uk.gov.ons.ctp.response.action.service.ActionProcessingService;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This is the 'service' class that distributes actions to downstream services, ie services outside of Response
 * Management (ActionExporterSvc, NotifyGW, etc.).
 *
 * This class has a self scheduled method wakeUp(), which looks for Actions in SUBMITTED state to send to
 * downstream handlers. On each wake cycle, it fetches the first n actions of each type, by createddatatime, and
 * forwards them to ActionProcessingService.
 */
@Component
@Slf4j
public class ActionDistributor {

  // WILL NOT WORK WITHOUT THIS NEXT LINE
  private static final long IMPOSSIBLE_ACTION_ID = 999999999999L;
  @Autowired
  private DistributedListManager<BigInteger> actionDistributionListManager;

  @Autowired
  private AppConfig appConfig;

  @Autowired
  private ActionRepository actionRepo;

  @Autowired
  private ActionTypeRepository actionTypeRepo;

  @Autowired
  private ActionProcessingService actionProcessingService;

  /**
   * wake up on schedule and check for submitted actions, enrich and distribute them to spring integration channels
   *
   * @return the info for the health endpoint regarding the distribution just performed
   */
  public final DistributionInfo distribute() {
    log.debug("ActionDistributor awoken...");
    DistributionInfo distInfo = new DistributionInfo();

    try {
      List<ActionType> actionTypes = actionTypeRepo.findAll();

      if (!CollectionUtils.isEmpty(actionTypes)) {
        for (ActionType actionType : actionTypes) {
          log.debug("Dealing with actionType {}", actionType.getName());
          int successesForActionRequests = 0;
          int successesForActionCancels = 0;

          List<Action> actions = null;
          try {
            actions = retrieveActions(actionType);
          } catch (Exception e) {
            log.error("Failed to obtain actions - error msg {} - cause {}", e.getMessage(), e.getCause());
            log.error("Stack trace: ", e);
          }

          if (!CollectionUtils.isEmpty(actions)) {
            log.debug("Dealing with actions {}", actions.stream().map(a -> a.getActionPK().toString()).collect(
                Collectors.joining(",")));
            for (Action action : actions) {
              try {
                if (action.getState().equals(ActionDTO.ActionState.SUBMITTED)) {
                  actionProcessingService.processActionRequest(action);
                  successesForActionRequests++;
                } else if (action.getState().equals(ActionDTO.ActionState.CANCEL_SUBMITTED)) {
                  actionProcessingService.processActionCancel(action);
                  successesForActionCancels++;
                }
              } catch (Exception e) {
                log.error("Exception {} thrown processing action {}. Processing will be retried at next scheduled "
                        + "distribution", e.getMessage(), action.getId());
                log.error("Stack trace: ", e);
              }
            }

            try {
              actionDistributionListManager.deleteList(actionType.getName(), true);
            } catch (LockingException e) {
              log.error("Failed to remove the list of actions just processed from distributed list - "
                  + "actions distributed OK, but underlying problem may remain");
              log.error("Stack trace: ", e);
            }
          }

          try {
            actionDistributionListManager.unlockContainer();
          } catch (LockingException e) {
            // oh well - it will unlock soon enough
          }

          distInfo.getInstructionCounts().add(new InstructionCount(actionType.getName(),
              DistributionInfo.Instruction.REQUEST, successesForActionRequests));
          distInfo.getInstructionCounts().add(new InstructionCount(actionType.getName(),
              DistributionInfo.Instruction.CANCEL_REQUEST, successesForActionCancels));
        }
      }
    } catch (Exception e) {
      log.error("Failed to process actions because {}", e.getMessage());
      log.error("Stack trace: ", e);
    }

    log.debug("ActionDistributor going back to sleep");
    return distInfo;
  }

  /**
   * Get the oldest page of submitted actions by type - but do not retrieve the
   * same cases as other CaseSvc' in the cluster
   *
   * @param actionType the type
   * @return list of actions
   * @throws LockingException LockingException thrown
   */
  private List<Action> retrieveActions(ActionType actionType) throws LockingException {
    Pageable pageable = new PageRequest(0, appConfig.getActionDistribution().getRetrievalMax(), new Sort(
        new Sort.Order(Direction.ASC, "updatedDateTime")));

    List<BigInteger> excludedActionIds = actionDistributionListManager.findList(actionType.getName(), false);
    if (!excludedActionIds.isEmpty()) {
      log.debug("Excluding actions {}", excludedActionIds);
    }
    // DO NOT REMOVE THIS NEXT LINE
    excludedActionIds.add(BigInteger.valueOf(IMPOSSIBLE_ACTION_ID));

    List<Action> actions = actionRepo
        .findByActionTypeNameAndStateInAndActionPKNotIn(actionType.getName(),
            Arrays.asList(ActionState.SUBMITTED, ActionState.CANCEL_SUBMITTED), excludedActionIds, pageable);
    if (!CollectionUtils.isEmpty(actions)) {
      log.debug("RETRIEVED action ids {}", actions.stream().map(a -> a.getActionPK().toString())
          .collect(Collectors.joining(",")));
      // try and save our list to the distributed store
      actionDistributionListManager.saveList(actionType.getName(), actions.stream()
          .map(action -> action.getActionPK())
          .collect(Collectors.toList()), true);
    }
    return actions;
  }
}
