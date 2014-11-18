Grub
===============

Grub is a real-time synced grocery list. Mainly it's a way for me to play around with Clojure[Script] and core.async.

Install dependencies
------------
- Java 7+
<pre>
sudo apt-get install openjdk-7-jdk
</pre>
- MongoDB
<pre>
$ sudo apt-get install mongodb
</pre>
- leiningen 2.1.2+
<pre>
# For example:
$ mkdir ~/bin
$ echo "export PATH=\$:$HOME/bin" >> $HOME/.bashrc
$ cd ~/bin
$ wget https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein
$ chmod a+x ~/bin/lein
</pre>

Build
-------
<pre>
$ lein cljx
$ lein cljsbuild once dev
</pre>

Run
-------
<pre>
$ lein run dev
</pre>

By default it runs at http://localhost:3000
