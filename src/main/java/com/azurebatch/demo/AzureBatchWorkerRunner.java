package com.azurebatch.demo;

import org.springframework.batch.core.*;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.batch.core.step.NoSuchStepException;
import org.springframework.batch.integration.partition.BeanFactoryStepLocator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.cloud.task.batch.partition.DeployerPartitionHandler;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;


@Component
public class AzureBatchWorkerRunner implements CommandLineRunner {

    @Autowired
    private ConfigurableApplicationContext configurableApplicationContext;

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private JobExplorer jobExplorer;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private Environment environment;

    @Autowired
    private Job job;

    @Override
    public void run(String... args) {

        List profiles = Arrays.asList(environment.getActiveProfiles());
        try {
            if (profiles.contains("manager")) {
                runManager();
            } else if (profiles.contains("worker")) {
                runWorker();
            }
        } catch (Exception e) {
        }
    }

    private void runManager() throws JobParametersInvalidException, JobExecutionAlreadyRunningException, JobRestartException, JobInstanceAlreadyCompleteException {
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis()) // this is to make it as a new instance of the job so that it can run again.
                .toJobParameters();

        jobLauncher.run(job, jobParameters);
    }

    private void runWorker() throws Exception {
        BeanFactoryStepLocator stepLocator = new BeanFactoryStepLocator();
        stepLocator.setBeanFactory(configurableApplicationContext);
        Long jobExecutionId = Long.parseLong(environment.getProperty(DeployerPartitionHandler.SPRING_CLOUD_TASK_JOB_EXECUTION_ID.replaceAll("\\.|-", "_").toUpperCase()));
        Long stepExecutionId = Long.parseLong(environment.getProperty(DeployerPartitionHandler.SPRING_CLOUD_TASK_STEP_EXECUTION_ID.replaceAll("\\.|-", "_").toUpperCase()));
        StepExecution stepExecution = jobExplorer.getStepExecution(jobExecutionId, stepExecutionId);
        if (stepExecution == null) {
            throw new NoSuchStepException(String.format("No StepExecution could be located for step execution id %s within job execution %s", stepExecutionId, jobExecutionId));
        } else {
            String stepName = environment.getProperty(DeployerPartitionHandler.SPRING_CLOUD_TASK_STEP_NAME.replaceAll("\\.|-", "_").toUpperCase());
            Step step = stepLocator.getStep(stepName);

            try {
                step.execute(stepExecution);
            } catch (JobInterruptedException e) {
                stepExecution.setStatus(BatchStatus.STOPPED);
                jobRepository.update(stepExecution);
                throw e;
            } catch (Exception e) {
                stepExecution.addFailureException(e);
                stepExecution.setStatus(BatchStatus.FAILED);
                jobRepository.update(stepExecution);
                throw e;
            }
        }
    }
}
