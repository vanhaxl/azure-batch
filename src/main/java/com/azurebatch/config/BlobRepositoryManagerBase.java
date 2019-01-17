package com.azurebatch.config;

import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class BlobRepositoryManagerBase {

    public static String STORAGE_ACCOUNT_PROTOCOL = "DefaultEndpointsProtocol=https;";
    public static String STORAGE_ACCOUNT_NAME = "AccountName=";
    public static String STORAGE_ACCOUNT_KEY = "AccountKey=";
    public static String SEMICOLON_DELIMITER = ";";

    public static String storageAccountName = "vanhanguyenstorage";
    public static String storageAccountPrimaryKey = "q2ZpV/KJ8llJHz3GNk5UnaT1wksijABDYD77Ain6ViLVOcOOyWxVZAxQY5cKPJyAlh86J4gn6UnHXmMzR0rHAA==";


    CloudBlobClient cloudBlobClient;

    @PostConstruct
    public void init() {

        if (cloudBlobClient == null) {
            StringBuilder sb = new StringBuilder();
            sb.append(STORAGE_ACCOUNT_PROTOCOL).append(STORAGE_ACCOUNT_NAME).append(storageAccountName)
                    .append(SEMICOLON_DELIMITER).append(STORAGE_ACCOUNT_KEY).append(storageAccountPrimaryKey)
                    .append(SEMICOLON_DELIMITER);

            String bsConnectionString = sb.toString();
            try {
                CloudStorageAccount cloudStorageAccount = CloudStorageAccount.parse(bsConnectionString);
                cloudBlobClient = cloudStorageAccount.createCloudBlobClient();
            } catch (Exception e) {
                System.out.println("connect to storage account fail");
            }
        }

    }

    public CloudBlobClient getStorageBlobClient() {
        return cloudBlobClient;
    }


}