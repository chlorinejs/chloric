# Chloric

A watcher/compiler for Clojure/[Chlorine](https://github.com/myguidingstar/chlorine)

## Get Chloric

You can build chloric with:
```
lein uberjar
```
or [download](https://github.com/myguidingstar/chloric/downloads) the jar file.

## Usage

```
java -jar chloric-0.1.0-standalone.jar -r 2000 some-dirs-or-files
```
A watcher will start and check for changes every 2 seconds.

## License

Copyright © 2012 Hoang Minh Thang

Distributed under the Eclipse Public License, the same as Clojure.
