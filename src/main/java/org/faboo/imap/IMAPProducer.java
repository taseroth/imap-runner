package org.faboo.imap;

import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.mail.FetchProfile;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.URLName;
import javax.mail.internet.InternetAddress;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;

/**
 * @author Bert
 */
@Component
public class IMAPProducer implements Runnable {

    private final Logger log = LoggerFactory.getLogger(IMAPProducer.class);

    private final BlockingQueue<OfflineIMAPMessage> queue;

    private FetchProfile fetchProfile;

    @Value("${imap.password}")
    private String password;

    @Autowired
    public IMAPProducer(@Qualifier("IMAPSourceQueue") BlockingQueue<OfflineIMAPMessage> queue) {
        this.queue = queue;
    }

    @PostConstruct
    public void init() {
        fetchProfile = new FetchProfile();
        fetchProfile.add(FetchProfile.Item.ENVELOPE);
    }

    @Override
    public void run() {

        IMAPFolder inbox = null;
        try {

            inbox = openFolder("INBOX");

            while (true) {

                boolean reconnect = fetchOfflineMessages(inbox);

                if (reconnect) {
                    inbox = openFolder("INBOX");
                } else {
                    // start idle command
                    try {
                        inbox.idle();
                    } catch (MessagingException e) {
                        inbox = openFolder("INBOX");
                    }
                }

            }

        } catch (InterruptedException e) {
            log.info("closing producer down");
        } finally {
            try {
                if (inbox != null) {
                    inbox.getStore().close();
                }
            } catch (MessagingException e) {
                log.error("error closing store", e);
            }
        }
    }

    private boolean fetchOfflineMessages(IMAPFolder folder) throws InterruptedException {

        try {
            while (folder.getMessageCount() > 0) {
                int fetchSize = Math.max(1, queue.remainingCapacity());
                log.debug("fetching {} messages for offline use", fetchSize);

                Message messages[] = folder.getMessages(1, fetchSize);
                folder.fetch(messages, fetchProfile);

                for (Message message : messages) {
                    queue.put(new OfflineIMAPMessage(
                            folder.getUID(message)
                            , ((InternetAddress)message.getFrom()[0]).getAddress()
                            , ((InternetAddress) message.getRecipients(Message.RecipientType.TO)[0]).getAddress()
                            , message.getSubject()
                            , message.getSentDate()
                    ));
                }
            }
            return true;
        } catch (MessagingException e) {
            log.error("error fetching messages", e);
            return false;
        }
    }

    private IMAPFolder openFolder(String folderName) throws InterruptedException {

        IMAPFolder folder = null;

        while (folder == null || !folder.isOpen()) {
            IMAPStore store = null;

            try {
                Properties props = new Properties();
                props.setProperty("mail.store.protocol", "imap");

                Session session = Session.getInstance(props);

                URLName urlName = new URLName("imap", "localhost", -1, "", "bert", password);
                store = new IMAPStore(session, urlName);

                log.info("connecting to IMAP");
                store.connect();
                folder = (IMAPFolder) store.getFolder(folderName);
                folder.open(Folder.READ_ONLY);

            } catch (MessagingException e) {
                log.error("error opening folder, waiting and 1s and retrying", e);
                try {
                    if (store.isConnected()) {
                        store.close();
                    }
                } catch (MessagingException inner) {
                    log.error("error closing store", e);
                }
                Thread.sleep(1000);
            }
        }

        return folder;
    }
}
