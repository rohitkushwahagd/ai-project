package dev.rohit.rag;

import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.KafkaAdmin.NewTopics;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.*;
import org.springframework.util.backoff.FixedBackOff;
import org.apache.kafka.common.TopicPartition;

@Configuration
public class KafkaConfig {
    @Bean NewTopics topics() {
        return new NewTopics(java.util.stream.Stream.of("ingest.docs","ingest.tickets","ingest.runbooks",
                "ingest.docs.DLT","ingest.tickets.DLT","ingest.runbooks.DLT")
            .map(n->TopicBuilder.name(n).partitions(1).replicas(1).build()).toArray(org.apache.kafka.clients.admin.NewTopic[]::new));
    }
    @Bean DefaultErrorHandler errorHandler(KafkaTemplate<String,String> kafka) {
        var recoverer=new DeadLetterPublishingRecoverer(kafka,(r,e)->new TopicPartition(r.topic()+".DLT",r.partition()));
        recoverer.setFailIfSendResultIsError(true);
        return new DefaultErrorHandler(recoverer,new FixedBackOff(2000,2));
    }
    @Bean ConcurrentKafkaListenerContainerFactory<Object,Object> deadLetterContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,ConsumerFactory<Object,Object> consumers) {
        var factory=new ConcurrentKafkaListenerContainerFactory<Object,Object>();
        configurer.configure(factory,consumers);
        // Updating FAILED is the terminal database action. A database outage must keep
        // the original DLT record pending, not send it to an unconsumed *.DLT.DLT topic.
        var handler=new DefaultErrorHandler((record,error)->{
            throw new IllegalStateException("Dead-letter update failed; retain record for retry",error);
        },new FixedBackOff(2000,FixedBackOff.UNLIMITED_ATTEMPTS));
        handler.setClassifications(java.util.Map.of(),true);
        factory.setCommonErrorHandler(handler);
        return factory;
    }
}
