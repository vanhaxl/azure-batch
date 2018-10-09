package com.azurebatch.common;

import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public abstract class BaseJobConfig {

    @Autowired
    protected JobBuilderFactory jobBuilderFactory;

    @Autowired
    protected StepBuilderFactory stepBuilderFactory;

    @Autowired
    protected JobRepository jobRepository;

    @Autowired
    protected JobExplorer jobExplorer;

    protected JobBuilder jobBuilder(){
        Profile profile = this.getClass().getAnnotation(Profile.class);
        return jobBuilderFactory.get(profile.value()[0]);
    }

}
