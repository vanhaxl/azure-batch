package com.azurebatch.demo;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.task.configuration.EnableTask;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.azurebatch")
@EnableBatchProcessing
@EnableTask
public class PartitionedBatchJobApplication {

	public static void main(String[] args) {
		SpringApplication.run(PartitionedBatchJobApplication.class, args);
	}
}
