package com.azurebatch.demo;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.task.configuration.EnableTask;
import org.springframework.context.annotation.ComponentScan;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

@SpringBootApplication
@ComponentScan(basePackages = "com.azurebatch")
@EnableTask
@EnableBatchProcessing
public class PartitionedBatchJobApplication {

	public static void main(String[] args) throws IOException, XmlPullParserException {

		SpringApplication.run(PartitionedBatchJobApplication.class, args);
	}


}
