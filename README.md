Grub
===============

Grub is a real-time synced grocery list. Mainly it's a way for me to play around with Clojure[Script] and core.async.

Dependencies
------------
- mongodb
- Java 7+
<pre>
$ sudo apt-get install oracle-java7-installer
$ sudo apt-get install oracle-java7-set-default
</pre>
- leiningen 2.1.2+
<pre>
$ mkdir ~/bin
$ wget https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein
$ mv lein ~/bin/.
$ chmod a+x ~/bin/lein
$ lein
</pre>

Running
-------
<pre>
$ lein cljsbuild once
$ lein run dev
</pre>
- By default it will be running at http://localhost:3000
