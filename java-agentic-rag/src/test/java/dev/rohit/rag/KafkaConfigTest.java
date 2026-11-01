package dev.rohit.rag;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.kafka.support.TopicPartitionOffset;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class KafkaConfigTest {
    @Test void deadLetterDatabaseFailureKeepsRetryingInsteadOfRecoveringToAnotherTopic() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration.class))
            .withUserConfiguration(KafkaConfig.class)
            .withPropertyValues("spring.kafka.admin.auto-create=false","spring.kafka.listener.auto-startup=false",
                "spring.kafka.listener.ack-mode=record","spring.kafka.bootstrap-servers=127.0.0.1:1")
            .run(context->{
                assertThat(context).hasNotFailed();
                var factory=context.getBean("deadLetterContainerFactory",ConcurrentKafkaListenerContainerFactory.class);
                var container=factory.createContainer("ingest.docs.DLT");
                var handler=(DefaultErrorHandler)container.getCommonErrorHandler();
                assertThat(handler).isNotSameAs(context.getBean("errorHandler"));
                var retries=mock(RetryListener.class);
                handler.setRetryListeners(retries);
                var record=new ConsumerRecord<>("ingest.docs.DLT",0,7L,"source","source");
                var consumer=mock(Consumer.class);
                var failure=new DataAccessResourceFailureException("database unavailable");
                // More than the normal listener's two retries. A stopped, unstarted
                // container lets the backoff return promptly without a real broker.
                for(int attempt=0;attempt<5;attempt++) {
                    assertThat(handler.handleOne(failure,record,consumer,container)).isFalse();
                }
                assertThat(handler.deliveryAttempt(new TopicPartitionOffset("ingest.docs.DLT",0,7L))).isEqualTo(6);
                verify(retries,never()).recovered(any(ConsumerRecord.class),any());
                verify(retries,never()).recoveryFailed(any(ConsumerRecord.class),any(),any());
                verifyNoInteractions(consumer);
                handler.clearThreadState();
            });
    }

    @Test void deadLetterListenerUsesTheDedicatedFactory() throws Exception {
        var listener=IngestionPipeline.class.getMethod("deadLetter",String.class).getAnnotation(KafkaListener.class);
        assertThat(listener.containerFactory()).isEqualTo("deadLetterContainerFactory");
    }
}
