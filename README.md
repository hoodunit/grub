Grub
===============

Grub is a real-time synced grocery list. Mainly it's a way for me to play around with Clojure[Script] and core.async.

Install dependencies
------------
- Java
- Datomic
  - Set environment variables:

      `DATOMIC_HOME=<Datomic directory>`

      `DATOMIC_TRANSACTOR_PROPERTIES_DIR=<directory with transactor.properties file>`
  - Add Datomic credentials to `~/.lein/credentials.clj.gpg` per Datomic instructions.

- Leiningen

Build for development
-------

```
$ ./scripts/start_datomic
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
