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
import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentNotFoundException;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.repository.RepositoryManager;
import org.nuxeo.ecm.core.work.AbstractWork;
import org.nuxeo.ecm.retention.adapter.Record;
import org.nuxeo.ecm.retention.adapter.Record.RecordRule;
import org.nuxeo.ecm.retention.service.RetentionService;
import org.nuxeo.runtime.api.Framework;

public class RetentionRecordUpdaterWork extends AbstractWork {

    public static final Log log = LogFactory.getLog(RetentionRecordUpdaterWork.class);

    private static final long serialVersionUID = 1L;

    public static final String TITLE = "Retention Record Updater";
    
    public static final String CATEGORY = "retentionRecordUpdater";


    protected Date maxCutOffDate;


    public RetentionRecordUpdaterWork(List<String> docs, Date maxCutOffDate) {
        setDocuments(Framework.getService(RepositoryManager.class).getDefaultRepositoryName(), docs);
        this.maxCutOffDate = maxCutOffDate;
    }

    @Override
    public String getTitle() {
        return TITLE;
    }

    @Override
    public String getCategory() {
        return CATEGORY;
    }

    
    @Override
    public void work() {
        openSystemSession();
        RetentionService service = Framework.getService(RetentionService.class);
        for (String string : docIds) {
            DocumentModel doc = null;
            try {
                doc = session.getDocument(new IdRef(string));
            } catch (DocumentNotFoundException e) {
                // this is executed post commit so the document could have been modified to start retention and removed
                // in the same transaction
                log.warn("Document impacted by retention no longer exists:" + string);
                continue;

            }
            Record record = doc.getAdapter(Record.class);
            if (record == null) {
                log.warn("Document should be impacted by retention but is no longer a Record:" + string);
                continue;
            }

            Calendar maxCutOff = Calendar.getInstance();
            maxCutOff.setTime(maxCutOffDate);
            List<RecordRule> rules = record.getRecordRules();
            for (RecordRule recordRule : rules) {
                if (recordRule.getCutoffStart() != null && recordRule.getCutoffStart().before(maxCutOff)) {
                    service.startRetention(record, service.getRetentionRule(recordRule.getRuleId(), session), true,
                            session);

                }
            }
        }
    }
}