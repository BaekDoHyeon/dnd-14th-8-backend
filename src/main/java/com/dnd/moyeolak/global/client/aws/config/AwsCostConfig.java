package com.dnd.moyeolak.global.client.aws.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;
import software.amazon.awssdk.services.costexplorer.CostExplorerClientBuilder;

import java.time.Duration;

/**
 * Cost Explorer 클라이언트. 알림이 꺼져 있으면 SDK 클라이언트를 아예 만들지 않는다.
 */
@Configuration
@ConditionalOnProperty(name = "aws.cost.enabled", havingValue = "true")
public class AwsCostConfig {

    /**
     * Cost Explorer는 리전 단위 서비스가 아니라 us-east-1 글로벌 엔드포인트만 받는다.
     * 키가 비어 있으면 EC2 인스턴스 프로파일 등 SDK 기본 자격증명 체인으로 넘어간다.
     */
    @Bean
    public CostExplorerClient costExplorerClient(
            @Value("${aws.cost.access-key:}") String accessKey,
            @Value("${aws.cost.secret-key:}") String secretKey
    ) {
        CostExplorerClientBuilder builder = CostExplorerClient.builder()
                .region(Region.US_EAST_1)
                .httpClient(UrlConnectionHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(5))
                        .socketTimeout(Duration.ofSeconds(10))
                        .build());

        // 키를 지정하지 않으면 SDK 기본 자격증명 체인(환경변수, 인스턴스 프로파일 등)을 그대로 쓴다
        if (!accessKey.isBlank() && !secretKey.isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)));
        }
        return builder.build();
    }
}
