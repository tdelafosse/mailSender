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
package org.xwiki.commons;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Scanner;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.internet.InternetAddress;

import org.apache.velocity.VelocityContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.xwiki.bridge.DocumentAccessBridge;
import org.xwiki.commons.internal.DefaultMailSender;
import org.xwiki.commons.internal.Mail;
import org.xwiki.component.util.ReflectionUtils;
import org.xwiki.test.AbstractMockingComponentTestCase;
import org.xwiki.test.annotation.MockingRequirement;

import org.jmock.Mock;
import org.jmock.Mockery;
import org.jmock.Expectations;
import org.jvnet.mock_javamail.Mailbox;

/**
 * Tests for the {@link MailSender} component.
 */
@MockingRequirement(DefaultMailSender.class)
public class MailSenderTest extends AbstractMockingComponentTestCase<MailSender>
{
    private MailSender mailSender;

    @Before
    public void configure() throws Exception
    {
        this.mailSender = getComponentManager().getInstance(MailSender.class);
        Mailbox.clearAll();
    }

    @Test
    public void testNewMail()
    {
        Mail mail = this.mailSender.newMail("john@acme.org", "peter@acme.org", "mary@acme.org", null, "Test");
        mail.addContent("text/plain", "Test");
        Assert
            .assertEquals(
                "From [john@acme.org], To [peter@acme.org], Cc [mary@acme.org], Subject [Test], Contents [text/plain:Test \n ]",
                mail.toString());
    }

    @Test
    public void testSendMail() throws Exception
    {
        final int port = 25;

        Mockery mockery = getMockery();
        final Logger logger = mockery.mock(Logger.class);
        ReflectionUtils.setFieldValue(this.mailSender, "logger", logger);
        final DocumentAccessBridge documentAccessBridge = mockery.mock(DocumentAccessBridge.class, "mockDAB");
        ReflectionUtils.setFieldValue(this.mailSender, "documentAccessBridge", documentAccessBridge);

        mockery.checking(new Expectations()
        {
            {
                oneOf(documentAccessBridge).getProperty("XWiki.XWikiPreferences", "smtp_server");
                will(returnValue("myserver"));
                oneOf(documentAccessBridge).getProperty("XWiki.XWikiPreferences", "smtp_port");
                will(returnValue(port));
                oneOf(documentAccessBridge).getProperty("XWiki.XWikiPreferences", "smtp_server_username");
                will(returnValue(""));
                oneOf(documentAccessBridge).getProperty("XWiki.XWikiPreferences", "smtp_server_password");
                will(returnValue(""));
                oneOf(documentAccessBridge).getProperty("XWiki.XWikiPreferences", "javamail_extra_props");
                will(returnValue(""));
            }
        });

        mockery.checking(new Expectations()
        {
            {
                oneOf(logger).info("Sending mail : Initializing properties");
            }
        });

        Mail mail =
            this.mailSender.newMail("john@acme.org", "peter@acme.org, alfred@acme.org", null, null, "Test subject");
        mail.addContent("text/html", "<p>Test</p>");
        mail.addContent("text/plain", "Test");
        int result = this.mailSender.send(mail);
        mockery.assertIsSatisfied();
        Assert.assertEquals(1, result);

        // Verify that the email was received
        List<Message> inbox = Mailbox.get("peter@acme.org");
        Assert.assertEquals(1, inbox.size());
        Message message = inbox.get(0);
        Assert.assertEquals("Test subject", message.getSubject());
        Assert.assertEquals("john@acme.org", ((InternetAddress) message.getFrom()[0]).getAddress());
        Address[] recipients = message.getAllRecipients();
        Assert.assertEquals(2, recipients.length);
        Assert.assertEquals("alfred@acme.org", recipients[1].toString());
    }

    @Test
    public void testNoRecipient()
    {
        Mockery mockery = getMockery();
        final Logger logger = mockery.mock(Logger.class);
        ReflectionUtils.setFieldValue(this.mailSender, "logger", logger);
        mockery.checking(new Expectations()
        {
            {
                oneOf(logger).error("This mail has no recipient");
            }
        });

        Mail mail = this.mailSender.newMail("john@acme.org", null, null, null, "Test");
        mail.addContent("text/plain", "Test");
        int result = this.mailSender.send(mail);
        mockery.assertIsSatisfied();
        Assert.assertEquals(0, result);
    }

    @Test
    public void testNoContent()
    {
        Mockery mockery = getMockery();
        final Logger logger = mockery.mock(Logger.class);
        ReflectionUtils.setFieldValue(this.mailSender, "logger", logger);
        mockery.checking(new Expectations()
        {
            {
                oneOf(logger).error("This mail is empty. You should add a content");
            }
        });

        Mail mail = this.mailSender.newMail("john@acme.org", "peter@acme.org", null, null, "Test");
        int result = this.mailSender.send(mail);
        mockery.assertIsSatisfied();
        Assert.assertEquals(0, result);
    }

    @Test
    public void testCalendar() // Check that the dates in the calendar are correctly formated
    {
        Calendar cal = Calendar.getInstance();
        cal.set(2013, 0, 1, 9, 5, 5);
        Date date = new Date();
        date.setTime(cal.getTimeInMillis());

        String calendar = this.mailSender.createCalendar("Paris", "Party", date, date);
        Scanner dateScan = new Scanner(calendar);
        dateScan.useDelimiter("DTSTART;TZID=Europe/Paris:");
        dateScan.next();
        String endCalendar = dateScan.next();
        Scanner getLine = new Scanner(endCalendar);
        getLine.useDelimiter("\n");
        Assert.assertEquals("20130101T090505Z", getLine.next());
    }
}
