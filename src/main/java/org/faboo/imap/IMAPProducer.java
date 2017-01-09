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
import javax.mail.*;
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

    @Value("${imap.folderName}")
    private String folderName;

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

        log.info("starting IMAP-producer");

        IMAPFolder folder = null;
        try {

            //noinspection InfiniteLoopStatement
            while (true) {

                folder = fetchOfflineMessages(folder);
                // start idle command
                goIdle(folder);

            }

        } catch (InterruptedException e) {
            log.info("closing producer down");
        } finally {
            try {
                if (folder != null) {
                    folder.getStore().close();
                }
            } catch (MessagingException e) {
                log.error("error closing store", e);
            }
        }
    }

    private void goIdle(IMAPFolder folder) {

        try {
            folder.idle(true);
        } catch (MessagingException | IllegalStateException e) {
            log.warn("received error while attempting to IDLE", e);
        }

    }

    private IMAPFolder fetchOfflineMessages(IMAPFolder folder) throws InterruptedException {

        boolean keepOnFetching = true;

        while (keepOnFetching) {

            try {
                folder = openFolder(folder);
                int messageCount = folder.getUnreadMessageCount();
                int fetchSize = Math.max(1, Math.min(queue.remainingCapacity(), messageCount));
                keepOnFetching = messageCount > fetchSize;

                log.debug("fetching {} messages of {} for offline use", fetchSize, messageCount);

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
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                log.error("error fetching messages", e);
            }
        }
        return folder;
    }

    /**
     * Re-opens the folder, re-connecting if necessary.
     * Returns only after successfully opening the folder.
     * @param folder existing folder
     * @return connected folder
     * @throws InterruptedException when interrupted while waiting to connect.
     */
    private IMAPFolder openFolder(IMAPFolder folder) throws InterruptedException {

        while (folder == null || !folder.isOpen()) {
            IMAPStore store = null;

            try {

                if (folder != null) {
                    store = (IMAPStore) folder.getStore();
                }

                if (store == null ||  ! store.isConnected()) {
                    Properties props = new Properties();
                    props.setProperty("mail.store.protocol", "imap");

                    Session session = Session.getInstance(props);

                    URLName urlName = new URLName("imap", "localhost", -1, "", "bert", password);
                    store = new IMAPStore(session, urlName);

                    log.info("connecting to IMAP");
                    store.connect();
                }

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
