import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import javax.print.attribute.standard.PrinterLocation;

public class Main {

	
	public static void main(String[] args) {

	DirectGraph net = new DirectGraph("main");
		
		
		net.addVertex("source",1);
		net.addVertex("1", 1);
		net.addVertex("2", 1);
		net.addVertex("3", 1);
		net.addVertex("4", 1);
		
		net.addVertex("5", 2);
		net.addVertex("6", 2);
		net.addVertex("7", 2);
		net.addVertex("8", 2);
		
		net.addVertex("9", 3);
		net.addVertex("10", 3);
		net.addVertex("11", 3);
		net.addVertex("sink", 3);
		
		net.addVertex("r1", 0);
		net.addVertex("r2", 0);
		net.addVertex("r3", 0);
		net.addVertex("r4", 0);
		net.addVertex("r5", 0);
		
		
		net.addEdge("source", "r1",0,(short)0,(short)0);
		net.addEdge("source", "r2",0,(short)0,(short)0);
		net.addEdge("source", "r3",0,(short)0,(short)0);
		net.addEdge("source", "r4",0,(short)0,(short)0);
		
		net.addEdge("r1","source", 0,(short)0,(short)0);
		net.addEdge("r2","source", 0,(short)0,(short)0);
		net.addEdge("r3","source", 0,(short)0,(short)0);
		net.addEdge("r4","source", 0,(short)0,(short)0);

		
		net.addEdge("r1", "1",0,(short)0,(short)0);
		net.addEdge("r2", "2",0,(short)0,(short)0);
		net.addEdge("r3", "3",0,(short)0,(short)0);
		net.addEdge("r4", "4",0,(short)0,(short)0);
		
		net.addEdge("r1", "5",0,(short)0,(short)0);
		net.addEdge("r2", "6",0,(short)0,(short)0);
		net.addEdge("r3", "7",0,(short)0,(short)0);
		net.addEdge("r3", "10",0,(short)0,(short)0);
		net.addEdge("r4", "9",0,(short)0,(short)0);
		net.addEdge("r5", "8",0,(short)0,(short)0);
		net.addEdge("r5", "11",0,(short)0,(short)0);

		net.addEdge("5", "6",0,(short)0,(short)0);
		net.addEdge("5", "7",0,(short)0,(short)0);
		net.addEdge("5", "8",0,(short)0,(short)0);
		net.addEdge("6", "7",0,(short)0,(short)0);
		net.addEdge("6", "8",0,(short)0,(short)0);
		net.addEdge("7", "8",0,(short)0,(short)0);
		
		net.addEdge("sink", "10",0,(short)0,(short)0);
		net.addEdge("sink", "9",0,(short)0,(short)0);
		net.addEdge("sink", "11",0,(short)0,(short)0);


		net.addEdge("1", "r1",0,(short)0,(short)0);
		net.addEdge("2", "r2",0,(short)0,(short)0);
		net.addEdge("3","r3", 0,(short)0,(short)0);
		net.addEdge("4","r4", 0,(short)0,(short)0);
		
		net.addEdge("5","r1", 0,(short)0,(short)0);
		net.addEdge("6","r2", 0,(short)0,(short)0);
		net.addEdge("7","r3", 0,(short)0,(short)0);
		net.addEdge("10","r3", 0,(short)0,(short)0);
		net.addEdge("9","r4", 0,(short)0,(short)0);
		net.addEdge("8","r5", 0,(short)0,(short)0);
		net.addEdge("11","r5", 0,(short)0,(short)0);

		net.addEdge("6","5", 0,(short)0,(short)0);
		net.addEdge("7","5", 0,(short)0,(short)0);
		net.addEdge("8","5", 0,(short)0,(short)0);
		net.addEdge("7","6", 0,(short)0,(short)0);
		net.addEdge("8","6", 0,(short)0,(short)0);
		net.addEdge("8","7", 0,(short)0,(short)0);
		
		net.addEdge("10","sink", 0,(short)0,(short)0);
		net.addEdge("9","sink", 0,(short)0,(short)0);
		net.addEdge("11","sink", 0,(short)0,(short)0);

		
		
		
		DirectGraph redu = net.reduction("source", "sink");

		redu.dinicsMaxFlow(redu.getVertex("sdn1"), redu.getVertex("sdn3"));

		
		

	}
	

}
