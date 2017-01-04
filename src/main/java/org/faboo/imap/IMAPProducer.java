package org.faboo.imap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.mail.Folder;
import javax.mail.Session;
import javax.mail.URLName;

import com.sun.mail.imap.IMAPStore;
import com.sun.mail.imap.IMAPFolder;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;

/**
 * @author Bert
 */
@Component
public class IMAPProducer implements Runnable {

    private final Logger logger = LoggerFactory.getLogger(IMAPProducer.class);

    private final BlockingQueue queue;

    @Value("${imap.password}")
    private String password;

    @Autowired
    public IMAPProducer(@Qualifier("IMAPSourceQueue") BlockingQueue queue) {
        this.queue = queue;
    }

    @PostConstruct
    public void init() {
        run();
    }

    @Override
    public void run() {

        try {
            Properties props = new Properties();
            props.setProperty("mail.store.protocol", "imap");

            Session session = Session.getInstance(props);

            URLName urlName = new URLName("imap", "localhost", -1, "", "bert", password);
            IMAPStore store = new IMAPStore(session, urlName);

            logger.info("connecting to IMAP");

            store.connect("localhost", "bert", password);
            IMAPFolder inbox = (IMAPFolder) store.getFolder("INBOX");

            inbox.open(Folder.READ_ONLY);

            logger.debug("inbox message count {}", inbox.getMessageCount());

        } catch (Exception e) {
            logger.error("error", e);
        } finally {

        }

    }
}
