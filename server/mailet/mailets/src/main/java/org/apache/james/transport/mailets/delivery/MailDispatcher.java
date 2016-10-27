/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/
package org.apache.james.transport.mailets.delivery;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.commons.logging.Log;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.MailetContext;
import org.apache.mailet.base.RFC2822Headers;

import com.google.common.base.Preconditions;

/**
 * Contains resource bindings.
 */
public class MailDispatcher {

    public static final String DELIVERED_TO = "Delivered-To";

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private MailStorer mailStorer;
        private boolean consume;
        private MailetContext mailetContext;
        private Log log;

        public Builder consume(boolean consume) {
            this.consume = consume;
            return this;
        }

        public Builder mailStorer(MailStorer mailStorer) {
            this.mailStorer = mailStorer;
            return this;
        }

        public Builder log(Log log) {
            this.log = log;
            return this;
        }

        public Builder mailetContext(MailetContext mailetContext) {
            this.mailetContext = mailetContext;
            return this;
        }

        public MailDispatcher build() throws MessagingException {
            Preconditions.checkNotNull(mailStorer);
            Preconditions.checkNotNull(log);
            Preconditions.checkNotNull(mailetContext);
            return new MailDispatcher(mailStorer, consume, log, mailetContext);
        }

    }

    private final MailStorer mailStorer;
    private final boolean consume;
    private final MailetContext mailetContext;
    private final Log log;

    private MailDispatcher(MailStorer mailStorer, boolean consume, Log log, MailetContext mailetContext) {
        this.mailStorer = mailStorer;
        this.consume = consume;
        this.log = log;
        this.mailetContext = mailetContext;
    }

    /**
     * Delivers a mail to a local mailbox.
     *
     * @param mail
     *            the mail being processed
     *
     * @throws MessagingException
     *             if an error occurs while storing the mail
     */
    @SuppressWarnings("unchecked")
    public void dispatch(Mail mail) throws MessagingException {
        Collection<MailAddress> recipients = mail.getRecipients();
        Collection<MailAddress> errors = new Vector<MailAddress>();

        MimeMessage message = mail.getMessage();

        // Set Return-Path and remove all other Return-Path headers from the
        // message
        // This only works because there is a placeholder inserted by
        // MimeMessageWrapper
        message.setHeader(RFC2822Headers.RETURN_PATH, DeliveryUtils.prettyPrint(mail.getSender()));

        List<String> deliveredToHeader = Collections.list(message.getMatchingHeaders(new String[] { DELIVERED_TO }));
        message.removeHeader(DELIVERED_TO);

        for (MailAddress recipient : recipients) {
            try {
                // Add qmail's de facto standard Delivered-To header
                message.addHeader(DELIVERED_TO, recipient.toString());
                mailStorer.storeMail(mail.getSender(), recipient, mail);
                message.removeHeader(DELIVERED_TO);
            } catch (Exception ex) {
                log.error("Error while storing mail.", ex);
                errors.add(recipient);
            }
        }
        for (String deliveredTo : deliveredToHeader) {
            message.addHeader(DELIVERED_TO, deliveredTo);
        }
        if (!errors.isEmpty()) {
            // If there were errors, we redirect the email to the ERROR
            // processor.
            // In order for this server to meet the requirements of the SMTP
            // specification, mails on the ERROR processor must be returned to
            // the sender. Note that this email doesn't include any details
            // regarding the details of the failure(s).
            // In the future we may wish to address this.
            mailetContext.sendMail(mail.getSender(), errors, mail.getMessage(), Mail.ERROR);
        }
        if (consume) {
            // Consume this message
            mail.setState(Mail.GHOST);
        }
    }
}
