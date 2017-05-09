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

import java.util.Calendar;
import java.util.List;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.repository.RepositoryManager;
import org.nuxeo.ecm.core.work.AbstractWork;
import org.nuxeo.ecm.retention.adapter.Record;
import org.nuxeo.ecm.retention.adapter.Record.RecordRule;
import org.nuxeo.ecm.retention.service.RetentionService;
import org.nuxeo.runtime.api.Framework;

public class RetentionRecordUpdaterWork extends AbstractWork {

    private static final long serialVersionUID = 1L;

    public static final String TITLE = "Retention Record Updater";

    // ToDo : Queue - Category?

    public RetentionRecordUpdaterWork(List<String> docs) {
        setDocuments(Framework.getService(RepositoryManager.class).getDefaultRepositoryName(), docs);
    }

    @Override
    public String getTitle() {
        return TITLE;
    }

    @Override
    public void work() {
        openSystemSession();
        RetentionService service = Framework.getService(RetentionService.class);
        for (String string : docIds) {
            DocumentModel doc = session.getDocument(new IdRef(string));
            Record record = doc.getAdapter(Record.class);
            if (record == null) {
                continue;
            }

            Calendar now = Calendar.getInstance();
            List<RecordRule> rules = record.getRecordRules();
            for (RecordRule recordRule : rules) {
                if (recordRule.getCutoffStart().before(now)) {
                    service.startRetention(record, service.getRetentionRule(recordRule.getRuleId(), session), true,
                            session);

                }
            }
        }
    }
}