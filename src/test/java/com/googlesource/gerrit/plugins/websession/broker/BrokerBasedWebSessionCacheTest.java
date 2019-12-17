// Copyright (C) 2019 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.plugins.websession.broker;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.gerritforge.gerrit.eventbroker.BrokerApi;
import com.gerritforge.gerrit.eventbroker.EventMessage;
import com.gerritforge.gerrit.eventbroker.EventMessage.Header;
import com.google.common.cache.Cache;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.httpd.WebSessionManager.Val;
import com.google.gerrit.server.events.Event;
import com.googlesource.gerrit.plugins.websession.broker.BrokerBasedWebSessionCache.WebSessionEvent;
import com.googlesource.gerrit.plugins.websession.broker.BrokerBasedWebSessionCache.WebSessionEvent.Operation;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BrokerBasedWebSessionCacheTest {

  private static final int DEFAULT_ACCOUNT_ID = 1000000;
  private static final String KEY = "aSceprtma6B0qZ0hKxXHvQ5iyfUhCcFXxG";
  private byte[] emptyPayload = new byte[] {-84, -19, 0, 5, 112};
  byte[] defaultPayload =
      new byte[] {
        -84, -19, 0, 5, 115, 114, 0, 45, 99, 111, 109, 46, 103, 111, 111, 103, 108, 101, 46, 103,
        101, 114, 114, 105, 116, 46, 104, 116, 116, 112, 100, 46, 87, 101, 98, 83, 101, 115, 115,
        105, 111, 110, 77, 97, 110, 97, 103, 101, 114, 36, 86, 97, 108, 0, 0, 0, 0, 0, 0, 0, 2, 3,
        0, 0, 120, 112, 119, 97, 1, -64, -124, 61, 2, 0, 0, 1, 111, 13, -8, 90, 7, 3, 0, 5, 34, 97,
        83, 99, 101, 112, 114, 113, 86, 87, 54, 85, 79, 45, 88, 51, 107, 51, 116, 102, 85, 109, 86,
        103, 82, 73, 90, 56, 53, 99, 99, 52, 71, 114, 87, 6, 0, 0, 1, 111, 16, 84, -103, -121, 7,
        34, 97, 83, 99, 101, 112, 114, 114, 82, 103, 119, 49, 71, 110, 90, 56, 122, 54, 49, 49, 86,
        52, 121, 110, 65, 100, 110, 113, 99, 68, 45, 105, 99, 75, 97, 0, 120
      };

  @Mock BrokerApi brokerApi;
  @Mock Cache<String, Val> cache;
  @Captor ArgumentCaptor<EventMessage> eventCaptor;
  @Captor ArgumentCaptor<Val> valCaptor;

  BrokerBasedWebSessionCache objectUnderTest;

  @Before
  public void setup() {
    DynamicItem<BrokerApi> item = DynamicItem.itemOf(BrokerApi.class, brokerApi);
    objectUnderTest = new BrokerBasedWebSessionCache(cache, "web_session_topic", item);
  }

  @Test
  public void shouldPublishMessageWhenLoginEvent() {
    EventMessage eventMessage = createEventMessage();
    Val value = createVal(eventMessage);
    when(brokerApi.newMessage(any(UUID.class), any(Event.class))).thenReturn(eventMessage);

    objectUnderTest.put(KEY, value);

    verify(brokerApi, times(1)).send(anyString(), eventCaptor.capture());

    assertThat(eventCaptor.getValue().getEvent()).isNotNull();
    WebSessionEvent event = (WebSessionEvent) eventCaptor.getValue().getEvent();
    assertThat(event.operation).isEqualTo(WebSessionEvent.Operation.ADD);
    assertThat(event.key).isEqualTo(KEY);
    assertThat(event.payload).isEqualTo(defaultPayload);
  }

  @Test
  public void shouldPublishMessageWhenLogoutEvent() {
    EventMessage eventMessage = createEventMessage(emptyPayload, Operation.REMOVE);
    when(brokerApi.newMessage(any(UUID.class), any(Event.class))).thenReturn(eventMessage);

    objectUnderTest.invalidate(KEY);

    verify(brokerApi, times(1)).send(anyString(), eventCaptor.capture());

    assertThat(eventCaptor.getValue().getEvent()).isNotNull();
    WebSessionEvent event = (WebSessionEvent) eventCaptor.getValue().getEvent();
    assertThat(event.operation).isEqualTo(WebSessionEvent.Operation.REMOVE);
    assertThat(event.key).isEqualTo(KEY);
    assertThat(event.payload).isEqualTo(emptyPayload);
  }

  @Test
  public void shouldUpdateCacheWhenLoginMessageReceived() {
    EventMessage eventMessage = createEventMessage();

    objectUnderTest.processMessage(eventMessage);

    verify(cache, times(1)).put(anyString(), valCaptor.capture());

    assertThat(valCaptor.getValue()).isNotNull();
    Val val = valCaptor.getValue();
    assertThat(val.getAccountId().get()).isEqualTo(DEFAULT_ACCOUNT_ID);
  }

  @Test
  public void shouldUpdateCacheWhenLogoutMessageReceived() {
    EventMessage eventMessage = createEventMessage(emptyPayload, Operation.REMOVE);

    objectUnderTest.processMessage(eventMessage);

    verify(cache, times(1)).invalidate(KEY);
  }

  @Test
  public void shouldSkipCacheUpdateWhenUnknownEventType() {
    Header header =
        new Header(
            UUID.fromString("7cb80dbe-65c4-4f2c-84de-580d98199d4a"),
            UUID.fromString("97711495-1013-414e-bfd2-44776787520d"));
    Event event = new Event("sample-event") {};
    EventMessage eventMessage = new EventMessage(header, event);
    objectUnderTest.processMessage(eventMessage);

    verifyZeroInteractions(cache);
  }

  @Test
  public void shouldSkipCacheUpdateWhenInvalidPayload() {
    EventMessage eventMessage = createEventMessage(new byte[] {1, 2, 3, 4}, Operation.ADD);
    objectUnderTest.processMessage(eventMessage);

    verifyZeroInteractions(cache);
  }

  @SuppressWarnings("unchecked")
  private Val createVal(EventMessage message) {
    ArgumentCaptor<Val> valArgumentCaptor = ArgumentCaptor.forClass(Val.class);

    objectUnderTest.processMessage(message);
    verify(cache).put(anyString(), valArgumentCaptor.capture());
    reset(cache);
    return valArgumentCaptor.getValue();
  }

  private EventMessage createEventMessage() {

    return createEventMessage(defaultPayload, Operation.ADD);
  }

  private EventMessage createEventMessage(byte[] payload, Operation operation) {

    Header header =
        new Header(
            UUID.fromString("7cb80dbe-65c4-4f2c-84de-580d98199d4a"),
            UUID.fromString("97711495-1013-414e-bfd2-44776787520d"));
    WebSessionEvent event = new WebSessionEvent(KEY, payload, operation);
    return new EventMessage(header, event);
  }
}
