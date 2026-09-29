package com.sandeeprathore.reviewagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ReviewAgentApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReviewAgentApplication.class, args);
	}

}
