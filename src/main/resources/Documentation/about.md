This plugin replaces the built-in Gerrit H2 based websession
cache with a broker based implementation. This allows to share
sessions without common file system. This is particularly useful for
multi-site scenario.

## Setup

Prerequisites:

* Message broker deployed across all the sites

For the masters:

* Install and configure @PLUGIN@ plugin

For further information and supported options, refer to [config](config.md)
documentation.
