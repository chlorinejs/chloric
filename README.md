# Chloric

DEPRECATED in favour of [lein-cl2c](https://github.com/chlorinejs/lein-cl2c)

A watcher/compiler for Clojure/[Chlorine](https://github.com/chlorinejs/chlorine)

## Get Chloric

You can build chloric with:
```
lein uberjar
```
or [download](https://github.com/chlorinejs/chlorine/wiki/Downloads) the latest standalone jar file.

## Usage

```
java -jar chloric-{VERSION}-standalone.jar -r 2000 some-dirs-or-files
```
...or you may prefer [`drip`](https://github.com/flatland/drip/) to `java` command.
A watcher will start and check for changes every 2 seconds.

A bash script named `chloric` should be even more convenient:

```bash
#!/bin/sh
drip -jar {PATH_TO}/chloric-{VERSION}-standalone.jar "$@"
```
## License

Copyright © 2012 Hoang Minh Thang

Distributed under the Eclipse Public License, the same as Clojure.
