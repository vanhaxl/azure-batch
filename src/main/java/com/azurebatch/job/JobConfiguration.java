/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.azurebatch.job;

import com.azurebatch.common.BaseJobConfig;
import com.azurebatch.tasklauncher.ContainerResource;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.explore.support.JobExplorerFactoryBean;
import org.springframework.batch.core.partition.PartitionHandler;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.deployer.resource.support.DelegatingResourceLoader;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;
import org.springframework.cloud.task.batch.partition.DeployerPartitionHandler;
import org.springframework.cloud.task.batch.partition.SimpleEnvironmentVariablesProvider;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;

import javax.sql.DataSource;
import java.util.*;


@Configuration
@Profile("jobA")
public class JobConfiguration extends BaseJobConfig {

    @Autowired
    private Environment environment;

    @Value("${docker-container-name:none}")
    private String dockerContainerName;

    private static final int GRID_SIZE = 4;

    @Bean
    public PartitionHandler partitionHandler(TaskLauncher taskLauncher) throws Exception {
        //Resource resource = resourceLoader.getResource("file:/Users/vanhanguyen/Desktop/tmp/demo-0.0.1-SNAPSHOT.jar");

        Resource resource = new ContainerResource(dockerContainerName);

        DeployerPartitionHandler partitionHandler =
                new DeployerPartitionHandler(taskLauncher, jobExplorer, resource, "workerStep");

        partitionHandler.setDefaultArgsAsEnvironmentVars(true);
        //pass the StepExecutionRequest through environment Variable
        SimpleEnvironmentVariablesProvider environmentVariablesProvider = new SimpleEnvironmentVariablesProvider(environment);
        environmentVariablesProvider.setIncludeCurrentEnvironment(false);
        partitionHandler.setEnvironmentVariablesProvider(environmentVariablesProvider);

        //monitor workers every 5s and see if they are completed
        partitionHandler.setPollInterval(5000);
        partitionHandler.setGridSize(GRID_SIZE);
        partitionHandler.setApplicationName("PartitionedBatchJobTask");
        partitionHandler.afterPropertiesSet();

        return partitionHandler;
    }

    @Bean
    public Partitioner partitioner() {
        return new Partitioner() {
            @Override
            public Map<String, ExecutionContext> partition(int gridSize) {
                Map<String, ExecutionContext> partitions = new HashMap<>(gridSize);

                for (int i = 0; i < gridSize; i++) {
                    ExecutionContext context1 = new ExecutionContext();
                    context1.put("partitionNumber", i);

                    partitions.put("partition" + i, context1);
                }

                return partitions;
            }
        };
    }

    @Bean
    @StepScope
    public Tasklet workerTasklet(
            final @Value("#{stepExecutionContext['partitionNumber']}") Integer partitionNumber) {

        return new Tasklet() {
            @Override
            public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
                System.out.println("This tasklet ran partition: " + partitionNumber);

                return RepeatStatus.FINISHED;
            }
        };
    }

//master step
    @Bean
    public Step step1(PartitionHandler partitionHandler) throws Exception {
        return stepBuilderFactory.get("step1")
                .partitioner(workerStep().getName(), partitioner())
                .step(workerStep())
                .partitionHandler(partitionHandler)
                .build();
    }
//slave step
    @Bean
    public Step workerStep() {
        return stepBuilderFactory.get("workerStep")
                .tasklet(workerTasklet(null))
                .build();
    }

    @Bean(name="jobA")
    public Job testJob(PartitionHandler partitionHandler) throws Exception {
        Random random = new Random();
        return jobBuilderFactory.get("partitionedJob" + random.nextInt())
                .start(step1(partitionHandler))
                .build();
    }
}
