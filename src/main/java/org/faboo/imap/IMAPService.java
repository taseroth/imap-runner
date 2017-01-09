package org.faboo.imap;

import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.mail.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * @author bert
 */
@Service
public class IMAPService {

    private final Logger log = LoggerFactory.getLogger(IMAPService.class);


    @Value("${imap.password}")
    private String password;

    @Value("${imap.source.folderName}")
    private String sourceFolderName;


    private IMAPStore store = null;
    private IMAPFolder sourceFolder = null;

    private Map<String,IMAPFolder> targetFolders = new HashMap<>();


    public void moveToTargetFolder(OfflineIMAPMessage offlineMessage, String targetFolderName)
            throws InterruptedException {

        IMAPFolder targetFolder = getOpenFolder(targetFolderName);

        try {
            Message message = sourceFolder.getMessageByUID(offlineMessage.getUid());

            if (message == null) {
                // strange behaviour: after an idle cycle, the first fetch return null ..
                log.warn("could not read message by uid, retrying");
                Thread.sleep(100);
                message = sourceFolder.getMessageByUID(offlineMessage.getUid());
            }

            sourceFolder.moveMessages(new Message[]{message}, targetFolder);

        } catch (MessagingException e) {
            log.error("error moving message {} to folder {}: {}", offlineMessage, targetFolderName, e.getMessage());
        }

    }

    @PostConstruct
    public void init() throws InterruptedException {
        openStoreAndSource();
    }

    private synchronized IMAPFolder getOpenFolder(String folderName) throws InterruptedException {

        IMAPFolder targetFolder = targetFolders.get(folderName);

        if (targetFolder != null && targetFolder.isOpen()) {
            return targetFolder;
        }

        while (true) {
            log.info("opening target folder: {}", folderName);
            try {
                if (store == null || !store.isConnected()) {
                    openStoreAndSource();
                }
                targetFolder = (IMAPFolder)store.getFolder(folderName);
                if (!targetFolder.exists()) {
                    log.info("creating folder {}", folderName);
                    targetFolder.create(Folder.HOLDS_MESSAGES);
                }
                targetFolder.open(Folder.READ_WRITE);
                targetFolders.put(folderName, targetFolder);
                return targetFolder;

            } catch (MessagingException e) {
                log.error("error opening target folder, retrying in 1s", e);
                Thread.sleep(1000);
            }
        }
    }

    private synchronized void openStoreAndSource() throws InterruptedException {

        log.info("(re-) connecting store and source folder");

        boolean keepTrying = true;

        while (keepTrying) {
            try {
                if (store != null && store.isConnected()) {
                    store.close();
                }

                Properties props = new Properties();
                props.setProperty("mail.store.protocol", "imap");

                Session session = Session.getInstance(props);

                URLName urlName = new URLName("imap", "localhost", -1, "", "bert", password);
                store = new IMAPStore(session, urlName);

                log.info("connecting to IMAP");
                store.connect();

                sourceFolder = (IMAPFolder) store.getFolder(sourceFolderName);
                sourceFolder.open(Folder.READ_WRITE);

                keepTrying = false;
            } catch (MessagingException e) {
                log.error("error (re-) connecting store, retry after 1s", e);
                Thread.sleep(1000);
            }

        }
    }
}
