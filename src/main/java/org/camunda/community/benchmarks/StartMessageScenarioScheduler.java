package org.camunda.community.benchmarks;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.apache.commons.lang.SerializationUtils;
import org.camunda.community.benchmarks.config.BenchmarkConfiguration;
import org.camunda.community.benchmarks.model.Message;
import org.camunda.community.benchmarks.model.MessagesScenario;
import org.camunda.community.benchmarks.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;

import io.camunda.zeebe.client.ZeebeClient;

@Component
@ConditionalOnProperty(name = "benchmark.messageScenario", matchIfMissing = false)
public class StartMessageScenarioScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(StartMessageScenarioScheduler.class);

  private BenchmarkConfiguration config;

  private ZeebeClient client;

  private TaskScheduler taskScheduler;


  private MessagesScenario scenario;
  private long nbMessages = 0;
  long currentScenario=0;
  long loadDuration = 0;
  private Duration messageTtl = null;
  
  public StartMessageScenarioScheduler(ZeebeClient client, 
      BenchmarkConfiguration config,
      TaskScheduler taskScheduler) {
    this.client = client;
    this.config = config;
    this.taskScheduler = taskScheduler;
    messageTtl = Duration.ofMinutes(config.getMessagesTtl());
  }

  @PostConstruct
  public void init() throws StreamReadException, DatabindException, IOException {
    scenario = JsonUtils.fromJsonInputStream(config.getMessageScenario().getInputStream(), MessagesScenario.class);
    nbMessages = scenario.getMessageSequence().size();
    LOG.warn("Using scenario "+config.getMessageScenario()+" with "+nbMessages+" steps");
  }

  //every 100ms
  @Scheduled(fixedRate = 100, initialDelay = 2000)
  public void startScenarios() {
    if (loadDuration<config.getMessagesLoadDuration()) {
      loadDuration+=100;
      for(int i=0;i<config.getMessagesScenariosPerSecond()/10;i++) {
        MessagesScenario newScenario = (MessagesScenario) SerializationUtils.clone(scenario);
        replacePlaceHolders(newScenario, String.valueOf(currentScenario));

        MessageSender messageSender = new MessageSender(newScenario);
        messageSender.run();
        currentScenario++;
      }
    }
  }

  @SuppressWarnings("unchecked")
  private void replacePlaceHolders(MessagesScenario scenario, String counter) {
    for(Message message : scenario.getMessageSequence()) {
      message.setCorrelationKey(message.getCorrelationKey().replace("${COUNT}", counter));
      Map<String, Object> variables = message.getVariables();
      if (variables!=null) {
        for(Map.Entry<String, Object> var : variables.entrySet()) {
          if (var.getValue() instanceof String) {
            if (((String)var.getValue()).contains("${COUNT}")) {
              variables.put(var.getKey(), ((String)var.getValue()).replace("${COUNT}", counter));
            }
          } else if (var.getValue() instanceof List) {
            List<String> newList = new ArrayList<>();
            for(String listItem : (List<String>)var.getValue()) {
              newList.add(listItem.replace("${COUNT}", counter));
            }
            variables.put(var.getKey(), newList);
          }
        }
      } else {
        message.setVariables(new HashMap<>());
      }
    }
  }

  class MessageSender implements Runnable{
    private MessagesScenario scenario;

    public MessageSender(MessagesScenario scenario){
      this.scenario = scenario;
    }

    @Override
    public void run() {
      Message message = scenario.getMessageSequence().get(scenario.getCurrentPosition());
      client
      .newPublishMessageCommand()
      .messageName(message.getMessageName())
      .correlationKey(message.getCorrelationKey())
      .timeToLive(messageTtl)
      .variables(message.getVariables())
      .send();
      System.out.println(System.currentTimeMillis()+" : "+message.getMessageName()+" "+message.getCorrelationKey());
      scenario.setCurrentPosition(scenario.getCurrentPosition()+1);
      if (scenario.getCurrentPosition()<nbMessages) {
        taskScheduler.schedule(
          this,
          new Date(System.currentTimeMillis() + config.getDelayBetweenMessages())
        );
      }
    }
  }

}
