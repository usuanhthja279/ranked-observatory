package com.valorant.leaderboard.matchintelligence;

public record MatchValidationIssue(Severity severity, String code, String path, String message) {
  public enum Severity { INFO, WARNING, ERROR }

  public MatchValidationIssue {
    if (severity == null || code == null || path == null || message == null) {
      throw new IllegalArgumentException("Validation issue fields are required.");
    }
  }
}
