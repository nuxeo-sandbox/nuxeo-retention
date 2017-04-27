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

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import javax.inject.Inject;
import java.io.Serializable;
import java.util.Calendar;
import java.util.GregorianCalendar;

@RunWith(FeaturesRunner.class)
@Features(CoreFeature.class)
@Deploy({ "org.nuxeo.ecm.retention.service.nuxeo-retention-service" })
public class RecordAdapterTest {
    @Inject
    CoreSession session;

    @Test
    public void shouldCallTheAdapter() {
        DocumentModel doc = session.createDocumentModel("/", "test-adapter", "Document");

        Assert.assertNull("Adapter cannot be instantiated", doc.getAdapter(Record.class));

        doc.addFacet("Record");
        Record adapter = doc.getAdapter(Record.class);

        Assert.assertNotNull("Adapter should be created if and only if it contains Record facet", adapter);
    }

    @Test
    public void testTheAdapterAccessors() {
        // tests all Record Getter and Setter Accessors
        DocumentModel doc = session.createDocumentModel("/", "test-adapter", "Document");
        doc = session.createDocument(doc);
        doc.addFacet("Record");
        Record adapter = doc.getAdapter(Record.class);
        Calendar currentTime = GregorianCalendar.getInstance();

        // use adapter to set metadata
        adapter.setStatus("managed");
        adapter.setMinCutoffAt(currentTime);
        adapter.setMaxRetentionAt(currentTime);
        session.saveDocument(doc);
        session.save();

        doc = session.getDocument(doc.getRef());
        adapter = doc.getAdapter(Record.class);

        // use adapter to get metadata
        Serializable teststatus = adapter.getStatus();
        Calendar testcutoff = adapter.getMinCutoffAt();
        Calendar testmaxretention = adapter.getMaxRetentionAt();

        // assert that they are the same
        Assert.assertEquals("managed", teststatus);
        Assert.assertEquals(currentTime, testcutoff);
        Assert.assertEquals(currentTime, testmaxretention);
    }
}
