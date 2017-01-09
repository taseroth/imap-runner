package org.faboo.imap;

import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPStore;
import com.sun.mail.imap.SortTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.search.FlagTerm;
import javax.mail.search.SearchTerm;
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

    private SearchTerm searchTerm;

    private SortTerm[] sortTerms;

    private Flags seenFlag;

    @Value("${imap.password}")
    private String password;

    @Value("${imap.source.folderName}")
    private String folderName;

    @Autowired
    public IMAPProducer(@Qualifier("IMAPSourceQueue") BlockingQueue<OfflineIMAPMessage> queue) {
        this.queue = queue;
    }

    @PostConstruct
    public void init() {
        fetchProfile = new FetchProfile();
        fetchProfile.add(FetchProfile.Item.ENVELOPE);

        seenFlag = new Flags(Flags.Flag.SEEN);
        searchTerm = new FlagTerm(seenFlag, false);
        sortTerms = new SortTerm[]{SortTerm.DATE};
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

    private void goIdle(IMAPFolder folder) throws InterruptedException {

        try {
            log.debug("going idle");
            folder.idle(true);
            Thread.sleep(50);
        } catch (MessagingException | IllegalStateException e) {
            log.warn("received error while attempting to IDLE", e);
        }

    }

    private IMAPFolder fetchOfflineMessages(IMAPFolder folder) throws InterruptedException {

        boolean keepOnFetching = true;

        while (keepOnFetching) {

            try {
                folder = openFolder(folder);

                log.info("fetching messages for offline use");

                Message messages[] = folder.getSortedMessages(sortTerms, searchTerm);
                folder.fetch(messages, fetchProfile);
                log.debug("fetched {} messages", messages.length);

                for (Message message : messages) {
                    queue.put(new OfflineIMAPMessage(
                            folder.getUID(message)
                            , ((InternetAddress)message.getFrom()[0]).getAddress()
                            , ((InternetAddress) message.getRecipients(Message.RecipientType.TO)[0]).getAddress()
                            , message.getSubject()
                            , message.getSentDate()
                    ));
                    folder.setFlags(new Message[]{message}, seenFlag,true);
                }
                keepOnFetching = folder.getUnreadMessageCount() > 0;
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                log.error("error fetching messages", e);
                Thread.sleep(1000);
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
                folder.open(Folder.READ_WRITE);

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
