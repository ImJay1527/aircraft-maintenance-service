package pt.isep.sidis.aircraft.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
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
    public static final String DLX_NAME = "aisafe.dlx";
    public static final String DLQ_NAME = "aircraft.events.dlq";

    public static final String ROUTING_KEY_AIRCRAFT_STATUS = "aircraft.status.changed";
    public static final String ROUTING_KEY_AIRCRAFT_CREATED = "aircraft.created";
    public static final String ROUTING_KEY_MAINTENANCE_SCHEDULED = "aircraft.maintenance.scheduled";
    public static final String ROUTING_KEY_MAINTENANCE_COMPLETED = "aircraft.maintenance.completed";

    @Bean
    public TopicExchange aisafeExchange(@Value("${aisafe.rabbitmq.exchange:" + EXCHANGE_NAME + "}") String exchangeName) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(DLX_NAME, true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ_NAME).build();
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, TopicExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with("#");
    }

    /**
     * Dedicated queue per replica instance (Publish/Subscribe pattern).
     * Bound exclusively to flight.# events from flight-operations-service,
     * avoiding self-loopback of aircraft.* events and preventing competing-consumer message loss.
     */
    @Bean
    public Queue aircraftQueue(@Value("${aisafe.rabbitmq.aircraft-queue:aircraft.flight-events.${aircraft.instance-name:instance1}}") String queueName) {
        return QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange", DLX_NAME)
                .withArgument("x-dead-letter-routing-key", DLQ_NAME)
                .build();
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
