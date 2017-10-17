package uk.gov.ons.ctp.response.action;

import net.sourceforge.cobertura.CoverageIgnore;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.client.RestTemplate;
import uk.gov.ons.ctp.common.distributed.*;
import uk.gov.ons.ctp.common.error.RestExceptionHandler;
import uk.gov.ons.ctp.common.jackson.CustomObjectMapper;
import uk.gov.ons.ctp.common.rest.RestUtility;
import uk.gov.ons.ctp.common.state.StateTransitionManager;
import uk.gov.ons.ctp.common.state.StateTransitionManagerFactory;
import uk.gov.ons.ctp.response.action.config.AppConfig;
import uk.gov.ons.ctp.response.action.representation.ActionDTO;
import uk.gov.ons.ctp.response.action.state.ActionSvcStateTransitionManagerFactory;

import java.math.BigInteger;

/**
 * The main entry point into the Action Service SpringBoot Application.
 */
@CoverageIgnore
@SpringBootApplication
@EnableTransactionManagement
@IntegrationComponentScan
@ComponentScan(basePackages = {"uk.gov.ons.ctp.response"})
@EnableJpaRepositories(basePackages = {"uk.gov.ons.ctp.response"})
@EntityScan("uk.gov.ons.ctp.response")
@EnableAsync
@EnableCaching
@EnableScheduling
@ImportResource("springintegration/main.xml")
public class ActionSvcApplication {

  public static final String ACTION_DISTRIBUTION_LIST = "actionsvc.action.distribution";
  public static final String ACTION_EXECUTION_LOCK = "actionsvc.action.execution";
  public static final String REPORT_EXECUTION_LOCK = "actionsvc.report.execution";

  @Autowired
  private AppConfig appConfig;

  @Autowired
  private StateTransitionManagerFactory actionSvcStateTransitionManagerFactory;

  /**
   * Bean used to access Distributed List Manager
   *
   * @param redissonClient Redisson Client
   * @return the Distributed List Manager
   */
  @Bean
  public DistributedListManager<BigInteger> actionDistributionListManager(RedissonClient redissonClient) {
    return new DistributedListManagerRedissonImpl<BigInteger>(ActionSvcApplication.ACTION_DISTRIBUTION_LIST,
            redissonClient,
        appConfig.getDataGrid().getListTimeToWaitSeconds(),
        appConfig.getDataGrid().getListTimeToLiveSeconds());
  }

  /**
   * Bean used to access Distributed Lock Manager
   *
   * @param redissonClient Redisson Client
   * @return the Distributed Lock Manager
   */
  @Bean
  public DistributedLockManager actionPlanExecutionLockManager(RedissonClient redissonClient) {
    return new DistributedLockManagerRedissonImpl(ActionSvcApplication.ACTION_EXECUTION_LOCK, redissonClient,
        appConfig.getDataGrid().getLockTimeToLiveSeconds());
  }

  /**
   * Bean used to access Distributed Lock Manager
   *
   * @param redissonClient Redisson Client
   * @return the Distributed Lock Manager
   */
  @Bean
  public DistributedInstanceManager reportDistributedInstanceManager(RedissonClient redissonClient) {
    return new DistributedInstanceManagerRedissonImpl(REPORT_EXECUTION_LOCK, redissonClient);
  }

  /**
   * Bean used to access Distributed Latch Manager
   *
   * @param redissonClient Redisson Client
   * @return the Distributed Lock Manager
   */
  @Bean
  public DistributedLatchManager reportDistributedLatchManager(RedissonClient redissonClient) {
    return new DistributedLatchManagerRedissonImpl(REPORT_EXECUTION_LOCK, redissonClient,
            appConfig.getDataGrid().getReportLockTimeToLiveSeconds());
  }

  /**
   * Bean used to access Distributed Execution Lock Manager
   *
   * @param redissonClient Redisson Client
   * @return the Distributed Lock Manager
   */
  @Bean
  public DistributedLockManager reportDistributedLockManager(RedissonClient redissonClient) {
    return new DistributedLockManagerRedissonImpl(REPORT_EXECUTION_LOCK, redissonClient,
            appConfig.getDataGrid().getReportLockTimeToLiveSeconds());
  }

  /**
   * Bean used to create and configure Redisson Client
   *
   * @return the Redisson client
   */
  @Bean
  public RedissonClient redissonClient() {
    Config config = new Config();
    config.useSingleServer()
        .setAddress(appConfig.getDataGrid().getAddress())
        .setPassword(appConfig.getDataGrid().getPassword());
    return Redisson.create(config);
  }
  
  /**
   * The restTemplate bean injected in REST client classes
   *
   * @return the restTemplate used in REST calls
   */
  @Bean
  public RestTemplate restTemplate() {
    return new RestTemplate();
  }

  /**
   * Bean used to access case frame service through REST calls
   *
   * @return the service client
   */
  @Bean
  @Qualifier("caseSvcClient")
  public RestUtility caseClient() {
    RestUtility restUtility = new RestUtility(appConfig.getCaseSvc().getConnectionConfig());
    return restUtility;
  }

  /**
   * Bean used to access collection exercise service through REST calls
   *
   * @return the service client
   */
  @Bean
  @Qualifier("collectionExerciseSvcClient")
  public RestUtility collectionClient() {
    RestUtility restUtility = new RestUtility(appConfig.getCollectionExerciseSvc().getConnectionConfig());
    return restUtility;
  }

  /**
   * Bean used to access party frame service through REST calls
   *
   * @return the service client
   */
  @Bean
  @Qualifier("partySvcClient")
  public RestUtility partyClient() {
    RestUtility restUtility = new RestUtility(appConfig.getPartySvc().getConnectionConfig());
    return restUtility;
  }

  /**
   * Bean used to access survey service through REST calls
   *
   * @return the service client
   */
  @Bean
  @Qualifier("surveySvcClient")
  public RestUtility surveyClient() {
    RestUtility restUtility = new RestUtility(appConfig.getSurveySvc().getConnectionConfig());
    return restUtility;
  }

  /**
   * Bean to allow application to make controlled state transitions of Actions
   *
   * @return the state transition manager specifically for Actions
   */
  @Bean
  public StateTransitionManager<ActionDTO.ActionState, ActionDTO.ActionEvent> actionSvcStateTransitionManager() {
    return actionSvcStateTransitionManagerFactory.getStateTransitionManager(
        ActionSvcStateTransitionManagerFactory.ACTION_ENTITY);
  }

  /**
   * Rest Exception Handler
   *
   * @return a Rest Exception Handler
   */
  @Bean
  public RestExceptionHandler restExceptionHandler() {
    return new RestExceptionHandler();
  }

  /**
   * Custom Object Mapper
   *
   * @return a customer object mapper
   */
  @Bean @Primary
  public CustomObjectMapper customObjectMapper() {
    return new CustomObjectMapper();
  }

  /**
   * This method is the entry point to the Spring Boot application.
   *
   * @param args These are the optional command line arguments
   */
  public static void main(final String[] args) {
    SpringApplication.run(ActionSvcApplication.class, args);
  }
}
