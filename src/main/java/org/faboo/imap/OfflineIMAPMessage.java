package org.faboo.imap;

import java.util.Date;

/**
 * @author Bert
 */
public class OfflineIMAPMessage {

    private final long uid;
    private final String sender;
    private final String receiver;
    private final String subject;
    private final Date sendDate;


    public OfflineIMAPMessage(long uid, String sender, String receiver, String subject, Date sendDate) {
        this.uid = uid;
        this.sender = sender;
        this.receiver = receiver;
        this.subject = subject;
        this.sendDate = sendDate;
    }

    public long getUid() {
        return uid;
    }

    public String getSender() {
        return sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public String getSubject() {
        return subject;
    }

    public Date getSendDate() {
        return sendDate;
    }

    @Override
    public String toString() {
        return "OfflineIMAPMessage{" +
                "uid='" + uid + '\'' +
                ", sender='" + sender + '\'' +
                ", receiver='" + receiver + '\'' +
                ", subject='" + subject + '\'' +
                ", sendDate=" + sendDate +
                '}';
    }
}
