package br.com.jodiel.transactionsapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TransactionsApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionsApiApplication.class, args);
    }
}
