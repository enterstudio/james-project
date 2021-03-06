/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.apache.james.mailbox.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class MailboxPathTest {

    @Test
    public void getHierarchyLevelsShouldBeOrdered() {
        assertThat(new MailboxPath("#private", "user", "inbox.folder.subfolder")
            .getHierarchyLevels('.'))
            .containsExactly(
                new MailboxPath("#private", "user", "inbox"),
                new MailboxPath("#private", "user", "inbox.folder"),
                new MailboxPath("#private", "user", "inbox.folder.subfolder"));
    }

    @Test
    public void getHierarchyLevelsShouldReturnPathWhenOneLevel() {
        assertThat(new MailboxPath("#private", "user", "inbox")
            .getHierarchyLevels('.'))
            .containsExactly(
                new MailboxPath("#private", "user", "inbox"));
    }

    @Test
    public void getHierarchyLevelsShouldReturnPathWhenEmptyName() {
        assertThat(new MailboxPath("#private", "user", "")
            .getHierarchyLevels('.'))
            .containsExactly(
                new MailboxPath("#private", "user", ""));
    }

    @Test
    public void getHierarchyLevelsShouldReturnPathWhenNullName() {
        assertThat(new MailboxPath("#private", "user", null)
            .getHierarchyLevels('.'))
            .containsExactly(
                new MailboxPath("#private", "user", null));
    }
}
