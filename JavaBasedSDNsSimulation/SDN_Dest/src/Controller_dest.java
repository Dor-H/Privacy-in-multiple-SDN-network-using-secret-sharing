
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;



/**
 * 
 */

/**
 * @author root
 *
 */
public class Controller_dest 
{

	/**
	 * @param args
	 */
	public static void main(String[] args) 
	{

		String[] topo = {
				"s10,s11,s12,s13",
				"s16,s17,s18",
				"s10-s13-1,s11-s13-1,s12-s13-1",
				"s17-s10-1,s16-s11-1,s18-s12-1"
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
		private DirectGraph destSDN;

		MultiThreadServer(String serverIp, String[] topo)
		{
			this.destSDN = buildDefaultGraph(topo);
			new Thread(new GraphVisual(destSDN, "Destination Controller \\n Default Graph", "DefaultGraph")).start();
		}
		@Override
		public void run() {

			/**
			 * Step#

				Step1			Wait for CU.
				Step2			read CU msg.
				Step3			calculate mincut sets.
				Step4			connect to CU and send mincut sets.
				Step5			get answer for flows roles.
				Step6			install roles.
			 **/
			//********************************************************************************
			//********************************************************************************
			
			try {
				serverSocket = new ServerSocket(12343);
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
			DirectGraph mincutSets = buildMincutSetsGraph(destSDN);

			int mincut = mincutSets.dinicsMaxFlow(mincutSets.getVertex(srcSwitch), mincutSets.getVertex("s13"));

			String mincutSet = mincutSets(mincutSets);

			System.out.println("mincutSet: " + mincutSet);
			new Thread(new GraphVisual(destSDN, "Destination Controller \\n Best Mincut Set", "bestMincutGraph")).start();
			
			//********************************************************************************
			//********************************************************************************
			String answerForFlowRoles = "";
			DataOutputStream out;
			try {
				out = new DataOutputStream(server.getOutputStream());
				out.writeUTF(mincutSet + "@" + mincut);
				
				DataInputStream in = new DataInputStream(server.getInputStream());
				answerForFlowRoles = in.readUTF(in);
				System.out.println("MSG From Central Unit: " + answerForFlowRoles);
				server.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			
			//********************************************************************************
			//********************************************************************************

			String[] flowFromCU = answerForFlowRoles.split(";");
			installRules(flowFromCU,mincutSets);
			
			cleanGraph();
			
			for (Vertex v : destSDN.getVerticies().values()) {
				if(v.isMyVertex())
				{
					System.out.println("Vertex " + v.getName() + " Rules:");
					System.out.println(v.getRules());
				}
	
			}
			new Thread(new GraphVisual(destSDN, "Destination Controller \\n Final Graph", "finalGraph")).start();
			
		}
		
		/**
		 * Clean the graph flow
		 * 
		 * @return void
		 */
		public void cleanGraph()
		{
			List <Edge> edges = destSDN.getEdges();
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
		 * Build Default Graph according to topo String
		 * 
		 * @param topo
		 *            - arr of Strings contain topology 
		 * 
		 * @return DirectGraph
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
					if(vertexToInstall == destVertex)
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

			return "problem!!!!!!!!!! " + flow;
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
		 * Build DirectGraph based on the known graph. add Vertex Sink and edges to it.
		 * 
		 * @param knownGraph
		 *            - DirectGraph to calculate on it.
		 * @return DirectGraph
		 */
		public DirectGraph buildMincutSetsGraph(DirectGraph knownGraph)
		{
			DirectGraph dg = knownGraph;

			dg.addVertex("Source");
			for (Vertex v : dg.getVerticies().values()) 
			{
				if(!v.isMyVertex())
					dg.addEdge("Source",v.getName(), 1, (short)0, (short)0);
			}

			return dg;
		}
		/**
		 * Install Rules on the vertex according to CU request
		 * 
		 * @param flowFromCU
		 *            - String array containing paths.
		 * @param g
		 *            - DirectGraph that the mincut sets were calculate on.
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
				String from_switch = temp[0];

				if(destSDN.getVertex(from_switch).isMyVertex())
				{
					List<Edge> e = g.getVertex(from_switch).getIncomingEdges();
					for (Edge edge : e) 
					{
						if(edge.getFlow()>0)
						{	
							destSDN.getVertex(from_switch).addRule(edge.getFrom().getName(), to_switch, flowId);
						}
					}
				}
			}
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
			for (Vertex v : vertices.values()) {
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