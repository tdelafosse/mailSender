/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.commons.internal;

import org.apache.commons.lang3.StringUtils;
import org.apache.velocity.VelocityContext;
import org.slf4j.Logger;
import org.xwiki.bridge.DocumentAccessBridge;
import org.xwiki.commons.MailSender;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.context.Execution;
import org.xwiki.context.ExecutionContext;
import org.xwiki.model.reference.DocumentReference;

import org.xwiki.rendering.parser.StreamParser;
import org.xwiki.rendering.renderer.PrintRendererFactory;
import org.xwiki.rendering.renderer.printer.DefaultWikiPrinter;
import org.xwiki.rendering.renderer.printer.WikiPrinter;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.velocity.VelocityManager;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.api.Attachment;
import com.xpn.xwiki.api.Document;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.render.XWikiVelocityRenderer;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.SendFailedException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Implementation of a <tt>Mail Sender</tt> component.
 */
@Component
@Singleton
public class DefaultMailSender implements MailSender
{
    static String EMAIL_ENCODING = "UTF-8";

    /** The name of the Object Type holding mail templates. */
    public static final String EMAIL_XWIKI_CLASS_NAME = "XWiki.Mail";

    @Inject
    private DocumentAccessBridge documentAccessBridge;

    @Inject
    private VelocityManager velocityManager;

    @Inject
    private Logger logger;

    @Inject
    private Execution execution;

    @Inject
    @Named("html/4.01")
    private StreamParser htmlStreamParser;

    @Inject
    private ComponentManager componentManager;

    @Override
    public Mail newMail(String from, String to, String cc, String bcc, String subject)
    {
        return new Mail(from, to, cc, bcc, subject);
    }

    @Override
    public int sendMailFromTemplate(String templateDocFullName, String from, String to, String cc, String bcc,
        String language, Map<String, Object> parameters)
    {
        VelocityContext vContext = velocityManager.getVelocityContext();
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            vContext.put(entry.getKey(), entry.getValue());
        }
        return sendMailFromTemplate(templateDocFullName, from, to, cc, bcc, language, vContext);
    }

    @Override
    public int sendMailFromTemplate(String templateDocFullName, String from, String to, String cc, String bcc,
        String language, VelocityContext vContext)
    {
        if (!documentAccessBridge.hasProgrammingRights()) // Forbids the use of a template created by a user having no
                                                          // programming rights
        {
            logger
                .error("No mail has been sent : The author of the document needs programming rights to be able to use the sendMailFromTemplate method");
            return 0;
        }
        try {
            ExecutionContext context = this.execution.getContext();
            XWikiContext xwikiContext = (XWikiContext) context.getProperty("xwikicontext");
            if (vContext == null)
                vContext = velocityManager.getVelocityContext();
            vContext.put("from.name", from);
            vContext.put("from.address", from);
            vContext.put("to.name", to);
            vContext.put("to.address", to);
            vContext.put("to.cc", cc);
            vContext.put("to.bcc", bcc);
            vContext.put("bounce", from);

            String wiki = documentAccessBridge.getCurrentDocumentReference().getWikiReference().getName();
            String templateSpace = "";
            String templatePage = "";
            Scanner scan = new Scanner(templateDocFullName);
            scan.useDelimiter("\\.");
            if (scan.hasNext())
                templateSpace = scan.next();
            if (scan.hasNext())
                templatePage = scan.next();
            else {
                logger.error("Template reference is invalid");
                return 0;
            }
            DocumentReference template = new DocumentReference(wiki, templateSpace, templatePage);
            boolean hasRight = checkAccess(template, xwikiContext);
            if (!hasRight) // If the current user is not allowed to view the page of the template, he can't use it to
                           // send mails
            {
                logger.error("You haven't the right to use this mail template !");
                return 0;
            }
            DocumentReference mailClass = new DocumentReference(wiki, "XWiki", "Mail");
            int n = -1;

            n = documentAccessBridge.getObjectNumber(template, mailClass, "language", language);

            if (n == -1) {
                n = documentAccessBridge.getObjectNumber(template, mailClass, "language", "en");
                logger.warn("No mail object found with language = " + language);
            }
            if (n == -1) {
                logger.error("No mail object found in the document " + templateDocFullName);
                return 0;
            }

            String subject = documentAccessBridge.getProperty(template, mailClass, n, "subject").toString();
            subject = XWikiVelocityRenderer.evaluate(subject, templateDocFullName, vContext, xwikiContext);
            String text = documentAccessBridge.getProperty(template, mailClass, n, "text").toString();
            text = XWikiVelocityRenderer.evaluate(text, templateDocFullName, vContext, xwikiContext);
            String html = documentAccessBridge.getProperty(template, mailClass, n, "html").toString();
            html = XWikiVelocityRenderer.evaluate(html, templateDocFullName, vContext, xwikiContext);

            Mail mail = new Mail();
            mail.setFrom(from);
            mail.setTo(to);
            mail.setCc(cc);
            mail.setBcc(bcc);
            mail.setSubject(subject);
            mail.addContent("text/plain", text);
            if (!StringUtils.isEmpty(html))
                mail.addContent("text/html", html);
            return this.send(mail);
        } catch (Exception e) {
            return 0;
        }

    }

    private boolean checkAccess(DocumentReference document, XWikiContext context)
    {
        XWikiDocument xdoc = new XWikiDocument(document);
        Document doc = new Document(xdoc, context);
        return doc.checkAccess("view");
    }

    @Override
    public int send(Mail mail)
    {
        Session session = null;
        Transport transport = null;
        if ((mail.getTo() == null || StringUtils.isEmpty(mail.getTo()))
            && (mail.getCc() == null || StringUtils.isEmpty(mail.getCc()))
            && (mail.getBcc() == null || StringUtils.isEmpty(mail.getBcc()))) {
            logger.error("This mail has no recipient");
            return 0;
        }
        if (mail.getContents().size() == 0) {
            logger.error("This mail is empty. You should add a content");
            return 0;
        }
        try {
            if ((transport == null) || (session == null)) {
                logger.info("Sending mail : Initializing properties");
                Properties props = initProperties();
                session = Session.getInstance(props, null);
                transport = session.getTransport("smtp");
                if (session.getProperty("mail.smtp.auth") == "true")
                    transport.connect(session.getProperty("mail.smtp.server.username"),
                        session.getProperty("mail.smtp.server.password"));
                else
                    transport.connect();
                try {
                    Multipart wrapper = generateMimeMultipart(mail);
                    InternetAddress[] adressesTo = this.toInternetAddresses(mail.getTo());
                    MimeMessage message = new MimeMessage(session);
                    message.setSentDate(new Date());
                    message.setSubject(mail.getSubject());
                    message.setFrom(new InternetAddress(mail.getFrom()));
                    message.setRecipients(javax.mail.Message.RecipientType.TO, adressesTo);
                    if (mail.getReplyTo() != null && !StringUtils.isEmpty(mail.getReplyTo())) {
                        logger.info("Adding ReplyTo field");
                        InternetAddress[] adressesReplyTo = this.toInternetAddresses(mail.getReplyTo());
                        if (adressesReplyTo.length != 0)
                            message.setReplyTo(adressesReplyTo);
                    }
                    if (mail.getCc() != null && !StringUtils.isEmpty(mail.getCc())) {
                        logger.info("Adding Cc recipients");
                        InternetAddress[] adressesCc = this.toInternetAddresses(mail.getCc());
                        if (adressesCc.length != 0)
                            message.setRecipients(javax.mail.Message.RecipientType.CC, adressesCc);
                    }
                    if (mail.getBcc() != null && !StringUtils.isEmpty(mail.getBcc())) {
                        InternetAddress[] adressesBcc = this.toInternetAddresses(mail.getBcc());
                        if (adressesBcc.length != 0)
                            message.setRecipients(javax.mail.Message.RecipientType.BCC, adressesBcc);
                    }
                    message.setContent(wrapper);
                    message.setSentDate(new Date());
                    transport.sendMessage(message, message.getAllRecipients());
                    transport.close();
                } catch (SendFailedException sfex) {
                    logger.error("Error encountered while trying to send the mail");
                    logger.error("SendFailedException has occured.", sfex);
                    try {
                        transport.close();
                    } catch (MessagingException Mex) {
                        logger.error("MessagingException has occured.", Mex);
                    }
                    return 0;
                } catch (MessagingException mex) {
                    logger.error("Error encountered while trying to send the mail");
                    logger.error("MessagingException has occured.", mex);

                    try {
                        transport.close();
                    } catch (MessagingException ex) {
                        logger.error("MessagingException has occured.", ex);
                    }
                    return 0;
                }
            }
        } catch (Exception e) {
            System.out.println(e.toString());
            logger.error("Error encountered while trying to setup mail properties");
            try {
                if (transport != null) {
                    transport.close();
                }
            } catch (MessagingException ex) {
                logger.error("MessagingException has occured.", ex);
            }
            return 0;
        }

        return 1;
    }

    private Multipart generateMimeMultipart(Mail mail) throws MessagingException
    {
        Multipart contentsMultipart = new MimeMultipart("alternative");
        List<String> foundEmbeddedImages = new ArrayList<String>();
        boolean hasAttachment = false;

        if (mail.getContents().size() == 1) // To add an alternative plain part
        {
            String[] content = mail.getContents().get(0);
            if (content[0].equals("text/plain")) {
                BodyPart alternativePart = new MimeBodyPart();
                alternativePart.setContent(content[1], content[0] + "; charset=" + EMAIL_ENCODING);
                alternativePart.setHeader("Content-Disposition", "inline");
                alternativePart.setHeader("Content-Transfer-Encoding", "quoted-printable");
                contentsMultipart.addBodyPart(alternativePart);
            }
            if (content[0].equals("text/html")) {
                BodyPart alternativePart = new MimeBodyPart();
                String parsedText = createPlain(content[1]);
                alternativePart.setContent(parsedText, "text/plain; charset=" + EMAIL_ENCODING);
                alternativePart.setHeader("Content-Disposition", "inline");
                alternativePart.setHeader("Content-Transfer-Encoding", "quoted-printable");
                contentsMultipart.addBodyPart(alternativePart);
            }
        }

        for (String[] content : mail.getContents()) {
            BodyPart contentPart = new MimeBodyPart();
            contentPart.setContent(content[1], content[0] + "; charset=" + EMAIL_ENCODING);
            contentPart.setHeader("Content-Disposition", "inline");
            contentPart.setHeader("Content-Transfer-Encoding", "quoted-printable");
            if (content[0].equals("text/html")) {
                boolean hasEmbeddedImages = false;
                List<MimeBodyPart> embeddedImages = new ArrayList<MimeBodyPart>();
                Pattern cidPattern =
                    Pattern.compile("src=('|\")cid:([^'\"]*)('|\")", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
                Matcher matcher = cidPattern.matcher(content[1]);
                String filename;
                while (matcher.find()) {
                    filename = matcher.group(2);
                    for (Attachment attachment : mail.getAttachments()) {
                        if (filename.equals(attachment.getFilename())) {
                            hasEmbeddedImages = true;
                            MimeBodyPart part = createAttachmentPart(attachment);
                            embeddedImages.add(part);
                            foundEmbeddedImages.add(filename);
                        }
                    }
                }
                if (hasEmbeddedImages) {
                    Multipart htmlMultipart = new MimeMultipart("related");
                    htmlMultipart.addBodyPart(contentPart);
                    for (MimeBodyPart imagePart : embeddedImages) {
                        htmlMultipart.addBodyPart(imagePart);
                    }
                    BodyPart HtmlWrapper = new MimeBodyPart();
                    HtmlWrapper.setContent(htmlMultipart);
                    contentsMultipart.addBodyPart(HtmlWrapper);
                } else
                    contentsMultipart.addBodyPart(contentPart);
            } else {
                contentsMultipart.addBodyPart(contentPart);
            }
        }

        Multipart attachmentsMultipart = new MimeMultipart();
        for (Attachment attachment : mail.getAttachments()) {
            if (!foundEmbeddedImages.contains(attachment.getFilename())) {
                hasAttachment = true;
                MimeBodyPart part = this.createAttachmentPart(attachment);
                attachmentsMultipart.addBodyPart(part);
            }
        }
        if (hasAttachment) {
            Multipart wrapper = new MimeMultipart("mixed");
            BodyPart body = new MimeBodyPart();
            body.setContent(contentsMultipart);
            wrapper.addBodyPart(body);
            BodyPart attachments = new MimeBodyPart();
            attachments.setContent(attachmentsMultipart);
            wrapper.addBodyPart(attachments);
            return wrapper;
        }
        return contentsMultipart;
    }

    public String createPlain(String html)
    {
        String converted = null;
        try {

            WikiPrinter printer = new DefaultWikiPrinter();
            PrintRendererFactory printRendererFactory =
                componentManager.getInstance(PrintRendererFactory.class, Syntax.PLAIN_1_0.toIdString());
            htmlStreamParser.parse(new StringReader(html), printRendererFactory.createRenderer(printer));
            converted = printer.toString();
        } catch (Throwable t) {
            logger.warn("Conversion from HTML to plain text threw exception", t);
            converted = null;
        }
        return converted;
    }

    public Properties initProperties()
    {
        Properties properties = new Properties();

        // Note: The full list of available properties that we can set is defined here:
        // http://java.sun.com/products/javamail/javadocs/com/sun/mail/smtp/package-summary.html

        String host = documentAccessBridge.getProperty("XWiki.XWikiPreferences", "smtp_server").toString();
        String port = documentAccessBridge.getProperty("XWiki.XWikiPreferences", "smtp_port").toString();
        String server_userName =
            documentAccessBridge.getProperty("XWiki.XWikiPreferences", "smtp_server_username").toString();
        String server_password =
            documentAccessBridge.getProperty("XWiki.XWikiPreferences", "smtp_server_password").toString();
        String extraPropertiesString =
            documentAccessBridge.getProperty("XWiki.XWikiPreferences", "javamail_extra_props").toString();
        properties.put("mail.smtp.port", Integer.parseInt(port));
        properties.put("mail.smtp.host", host);
        properties.put("mail.smtp.localhost", "localhost");
        properties.put("mail.host", "localhost");
        properties.put("mail.debug", "false");
        // properties.put("mail.smtp.from", from);
        if (!StringUtils.isEmpty(server_password) && !StringUtils.isEmpty(server_userName)) {
            properties.put("mail.smtp.auth", "true");
            properties.put("mail.smtp.server.username", server_userName);
            properties.put("mail.smtp.server.password", server_password);
        } else
            properties.put("mail.smtp.auth", "false");
        if (extraPropertiesString != null && !StringUtils.isEmpty(extraPropertiesString)) {
            InputStream is = new ByteArrayInputStream(extraPropertiesString.getBytes());
            Properties extraProperties = new Properties();
            try {
                extraProperties.load(is);
            } catch (IOException e) {
                throw new RuntimeException("Error configuring mail connection.", e);
            }
            for (Entry<Object, Object> e : extraProperties.entrySet()) {
                String propName = (String) e.getKey();
                String propValue = (String) e.getValue();
                if (properties.getProperty(propName) == null) {
                    properties.setProperty(propName, propValue);
                }
            }
        }
        return properties;
    }

    private MimeBodyPart createAttachmentPart(Attachment attachment)
    {
        try {
            String name = attachment.getFilename();
            byte[] stream = attachment.getContent();
            File temp = File.createTempFile("tmpfile", ".tmp");
            FileOutputStream fos = new FileOutputStream(temp);
            fos.write(stream);
            fos.close();
            DataSource source = new FileDataSource(temp);
            MimeBodyPart part = new MimeBodyPart();
            String mimeType = MimeTypesUtil.getMimeTypeFromFilename(name);

            part.setDataHandler(new DataHandler(source));
            part.setHeader("Content-Type", mimeType);
            part.setFileName(name);
            part.setContentID("<" + name + ">");
            part.setDisposition("inline");

            temp.deleteOnExit();
            return part;
        } catch (Exception e) {
            return new MimeBodyPart();
        }
    }

    /**
     * Split comma separated list of emails
     * 
     * @param email comma separated list of emails
     * @return An array containing the emails
     */
    private String[] parseAddresses(String email)
    {
        if (email == null) {
            return null;
        }
        email = email.trim();
        String[] emails = email.split(",");
        for (int i = 0; i < emails.length; i++) {
            emails[i] = emails[i].trim();
        }
        return emails;
    }

    /**
     * Filters a list of emails : removes illegal addresses
     * 
     * @param email List of emails
     * @return An Array containing the correct adresses
     */
    private InternetAddress[] toInternetAddresses(String email) throws AddressException
    {
        String[] mails = parseAddresses(email);
        if (mails == null) {
            return null;
        }
        InternetAddress[] address = new InternetAddress[mails.length];
        for (int i = 0; i < mails.length; i++) {
            address[i] = new InternetAddress(mails[i]);
        }
        return address;
    }

    public String createCalendar(String location, String summary, Date startDate, Date endDate)
    {
        String startDateString = formatDate(startDate);
        String endDateString = formatDate(endDate);
        String creationDate = formatDate(new Date());

        String calendar =
            "BEGIN:VCALENDAR" + "\n" + "VERSION:2.0" + "\n" + "PRODID:-//XWiki//XWiki Calendar 1.0//EN" + "\n"
                + "X-WR-TIMEZONE:Europe/Paris" + "\n" + "BEGIN:VEVENT" + "\n";
        calendar += "DTSTAMP:" + creationDate + "\n";
        calendar += "LOCATION:" + location + "\n";
        calendar += "DTSTART;TZID=Europe/Paris:" + startDateString + "\n";
        calendar += "DTEND;TZID=Europe/Paris:" + endDateString + "\n";
        calendar += "SUMMARY:" + summary + "\n";
        calendar += "END:VEVENT" + "\n" + "END:VCALENDAR";
        return calendar;
    }

    private String formatDate(Date date)
    {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        String dateFormatted = "";
        int year = cal.get(Calendar.YEAR);
        String yearAsString = Integer.toString(year);
        int month = cal.get(Calendar.MONTH) + 1; // January is 0 with Java calendar
        String monthAsString = Integer.toString(month);
        if (month < 10)
            monthAsString = "0" + monthAsString;
        int day = cal.get(Calendar.DAY_OF_MONTH);
        String dayAsString = Integer.toString(day);
        if (day < 10)
            dayAsString = "0" + dayAsString;
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        String hourAsString = Integer.toString(hour);
        if (hour < 10)
            hourAsString = "0" + hourAsString;
        int minutes = cal.get(Calendar.MINUTE);
        String minutesAsString = Integer.toString(minutes);
        if (minutes < 10)
            minutesAsString = "0" + minutesAsString;
        int seconds = cal.get(Calendar.SECOND);
        String secondsAsString = Integer.toString(seconds);
        if (seconds < 10)
            secondsAsString = "0" + secondsAsString;
        dateFormatted =
            yearAsString + monthAsString + dayAsString + "T" + hourAsString + minutesAsString + secondsAsString + "Z";
        return dateFormatted;
    }

}
