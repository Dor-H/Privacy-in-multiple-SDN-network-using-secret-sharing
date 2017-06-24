import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
public class Controller_src 
{

	/**
	 * @param args
	 */
	public static void main(String[] args) 
	{

		String[] topo = {
				"s1,s2,s3,s4,s5",
				"s14,s15,s16,s17",
				"s1-s2-1,s1-s3-1,s1-s4-1,s1-s5-1",
				"s2-s14-1,s3-s15-1,s4-s16-1,s5-s17-1"
		};
		String serverIp = "192.168.56.105";

		(new Thread(new MultiThreadServer(serverIp,topo))).start();

	}


	private static class MultiThreadServer implements Runnable 
	{
		private String srcSwitch;
		private String destSwitch;
		private ServerSocket serverSocket;
		private Socket csocket,server;
		private final Lock lock = new ReentrantLock();
		private DirectGraph srcSDN;
		private String centralUnitIp;

		MultiThreadServer(String serverIp, String[] topo)
		{
			this.srcSDN = buildDefaultGraph(topo);
			this.centralUnitIp = serverIp;
			new Thread(new GraphVisual(srcSDN, "Source SDN \\n Default Graph", "DefaultGraph")).start();
		}
		@Override
		public void run() {

			/**
			 * Step#

				Step1			Wait for client.
				Step2			read client msg.
				Step3			calculate mincut sets.
				Step4			connect to CU and send req with parameters.
				Step5			get answer for flows roles.
				Step6			install roles.
			 **/
			//************************************** Handle with Client *************************************************************************

			try {
				serverSocket = new ServerSocket(12345);
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			String msgFromHost = "";


			try {
				System.out.println("Waiting for client on port " + 
						serverSocket.getLocalPort() + "...");
				csocket = serverSocket.accept();

				System.out.println("Just connected to " + csocket.getRemoteSocketAddress());
				DataInputStream in = new DataInputStream(csocket.getInputStream());
				msgFromHost = in.readLine();
				//System.out.println(in.readUTF());
				DataOutputStream out = new DataOutputStream(csocket.getOutputStream());
				out.writeUTF("Thank you for connecting to " + csocket.getLocalSocketAddress()
				+ "\nGoodbye!");
				csocket.close();

			}catch(SocketTimeoutException s) {
				System.out.println("Socket timed out!");
			}catch(IOException e) {
				e.printStackTrace();
			}


			String[] msgFromHostParameters = msgFromHost.split(" ");
			srcSwitch = msgFromHostParameters[0];
			destSwitch = msgFromHostParameters[1];


			//*********************************************Calculate mincut sets******************************************************************
			DirectGraph mincutSets = buildMincutSetsGraph(srcSDN);

			int mincut = mincutSets.dinicsMaxFlow(mincutSets.getVertex(srcSwitch), mincutSets.getVertex("Sink"));


			String mincutSet = mincutSets(mincutSets);

			System.out.println("mincutSet: " + mincutSet);
			
			new Thread(new GraphVisual(srcSDN, "Source SDN \\n Best Mincut Graph", "bestMincutGraph")).start();
			
			//*****************************************Connect to CU**********************************************************************			
			String answerForFlowRoles = "";
			try {
				System.out.println("Connecting to " + centralUnitIp + " on port " + 12341);
				server = new Socket(centralUnitIp, 12341);

				System.out.println("Just connected to " + server.getRemoteSocketAddress());
				OutputStream outToServer = server.getOutputStream();
				DataOutputStream out = new DataOutputStream(outToServer);

				out.writeUTF(srcSwitch + " " + destSwitch + " " +mincutSet + "@" + mincut);
				InputStream inFromServer = server.getInputStream();
				DataInputStream in = new DataInputStream(inFromServer);
				answerForFlowRoles = in.readUTF(in);
				System.out.println("Server says " + answerForFlowRoles);
				server.close();
			}catch(IOException e) {
				e.printStackTrace();
			}



			//*****************************************	Install rules*****************************************	*****************************************	*****************************************	

			String[] flowFromCU = answerForFlowRoles.split(";");
			installRules(flowFromCU,mincutSets);
			cleanGraph();
			for (Vertex v : srcSDN.getVerticies().values()) 
			{
				if(v.isMyVertex())
				{
					System.out.println("Vertex " + v.getName() + " Rules:");
					System.out.println(v.getRules());
				}
			}
			
			new Thread(new GraphVisual(srcSDN, "Source SDN \\n Final Graph with Rules", "finalGraph")).start();
			
		}
		/**
 		* Clean the graph Flow
 		* 
 		* @return void
		*/
		private void cleanGraph()
		{
			List<Edge> edges = srcSDN.getEdges();
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
			//Topo[] = [vertices: sdn switches responsibility][vertices: bonded switches][Edges:iner links][Edges: out links]
			//String[] topo = {"s1,s2,s3,s4,s5","s14,s15,s16,s17","s1-s2-1,s1-s3-1,s1-s4-1,s1-s5-1", "s2-s14-1,s3-s15-1,s4-s16-1,s5-s17-1"};
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
					//mincutSets += "-";
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

			dg.addVertex("Sink");
			for (Vertex v : dg.getVerticies().values()) 
			{
				if(!v.isMyVertex())
					dg.addEdge(v.getName(), "Sink", 1, (short)0, (short)0);
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
				String m_switch = temp[0];


				List<Edge> e = g.getVertex(m_switch).getIncomingEdges();
				for (Edge edge : e) 
				{
					if(edge.getFlow()>0)
					{
						srcSDN.getVertex(m_switch).addRule(edge.getFrom().getName(), to_switch, flowId);
					}
				}
			}

		}
	}
	
	private static class GraphVisual implements Runnable
	{
		//private ServerSocket serverSocket;
		private DirectGraph dg;
		private String description;
		private String fileName;
		//private Graph graph;


		GraphVisual(DirectGraph dg, String description, String header)
		{
			this.dg = copyGraph(dg);
			this.description = description;
			this.fileName = header;
			//graph = new MultiGraph(header);
			//graph.display( false ); 
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
