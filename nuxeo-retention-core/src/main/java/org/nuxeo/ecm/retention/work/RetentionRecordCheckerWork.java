/*
 * (C) Copyright 2017 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     mcedica@nuxeo.com
 */
package org.nuxeo.ecm.retention.work;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.repository.RepositoryManager;
import org.nuxeo.ecm.core.work.AbstractWork;
import org.nuxeo.ecm.retention.adapter.Record;
import org.nuxeo.ecm.retention.service.RetentionService;
import org.nuxeo.runtime.api.Framework;

public class RetentionRecordCheckerWork extends AbstractWork {

    private static final long serialVersionUID = 1L;

    public static final String TITLE = "Retention record Checker";

    protected Map<String, List<String>> docsToCheckAndEvents;

    protected Date dateToCheck;

    public RetentionRecordCheckerWork(Map<String, List<String>> docsToCheckAndEvents, Date dateTocheck) {
        this.docsToCheckAndEvents = docsToCheckAndEvents;
        List<String> docs = new ArrayList<String>();
        docs.addAll(docsToCheckAndEvents.keySet());
        setDocuments(Framework.getService(RepositoryManager.class).getDefaultRepositoryName(), docs);
        this.dateToCheck = dateTocheck;

    }

    @Override
    public String getTitle() {
        return TITLE;
    }

    @Override
    public void work() {
        openSystemSession();
        for (String string : docIds) {
            DocumentModel doc = session.getDocument(new IdRef(string));
            Record record = doc.getAdapter(Record.class);
            if (record == null) {
                continue;
            }
            Framework.getService(RetentionService.class).evalRules((Record) doc.getAdapter(Record.class),
                    docsToCheckAndEvents.get(string), dateToCheck, session);
        }

    }
}
