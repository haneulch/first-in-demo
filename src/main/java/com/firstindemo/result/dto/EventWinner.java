package com.firstindemo.result.dto;

import java.util.List;

public record EventWinner(String userId) {
  public static List<EventWinner> of(List<String> winners) {
    return winners.stream().map(EventWinner::new).toList();
  }
}
