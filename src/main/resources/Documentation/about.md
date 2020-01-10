This plugin replaces the built-in Gerrit H2 based websession
cache with a broker based implementation. This allows to share
sessions without common file system. This is particularly useful for
multi-site scenario.

## Setup

Prerequisites:

* A message broker implementation has to be deployed across all the sites

For the masters:

* Install and configure @PLUGIN@ plugin
  @PLUGIN@ plugin requires message broker dynamic item setup.
  To fulfil this requirement events-broker needs to be installed as a library
  module in all the masters' `$GERRIT_SITE/lib` directory and the following
  property must be added to the `$GERRIT_SITE/etc/gerrit.config`

```
[gerrit]
    installModule = com.gerritforge.gerrit.eventbroker.BrokerApiModule
```

For further information and supported options, refer to [config](config.md)
documentation.
