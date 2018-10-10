package com.azurebatch.tasklauncher;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;
import org.springframework.cloud.deployer.spi.task.LaunchState;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;
import org.springframework.cloud.deployer.spi.task.TaskStatus;
import org.springframework.cloud.task.batch.partition.DeployerPartitionHandler;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;


@Slf4j
@Component
@Profile("local")
public class LocalTaskLauncher implements TaskLauncher {

    @Override
    public String launch(AppDeploymentRequest appDeploymentRequest) {
        Map<String, String> environmentVariables = appDeploymentRequest.getDefinition().getProperties();

        String taskId = environmentVariables.get(DeployerPartitionHandler.SPRING_CLOUD_TASK_NAME)
                .replace(":", "-");

        StringBuilder runOptionBuilder = new StringBuilder().append("Worker: " + taskId + "\n");
        for (Map.Entry<String, String> entry:environmentVariables.entrySet()){
            runOptionBuilder.append("    " + entry.getKey().replaceAll("\\.|-", "_").toUpperCase() + "=" + entry.getValue() + "\n");
        }
        System.out.println("environment: " + environmentVariables);
        System.out.println("run option builder: " + runOptionBuilder);
        System.out.println("end---run option builder");

        return taskId;
    }

    @Override
    public void cancel(String id) {
    }

    @Override
    public TaskStatus status(String id) {
        System.out.println("calling azure batch status :"+id);
        return new TaskStatus(id, LaunchState.unknown, new HashMap<>());
    }

    @Override
    public void cleanup(String id) {
        System.out.println("calling azure batch cleaning up :"+id);

    }

    @Override
    public void destroy(String id) {
        System.out.println("calling azure batch destroying :"+id);
    }

    @Override
    public RuntimeEnvironmentInfo environmentInfo() {
        return new RuntimeEnvironmentInfo.Builder()
                .spiClass(TaskLauncher.class)
                .implementationName(this.getClass().getSimpleName())
                .implementationVersion("1.0")
                .platformType("Local")
                .platformApiVersion("1.0")
                .platformClientVersion("1.0")
                .platformHostVersion("1.0")
                .build();
    }
}
