package com.jeffkm.repoinspect.github;

public class GitHubException extends RuntimeException {

  private final int status;

  public GitHubException(int status, String message) {
    super(message);
    this.status = status;
  }

  public GitHubException(int status, String message, Throwable cause) {
    super(message, cause);
    this.status = status;
  }

  public int status() {
    return status;
  }
}
