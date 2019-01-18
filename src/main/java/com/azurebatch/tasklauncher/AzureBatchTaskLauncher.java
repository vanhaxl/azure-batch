package com.azurebatch.tasklauncher;

import com.azurebatch.config.BlobRepositoryManagerBase;
import com.microsoft.azure.batch.BatchClient;
import com.microsoft.azure.batch.protocol.models.*;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.SharedAccessBlobPermissions;
import com.microsoft.azure.storage.blob.SharedAccessBlobPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;
import org.springframework.cloud.deployer.spi.task.LaunchState;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;
import org.springframework.cloud.deployer.spi.task.TaskStatus;
import org.springframework.cloud.task.batch.partition.DeployerPartitionHandler;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@Profile("!local")
public class AzureBatchTaskLauncher implements TaskLauncher {

    @Autowired
    private BatchClient batchClient;

    @Value("${docker-container-registry-server}")
    private String containerRegistryServer;

    @Value("${docker-container-registry-username}")
    private String containerRegistryUsername;

    @Value("${docker-container-registry-password}")
    private String containerRegistryPassword;

    @Autowired
    private BlobRepositoryManagerBase blobRepositoryManagerBase;

    @Override
    public String launch(AppDeploymentRequest appDeploymentRequest) {

        StringBuilder runOptionBuilder = new StringBuilder().append("--rm ");

        String newProfile = System.getenv("SPRING_PROFILES_ACTIVE").replace("manager", "worker");
        runOptionBuilder.append("-e SPRING_PROFILES_ACTIVE=" + newProfile + " ");

        Map<String, String> environmentVariables = appDeploymentRequest.getDefinition().getProperties();
        for (Map.Entry<String, String> entry : environmentVariables.entrySet()) {
            runOptionBuilder.append("-e \"" + entry.getKey().replaceAll("\\.|-", "_").toUpperCase() + "=" + entry.getValue() + "\" ");
        }

        TaskContainerSettings containerSettings = new TaskContainerSettings()
                .withRegistry(new ContainerRegistry()
                        .withRegistryServer(containerRegistryServer)
                        .withUserName(containerRegistryUsername)
                        .withPassword(containerRegistryPassword))
                .withImageName(appDeploymentRequest.getResource().getDescription())
                .withContainerRunOptions(runOptionBuilder.toString());

        System.out.println("get resource : " + appDeploymentRequest.getResource().getDescription());
        System.out.println("run option: " + runOptionBuilder.toString());

        // Create job run at the specified pool
        // String jobId = System.getenv("AZ_BATCH_JOB_ID");

        String jobId = "test-azurebatch-job";

        String taskId = environmentVariables.get(DeployerPartitionHandler.SPRING_CLOUD_TASK_NAME)
                .replace(":", "-");

        if (taskId.length() > 64) {
            taskId = taskId.substring(taskId.length() - 64);
        }
        LocalDateTime now = LocalDateTime.now();

        List<OutputFile> outputFiles = generateOutputFiles(taskId, now);

        TaskAddParameter taskAddParameter = new TaskAddParameter().withId(taskId)
                .withContainerSettings(containerSettings)
                .withCommandLine("echo " + taskId + " Start").withOutputFiles(outputFiles);


        try {
            batchClient.taskOperations().createTask(jobId, taskAddParameter);
            System.out.println("Worker Job Submitted:" + taskId);
        } catch (IOException e) {
            System.out.println("Unexpected exception when creating task" + e);
        }

        return taskId;
    }


    public List<OutputFile> generateOutputFiles(String taskId, LocalDateTime now) {
        CloudBlobContainer cloudBlobContainer = generateCloudBlobContainer();
        SharedAccessBlobPolicy writePolicy = generateWriteAccessPolicy(now);
        String containerSasToken = generateContainerSasToken(cloudBlobContainer, writePolicy);
        String containerSasUrl = generateContainerSasUrl(cloudBlobContainer, containerSasToken);

        OutputFileDestination[] outputFileDestinationArray = generateOutputFileDestination(taskId, now, containerSasUrl);
        OutputFileUploadOptions outputFileUploadOptions = generateUploadOptions();
        OutputFile stdoutFile = new OutputFile().withFilePattern("../stdout.txt").withDestination(outputFileDestinationArray[0]).withUploadOptions(outputFileUploadOptions);
        OutputFile stderrFile = new OutputFile().withFilePattern("../stderr.txt").withDestination(outputFileDestinationArray[1]).withUploadOptions(outputFileUploadOptions);

        List<OutputFile> outputFiles = new ArrayList<>();
        outputFiles.add(stdoutFile);
        outputFiles.add(stderrFile);

        return outputFiles;

    }

    public OutputFileDestination[] generateOutputFileDestination(String taskId, LocalDateTime now, String containerSasUrl) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyMMdd");
        StringBuilder pathStringBuilder = new StringBuilder();
        pathStringBuilder.append("AZBatchLogs-");
        pathStringBuilder.append(now.format(formatter));

        StringBuilder pathStdout = new StringBuilder(pathStringBuilder);
        pathStdout.append("/");
        pathStdout.append("stdout_");
        pathStdout.append(taskId);
        pathStdout.append(".txt");

        StringBuilder pathStderr = new StringBuilder(pathStringBuilder);
        pathStderr.append("/");
        pathStderr.append("stderr_");
        pathStderr.append(taskId);
        pathStderr.append(".txt");

        OutputFileBlobContainerDestination outputFileBlobContainerDestination = new OutputFileBlobContainerDestination()
                .withContainerUrl(containerSasUrl)
                .withPath(pathStdout.toString());
        OutputFileBlobContainerDestination outputFileBlobContainerDestination2 = new OutputFileBlobContainerDestination()
                .withContainerUrl(containerSasUrl)
                .withPath(pathStderr.toString());

        OutputFileDestination outputFileDestination = new OutputFileDestination().withContainer(outputFileBlobContainerDestination);
        OutputFileDestination outputFileDestination2 = new OutputFileDestination().withContainer(outputFileBlobContainerDestination2);
        return new OutputFileDestination[]{outputFileDestination, outputFileDestination2};
    }

    public CloudBlobContainer generateCloudBlobContainer() {
        CloudBlobContainer cloudBlobContainer = null;
        try {
            cloudBlobContainer = blobRepositoryManagerBase.getStorageBlobClient().getContainerReference("logs");
            cloudBlobContainer.createIfNotExists();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        } catch (StorageException e) {
            e.printStackTrace();
        }
        return cloudBlobContainer;
    }

    public String generateContainerSasToken(CloudBlobContainer cloudBlobContainer, SharedAccessBlobPolicy writePolicy) {
        String containerSasToken = "";
        try {
            containerSasToken = cloudBlobContainer.generateSharedAccessSignature(writePolicy, null);
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (StorageException e) {
            e.printStackTrace();
        }
        return containerSasToken;
    }

    public OutputFileUploadOptions generateUploadOptions() {
        OutputFileUploadCondition outputFileUploadCondition = OutputFileUploadCondition.TASK_COMPLETION;
        OutputFileUploadOptions outputFileUploadOptions = new OutputFileUploadOptions().withUploadCondition(outputFileUploadCondition);
        return outputFileUploadOptions;
    }

    public SharedAccessBlobPolicy generateWriteAccessPolicy(LocalDateTime now) {
        // calculate Start Time
        Instant result = now.minusDays(1).atZone(ZoneOffset.UTC).toInstant();
        Date startTime = Date.from(result);

        // calculate Expiration Time
        result = now.plusDays(10).atZone(ZoneOffset.UTC).toInstant();
        Date expirationTime = Date.from(result);
        // define a base policy that allows writing for uploads
        SharedAccessBlobPolicy writePolicy = new SharedAccessBlobPolicy();
        writePolicy.setPermissions(EnumSet.of(SharedAccessBlobPermissions.WRITE, SharedAccessBlobPermissions.CREATE));

        writePolicy.setSharedAccessStartTime(startTime);
        writePolicy.setSharedAccessExpiryTime(expirationTime);
        return writePolicy;
    }

    public String generateContainerSasUrl(CloudBlobContainer cloudBlobContainer, String containerSasToken) {
        StringBuilder containerSasUrl = new StringBuilder();
        if (cloudBlobContainer != null) {
            containerSasUrl.append(cloudBlobContainer.getStorageUri().getPrimaryUri().toString());
            containerSasUrl.append("?");
            containerSasUrl.append(containerSasToken);
        }
        return containerSasUrl.toString();
    }

    @Override
    public void cancel(String id) {
        System.out.println("calling azure batch cancel :" + id);
    }

    @Override
    public TaskStatus status(String id) {
        System.out.println("calling azure batch status :" + id);
        return new TaskStatus(id, LaunchState.unknown, new HashMap<>());
    }

    @Override
    public void cleanup(String id) {
        System.out.println("calling azure batch cleaning up :" + id);

    }

    @Override
    public void destroy(String id) {
        System.out.println("calling azure batch destroying :" + id);
    }

    @Override
    public RuntimeEnvironmentInfo environmentInfo() {
        return new RuntimeEnvironmentInfo.Builder()
                .spiClass(TaskLauncher.class)
                .implementationName(this.getClass().getSimpleName())
                .implementationVersion("1.0")
                .platformType("Azure Batch")
                .platformApiVersion("1.0")
                .platformClientVersion("1.0")
                .platformHostVersion("1.0")
                .build();
    }
}
