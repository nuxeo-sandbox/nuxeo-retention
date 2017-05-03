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
import java.util.Map;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.retention.service.RetentionRuleConditionDescriptor;
import org.nuxeo.ecm.retention.service.RetentionRuleDescriptor;
import org.nuxeo.ecm.retention.service.Rule;

public class RetentionRule implements Rule {

    public static final String RULE_ID_PROPERTY = "rule:rule_id";

    public static final String RULE_BEGIN_CONDITION_PROPERTY = "rule:begin_condition";

    public static final String RULE_BEGIN_CONDITION_DOC_TYPE_PROPERTY = "rule:begin_condition/docType";

    public static final String RULE_BEGIN_CONDITION_EVENT_PROPERTY = "rule:begin_condition/event";

    public static final String RULE_BEGIN_CONDITION_STATE_PROPERTY = "rule:begin_condition/lifeCycleState";

    public static final String RULE_END_CONDITION_PROPERTY = "rule:end_condition";

    public static final String RULE_BEGIN_DELAY_PROPERTY = "rule:begin_delay";

    public static final String RULE_BEGIN_ACTION_PROPERTY = "rule:begin_action";

    public static final String RULE_END_ACTION_PROPERTY = "rule:end_action";

    public static final String RULE_END_CONDITION_EVENT_PROPERTY = "rule:end_condition/event";

    public static final String RULE_END_CONDITION_STATE_PROPERTY = "rule:end_condition/lifeCycleState";

    protected String id;

    protected RetentionRuleCondition beginCondition;

    protected String beginDelay;

    protected String beginAction;

    protected String endAction;

    protected RetentionRuleCondition endCondition;

    public RetentionRule(RetentionRuleDescriptor ruleDescriptor) {
        this.id = ruleDescriptor.getId();
        this.beginCondition = new RetentionRuleCondition(ruleDescriptor.getBeginCondition());
        this.beginDelay = ruleDescriptor.getBeginDelay();
        this.beginAction = ruleDescriptor.getBeginAction();
        this.endAction = ruleDescriptor.getEndAction();
        this.endCondition = new RetentionRuleCondition(ruleDescriptor.getEndCondition());
    }

    @SuppressWarnings("unchecked")
    public RetentionRule(DocumentModel doc) {
        this.id = (String) doc.getPropertyValue(RULE_ID_PROPERTY);
        this.beginCondition = new RetentionRuleCondition(
                (Map<String, Serializable>) doc.getPropertyValue(RULE_BEGIN_CONDITION_PROPERTY));
        this.endCondition = new RetentionRuleCondition(
                (Map<String, Serializable>) doc.getPropertyValue(RULE_END_CONDITION_PROPERTY));
        this.beginDelay = (String) doc.getPropertyValue(RULE_BEGIN_DELAY_PROPERTY);
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
    public RetentionRuleCondition getEndCondition() {
        return endCondition;
    }

    public class RetentionRuleCondition implements RuleCondition {

        public RetentionRuleCondition(RetentionRuleConditionDescriptor conditionDesc) {
            this.docType = conditionDesc.getDocType();
            this.event = conditionDesc.getEvent();
            this.lifeCycleState = conditionDesc.getLifeCycleState();
        }

        public RetentionRuleCondition(Map<String, Serializable> conditionProperty) {
            this.docType = (String) conditionProperty.get("docType");
            this.event = (String) conditionProperty.get("event");
            this.lifeCycleState = (String) conditionProperty.get("lifeCycleState");
        }

        protected String docType;

        protected String event;

        protected String lifeCycleState;

        @Override
        public String getDocType() {
            return docType;
        }

        @Override
        public String getEvent() {
            return event;
        }

        @Override
        public String getLifeCycleState() {
            return lifeCycleState;
        }

    }

}
