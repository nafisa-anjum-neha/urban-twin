package com.urbantwin.common;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Declares the four ingest topics plus twin/simulation streams (contract §5). */
@Configuration
public class KafkaTopicConfig {
    @Bean
    public NewTopic trafficReadings() { return topic("traffic.readings"); }

    @Bean
    public NewTopic drainageReadings() { return topic("drainage.readings"); }

    @Bean
    public NewTopic energyReadings() { return topic("energy.readings"); }

    @Bean
    public NewTopic twinUpdates() { return topic("twin.updates"); }

    @Bean
    public NewTopic simulationEvents() { return topic("simulation.events"); }

    private static NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }
}
