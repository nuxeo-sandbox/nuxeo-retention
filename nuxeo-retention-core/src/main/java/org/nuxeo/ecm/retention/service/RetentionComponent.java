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
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.el.ExpressionFactoryImpl;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentNotFoundException;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.UnrestrictedSessionRunner;
import org.nuxeo.ecm.core.api.repository.RepositoryManager;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.ecm.platform.actions.ELActionContext;
import org.nuxeo.ecm.platform.el.ExpressionContext;
import org.nuxeo.ecm.platform.query.api.PageProvider;
import org.nuxeo.ecm.platform.query.api.PageProviderService;
import org.nuxeo.ecm.platform.query.core.CoreQueryPageProviderDescriptor;
import org.nuxeo.ecm.platform.query.nxql.CoreQueryDocumentPageProvider;
import org.nuxeo.ecm.retention.adapter.Record;
import org.nuxeo.ecm.retention.adapter.RetentionRule;
import org.nuxeo.ecm.retention.work.RetentionRecordCheckerWork;
import org.nuxeo.ecm.retention.work.RetentionRecordUpdaterWork;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;

public class RetentionComponent extends DefaultComponent implements RetentionService {

    public static final String RULES_EP = "rules";

    protected RetentionRulesContributionRegistry registry = new RetentionRulesContributionRegistry();

    public static Log log = LogFactory.getLog(RetentionComponent.class);

    @Override
    public void registerContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {
        if (RULES_EP.equals(extensionPoint)) {
            registry.addContribution((RetentionRuleDescriptor) contribution);
        }
    }

    @Override
    public void attachRule(String ruleId, DocumentModel doc) {
        new UnrestrictedSessionRunner(Framework.getLocalService(RepositoryManager.class).getDefaultRepositoryName()) {

            @Override
            public void run() {
                if (!doc.hasFacet(RECORD_FACET)) {
                    doc.addFacet(RECORD_FACET);
                }
                Record record = doc.getAdapter(Record.class);
                if (record.hasRule(ruleId)) {
                    return;
                }

                RetentionRule rule = getRetentionRule(ruleId, session);
                record.addRule(ruleId);
                // start the rule if possible
                Boolean ruleApplies = rule.getBeginCondition() != null ? evaluateConditionExpression(
                        initActionContext(record.getDoc(), session), rule.getBeginCondition().getExpression(),
                        record.getDoc(), session) : true;
                if (rule.getBeginCondition().getEvent() == null) {
                    if (ruleApplies) {
                        setRetentionDatesAndStartIfNoDelay(record, rule, false, session);
                    }
                }
                record.save(session);
            }

        }.runUnrestricted();

    }

    @Override
    public void attachRule(String ruleId, String query, CoreSession session) {
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
            // this should not be to expensive to be done inline here
            for (DocumentModel documentModel : nextDocumentsToBeUpdated) {
                attachRule(ruleId, documentModel);
            }
            offset += maxResult;
        } while (nextDocumentsToBeUpdated.size() == maxResult && pp.isNextPageAvailable());
    }

    @Override
    public void evalRules(Record record, List<String> events, Date dateToCheck, CoreSession session) {
        if (record == null) {
            return; // nothing to do
        }
        String retentionStatus = record.getStatus();
        if (RETENTION_STATE.expired.name().equals(retentionStatus)) {
            // retention expired, nothing to do, should remove the facet?
            return;

        }
        // no need to init it for every rule, its the same
        ELActionContext actionContext = initActionContext(record.getDoc(), session);
        List<Record.RecordRule> recordRules = record.getRecordRules();
        for (Record.RecordRule rr : recordRules) {
            // for every rule on the document;
            // if cutoff start and disposalDate have been set, and the retention is active only need to check if the
            // disposal date has been reached
            if (RETENTION_STATE.active.name().equalsIgnoreCase(record.getStatus()) && rr.getCutoffStart() != null
                    && rr.getDisposalDate() != null) {
                // disposal date has passed
                Calendar maxCutOff = Calendar.getInstance();
                maxCutOff.setTime(dateToCheck);

                if (rr.getDisposalDate().before(maxCutOff)) {
                    // record is still active? if yes execute endAction and set to expired
                    endRetention(record, getRetentionRule(rr.getRuleId(), session), session);
                    return; // no need to check other rules? the first one that applies sets the document to
                            // expired?
                }
                continue; // if the dates are already set, no need to inspect the condition
            }

            RetentionRule rule = getRetentionRule(rr.getRuleId(), session);
            Boolean ruleApplies = evaluateConditionExpression(actionContext, rule.getBeginCondition().getExpression(),
                    record.getDoc(), session);
            // if either there is an event to match or there is no event
            if ((ruleApplies && events != null && events.contains(rule.getBeginCondition().getEvent()))
                    || (ruleApplies && rule.getBeginCondition().getEvent() == null)) {
                setRetentionDatesAndStartIfNoDelay(record, rule, true, session); // should we check the condition also
                                                                                 // when
                // we have an event?
            }

        }
    }

    @Override
    public void evalRules(Map<String, List<String>> docsToCheckAndEvents, Date dateToCheck) {
        RetentionRecordCheckerWork work = new RetentionRecordCheckerWork(docsToCheckAndEvents, dateToCheck);
        if (docsToCheckAndEvents.isEmpty()) {
            return;
        }
        Framework.getService(WorkManager.class).schedule(work, WorkManager.Scheduling.ENQUEUE);
    }

    protected void startRetention(List<String> docs, Date maxCutoffDate) {
        if (docs.isEmpty()) {
            return;
        }
        RetentionRecordUpdaterWork work = new RetentionRecordUpdaterWork(docs, maxCutoffDate);
        Framework.getService(WorkManager.class).schedule(work, WorkManager.Scheduling.ENQUEUE);
    }

    @Override
    public void queryDocsAndEvalRulesForDate(Date dateToCheck) {
        // docs in active retention that might have been expired
        evalRulesForActiveDocs(dateToCheck);
        // docs not in active retention because of the initial delay
        startRetentionForUnmanagedDocs(dateToCheck);
    }

    protected void startRetentionForUnmanagedDocs(Date dateToCheck) {
        evalRules(dateToCheck, "unmanaged_records", false);
    }

    protected void evalRulesForActiveDocs(Date dateToCheck) {
        evalRules(dateToCheck, "active_records", true);
    }

    protected void evalRules(Date dateToCheck, String providerName, boolean activeRetention) {

        new UnrestrictedSessionRunner(Framework.getLocalService(RepositoryManager.class).getDefaultRepositoryName()) {

            @Override
            public void run() {
                long offset = 0;
                List<DocumentModel> nextDocumentsToBeChecked;

                Map<String, Serializable> props = new HashMap<String, Serializable>();
                props.put(CoreQueryDocumentPageProvider.CORE_SESSION_PROPERTY, (Serializable) session);
                Object[] params = new Object[1];
                params[0] = new SimpleDateFormat("yyyy-MM-dd").format(dateToCheck);
                props.put(CoreQueryDocumentPageProvider.CORE_SESSION_PROPERTY, (Serializable) session);

                @SuppressWarnings("unchecked")
                PageProvider<DocumentModel> pp = (PageProvider<DocumentModel>) Framework.getService(
                        PageProviderService.class).getPageProvider(providerName, null, batchSize, 0L, props, params);
                long maxResult = pp.getPageSize();
                do {
                    pp.setCurrentPageOffset(offset);
                    pp.refresh();
                    nextDocumentsToBeChecked = pp.getCurrentPage();
                    if (nextDocumentsToBeChecked.isEmpty()) {
                        break;
                    }
                    Map<String, List<String>> map = new HashMap<String, List<String>>();
                    for (DocumentModel documentModel : nextDocumentsToBeChecked) {
                        map.put(documentModel.getId(), new ArrayList<String>());
                    }

                    if (activeRetention) {
                        evalRules(map, dateToCheck);
                    } else {
                        startRetention(
                                nextDocumentsToBeChecked.stream()
                                                        .map(DocumentModel::getId)
                                                        .collect(Collectors.toList()), dateToCheck);
                    }
                    offset += maxResult;
                } while (nextDocumentsToBeChecked.size() == maxResult && pp.isNextPageAvailable());

            }
        }.runUnrestricted();

    }

    @Override
    public void endRetention(Record record, RetentionRule rule, CoreSession session) {
        executeRuleAction(rule.getEndAction(), record.getDoc(), session);
        record.setStatus(RETENTION_STATE.expired.name());
        record.save(session);
    }

    protected void setRetentionDatesAndStartIfNoDelay(Record record, RetentionRule rule, boolean save,
            CoreSession session) {
        LocalDateTime cutoffDate = LocalDateTime.now();
        LocalDateTime startReminderDate = null;
        if (!rule.getBeginDealyAsPeriod().isZero()) {
            Period delayPeriod = rule.getBeginDealyAsPeriod();
            cutoffDate = cutoffDate.plusYears(delayPeriod.getYears())
                                   .plusMonths(delayPeriod.getMonths())
                                   .plusDays(delayPeriod.getDays());
        }

        Period retentionPeriod = rule.getRetentionDurationAsPeriod();
        LocalDateTime disposalDate = cutoffDate.plusYears(retentionPeriod.getYears())
                                               .plusMonths(retentionPeriod.getMonths())
                                               .plusDays(retentionPeriod.getDays());

        if (rule.getRetentionReminderDays() > 0) {
            startReminderDate = disposalDate.minusDays(rule.getRetentionReminderDays());
            // the reminder starts at the disposalDate - retentionReminder days
        }

        record.setRuleDatesAndUpdateGlobalRetentionDetails(
                rule.getId(),
                GregorianCalendar.from(ZonedDateTime.of(cutoffDate, ZoneId.systemDefault())),
                GregorianCalendar.from(ZonedDateTime.of(disposalDate, ZoneId.systemDefault())),
                startReminderDate != null ? GregorianCalendar.from(ZonedDateTime.of(startReminderDate,
                        ZoneId.systemDefault())) : null);
        if (rule.getBeginDealyAsPeriod().isZero()) {
            startRetention(record, rule, false, session);
        }

        if (save) {
            record.save(session);
        }
    }

    @Override
    public void startRetention(Record record, RetentionRule rule, boolean save, CoreSession session) {
        executeRuleAction(rule.getBeginAction(), record.getDoc(), session);
        if (!RETENTION_STATE.active.name().equals(record.getStatus())) {
            record.setStatus(RETENTION_STATE.active.name());
            if (save) {
                record.save(session);
            }
        }
    }

    @Override
    public RetentionRule getRetentionRule(String ruleId, CoreSession session) throws NuxeoException {
        if (ruleId == null) {
            throw new NuxeoException("Can not attch null rule");
        }
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

    @Override
    public String createOrUpdateDynamicRuleOnDocument(String beginDelayPeriod, String retentionPeriod,
            int retentionReminder, String beginAction, String endAction, String beginCondExpression,
            String beginCondEvent, String endCondExpression, DocumentModel doc, CoreSession session) {
        if (!doc.hasFacet(RETENTION_RULE_FACET)) {
            doc.addFacet(RETENTION_RULE_FACET); // else is just updating an existing rule
        }
        doc.setPropertyValue(RetentionRule.RULE_ID_PROPERTY, doc.getId());
        doc.setPropertyValue(RetentionRule.RULE_BEGIN_DELAY_PERIOD_PROPERTY, beginDelayPeriod); // should validate with
                                                                                                // a
        // regexp?
        doc.setPropertyValue(RetentionRule.RULE_RETENTION_DURATION_PERIOD_PROPERTY, retentionPeriod);
        doc.setPropertyValue(RetentionRule.RULE_RETENTION_REMINDER_PROPERTY, retentionReminder);
        doc.setPropertyValue(RetentionRule.RULE_BEGIN_ACTION_PROPERTY, beginAction);
        doc.setPropertyValue(RetentionRule.RULE_END_ACTION_PROPERTY, endAction);
        doc.setPropertyValue(RetentionRule.RULE_BEGIN_CONDITION_EXPRESSION_TYPE_PROPERTY, beginCondExpression);
        doc.setPropertyValue(RetentionRule.RULE_BEGIN_CONDITION_EVENT_PROPERTY, beginCondEvent);
        doc.setPropertyValue(RetentionRule.RULE_END_CONDITION_EXPRESSION_PROPERTY, endCondExpression);

        doc = session.saveDocument(doc);
        return doc.getId();

    }

    protected ELActionContext initActionContext(DocumentModel doc, CoreSession session) {
        // handles only filters that can be resolved in this context
        ELActionContext ctx = new ELActionContext(new ExpressionContext(), new ExpressionFactoryImpl());
        ctx.setDocumentManager(session);
        ctx.setCurrentPrincipal((NuxeoPrincipal) session.getPrincipal());
        ctx.setCurrentDocument(doc);
        return ctx;
    }

    protected Boolean evaluateConditionExpression(ELActionContext ctx, String expression, DocumentModel doc,
            CoreSession session) {
        if (StringUtils.isEmpty(expression)) {
            return true;
        }
        Object res = ctx.checkCondition(expression);
        // if no condition, attach always
        if (res == null) {
            return true;
        }
        return (Boolean) res;
    }

    protected OperationContext getExecutionContext(DocumentModel doc, CoreSession session) {
        OperationContext context = new OperationContext(session);
        context.put("document", doc);
        context.setCommit(false); // no session save at end
        context.setInput(doc);
        return context;
    }

    protected void executeRuleAction(String actionId, DocumentModel doc, CoreSession session) {
        if (StringUtils.isEmpty(actionId)) {
            return;
        }
        // get base context
        OperationContext context = getExecutionContext(doc, session);
        AutomationService automationService = Framework.getLocalService(AutomationService.class);
        try {
            automationService.run(context, actionId);
        } catch (OperationException e) {
            throw new NuxeoException("Error running chain: " + actionId, e);
        }

    }

}
