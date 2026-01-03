package com.uit.sharedkernel.amqp;

import com.uit.sharedkernel.constants.RabbitMQConstants;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public MessageConverter jacksonConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory simpleRabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jacksonConverter());
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }

    // ==================== EXCHANGES ====================
    
    @Bean
    public TopicExchange transactionExchange() {
        return new TopicExchange(RabbitMQConstants.TRANSACTION_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange internalExchange() {
        return new TopicExchange(RabbitMQConstants.INTERNAL_EXCHANGE, true, false);
    }

    // ==================== QUEUES ====================
    
    @Bean
    public Queue userCreatedQueue() {
        return new Queue(RabbitMQConstants.USER_CREATED_QUEUE, true);
    }

    // ==================== BINDINGS ====================
    
    @Bean
    public Binding userCreatedBinding() {
        return BindingBuilder.bind(userCreatedQueue())
                .to(internalExchange())
                .with(RabbitMQConstants.USER_CREATED_ROUTING_KEY);
    }
}
