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

import java.util.ArrayList;
import java.util.List;

/**
 * 
 * @since 9.2
 */
import org.apache.commons.lang3.StringUtils;
import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XNodeList;
import org.nuxeo.common.xmap.annotation.XObject;

@XObject(value = "rule")
public class RetentionRuleDescriptor implements Rule {

    @XNode(value = "id")
    protected String id;

    @XNode("begin-condition")
    protected RetentionRuleConditionDescriptor beginCondition;

    @XNode(value = "begin-action")
    protected String beginAction;

    @XNodeList(value = "begin-actions/action", type = ArrayList.class, componentType = RetentionRuleActionsDescriptor.class)
    protected List<RetentionRuleActionsDescriptor> beginActionsDesc = new ArrayList<RetentionRuleActionsDescriptor>(0);

    @XNode(value = "end-action")
    protected String endAction;

    @XNodeList(value = "end-actions/action", type = ArrayList.class, componentType = RetentionRuleActionsDescriptor.class)
    protected List<RetentionRuleActionsDescriptor> endActionsDesc = new ArrayList<RetentionRuleActionsDescriptor>(0);

    @XNode(value = "end-condition")
    protected RetentionRuleConditionDescriptor endCondition;

    protected String beginDelay;

    @XNode(value = "begin-delay")
    public void setBeginDelay(String value) {
        beginDelay = value;
    }

    protected String retentionDuration;

    protected String[] beginActions = null;

    protected String[] endActions = null;

    @XNode(value = "retention-duration")
    public void setRetentionDuration(String value) {
        retentionDuration = value;
    }

    protected int retentionReminderDays;

    @XNode(value = "retention-reminder-days")
    public void setRetentionReminderDays(String value) {
        retentionReminderDays = StringUtils.isBlank(value) ? 0 : Integer.valueOf(value);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public RetentionRuleConditionDescriptor getBeginCondition() {
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
    public String[] getBeginActions() {
        if (beginActions == null) {
            beginActions = getActionsList(beginActionsDesc);
        }
        return beginActions;
    }

    @Override
    public String[] getEndActions() {
        if (endActions == null) {
            endActions = getActionsList(endActionsDesc);
        }
        return endActions;
    }

    @Override
    public RetentionRuleConditionDescriptor getEndCondition() {
        return endCondition;
    }

    @Override
    public String getRetentionDuration() {
        return retentionDuration;
    }

    @Override
    public int getRetentionReminderDays() {
        return retentionReminderDays;
    }

    protected String[] getActionsList(List<RetentionRuleActionsDescriptor> actionsDesc) {
        if (actionsDesc.size() == 0) {
            return new String[0];
        }

        actionsDesc.sort((RetentionRuleActionsDescriptor action1,
                RetentionRuleActionsDescriptor action2) -> action1.getOrder() - action2.getOrder());
        ArrayList<String> actions = new ArrayList<String>();
        for (RetentionRuleActionsDescriptor actionDesc : actionsDesc) {
            actions.add(actionDesc.getOperation());
        }
        return actions.stream().toArray(String[]::new);

    }

}
