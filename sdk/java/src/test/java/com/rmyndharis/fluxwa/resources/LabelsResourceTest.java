package com.rmyndharis.fluxwa.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rmyndharis.fluxwa.ClientConfig;
import com.rmyndharis.fluxwa.FluxWaClient;
import com.rmyndharis.fluxwa.http.HttpMethod;
import com.rmyndharis.fluxwa.model.AddLabelRequest;
import com.rmyndharis.fluxwa.model.LabelRecord;
import com.rmyndharis.fluxwa.support.MockTransport;
import org.junit.jupiter.api.Test;

class LabelsResourceTest {
    final MockTransport tx = new MockTransport();
    final FluxWaClient client = new FluxWaClient(
        ClientConfig.builder().baseUrl("http://h").apiKey("k").transport(tx).build());

    @Test
    void listHitsLabelsPath() {
        tx.respond(200, "[]");
        client.labels.list("s");
        assertEquals("http://h/api/sessions/s/labels", tx.lastRequest().url());
        assertEquals(HttpMethod.GET, tx.lastRequest().method());
    }

    /** Guards the wire contract: the backend `Label` carries `hexColor`, never `color`/`colorHex` (#754). */
    @Test
    void listDeserializesTheLabelWireShape() {
        tx.respond(200, "[{\"id\":\"l1\",\"name\":\"VIP\",\"hexColor\":\"#25D366\"}]");
        LabelRecord label = client.labels.list("s").get(0);
        assertEquals("l1", label.id());
        assertEquals("VIP", label.name());
        assertEquals("#25D366", label.hexColor());
    }

    @Test
    void getEncodesIds() {
        tx.respond(200, "{\"id\":\"a/b\",\"name\":\"VIP\"}");
        client.labels.get("s", "a/b");
        assertEquals("http://h/api/sessions/s/labels/a%2Fb", tx.lastRequest().url());
        assertEquals(HttpMethod.GET, tx.lastRequest().method());
    }

    @Test
    void forChatHitsChatPath() {
        tx.respond(200, "[]");
        client.labels.forChat("s", "628123@c.us");
        assertEquals("http://h/api/sessions/s/labels/chat/628123@c.us", tx.lastRequest().url());
        assertEquals(HttpMethod.GET, tx.lastRequest().method());
    }

    @Test
    void addToChatSendsBody() {
        tx.respond(200, "{\"success\":true}");
        client.labels.addToChat("s", "628123@c.us", AddLabelRequest.builder().labelId("lbl-42").build());
        assertEquals("http://h/api/sessions/s/labels/chat/628123@c.us", tx.lastRequest().url());
        assertEquals(HttpMethod.POST, tx.lastRequest().method());
        assertTrue(tx.lastRequest().body().contains("lbl-42"));
    }

    @Test
    void removeFromChatEncodesLabelId() {
        tx.respond(200, "{\"success\":true}");
        client.labels.removeFromChat("s", "628123@c.us", "lbl/42");
        assertEquals(
            "http://h/api/sessions/s/labels/chat/628123@c.us/lbl%2F42", tx.lastRequest().url());
        assertEquals(HttpMethod.DELETE, tx.lastRequest().method());
    }
}
