# Privacy-in-multiple-SDN-network-using-secret-sharing
 
This project's target is to achieve private interconnection on top of separate SDNs were each edge located on different network.
The project try to implementing the principles  of "Max-Flow Finding and Routing Distributed over Sub-Networks" research-article.

### System architecture

o Four VM machines.

o Each VM simulate a SDN controller.

o Each controller that written in JAVA, holds a graph object that simulate its network slice that under its responsibility and its neighbors from other networks (Boundary Switches).

o The graph nodes represent network switches, and the graph edges represent the network links.

o Every node object contain a table that represent flow rules table.

o Each controller not familiar with the others controllers topologies.

o One of the controllers used as a Central Unit and control the boundary switches.

o Each controller can communicate with the Central Unit using tcp connection.


### System Flow
 Python script simulates a host, connects to source controller and requests a private connection to a different host that is located on another SDN.

 Source Controller calculate its mincut and paths outside, divide those to &quot;mincut sets&quot; saves the options and sends to Central Unit a request that contain the request from host for private connection and also its mincut sets.

 Central Unit triggered by source request, sends a request to all other controllers to reply theirs mincut sets.

 Each controller calculates its mincut sets and send those back to Central Unit. The mincut sets contain only in and out switches of each SDN without the inner ones.

 Central Unit computes maxflow on each power set of the controllers’ options and decide which paths to use. After that sending each controller the option and rules of the boundary switches. Then install rules on the boundary switches.

 Each controller install rules according to the option chosen by Central Unit and complete the inner rules needed.

### Instructions
In JavaBasedSDNsSimulation folder you'll find four folders with 'SDN' prefix and a Python script.
Each folder should run on a different VM.
The client.py file can run on the host computer.

