package com.jeffkm.repoinspect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class RepoInspectApplication {

  public static void main(String[] args) {
    SpringApplication.run(RepoInspectApplication.class, args);
  }
}
