package com.azurebatch.tasklauncher;

import com.azurebatch.config.BlobRepositoryManagerBase;
import com.microsoft.azure.batch.BatchClient;
import com.microsoft.azure.batch.protocol.models.*;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.BlobContainerPermissions;
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
//        Random ran = new Random();
//        int x = ran.nextInt(1000000) + 0;
//        String taskId = "test_task_azure_" + x;

        if (taskId.length() > 64) {
            taskId = taskId.substring(taskId.length() - 64);
        }

        OutputFileUploadCondition outputFileUploadCondition = OutputFileUploadCondition.TASK_COMPLETION;
        OutputFileUploadOptions outputFileUploadOptions = new OutputFileUploadOptions().withUploadCondition(outputFileUploadCondition);

        CloudBlobContainer cloudBlobContainer = null;
        try {
            cloudBlobContainer = blobRepositoryManagerBase.getStorageBlobClient().getContainerReference("logs");
        } catch (URISyntaxException e) {
            e.printStackTrace();
        } catch (StorageException e) {
            e.printStackTrace();
        }

//        string containerSasToken = container.GetSharedAccessSignature(new SharedAccessBlobPolicy()
//        {
//            SharedAccessExpiryTime = DateTimeOffset.UtcNow.AddDays(1),
//            Permissions = SharedAccessBlobPermissions.Write
//        });

        BlobContainerPermissions permissions = new BlobContainerPermissions();

        // define a Read-only base policy for downloads
        SharedAccessBlobPolicy readPolicy = new SharedAccessBlobPolicy();
        readPolicy.setPermissions(EnumSet.of(SharedAccessBlobPermissions.READ));
        permissions.getSharedAccessPolicies().put("DownloadPolicy", readPolicy);

        // define a base policy that allows writing for uploads
        SharedAccessBlobPolicy writePolicy = new SharedAccessBlobPolicy();
        writePolicy.setPermissions(EnumSet.of(SharedAccessBlobPermissions.READ, SharedAccessBlobPermissions.WRITE, SharedAccessBlobPermissions.CREATE));
        permissions.getSharedAccessPolicies().put("UploadPolicy", writePolicy);
        String containerSasToken = "";
        try {
            containerSasToken = cloudBlobContainer.generateSharedAccessSignature(writePolicy, "UploadPolicy");
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (StorageException e) {
            e.printStackTrace();
        }

        String containerSasUrl = "";
        if (cloudBlobContainer != null) {
            try {
                cloudBlobContainer.createIfNotExists();
            } catch (StorageException e) {
                e.printStackTrace();
            }
            containerSasUrl = cloudBlobContainer.getStorageUri().getPrimaryUri().toString() ;//+ containerSasToken;

        }
        System.out.println("van ha: " + containerSasUrl);

        OutputFileBlobContainerDestination outputFileBlobContainerDestination = new OutputFileBlobContainerDestination()
                .withContainerUrl(containerSasUrl)
                .withPath("log1.txt");
        OutputFileBlobContainerDestination outputFileBlobContainerDestination2 = new OutputFileBlobContainerDestination()
                .withContainerUrl(containerSasUrl)
                .withPath("log2.txt");
        OutputFileDestination outputFileDestination = new OutputFileDestination().withContainer(outputFileBlobContainerDestination);
        OutputFileDestination outputFileDestination2 = new OutputFileDestination().withContainer(outputFileBlobContainerDestination2);
        OutputFile file1 = new OutputFile().withFilePattern("stdout.txt").withDestination(outputFileDestination).withUploadOptions(outputFileUploadOptions);
        OutputFile file2 = new OutputFile().withFilePattern("stderr.txt").withDestination(outputFileDestination2).withUploadOptions(outputFileUploadOptions);

        List<OutputFile> outputFiles = new ArrayList<>();
        outputFiles.add(file1);
        outputFiles.add(file2);

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
