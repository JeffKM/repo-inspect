package com.jeffkm.repoinspect.archive;

import java.io.IOException;

public class LimitExceededException extends IOException {

  public LimitExceededException(long limit) {
    super("크기 상한 초과: " + limit + "바이트");
  }
}
