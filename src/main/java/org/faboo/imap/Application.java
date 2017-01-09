package org.faboo.imap;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

@SpringBootApplication
public class Application implements ApplicationContextAware {

    @Autowired
    private IMAPProducer producer;

    private ApplicationContext applicationContext;

    private Collection<NeedsCleanUpBeforeEnd> consumers;
    private Thread producerThread;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean(name = "IMAPSourceQueue")
    public BlockingQueue<OfflineIMAPMessage> iMAPSourceQueue() {
        return new ArrayBlockingQueue<>(20);
    }

    @PostConstruct
    public void init() {

        consumers = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            MessageConsumer bean = applicationContext.getBean(MessageConsumer.class);
            Thread thread = new Thread(bean);
            thread.setName("PARSER-" +  i);
            thread.start();
            consumers.add(bean);
        }

        producerThread = new Thread(producer);
        producerThread.setName("PRODUCER");
        producerThread.start();
    }

    @PreDestroy
    public void destroy() {

        producerThread.interrupt();
        while (true) {
            if (iMAPSourceQueue().peek() == null) {
                break;
            }
        }
        consumers.forEach(NeedsCleanUpBeforeEnd::signalStop);

    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {

        this.applicationContext = applicationContext;
    }


}