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

import java.io.IOException;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.commons.logging.Log;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.transport.mailets.jsieve.ActionDispatcher;
import org.apache.james.transport.mailets.jsieve.ResourceLocator;
import org.apache.james.transport.mailets.jsieve.SieveMailAdapter;
import org.apache.james.user.api.UsersRepository;
import org.apache.jsieve.ConfigurationManager;
import org.apache.jsieve.SieveConfigurationException;
import org.apache.jsieve.SieveFactory;
import org.apache.jsieve.exception.SieveException;
import org.apache.jsieve.parser.generated.ParseException;
import org.apache.jsieve.parser.generated.TokenMgrError;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.MailetContext;

import com.google.common.base.Preconditions;

public class SieveMailStorer implements MailStorer {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private MailetContext mailetContext;
        private UsersRepository usersRepos;
        private SievePoster sievePoster;
        private String folder;
        private ResourceLocator resourceLocator;
        private Log log;
        private MailboxManager mailboxManager;

        public Builder folder(String folder) {
            this.folder = folder;
            return this;
        }

        public Builder usersRepository(UsersRepository usersRepository) {
            this.usersRepos = usersRepository;
            return this;
        }

        public Builder sievePoster(SievePoster sievePoster) {
            this.sievePoster = sievePoster;
            return this;
        }

        public Builder mailetContext(MailetContext mailetContext) {
            this.mailetContext = mailetContext;
            return this;
        }

        public Builder resourceLocator(ResourceLocator resourceLocator) {
            this.resourceLocator = resourceLocator;
            return this;
        }

        public Builder log(Log log) {
            this.log = log;
            return this;
        }

        public Builder mailboxManager(MailboxManager mailboxManager) {
            this.mailboxManager = mailboxManager;
            return this;
        }

        public SieveMailStorer build() throws MessagingException {
            Preconditions.checkNotNull(mailetContext);
            Preconditions.checkNotNull(usersRepos);
            Preconditions.checkNotNull(folder);
            Preconditions.checkNotNull(resourceLocator);
            Preconditions.checkNotNull(log);
            Preconditions.checkNotNull(sievePoster);
            return new SieveMailStorer(mailetContext, usersRepos, mailboxManager, folder, resourceLocator, log);
        }


    }

    private final MailetContext mailetContext;
    private final UsersRepository usersRepos;
    private final SievePoster sievePoster;
    private final String folder;
    private final ResourceLocator resourceLocator;
    private final SieveFactory factory;
    private final ActionDispatcher actionDispatcher;
    private final Log log;

    public SieveMailStorer(MailetContext mailetContext, UsersRepository usersRepos, MailboxManager mailboxManager, String folder,
                           ResourceLocator resourceLocator, Log log) throws MessagingException {
        this.mailetContext = mailetContext;
        this.usersRepos = usersRepos;
        this.sievePoster = new SievePoster(mailboxManager, folder, usersRepos, mailetContext);
        this.folder = folder;
        this.resourceLocator = resourceLocator;
        try {
            final ConfigurationManager configurationManager = new ConfigurationManager();
            configurationManager.setLog(log);
            factory = configurationManager.build();
        } catch (SieveConfigurationException e) {
            throw new MessagingException("Failed to load standard Sieve configuration.", e);
        }
        this.actionDispatcher = new ActionDispatcher();
        this.log = log;
    }

    public void storeMail(MailAddress sender, MailAddress recipient, Mail mail) throws MessagingException {
        Preconditions.checkNotNull(recipient, "Recipient for mail to be spooled cannot be null.");
        Preconditions.checkNotNull(mail.getMessage(), "Mail message to be spooled cannot be null.");

        sieveMessage(recipient, mail, log);
        // If no exception was thrown the message was successfully stored in the mailbox
        log.info("Local delivered mail " + mail.getName() + " sucessfully from " + DeliveryUtils.prettyPrint(sender) + " to " + DeliveryUtils.prettyPrint(recipient)
            + " in folder " + this.folder);
    }

    protected void sieveMessage(MailAddress recipient, Mail aMail, Log log) throws MessagingException {
        String username = DeliveryUtils.getUsername(recipient, usersRepos, log);
        try {
            final ResourceLocator.UserSieveInformation userSieveInformation = resourceLocator.get(getScriptUri(recipient, log));
            sieveMessageEvaluate(recipient, aMail, userSieveInformation, log);
        } catch (Exception ex) {
            // SIEVE is a mail filtering protocol.
            // Rejecting the mail because it cannot be filtered
            // seems very unfriendly.
            // So just log and store in INBOX
            log.error("Cannot evaluate Sieve script. Storing mail in user INBOX.", ex);
            storeMessageInbox(username, aMail.getMessage());
        }
    }

    private void sieveMessageEvaluate(MailAddress recipient, Mail aMail, ResourceLocator.UserSieveInformation userSieveInformation, Log log) throws MessagingException, IOException {
        try {
            SieveMailAdapter aMailAdapter = new SieveMailAdapter(aMail,
                mailetContext, actionDispatcher, sievePoster, userSieveInformation.getScriptActivationDate(),
                userSieveInformation.getScriptInterpretationDate(), recipient);
            aMailAdapter.setLog(log);
            // This logging operation is potentially costly
            log.debug("Evaluating " + aMailAdapter.toString() + "against \"" + getScriptUri(recipient, log) + "\"");
            factory.evaluate(aMailAdapter, factory.parse(userSieveInformation.getScriptContent()));
        } catch (SieveException ex) {
            handleFailure(recipient, aMail, ex, log);
        }
        catch (ParseException ex) {
            handleFailure(recipient, aMail, ex, log);
        }
        catch (TokenMgrError ex) {
            handleFailure(recipient, aMail, new SieveException(ex), log);
        }
    }

    protected String getScriptUri(MailAddress m, Log log) {
        return "//" + DeliveryUtils.getUsername(m, usersRepos, log) + "/sieve";
    }

    protected void handleFailure(MailAddress recipient, Mail aMail, Exception ex, Log log) throws MessagingException, IOException {
        String user = DeliveryUtils.getUsername(recipient, usersRepos, log);
        storeMessageInbox(user, SieveFailureMessageComposer.composeMessage(aMail, ex, user));
    }

    protected void storeMessageInbox(String username, MimeMessage message) throws MessagingException {
        sievePoster.post("mailbox://" + username + "/", message);
    }



}
