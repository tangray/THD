java -cp bin/ NodeDHT 10.108.150.217 2033 2050 12


#TO start the client
#  java -cp bin/ -Djava.security.policy=src/java/policyfile.txt ClientChord localhost 1900

#Syntax to start the NodeDHT:
#Syntax one - NodeDHT-First [LocalPortnumber] [numNodes]  
#Syntax two - NodeDHT-other [Known-HostIP]  [Known-HostPortnumber] [LocalPortnumber] [numNodes]
# - LocalPortNumber is the number that will receive connections in the Node
# - Known-HostName  is the hostIP of one DHTNode already in the net.
# - Known-HostPortnumber is the port which the Known-Host listening waiting for connections.
# - numNodes is the number of the DHTNode you designed 

#NodeDHT first
# java -cp bin/ NodeDHT 55511 numNodes

#NodeDHT first
# java -cp bin/ NodeDHT IP 55511 55512 numNodes

