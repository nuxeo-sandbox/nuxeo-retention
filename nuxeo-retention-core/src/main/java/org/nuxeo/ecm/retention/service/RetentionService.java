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

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.retention.adapter.Record;
import org.nuxeo.ecm.retention.adapter.RetentionRule;

public interface RetentionService {

    public static final String RECORD_FACET = "Record";

    public static final String RETENTION_RULE_FACET = "RetentionRule";

    public static enum RETENTION_STATE {
        active, expired, unmanaged
    };

    public static final Long batchSize = 10L;// maybe configurable through a property

    public static final String RETENTION_CHECKER_LISTENER_IGNORE_EVENT = "retentionRecordIgnore";

    public static final String RETENTION_CHECKER_EVENT = "checkRetentionEvent";

    /**
     * Attaches the given rule to the document using an unrestricted session. Starts the retention if the rule evaluates
     * to true at the current time (if the expression in the begin condition is true and the retention is not triggered
     * by an event)
     * 
     * @since 9.2
     */
    void attachRule(String ruleId, DocumentModel doc);

    /**
     * Performs the give query using an unrestricted session and attaches the given rule to the documents. Starts the
     * retention if the rule evaluates to true at the current time ( if the expression in the begin condition is true
     * and the retention is not triggered by an event)
     * 
     * @since 9.2
     */
    void attachRule(String ruleId, String query, CoreSession session);

    /**
     * Evaluates the rules for a given record against the list of events and the given date
     * 
     * @since 9.2
     */
    public void evalRules(Record record, List<String> eventId, Date dateToCheck , CoreSession session);

    /**
     * Evaluates the rules for a list of documents and a list of events for each document Queues a worker to eval the
     * given list.
     * 
     * @since 9.2
     */
    public void evalRules(Map<String, List<String>> docsToCheckAndEvents, Date dateToCheck);

    /**
     * Query for records to check if retention status is impacted by the given date. Queues workers to eval the a page
     * 
     * @since 9.2
     */
    public void queryDocsAndEvalRulesForDate(Date dateToCheck);

    /**
     * Starts retention for doc. Executes the beginAction and sets to Active if is not already the case. Used when a
     * delay was set
     * 
     * @since 9.2
     */
    public void startRetention(Record record, RetentionRule rule, boolean save, CoreSession session);

    /**
     * End the retention. Executes the endAction
     * 
     * @since 9.2
     */
    public void endRetention(Record record, RetentionRule rule, CoreSession session);

    /**
     * Returns a rule based on its id. First, we are trying to find a static rule, if none find, trying to fetch a
     * dynamic rule persisted on a document based on its id
     * 
     * @since 9.2
     */
    RetentionRule getRetentionRule(String ruleId, CoreSession session) throws NuxeoException;

    /**
     * Creates a dynamic rule persisted on the given document. The ruleId is the if of the document where the rule is
     * persisted. Returns the rule id.
     * 
     * @since 9.2
     */
    String createOrUpdateDynamicRuleOnDocument(String beginDelayPeriod, String retentionPeriod, int retentionReminder,
            String beginAction, String endAction, String beginCondExpression, String beginCondEvent,
            String endCondExpression, DocumentModel doc, CoreSession session);
}
