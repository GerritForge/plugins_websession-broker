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

import com.gerritforge.gerrit.eventbroker.BrokerApi;
import com.gerritforge.gerrit.eventbroker.EventMessage;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheStats;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.httpd.WebSessionManager;
import com.google.gerrit.httpd.WebSessionManager.Val;
import com.google.gerrit.server.events.Event;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;

@Singleton
public class BrokerBasedWebSessionCache implements Cache<String, WebSessionManager.Val> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  Cache<String, Val> cache;
  String webSessionTopicName;
  DynamicItem<BrokerApi> brokerApi;

  @Inject
  public BrokerBasedWebSessionCache(
      @Named(WebSessionManager.CACHE_NAME) Cache<String, Val> cache,
      @WebSessionTopicName String webSessionTopicName,
      DynamicItem<BrokerApi> brokerApi) {
    this.cache = cache;
    this.webSessionTopicName = webSessionTopicName;
    this.brokerApi = brokerApi;
    this.brokerApi.get().receiveAsync(webSessionTopicName, this::processMessage);
  }

  protected void processMessage(EventMessage message) {
    if (!WebSessionEvent.TYPE.equals(message.getEvent().getType())) {
      logger.atWarning().log(
          "Skipping web session message of unknown type:{}", message.getEvent().getType());
      return;
    }

    WebSessionEvent event = (WebSessionEvent) message.getEvent();

    switch (event.operation) {
      case ADD:
        try (ByteArrayInputStream in = new ByteArrayInputStream(event.payload);
            ObjectInputStream inputStream = new ObjectInputStream(in)) {

          cache.put(event.key, (Val) inputStream.readObject());
        } catch (IOException | ClassNotFoundException e) {
          logger.atSevere().withCause(e).log(
              "Malformed event '%s': [Exception: %s]", message.getHeader());
        }
        break;
      case REMOVE:
        cache.invalidate(event.key);
        break;
      default:
        logger.atWarning().log(
            "Skipping web session message of unknown operation type:{}", event.operation);
        break;
    }
  }

  @Override
  public @Nullable Val getIfPresent(Object key) {
    return cache.getIfPresent(key);
  }

  @Override
  public Val get(String key, Callable<? extends Val> valueLoader) throws ExecutionException {
    return cache.get(key, valueLoader);
  }

  @Override
  public ImmutableMap<String, Val> getAllPresent(Iterable<?> keys) {
    return cache.getAllPresent(keys);
  }

  @Override
  public void put(String key, Val value) {
    sendEvent(key, value, WebSessionEvent.Operation.ADD);
    cache.put(key, value);
  }

  @Override
  public void putAll(Map<? extends String, ? extends Val> keys) {
    for (Entry<? extends String, ? extends Val> e : keys.entrySet()) {
      put(e.getKey(), e.getValue());
    }
  }

  @Override
  public void invalidate(Object key) {
    sendEvent((String) key, null, WebSessionEvent.Operation.REMOVE);
    cache.invalidate(key);
  }

  @Override
  public void invalidateAll(Iterable<?> keys) {
    for (Object key : keys) {
      invalidate(key);
    }
  }

  @Override
  public void invalidateAll() {
    cache.asMap().forEach((key, value) -> invalidate(key));
  }

  @Override
  public long size() {
    return cache.size();
  }

  @Override
  public CacheStats stats() {
    return cache.stats();
  }

  @Override
  public ConcurrentMap<String, Val> asMap() {
    return cache.asMap();
  }

  @Override
  public void cleanUp() {
    cache.cleanUp();
  }

  private void sendEvent(String key, Val value, WebSessionEvent.Operation operation) {
    boolean succeeded = false;
    try (ByteArrayOutputStream out = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(out)) {

      objectOutputStream.writeObject(value);
      out.flush();
      byte[] serializedObject = out.toByteArray();
      EventMessage message =
          brokerApi
              .get()
              .newMessage(UUID.randomUUID(), new WebSessionEvent(key, serializedObject, operation));
      succeeded = brokerApi.get().send(webSessionTopicName, message);
    } catch (IOException e) {
      logger.atSevere().withCause(e).log(
          "Cannot serialize event for account id '%s': [Exception: %s]", value.getAccountId());
    } finally {
      if (!succeeded)
        logger.atSevere().log(
            "Cannot send web-session message for '%s Topic: '%s'", key, webSessionTopicName);
    }
  }

  public static class WebSessionEvent extends Event {

    public enum Operation {
      ADD,
      REMOVE;
    }

    static final String TYPE = "web-session";
    public String key;
    public byte[] payload;
    public Operation operation;

    protected WebSessionEvent(String key, byte[] payload, Operation operation) {
      super(TYPE);
      this.key = key;
      this.payload = payload;
      this.operation = operation;
    }
  }
}
