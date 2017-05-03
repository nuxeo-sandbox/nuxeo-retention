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

import static org.nuxeo.ecm.core.versioning.VersioningService.DISABLE_AUTO_CHECKOUT;
import static org.nuxeo.ecm.platform.audit.service.NXAuditEventsService.DISABLE_AUDIT_LOGGER;
import static org.nuxeo.ecm.platform.dublincore.listener.DublinCoreListener.DISABLE_DUBLINCORE_LISTENER;
import static org.nuxeo.ecm.platform.ec.notification.NotificationConstants.DISABLE_NOTIFICATION_SERVICE;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentNotFoundException;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.ecm.platform.query.api.PageProvider;
import org.nuxeo.ecm.platform.query.api.PageProviderService;
import org.nuxeo.ecm.platform.query.core.CoreQueryPageProviderDescriptor;
import org.nuxeo.ecm.platform.query.nxql.CoreQueryDocumentPageProvider;
import org.nuxeo.ecm.retention.adapter.RetentionRule;
import org.nuxeo.ecm.retention.work.RetentionRecordUpdateWork;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;

public class RetentionComponent extends DefaultComponent implements RetentionService {

    public static final String RULES_EP = "rules";

    protected RetentionRulesContributionRegistry registry = new RetentionRulesContributionRegistry();

    public static List<String> DISABLED_FLAGS = Arrays.asList( //
            DISABLE_AUDIT_LOGGER, //
            DISABLE_DUBLINCORE_LISTENER, DISABLE_NOTIFICATION_SERVICE, DISABLE_AUTO_CHECKOUT); // + Others?

    public static Log log = LogFactory.getLog(RetentionComponent.class);

    @Override
    public void registerContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {
        if (RULES_EP.equals(extensionPoint)) {
            registry.addContribution((RetentionRuleDescriptor) contribution);
        }
    }

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

    @Override
    public RetentionRule getRetentionRule(String ruleId, CoreSession session) throws NuxeoException {
        RetentionRuleDescriptor staticRule = registry.getRetentionRule(ruleId);
        if (staticRule != null) {
            return new RetentionRule(staticRule);
        }
        DocumentModel dynamicRuleDoc;
        // trying to fetch a dynamic rule
        try {
            dynamicRuleDoc = session.getDocument(new IdRef(ruleId));
        } catch (DocumentNotFoundException e) {
            log.error("Can not find dynamic rule with id " + ruleId);
            throw new NuxeoException(e);
        }
        return new RetentionRule(dynamicRuleDoc);

    }

    // ToDo: to be called
    protected void disableListeners(DocumentModel doc) {
        for (String flag : DISABLED_FLAGS) {
            doc.putContextData(flag, Boolean.TRUE);
        }
    }

    @Override
    public String createOrUpdateDynamicRuleRuleOnDocument(String beginDelay, String beginAction, String endAction,
            String beginCondType, String beginCondEvent, String beginCondState, String endCondEvent,
            String endCondState, DocumentModel doc, CoreSession session) {
        if (!doc.hasFacet(RETENTION_RULE_FACET)) {
            doc.addFacet(RETENTION_RULE_FACET); // else is just updating an existing rule
        }
        doc.setPropertyValue(RetentionRule.RULE_ID_PROPERTY, doc.getId());
        doc.setPropertyValue(RetentionRule.RULE_BEGIN_DELAY_PROPERTY, beginDelay); // should validate with a regexp?
        doc.setPropertyValue(RetentionRule.RULE_BEGIN_ACTION_PROPERTY, beginAction);
        doc.setPropertyValue(RetentionRule.RULE_END_ACTION_PROPERTY, endAction);
        doc.setPropertyValue(RetentionRule.RULE_BEGIN_CONDITION_DOC_TYPE_PROPERTY, beginCondType);
        doc.setPropertyValue(RetentionRule.RULE_BEGIN_CONDITION_EVENT_PROPERTY, beginCondEvent);
        doc.setPropertyValue(RetentionRule.RULE_BEGIN_CONDITION_STATE_PROPERTY, beginCondState);
        doc.setPropertyValue(RetentionRule.RULE_END_CONDITION_EVENT_PROPERTY, endCondEvent);
        doc.setPropertyValue(RetentionRule.RULE_END_CONDITION_STATE_PROPERTY, endCondState);

        doc = session.saveDocument(doc);
        return doc.getId();

    }

}
