

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.google.common.collect.Sets;



/**
 * 
 */

/**
 * @author root
 *
 */
public class Controller_general
{

	/**
	 * @param args
	 */
	public static void main(String[] args) 
	{

		String[] topo = {
				"s6,s7,s8,s9",
				"s14,s15,s16,s18","s6-s9-1", 
				"s6-s14-1,s7-s15-1,s8-s16-1,s9-s18-1"
				};
		String serverIp = "192.168.56.105";

		(new Thread(new MultiThreadServer(serverIp,topo))).start();

	}



	private static class MultiThreadServer implements Runnable 
	{
		private String srcIp;
		private String destIp;
		private Socket server;
		private ServerSocket serverSocket;
		private final Lock lock = new ReentrantLock();
		private DirectGraph generalSDN;

		MultiThreadServer(String serverIp, String[] topo)
		{
			this.generalSDN = buildDefaultGraph(topo);
			new Thread(new GraphVisual(generalSDN, "General Controlle \\n Default Graph", "DefaultGraph")).start();
		}
		@Override
		public void run() {

			/**
			 * Step#

				Step1			Wait for CU.
				Step2			read CU msg.
				Step3			calculate mincut sets.
				Step4			send mincut sets with parameters to CU.
				Step5			get answer for flows roles.
				Step6			install roles.
			 **/


			//********************************************************************************
			//********************************************************************************

			try {
				serverSocket = new ServerSocket(12342);
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			String msgFromCentralUnit = "";


			try {
				System.out.println("Waiting for Central Unit on port " + 
						serverSocket.getLocalPort() + "...");
				server = serverSocket.accept();

				System.out.println("Just connected to " + server.getRemoteSocketAddress());
				DataInputStream in = new DataInputStream(server.getInputStream());
				msgFromCentralUnit = in.readUTF(in);
				System.out.println("MSG From Central Unit: " + msgFromCentralUnit);

			}catch(SocketTimeoutException s) {
				System.out.println("Socket timed out!");
			}catch(IOException e) {
				e.printStackTrace();
			}

			//********************************************************************************
			//********************************************************************************

			String srcSwitch = "Source";

			Set<Set<String>> powerSets = getPowerSetOfVerticies();//contain all powerset of bonded switches without empty and all
			DirectGraph[] mincutSets = new DirectGraph[powerSets.size()];
			String[] mincutOptions = new String[powerSets.size()];
			String mincutOptionsForCU = "";
			int[] mincutValue = new int[powerSets.size()];
			int i = 0;
			int maxMincut = 0;
			for (Set<String> set : powerSets) 
			{
				mincutSets[i] = buildMincutSetsGraph(set);
				mincutValue[i] = mincutSets[i].dinicsMaxFlow(mincutSets[i].getVertex(srcSwitch), mincutSets[i].getVertex("Sink"));
				mincutOptions[i] = mincutSets(mincutSets[i]);
				if(!mincutOptions[i].isEmpty())
					mincutOptionsForCU += mincutOptions[i] + "\n";
				System.out.println( "i = " + i + " mincutValue "  + mincutValue[i] + " mincutOptions " + mincutOptions[i] + " set " + set.toString());
				if(mincutValue[i] > maxMincut)
					maxMincut = mincutValue[i];
				i++;
			}
			new Thread(new GraphVisual(mincutSets[13], "General Controlle \\n Best mincut Graph", "bestMincutGraph")).start();
			
			String answerForFlowRules = "";
			//********************************************************************************
			//********************************************************************************

			DataOutputStream out;
			try {
				out = new DataOutputStream(server.getOutputStream());
				out.writeUTF(mincutOptionsForCU + "@" + maxMincut);

				DataInputStream in = new DataInputStream(server.getInputStream());
				answerForFlowRules = in.readUTF(in);
				System.out.println("MSG From Central Unit: " + answerForFlowRules);
				server.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}


			//********************************************************************************
			//********************************************************************************


			String[] flowFromCU = answerForFlowRules.split(";");
			installRules(flowFromCU,mincutSets[13]);
			cleanGraph();
			new Thread(new GraphVisual(generalSDN, "General Controlle \\n Final Graph", "finalGraph")).start();
			
			for (Vertex v : generalSDN.getVerticies().values()) 
			{
				if(v.isMyVertex())
				{
					System.out.println("Vertex " + v.getName() + " Rules:");
					System.out.println(v.getRules());
				}

			}
		}

		public void cleanGraph()
		{
			List <Edge> edges = generalSDN.getEdges();
			for (Edge edge : edges) {
				if(edge.getFlow()>0)
					edge.setFlow(0);
			}
		}

		/**
		 * Build Default Graph according to topo String
		 * 
		 * @param topo
		 *            - arr of Strings contain topology 
		 * 
		 * @return DirectGraph
		 */
		private DirectGraph buildDefaultGraph(String[] topo)
		{
			DirectGraph dg = new DirectGraph();
			String[] myVertices =  topo[0].split(",");
			String[] bondedVertices = topo[1].split(",");
			String[] inerLinks = topo[2].split(",");
			String[] outLinks = topo[3].split(",");

			for (String v : myVertices) 
			{
				dg.addVertex(v);
				dg.getVertex(v).setMyVertex(true);
			}

			for (String v : bondedVertices) 
			{
				dg.addVertex(v);
				dg.getVertex(v).setMyVertex(false);

			}

			for (String l : inerLinks) 
			{
				String[] temp = l.split("-");
				dg.addEdge(temp[0], temp[1], Integer.valueOf(temp[2]), (short)2, (short)1);
				dg.addEdge(temp[1], temp[0], Integer.valueOf(temp[2]), (short)2, (short)1);
			}
			for (String l : outLinks) 
			{
				String[] temp = l.split("-");
				dg.addEdge(temp[0], temp[1], Integer.valueOf(temp[2]), (short)2, (short)1);
				dg.addEdge(temp[1], temp[0], Integer.valueOf(temp[2]), (short)2, (short)1);
			}

			return dg;

		}
		/**
		 * Calculate flow using DFS
		 * 
		 * @param dg
		 *            - DirectGraph to calculate on it.
		 * @param vertex
		 * 			  - Vertex to start the DFS on it.
		 * @return String
		 */
		private String getFlowDFS(DirectGraph dg, Vertex vertex)
		{
			String flow= "";
			Queue<Vertex> queue = new LinkedList<Vertex>();
			queue.add(vertex);
			Vertex vertexToInstall;
			Vertex destVertex = dg.getDestVertex();


			while(!queue.isEmpty())
			{
				vertexToInstall = queue.poll();
				for(Edge e : vertexToInstall.getOutgoingEdges())
				{
					if(vertexToInstall.getName() == destVertex.getName())
					{
						return flow;
					}
					if(e.getFlow()>0)
					{
						flow+= "-" + e.getTo().getName();
						queue.add(e.getTo());
					}
				}
			}

			return flow;
		}
		/**
		 * Calculate paths in a given DirectGraph
		 * 
		 * @param dg
		 *            - DirectGraph to calculate on it.
		 * @return String
		 */
		public String mincutSets(DirectGraph dg)
		{
			String mincutSets = "" ;

			for (Edge e : dg.getSourceVertex().getOutgoingEdges())
			{
				if(e.getFlow() > 0)
				{
					mincutSets += dg.getSourceVertex().getName() + "-" + e.getTo().getName();
					mincutSets += getFlowDFS(dg,e.getTo());
					mincutSets += ";";
				}
			}

			return mincutSets;
		}
		/**
		 * Build DirectGraph based on the known graph. add Vertices Sink&Source and edges to it.
		 * 
		 * @param set
		 *            - Set<String> to calculate on it.
		 * @return DirectGraph
		 */
		public DirectGraph buildMincutSetsGraph(Set<String> set)
		{
			Set<Vertex> notOurVertices = getNotOurVertices();
			DirectGraph dg = copyKnownGraph();
			dg.addVertex("Source");
			dg.getVertex("Source").setMyVertex(false);
			dg.addVertex("Sink");
			dg.getVertex("Sink").setMyVertex(false);

			for (Vertex vertex : notOurVertices)
			{
				if(set.contains(vertex.getName()))
					dg.addEdge(vertex.getName(), "Sink", Integer.MAX_VALUE, (short)0, (short)0);
				else
					dg.addEdge("Source",vertex.getName(), Integer.MAX_VALUE, (short)0, (short)0);
			}


			return dg;

		}
		/**
		 * Install rules on vertex according to Central Unit decisions
		 * 
		 * @param flowFromCU
		 *            - array of strings containing flows from Central Unit
		 *  @param g
		 *  		  - DirectGraph that mincut sets were calculate on it.           
		 * @return void
		 */
		public void installRules(String[] flowFromCU, DirectGraph g)
		{			
			int flowId;
			for (String flow : flowFromCU) 
			{
				String[] s = flow.split(","); 
				flowId = Integer.parseInt(s[1]);
				String[] temp = s[2].split("-");
				String to_switch = temp[1];
				String m_switch = temp[0];

				if(generalSDN.getVertex(m_switch).isMyVertex())
				{
					List<Edge> e = g.getVertex(m_switch).getIncomingEdges();
					for (Edge edge : e) 
					{
						if(edge.getFlow()>0 )
						{
							generalSDN.getVertex(m_switch).addRule(edge.getFrom().getName(), to_switch, flowId);
						}
					}
				}
			}

		}
		/**
		 * Getting the network switches and return a set of their powerset
		 *            
		 * @return Set<Set<String>>
		 */
		public Set<Set<String>> getPowerSetOfVerticies()
		{
			ConcurrentHashMap<String, Vertex> v = generalSDN.getVerticies();
			Set<String> vertexSet = new HashSet<String>();
			for (Vertex ver : v.values()) 
			{
				if(!ver.isMyVertex())
					vertexSet.add(ver.getName());
			}

			Set<Set<String>> tempPowerSet =  Sets.powerSet(vertexSet);
			Set<Set<String>> retPowerSet = new HashSet<Set<String>>();
			for (Set<String> set : tempPowerSet) 
			{
				if(!set.isEmpty() && !set.containsAll(vertexSet))
					retPowerSet.add(set);
			}
			return retPowerSet;
		}
		/**
		 * Getting the network vertices and return a set only of the vertices that not under this network responsibility
		 *            
		 * @return Set<Vertex>
		 */
		public Set<Vertex> getNotOurVertices()
		{
			ConcurrentHashMap<String, Vertex> v = generalSDN.getVerticies();
			Set<Vertex> vertexSet = new HashSet<Vertex>();
			for (Vertex ver : v.values()) 
			{
				if(!ver.isMyVertex())
				{
					vertexSet.add(ver);
				}
			}
			return vertexSet;
		}
		/**
		 * create a copy of the known graph 
		 *            
		 * @return DirectGraph
		 */
		public DirectGraph copyKnownGraph()
		{
			ConcurrentHashMap<String, Vertex> v = generalSDN.getVerticies();
			List<Edge> e = generalSDN.getEdges();
			DirectGraph knownGraph = new DirectGraph();

			for (Vertex ver : v.values())
			{
				knownGraph.addVertex(ver.getName());
				knownGraph.getVertex(ver.getName()).setMyVertex(ver.isMyVertex());
			}
			for (Edge edge : e) {
				knownGraph.addEdge(edge.getFrom().getName(), edge.getTo().getName(), edge.getCapacity(), edge.getSourcePort(), edge.getDestPort());
			}

			return knownGraph;
		}
	}
	
	private static class GraphVisual implements Runnable
	{
		private DirectGraph dg;
		private String description;
		private String fileName;


		GraphVisual(DirectGraph dg, String description, String header)
		{
			this.dg = copyGraph(dg);
			this.description = description;
			this.fileName = header;

		}
		@Override
		public void run() 
		{
			String graphToFile = createGraphvizGraph();
			String pathToFile = "/home/floodlight1/Desktop/" + fileName +".gv";
			try{
				PrintWriter writer = new PrintWriter(pathToFile, "UTF-8");
				writer.print(graphToFile);
				writer.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			System.out.println(graphToFile);

		}
		public DirectGraph copyGraph(DirectGraph g)
		{
			ConcurrentHashMap<String, Vertex> v = g.getVerticies();
			List<Edge> e = g.getEdges();
			DirectGraph newGraph = new DirectGraph();
			for (Vertex ver : v.values())
			{
					newGraph.addVertex(ver.getName());
					newGraph.getVertex(ver.getName()).setMyVertex(ver.isMyVertex());
					String temp = ver.getRules();

					if(!temp.isEmpty())
					{
						String[] rule = temp.split(" ");
						newGraph.getVertex(ver.getName()).addRule(rule[3], rule[5],Integer.valueOf(rule[1]));
					}
					
			}

			for (Edge edge : e) 
			{
					newGraph.addEdge(edge.getFrom().getName(), edge.getTo().getName(), edge.getCapacity(), edge.getSourcePort(), edge.getDestPort());
					if(edge.getFlow()>0)
						newGraph.getEdge(newGraph.getVertex(edge.getFrom().getName()), newGraph.getVertex(edge.getTo().getName())).setFlow(edge.getFlow());
			}

			return newGraph;
		}
		
		public String createGraphvizGraph()
		{
			String graphToFile = "digraph G {\n";
			graphToFile += fileName + " [label = \"" + description +"\",shape = box,style=filled,color=purple]\n";
			ConcurrentHashMap<String, Vertex> vertices = dg.getVerticies();
			for (Vertex v : vertices.values()) 
			{
				if(v.isMyVertex())
				{
					graphToFile += v.getName() + " [label = \"" + v.getName() + "\\nRuls:\\n"+ v.getRules() +"\" " + "style=filled,color=green]\n";
				}
					
			}
			List<Edge> edges = dg.getEdges();
			String edgeswithoutflow = "";
			String edgeswithflow = "";
			for (Edge edge : edges) {
				String from = edge.getFrom().getName();
				String to = edge.getTo().getName();
				String toadd = from + "->" + to + ";\n";
				if(toadd.contains("Sink") || toadd.contains("Source"))
					continue;
				else
				{
					if(edge.getFlow()>0)
						edgeswithflow += from + "->" + to + ";\n";
					else
						edgeswithoutflow += from + "->" + to + ";\n";
				}
			}

			graphToFile += edgeswithoutflow + "edge [color=red]\n" + edgeswithflow + "}";
			
			return graphToFile;
		}
		
	}
}


