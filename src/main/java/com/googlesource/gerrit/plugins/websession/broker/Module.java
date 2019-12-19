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
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.inject.Inject;

public class Module extends LifecycleModule {
  DynamicItem<BrokerApi> brokerApi;

  @Override
  protected void configure() {
    if (brokerApi == null) {
      DynamicItem.itemOf(binder(), BrokerApi.class);
    }
  }

  @Inject(optional = true)
  public void setBrokerApi(DynamicItem<BrokerApi> brokerApi) {
    this.brokerApi = brokerApi;
  }
}
