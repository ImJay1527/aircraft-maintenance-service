package pt.isep.sidis.aircraft.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "aisafe.events";
    public static final String AIRCRAFT_QUEUE_NAME = "aircraft.events.queue";

    public static final String ROUTING_KEY_AIRCRAFT_STATUS = "aircraft.status.changed";
    public static final String ROUTING_KEY_AIRCRAFT_CREATED = "aircraft.created";
    public static final String ROUTING_KEY_MAINTENANCE_SCHEDULED = "aircraft.maintenance.scheduled";
    public static final String ROUTING_KEY_MAINTENANCE_COMPLETED = "aircraft.maintenance.completed";

    @Bean
    public TopicExchange aisafeExchange(@Value("${aisafe.rabbitmq.exchange:" + EXCHANGE_NAME + "}") String exchangeName) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Queue aircraftQueue(@Value("${aisafe.rabbitmq.aircraft-queue:" + AIRCRAFT_QUEUE_NAME + "}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean
    public Binding aircraftBinding(Queue aircraftQueue, TopicExchange aisafeExchange) {
        return BindingBuilder.bind(aircraftQueue).to(aisafeExchange).with("aircraft.#");
    }

    @Bean
    public Binding flightOpsBinding(Queue aircraftQueue, TopicExchange aisafeExchange) {
        return BindingBuilder.bind(aircraftQueue).to(aisafeExchange).with("flight.#");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(mapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}
