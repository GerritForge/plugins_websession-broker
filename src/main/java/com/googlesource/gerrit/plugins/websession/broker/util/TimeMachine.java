package com.googlesource.gerrit.plugins.websession.broker.util;

import com.google.inject.Singleton;
import java.time.Clock;
import java.time.Instant;

@Singleton
public class TimeMachine {

  private Clock clock = Clock.systemDefaultZone();

  public Instant now() {
    return Instant.now(getClock());
  }

  public Clock getClock() {
    return clock;
  }
}
