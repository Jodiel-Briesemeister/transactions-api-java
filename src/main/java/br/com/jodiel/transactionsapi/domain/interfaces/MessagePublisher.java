package br.com.jodiel.transactionsapi.domain.interfaces;

import br.com.jodiel.transactionsapi.domain.enums.Queue;

import java.util.Map;

public interface MessagePublisher {
    void publish(Queue queue, Map<String, Object> payload);
}
