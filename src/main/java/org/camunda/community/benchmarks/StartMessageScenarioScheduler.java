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
 
  private long nbScenario=0;
  private long scenarioPerDecisecond=0;
  
  private AtomicLong scenarioCounter = new AtomicLong(0);
  
  private Duration initialMessageTtl = Duration.ofSeconds(0);
  private Duration nextMessageTtl = Duration.ofSeconds(45);
  
  @PostConstruct
  public void init() throws StreamReadException, DatabindException, IOException {
    scenario = JsonUtils.fromJsonFile(config.getMessageScenario().getFile(), MessagesScenario.class);
    scenarioPerDecisecond = config.getMessagesPerSecond() / (scenario.getMessageSequence().size()*10);
    nbScenario = config.getMessagesTotal() / scenario.getMessageSequence().size();
  }
  
  //every 100ms
  @Scheduled(fixedRate = 100, initialDelay = 2000)
  public void startSomeProcessInstances() {
    Set<Long> toBeRemoved = new HashSet<>();
    for(Map.Entry<Long, MessagesScenario> pending : pendingScenarios.entrySet()) {
      Long counter = pending.getKey();
      MessagesScenario pendingScenario = pending.getValue();
      Message message = pendingScenario.getMessageSequence().get(pendingScenario.getCurrentPosition());
      
      prepareAndSendMessage(message, String.valueOf(counter), nextMessageTtl);
      
      pendingScenario.incrementPosition();
      if(pendingScenario.getCurrentPosition()>=pendingScenario.getMessageSequence().size()) {
        toBeRemoved.add(counter);
      }
    }
    for(Long toRemove : toBeRemoved) {
      pendingScenarios.remove(toRemove);
    }
    if (scenarioCounter.get()<nbScenario) {
      for(int i = 0; i < scenarioPerDecisecond; i++) {
        long counter = scenarioCounter.incrementAndGet();
        MessagesScenario newScenario = (MessagesScenario) SerializationUtils.clone(scenario);
        
        Message message = newScenario.getMessageSequence().get(0);
        
        prepareAndSendMessage(message, String.valueOf(counter), initialMessageTtl);
        
        newScenario.setCurrentPosition(1);
        pendingScenarios.put(counter, newScenario);
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
