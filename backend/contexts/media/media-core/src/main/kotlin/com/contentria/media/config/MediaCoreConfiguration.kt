package com.contentria.media.config

import com.contentria.media.application.service.ImageAddresses
import com.contentria.media.application.service.UploadPolicy
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner

/**
 * Wires the media context into whichever application module hosts it: entities, use cases and
 * outbound adapters. The host provides a [java.time.Clock] bean and the `contentria.media` settings.
 */
@Configuration(proxyBeanMethods = false)
@EntityScan("com.contentria.media")
@EnableJpaRepositories("com.contentria.media.application.required")
@ComponentScan("com.contentria.media.application", "com.contentria.media.adapter.required")
@EnableConfigurationProperties(MediaProperties::class)
class MediaCoreConfiguration {

    @Bean
    fun mediaImageAddresses(properties: MediaProperties) = ImageAddresses(properties.publicBaseUrl)

    @Bean
    fun mediaUploadPolicy(properties: MediaProperties) = UploadPolicy(properties.uploadUrlTtl)

    @Bean(destroyMethod = "close")
    fun mediaS3Client(properties: MediaProperties): S3Client = with(properties.storage) {
        S3Client.builder()
            .endpointOverride(endpoint)
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(pathStyle).build())
            .build()
    }

    @Bean(destroyMethod = "close")
    fun mediaS3Presigner(properties: MediaProperties): S3Presigner = with(properties.storage) {
        S3Presigner.builder()
            .endpointOverride(endpoint)
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(pathStyle).build())
            .build()
    }
}
