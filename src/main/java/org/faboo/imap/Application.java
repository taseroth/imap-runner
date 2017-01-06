package org.faboo.imap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import javax.annotation.PostConstruct;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

@SpringBootApplication
public class Application {

    @Autowired
    private IMAPProducer producer;

    @Autowired
    private MessageConsumer consumer;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean(name = "IMAPSourceQueue")
    public BlockingQueue<OfflineIMAPMessage> imapBlockQueue() {
        return new ArrayBlockingQueue<>(20);
    }

    @PostConstruct
    public void init() {
        new Thread(producer).start();
        new Thread(consumer).start();
    }

}