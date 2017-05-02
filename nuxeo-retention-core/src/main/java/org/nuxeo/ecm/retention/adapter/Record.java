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

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;

import java.util.Calendar;

/**
 // Basic methods
 //
 // Note that we voluntarily expose only a subset of the DocumentModel API in this adapter.
 // You may wish to complete it without exposing everything!
 // For instance to avoid letting people change the document state using your adapter,
 // because this would be handled through workflows / buttons / events in your application.
 //
 */
public class Record {
    public static final String STATUS_FIELD = "record:status";

    public static final String MIN_CUTOFF_AT = "record:min_cutoff_at";

    public static final String RECORD_MAX_RETENTION_AT = "record:max_retention_at";

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

    public String getState() {
        return doc.getCurrentLifeCycleState();
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
}
