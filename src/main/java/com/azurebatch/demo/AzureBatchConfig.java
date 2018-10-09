package com.azurebatch.demo;

import com.microsoft.azure.batch.BatchClient;
import com.microsoft.azure.batch.auth.BatchSharedKeyCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AzureBatchConfig {

    @Value("${azure.batch.url}")
    private String batchUrl;

    @Value("${azure.batch.account}")
    private String batchAccount;

    @Value("${azure.batch.key}")
    private String batchKey;

    @Bean
    public BatchClient batchClient(){
        // Create job asyncDocumentClient
        BatchSharedKeyCredentials cred = new BatchSharedKeyCredentials(batchUrl, batchAccount, batchKey);
        return BatchClient.open(cred);
    }
}
