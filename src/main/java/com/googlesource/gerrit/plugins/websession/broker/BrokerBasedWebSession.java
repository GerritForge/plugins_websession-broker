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

import com.google.gerrit.extensions.annotations.RootRelative;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.httpd.CacheBasedWebSession;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.httpd.WebSessionManagerFactory;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.IdentifiedUser.RequestFactory;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.events.EventTypes;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.servlet.RequestScoped;
import com.google.inject.servlet.ServletScopes;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class BrokerBasedWebSession extends CacheBasedWebSession {
  public static class Module extends CacheModule {

    @Override
    protected void configure() {
      EventTypes.register(
          BrokerBasedWebSessionCache.WebSessionEvent.TYPE,
          BrokerBasedWebSessionCache.WebSessionEvent.class);
      bindScope(RequestScoped.class, ServletScopes.REQUEST);
      DynamicItem.bind(binder(), WebSession.class)
          .to(BrokerBasedWebSession.class)
          .in(RequestScoped.class);
    }
  }

  @Inject
  BrokerBasedWebSession(
      @RootRelative Provider<HttpServletRequest> request,
      @RootRelative Provider<HttpServletResponse> response,
      WebSessionManagerFactory managerFactory,
      BrokerBasedWebSessionCache cache,
      AuthConfig authConfig,
      Provider<AnonymousUser> anonymousProvider,
      RequestFactory identified) {
    super(
        request.get(),
        response.get(),
        managerFactory.create(cache),
        authConfig,
        anonymousProvider,
        identified);
  }
}
