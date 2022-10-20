package org.camunda.community.benchmarks;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.PostConstruct;

import org.apache.commons.lang.SerializationUtils;
import org.camunda.community.benchmarks.config.BenchmarkConfiguration;
import org.camunda.community.benchmarks.model.Message;
import org.camunda.community.benchmarks.model.MessagesScenario;
import org.camunda.community.benchmarks.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;

import io.camunda.zeebe.client.ZeebeClient;

@Component
@ConditionalOnProperty(name = "benchmark.messageScenario", matchIfMissing = false)
public class StartMessageScenarioScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(StartMessageScenarioScheduler.class);

  @Autowired
  private BenchmarkConfiguration config;

  @Autowired
  private ZeebeClient client;
  
  private MessagesScenario scenario;
  
  private Map<Long, MessagesScenario> pendingScenarios = new HashMap<>();
 
  long nbScenario = 0;
  long nbScenarioPerCycle = 0;
  long nbCycleBeforeNextStep = 0;
  int currentPosition = 0;
  long currentScenario=0;
  long currentCycle = 0;
  long loadDuration = 0;
  
  long overalRepetition=0;
  
  private Duration messageTtl = Duration.ofMinutes(5);
  
  
  
  @PostConstruct
  public void init() throws StreamReadException, DatabindException, IOException {
    scenario = JsonUtils.fromJsonFile(config.getMessageScenario().getFile(), MessagesScenario.class);
    
    nbScenario=config.getMessagesPerSecond()*(config.getDelayBetweenMessages()/1000);
    nbScenarioPerCycle = config.getMessagesPerSecond() / 10;
    nbCycleBeforeNextStep = config.getDelayBetweenMessages() / 100;
    
    for(long i = 0; i < nbScenario; i++) {
      MessagesScenario newScenario = (MessagesScenario) SerializationUtils.clone(scenario);
      
      pendingScenarios.put(i, newScenario);
    }
  }
  
  //every 100ms
  @Scheduled(fixedRate = 100, initialDelay = 6000)
  public void sendMessages() {
    if (!pendingScenarios.isEmpty()) {
      loadDuration+=100;
      boolean newScenarioLoop = true;
      while(newScenarioLoop || currentScenario%nbScenarioPerCycle!=0) {
        MessagesScenario pendingScenario = pendingScenarios.get(currentScenario);
        Message message = pendingScenario.getMessageSequence().get(currentPosition);
        prepareAndSendMessage(message, String.valueOf(currentScenario+overalRepetition*nbScenario), messageTtl);
        newScenarioLoop = false;
        currentScenario++;
      }
      currentCycle++;
      if (currentCycle==nbCycleBeforeNextStep) {
        currentPosition++;
        currentScenario=0;
        currentCycle=0;
      }
      if (currentPosition==scenario.getMessageSequence().size()) {
        if (loadDuration>=config.getMessagesLoadDuration()) {
          pendingScenarios.clear();//we stop the test here
        }
        currentPosition=0;
        overalRepetition++;
      }
    }
  }

  private void prepareAndSendMessage(Message message, String counter, Duration ttl) {
    Map<String, Object> variables = message.getVariables();
    if (variables!=null) {
      if (variables.containsKey("transactionId")) {
        String transactionId = (String) variables.get("transactionId");
        variables.put("transactionId", transactionId.replace("${COUNT}", counter));
      }
      if (variables.containsKey("parcelIds")) {
        List<String> parcels = (List<String>) variables.get("parcelIds");
        variables.put("parcelIds", List.of(parcels.get(0).replace("${COUNT}", counter)));
      }
    } else {
      variables = new HashMap<>();
    }
    String correlation=message.getCorrelationKey().replace("${COUNT}", counter);
    client
        .newPublishMessageCommand()
        .messageName(message.getMessageName())
        .correlationKey(correlation)
        .timeToLive(ttl)
        .variables(variables)
        .send();
    System.out.println("SENT "+message.getMessageName()+" correlation "+correlation);
  }
  
}
