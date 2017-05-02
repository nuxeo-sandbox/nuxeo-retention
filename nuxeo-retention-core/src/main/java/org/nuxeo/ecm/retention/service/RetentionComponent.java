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

package org.nuxeo.ecm.retention.service;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.ecm.platform.query.api.PageProvider;
import org.nuxeo.ecm.platform.query.api.PageProviderService;
import org.nuxeo.ecm.platform.query.core.CoreQueryPageProviderDescriptor;
import org.nuxeo.ecm.platform.query.nxql.CoreQueryDocumentPageProvider;
import org.nuxeo.ecm.retention.work.RetentionRecordUpdateWork;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.DefaultComponent;

public class RetentionComponent extends DefaultComponent implements RetentionService {

    public static Log log = LogFactory.getLog(RetentionComponent.class);

    @Override
    public void attachRule(String ruleId, DocumentModel doc, CoreSession session) {
        // ToDo: check if we need the unrestricted in the service
        // ToDo: compute min_cutoff_at and max_retention_at
        CoreInstance.doPrivileged(session, (CoreSession s) -> {
            if (!doc.hasFacet(RECORD_FACET)) {
                doc.addFacet(RECORD_FACET);
            }
            s.saveDocument(doc);
        });
    }

    @Override
    public void attachRule(String ruleId, String query, CoreSession session) {

        WorkManager workManager = Framework.getLocalService(WorkManager.class);
        long offset = 0;
        List<DocumentModel> nextDocumentsToBeUpdated;

        CoreQueryPageProviderDescriptor desc = new CoreQueryPageProviderDescriptor();
        desc.setPattern(query);
        Map<String, Serializable> props = new HashMap<String, Serializable>();
        props.put(CoreQueryDocumentPageProvider.CORE_SESSION_PROPERTY, (Serializable) session);

        @SuppressWarnings("unchecked")
        PageProvider<DocumentModel> pp = (PageProvider<DocumentModel>) Framework.getService(PageProviderService.class)
                                                                                .getPageProvider("", desc, null, null,
                                                                                        batchSize, 0L, props);
        final long maxResult = pp.getPageSize();
        do {
            pp.setCurrentPageOffset(offset);
            pp.refresh();
            nextDocumentsToBeUpdated = pp.getCurrentPage();
            if (nextDocumentsToBeUpdated.isEmpty()) {
                break;
            }
            List<String> docIds = nextDocumentsToBeUpdated.stream()
                                                          .map(DocumentModel::getId)
                                                          .collect(Collectors.toList());
            // ToDo: check to see if we really need an worker depending on what we need to process on the
            // document later
            RetentionRecordUpdateWork work = new RetentionRecordUpdateWork(ruleId);
            work.setDocuments(session.getRepositoryName(), docIds);
            workManager.schedule(work, WorkManager.Scheduling.IF_NOT_SCHEDULED, true);
            offset += maxResult;
        } while (nextDocumentsToBeUpdated.size() == maxResult && pp.isNextPageAvailable());
    }


    @Override
    public boolean checkRecord(DocumentModel doc, CoreSession session) {
        return false;
    }

}
