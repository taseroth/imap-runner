package org.faboo.imap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Bert
 */
@Component("Consumer")
@Scope(scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class MessageConsumer implements Runnable, NeedsCleanUpBeforeEnd {

    private final Logger log = LoggerFactory.getLogger(MessageConsumer.class);

    private AtomicBoolean keepRunning = new AtomicBoolean(true);

    @Value("${imap.target.folderName}")
    private String targetFolderName;

    private final BlockingQueue<OfflineIMAPMessage> queue;

    private final IMAPService imapService;

    @Autowired
    public MessageConsumer(BlockingQueue<OfflineIMAPMessage> queue, IMAPService imapService) {
        this.queue = queue;
        this.imapService = imapService;
    }

    @PostConstruct
    public void init() {
        log.info("starting consumer");
    }

    @Override
    public void run() {

        log.info("starting message-consumer");
        try {
            while (keepRunning.get()) {
                OfflineIMAPMessage message = queue.take();
                log.debug("retrieved from queue: {}", message);
                Thread.sleep(1000);
                imapService.moveToTargetFolder(message, targetFolderName);
            }
        } catch (InterruptedException e) {
            log.info("closing consumer down");
        }
    }

    @Override
    public void signalStop() {
        log.info("received shutdown command");
        keepRunning.set(false);
    }
}
