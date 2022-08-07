# NodeWatcher
Simple mempool watcher

# Usage
    java -jar nodeWatcher.jar cookiePath/credentials privkeyFile targetAddress
    
    java -jar nodeWatcher.jar http://__cookie__:blablabla@127.0.0.1:8332/ ./myPrivKeys.txt bc1qz2akvlch75rqdfg8pv7chqvz3m8jsl49k0kszc
    java -jar nodeWatcher.jar .bitcoin/.cookie ./myPrivKeys.txt bc1qz2akvlch75rqdfg8pv7chqvz3m8jsl49k0kszc

Private keys are expected in WIF format.

# Functionality
Program scans mempool transactions and if there is output for known address it produces transaction moving unspent output to destination address.

Program uses https://github.com/Polve/bitcoin-rpc-client

Contact
-------
Contact email: pawgo@protonmail.com
If you found this program useful, consider making a donation, I will appreciate it! 
**BTC**: `bc1qz2akvlch75rqdfg8pv7chqvz3m8jsl49k0kszc`
