package org.faboo.imap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;

/**
 * @author Bert
 */
@Component
@Scope(scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class MessageConsumer implements Runnable {

    private final Logger log = LoggerFactory.getLogger(MessageConsumer.class);

    @Autowired
    private BlockingQueue<OfflineIMAPMessage> queue;

    @Override
    public void run() {

        try {

            while (true) {
                OfflineIMAPMessage message = queue.take();
                log.debug("retrieved from queue: {}", message);
                Thread.sleep(100);
            }
        } catch (InterruptedException e) {
            log.info("closing consumer down");
        }

    }
}
