/*
 *  (C) Copyright 2017 Nuxeo (http://nuxeo.com/) and others.
 *  
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  
 *       http://www.apache.org/licenses/LICENSE-2.0
 *  
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *  
 *   Contributors:
 *       Ryan McCue
 *  
 */

package org.nuxeo.ecm.retention.adapter;

import static org.nuxeo.ecm.core.api.versioning.VersioningService.DISABLE_AUTO_CHECKOUT;
import static org.nuxeo.ecm.platform.audit.service.NXAuditEventsService.DISABLE_AUDIT_LOGGER;
import static org.nuxeo.ecm.platform.dublincore.listener.DublinCoreListener.DISABLE_DUBLINCORE_LISTENER;
import static org.nuxeo.ecm.platform.ec.notification.NotificationConstants.DISABLE_NOTIFICATION_SERVICE;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.retention.service.RetentionService;

public class Record {

    public static final String STATUS_FIELD = "record:status";

    public static final String MIN_CUTOFF_AT = "record:min_cutoff_at";

    public static final String RECORD_MAX_RETENTION_AT = "record:max_retention_at";

    public static final String RETENTION_REMINDER_START_DATE = "record:reminder_start_date";

    public static final String RETENTION_RULES = "record:rules";

    public static List<String> DISABLED_FLAGS = Arrays.asList( //
            DISABLE_AUDIT_LOGGER, //
            DISABLE_DUBLINCORE_LISTENER, DISABLE_NOTIFICATION_SERVICE, DISABLE_AUTO_CHECKOUT); // + Others?

    protected final DocumentModel doc;

    public Record(DocumentModel doc) {
        this.doc = doc;
    }

    public DocumentRef getParentRef() {
        return doc.getParentRef();
    }

    // Technical properties retrieval
    public String getId() {
        return doc.getId();
    }

    public String getName() {
        return doc.getName();
    }

    public String getPath() {
        return doc.getPathAsString();
    }

    public DocumentModel getDoc() {
        return doc;
    }

    // Metadata get / set

    public String getStatus() {
        return (String) doc.getPropertyValue(STATUS_FIELD);
    }

    public void setStatus(String status) {
        doc.setPropertyValue(STATUS_FIELD, status);
    }

    public Calendar getMinCutoffAt() {
        return (Calendar) doc.getPropertyValue(MIN_CUTOFF_AT);
    }

    public void setMinCutoffAt(Calendar cutOffDate) {
        doc.setPropertyValue(MIN_CUTOFF_AT, cutOffDate);
    }

    public Calendar getMaxRetentionAt() {
        return (Calendar) doc.getPropertyValue(RECORD_MAX_RETENTION_AT);
    }

    public void setMaxRetentionAt(Calendar maxRetentionDate) {
        doc.setPropertyValue(RECORD_MAX_RETENTION_AT, maxRetentionDate);
    }

    public Calendar getReminderStartDate() {
        return (Calendar) doc.getPropertyValue(RETENTION_REMINDER_START_DATE);
    }

    @SuppressWarnings("unchecked")
    public void addRule(String ruleId) {
        List<Map<String, Serializable>> rr = (List<Map<String, Serializable>>) doc.getPropertyValue(RETENTION_RULES);
        for (Map<String, Serializable> map : rr) {
            if (ruleId.equals(map.get("rule_id"))) {
                return; // no need to add it
            }
        }
        Map<String, Serializable> r = new HashMap<String, Serializable>();
        r.put("rule_id", ruleId);
        rr.add(r);
        doc.setPropertyValue(RETENTION_RULES, (Serializable) rr);
    }

    @SuppressWarnings("unchecked")
    public boolean hasRule(String ruleId) {
        List<Map<String, Serializable>> rr = (List<Map<String, Serializable>>) doc.getPropertyValue(RETENTION_RULES);
        return rr.stream().filter((rule) -> ruleId.equals(rule.get("rule_id"))).findFirst().isPresent();

    }

    @SuppressWarnings("unchecked")
    public List<RecordRule> getRecordRules() {
        List<Map<String, Serializable>> rr = (List<Map<String, Serializable>>) doc.getPropertyValue(RETENTION_RULES);
        return rr.stream().map(rule -> new RecordRule(rule)).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    public void setRuleDatesAndUpdateGlobalRetentionDetails(String ruleId, Calendar cutoff, Calendar disposal,
            Calendar retentionExpireReminderStartDate) {
        Calendar min_cutoff_at = getMinCutoffAt();
        Calendar max_retention_at = getMaxRetentionAt();

        List<Map<String, Serializable>> rr = (List<Map<String, Serializable>>) doc.getPropertyValue(RETENTION_RULES);
        for (Map<String, Serializable> map : rr) {
            if (ruleId.equals(map.get("rule_id"))) {
                map.put("cutoff_at", cutoff);
                map.put("disposal_at", disposal);
                if (min_cutoff_at == null || cutoff.before(min_cutoff_at)) {
                    min_cutoff_at = cutoff;
                }
                if (max_retention_at == null || disposal.after(max_retention_at)) {
                    max_retention_at = disposal;
                }
            }
        }
        if (retentionExpireReminderStartDate != null) {
            doc.setPropertyValue(RETENTION_REMINDER_START_DATE, retentionExpireReminderStartDate);
        }
        doc.setPropertyValue(MIN_CUTOFF_AT, min_cutoff_at);
        doc.setPropertyValue(RECORD_MAX_RETENTION_AT, disposal);
        doc.setPropertyValue(RETENTION_RULES, (Serializable) rr);
    }

    public void save(CoreSession session) {
        disableListeners(doc, session);
        session.saveDocument(doc);
        enableListeners(doc);
    }

    protected void disableListeners(DocumentModel doc, CoreSession session) {
        for (String flag : DISABLED_FLAGS) {
            doc.putContextData(flag, Boolean.TRUE);
        }
        doc.putContextData(RetentionService.RETENTION_CHECKER_LISTENER_IGNORE, Boolean.TRUE);
    }

    protected void enableListeners(DocumentModel doc) {
        for (String flag : DISABLED_FLAGS) {
            doc.putContextData(flag, null);
        }
        doc.getContextData().remove(RetentionService.RETENTION_CHECKER_LISTENER_IGNORE);
    }

    public class RecordRule {

        String ruleId;

        Calendar cutoffStart;

        Calendar disposalDate;

        public RecordRule(Map<String, Serializable> ruleProperty) {
            this.ruleId = (String) ruleProperty.get("rule_id");
            this.cutoffStart = (Calendar) ruleProperty.get("cutoff_at");
            this.disposalDate = (Calendar) ruleProperty.get("disposal_at");
        }

        public String getRuleId() {
            return ruleId;
        }

        public Calendar getCutoffStart() {
            return cutoffStart;
        }

        public Calendar getDisposalDate() {
            return disposalDate;
        }

    }
}
