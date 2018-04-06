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

package org.nuxeo.ecm.retention.adapter;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.retention.service.RetentionRuleConditionDescriptor;
import org.nuxeo.ecm.retention.service.RetentionRuleDescriptor;
import org.nuxeo.ecm.retention.service.Rule;

public class RetentionRule implements Rule {

    public static final Log log = LogFactory.getLog(RetentionRule.class);

    public static final String RULE_ID_PROPERTY = "rule:ruleId";

    public static final String RULE_BEGIN_CONDITION_PROPERTY = "rule:beginCondition";

    public static final String RULE_BEGIN_CONDITION_EXPRESSION_TYPE_PROPERTY = "rule:beginCondition/expression";

    public static final String RULE_BEGIN_CONDITION_EVENT_PROPERTY = "rule:beginCondition/event";

    public static final String RULE_END_CONDITION_PROPERTY = "rule:endCondition";

    public static final String RULE_BEGIN_DELAY_PERIOD_PROPERTY = "rule:beginDelayPeriod";

    public static final String RULE_RETENTION_DISPOSAL_DATE_PROPERTY = "rule:retentionDisposalDate";

    public static final String RULE_RETENTION_DISPOSAL_DATE_XPATH_PROPERTY = "rule:retentionDisposalDateXpath";

    public static final String RULE_RETENTION_DURATION_PERIOD_PROPERTY = "rule:retentionDurationPeriod";

    public static final String RULE_RETENTION_REMINDER_PROPERTY = "rule:retentionReminderInDays";

    public static final String RULE_BEGIN_ACTION_PROPERTY = "rule:beginAction";

    public static final String RULE_END_ACTION_PROPERTY = "rule:endAction";

    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMdd");

    protected String id;

    protected RetentionRuleCondition beginCondition;

    protected Period beginDelay;

    protected Period retentionDuration;

    protected Date retentionDisposalDate;

    protected String retentionDisposalDateXpath;

    protected int retentionReminder;

    protected String beginAction;

    protected String endAction;

    public RetentionRule(RetentionRuleDescriptor ruleDescriptor) {
        this.id = ruleDescriptor.getId();
        this.beginCondition = new RetentionRuleCondition(ruleDescriptor.getBeginCondition());
        this.beginDelay = parsePeriod(ruleDescriptor.getBeginDelay());
        this.retentionDuration = parsePeriod(ruleDescriptor.getRetentionDuration());
        this.retentionDisposalDate = parseDisposalDate(ruleDescriptor.getRetentionDisposalDate());
        this.retentionDisposalDateXpath = ruleDescriptor.getRetentionDisposalDateXpath();
        this.retentionReminder = ruleDescriptor.getRetentionReminderDays();
        this.beginAction = ruleDescriptor.getBeginAction();
        this.endAction = ruleDescriptor.getEndAction();
    }

    @SuppressWarnings("unchecked")
    public RetentionRule(DocumentModel doc) {
        this.id = (String) doc.getPropertyValue(RULE_ID_PROPERTY);
        this.beginCondition = new RetentionRuleCondition(
                (Map<String, Serializable>) doc.getPropertyValue(RULE_BEGIN_CONDITION_PROPERTY));
        this.beginDelay = parsePeriod((String) doc.getPropertyValue(RULE_BEGIN_DELAY_PERIOD_PROPERTY));
        this.retentionDuration = parsePeriod((String) doc.getPropertyValue(RULE_RETENTION_DURATION_PERIOD_PROPERTY));
        this.retentionDisposalDate = parseDisposalDate(
                (String) doc.getPropertyValue(RULE_RETENTION_DISPOSAL_DATE_PROPERTY));
        this.retentionDisposalDateXpath = (String) doc.getPropertyValue(RULE_RETENTION_DISPOSAL_DATE_XPATH_PROPERTY);
        this.retentionReminder = ((Long) doc.getPropertyValue(RULE_RETENTION_REMINDER_PROPERTY)).intValue();
        this.beginAction = (String) doc.getPropertyValue(RULE_BEGIN_ACTION_PROPERTY);
        this.endAction = (String) doc.getPropertyValue(RULE_END_ACTION_PROPERTY);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public RetentionRuleCondition getBeginCondition() {
        return beginCondition;
    }

    @Override
    public String getBeginDelay() {
        return beginDelay.toString();
    }

    public Period getBeginDealyAsPeriod() {
        return beginDelay;
    }

    @Override
    public String getBeginAction() {
        return beginAction;
    }

    @Override
    public String getEndAction() {
        return endAction;
    }

    @Override
    public String getRetentionDisposalDate() {
        return retentionDisposalDate == null ? null : retentionDisposalDate.toString();
    }

    public Date getRetentionDisposalDateAsDate() {
        return retentionDisposalDate;
    }

    @Override
    public String getRetentionDisposalDateXpath() {
        return retentionDisposalDateXpath;
    }

    @Override
    public String getRetentionDuration() {
        return retentionDuration.toString();
    }

    public Period getRetentionDurationAsPeriod() {
        return retentionDuration;
    }

    @Override
    public int getRetentionReminderDays() {
        return retentionReminder;
    }

    // ToDo - validate regexp?
    protected Period parsePeriod(String retentionPeriod) {
        if (StringUtils.isBlank(retentionPeriod)) {
            return Period.ZERO;
        }
        // this has to match a format like 1Y2M3D
        retentionPeriod = retentionPeriod.startsWith("P") ? retentionPeriod : "P" + retentionPeriod;
        try {
            return Period.parse(retentionPeriod);
        } catch (DateTimeParseException e) {
            throw new NuxeoException("Invalid retention duration: " + retentionPeriod, e);
        }
    }

    protected Date parseDisposalDate(String dateAsString) {
        if (StringUtils.isBlank(dateAsString)) {
            return null;
        }
        try {
            return DATE_FORMAT.parse(dateAsString);
        } catch (ParseException e) {
            throw new NuxeoException("Invalid retention disposal date: " + dateAsString, e);
        }
    }

    public class RetentionRuleCondition implements RuleCondition {

        public RetentionRuleCondition(RetentionRuleConditionDescriptor conditionDesc) {
            this.expression = conditionDesc.getExpression();
            this.event = conditionDesc.getEvent();
        }

        public RetentionRuleCondition(Map<String, Serializable> conditionProperty) {
            this.expression = (String) conditionProperty.get("expression");
            this.event = (String) conditionProperty.get("event");
        }

        protected String expression;

        protected String event;

        @Override
        public String getEvent() {
            return event;
        }

        @Override
        public String getExpression() {
            return expression;
        }

    }

}
