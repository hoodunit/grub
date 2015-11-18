Grub
===============

Grub is a real-time synced grocery list. Mainly it's a way for me to play around with Clojure[Script] and core.async.

Install dependencies
------------
- Java
- Datomic
- Leiningen

Build for development
-------

```
$ lein cljsbuild auto dev
$ lein run dev
```

Navigate to http://localhost:3000.

Build for production
-------

```
$ lein cljsbuild once prod
$ lein run prod
```

Or you can run the `scripts/build.sh` script to get a deployment JAR.
