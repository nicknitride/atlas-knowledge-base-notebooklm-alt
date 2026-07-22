package dev.atlas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class AtlasApplication {
  public static void main(String[] args) {
    SpringApplication.run(AtlasApplication.class, args);
  }
}
