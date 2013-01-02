package org.xwiki.commons.internal;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.xpn.xwiki.api.Attachment;

/**
 * The variables to, cc, bcc can contain several email addresses, separated by commas.
 */
public class Mail
{
    private String from;

    private String to;

    private String replyTo;

    private String cc;

    private String bcc;

    private String subject;

    private List<String[]> contents;

    private List<Attachment> attachments;

    private Map<String, String> headers;

    public Mail()
    {
        this.headers = new TreeMap<String, String>();
        this.contents = new LinkedList<String[]>();
        this.attachments = new LinkedList<Attachment>();
    }

    public Mail(String from, String to, String cc, String bcc, String subject)
    {
        this();
        this.from = from;
        this.to = to;
        this.cc = cc;
        this.bcc = bcc;
        this.subject = subject;
    }

    public List<Attachment> getAttachments()
    {
        return this.attachments;
    }

    public void setAttachments(List<Attachment> attachments)
    {
        this.attachments = attachments;
    }

    public void addFile(Attachment attachment)
    {
        this.attachments.add(attachment);
    }

    public String getFrom()
    {
        return this.from;
    }

    public void setFrom(String from)
    {
        this.from = from;
    }

    public String getTo()
    {
        return this.to;
    }

    public void setTo(String to)
    {
        this.to = to;
    }

    public String getReplyTo()
    {
        return this.replyTo;
    }

    public void setReplyTo(String replyTo)
    {
        this.replyTo = replyTo;
    }

    public String getCc()
    {
        return this.cc;
    }

    public void setCc(String cc)
    {
        this.cc = cc;
    }

    public String getBcc()
    {
        return this.bcc;
    }

    public void setBcc(String bcc)
    {
        this.bcc = bcc;
    }

    public String getSubject()
    {
        return this.subject;
    }

    public void setSubject(String subject)
    {
        this.subject = subject;
    }

    @Override
    public String toString()
    {
        StringBuffer buffer = new StringBuffer();

        if (getFrom() != null) {
            buffer.append("From [" + getFrom() + "]");
        }

        if (getTo() != null) {
            buffer.append(", To [" + getTo() + "]");
        }

        if (getCc() != null) {
            buffer.append(", Cc [" + getCc() + "]");
        }

        if (getBcc() != null) {
            buffer.append(", Bcc [" + getBcc() + "]");
        }

        if (getSubject() != null) {
            buffer.append(", Subject [" + getSubject() + "]");
        }

        if (getContents() != null) {
            buffer.append(", Contents [" + getContentsAsString() + "]");
        }

        if (!getHeaders().isEmpty()) {
            buffer.append(", Headers [" + toStringHeaders() + "]");
        }

        return buffer.toString();
    }

    private String toStringHeaders()
    {
        StringBuffer buffer = new StringBuffer();
        for (Map.Entry<String, String> header : getHeaders().entrySet()) {
            buffer.append('[');
            buffer.append(header.getKey());
            buffer.append(']');
            buffer.append(' ');
            buffer.append('=');
            buffer.append(' ');
            buffer.append('[');
            buffer.append(header.getValue());
            buffer.append(']');
        }
        return buffer.toString();
    }

    public void setHeader(String header, String value)
    {
        this.headers.put(header, value);
    }

    public String getHeader(String header)
    {
        return this.headers.get(header);
    }

    public void setHeaders(Map<String, String> headers)
    {
        this.headers = headers;
    }

    public List<String[]> getContents()
    {
        return this.contents;
    }

    public String getContentsAsString()
    {
        String result = "";
        for (String[] part : this.contents) {
            result += part[0] + ":" + part[1] + " \n ";
        }
        return result;
    }

    /**
     * Add a new part in the email multipart
     * 
     * @param contentType MimeType of the part to add
     * @param content Content of the part to add
     */
    public void addContent(String contentType, String content)
    {
        String[] newContent = {contentType, content};
        this.contents.add(newContent);
    }

    public void addHtmlContent(String html)
    {
        String[] newContent = {"text/html", html};
        this.contents.add(newContent);
    }

    public Map<String, String> getHeaders()
    {
        return this.headers;
    }

}
