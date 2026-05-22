package com.dlqanalyzer.dlq_analyzer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DlqAnalyzerApplication {

	public static void main(String[] args) {
		SpringApplication.run(DlqAnalyzerApplication.class, args);
	}

}
