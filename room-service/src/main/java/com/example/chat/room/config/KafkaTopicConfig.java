package com.example.chat.room.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic roomDeletedTopic() {
        return TopicBuilder.name("room-deleted-topic")
                .partitions(3)
                .replicas(1)
                .build();
    }
}