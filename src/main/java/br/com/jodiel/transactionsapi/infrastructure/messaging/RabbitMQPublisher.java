package br.com.jodiel.transactionsapi.infrastructure.messaging;

import br.com.jodiel.transactionsapi.domain.enums.Queue;
import br.com.jodiel.transactionsapi.domain.interfaces.MessagePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RabbitMQPublisher implements MessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public RabbitMQPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * When called inside a transaction, sending is deferred to after commit, so a rolled back
     * deposit never produces a "your deposit went through" email. Outside a transaction it is sent
     * straight away. Doing this here keeps the deferral out of the use cases.
     */
    @Override
    public void publish(Queue queue, Map<String, Object> payload) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(queue, payload);
                }
            });
            return;
        }

        send(queue, payload);
    }

    /**
     * Notifications are best-effort: a broker outage must not roll back a transaction that has
     * already been committed to the database.
     */
    private void send(Queue queue, Map<String, Object> payload) {
        try {
            rabbitTemplate.convertAndSend(queue.getName(), payload);
            log.debug("Message published queue={}", queue.getName());
        } catch (Exception e) {
            log.error("Failed to publish message queue={}", queue.getName(), e);
        }
    }
}
