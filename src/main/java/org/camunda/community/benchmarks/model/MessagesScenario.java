package org.camunda.community.benchmarks.model;

import java.io.Serializable;
import java.util.List;

public class MessagesScenario implements Serializable {
  
  /**
   * uid
   */
  private static final long serialVersionUID = 473458818374358324L;

  private int currentPosition = 0;

  private List<Message> messageSequence;

  public List<Message> getMessageSequence() {
    return messageSequence;
  }

  public void setMessageSequence(List<Message> messageSequence) {
    this.messageSequence = messageSequence;
  }

  public int getCurrentPosition() {
    return currentPosition;
  }

  public void setCurrentPosition(int currentPosition) {
    this.currentPosition = currentPosition;
  }

  public void incrementPosition() {
    this.currentPosition++;
  }
}
