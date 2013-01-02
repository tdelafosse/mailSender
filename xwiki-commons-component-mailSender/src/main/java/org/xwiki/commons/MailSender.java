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

import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.apache.velocity.VelocityContext;
import org.xwiki.commons.internal.Mail;
import org.xwiki.component.annotation.ComponentRole;

import com.xpn.xwiki.api.Attachment;

/**
 * Interface (aka Role) of the Component
 */
@ComponentRole
public interface MailSender
{
    Mail newMail(String from, String to, String cc, String bcc, String subject);

    Properties initProperties();

    String createPlain(String html);

    List<String> embeddedFound(Mail mail);

    List<String> imagesFromString(String s);

    int send(Mail mail);

    int sendMailFromTemplate(String templateDocFullName, String from, String to, String cc, String bcc,
        String language, VelocityContext vContext);

    String createCalendar(String location, String summary, Date startDate, Date endDate);

}
