package org.camunda.community.benchmarks.model;

import java.io.Serializable;
import java.util.Map;

public class Message implements Serializable {

  /**
   * serial version uid
   */
  private static final long serialVersionUID = 5552637159992980244L;

  private String messageName;
  
  private String correlationKey;
  
  private Map<String, Object> variables;

  public String getMessageName() {
    return messageName;
  }

  public void setMessageName(String messageName) {
    this.messageName = messageName;
  }

  public String getCorrelationKey() {
    return correlationKey;
  }

  public void setCorrelationKey(String correlationKey) {
    this.correlationKey = correlationKey;
  }

  public Map<String, Object> getVariables() {
    return variables;
  }

  public void setVariables(Map<String, Object> variables) {
    this.variables = variables;
  }
  
}
