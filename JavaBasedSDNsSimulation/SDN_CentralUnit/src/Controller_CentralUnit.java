

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.google.common.base.CaseFormat;
import com.google.common.collect.Sets;

import org.graphstream.graph.*;
import org.graphstream.graph.implementations.*;


/**
 * 
 */

/**
 * @author root
 *
 */
public class Controller_CentralUnit
{

	/**
	 * @param args
	 */
	public static void main(String[] args) 
	{

		String[] topo = {
				"s14,s15,s16,s17,s18",
				"s2,s3,s4,s5",
				"s6,s7,s8,s9",
				"s10,s11,s12",
				"s2-s14-1,s3-s15-1,s4-s16-1,s5-s17-1",
				"s6-s14-1,s7-s15-1,s8-s16-1,s9-s18-1",
				"s17-s10-1,s16-s11-1,s18-s12-1"
		};

		String srcIp = "192.168.56.102";
		String generalIp = "192.168.56.103";
		String destIp = "192.168.56.104";

		(new Thread(new MultiThreadServer(srcIp,generalIp,destIp,topo))).start();


	}



	private static class MultiThreadServer implements Runnable 
	{
		private String srcIp;
		private String destIp;
		private String generalIp;
		private Socket srcSocket,genSocket,destSocket;
		private final Lock lock = new ReentrantLock();
		private DirectGraph centralUnitSDN;
		private ServerSocket serverSocket;
		private ConcurrentHashMap<String, String> vertexSDN;
		private GraphVisual gv;

		MultiThreadServer(String srcIp, String generalIp, String destIp, String[] topo)
		{
			this.centralUnitSDN = buildDefaultGraph(topo);
			this.srcIp = srcIp;
			this.generalIp = generalIp;
			this.destIp = destIp;
			this.vertexSDN = sdnBelong();
			new Thread(new GraphVisual(centralUnitSDN,"Central Unit \\n Default Graph","DefaultGraph")).start();
		}
		@Override
		public void run() {

			/**
			 * Step#

				Step1			Wait for src Controller.
				Step2			read src Controller msg.
				Step3			Send mincut sets req
				Step4			calculate mincut sets.
				Step5			send answers for flows roles.
				Step6			install roles.

			 **/

			//*********************************Open Socket and wait for src Controller*************************************************************			
			try {
				serverSocket = new ServerSocket(12341);
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			String msgFromSrc = "";
			String genMincutSets = "";
			String destMincutSets = "";
			String msgFromdest = "";
			String msgFromGen = "";

			try {
				System.out.println("Waiting for Source Controller on port " + 
						serverSocket.getLocalPort() + "...");
				srcSocket = serverSocket.accept();

				System.out.println("Just connected to " + srcSocket.getRemoteSocketAddress());
				DataInputStream in = new DataInputStream(srcSocket.getInputStream());
				msgFromSrc = in.readUTF(in);
				System.out.println("MSG From Source Controller: " + msgFromSrc);

			}catch(SocketTimeoutException s) {
				System.out.println("Socket timed out!");
			}catch(IOException e) {
				e.printStackTrace();
			}
			//*********************************Connecting to General SDN*************************************************************			
			try {
				System.out.println("Connecting to " + generalIp + " on port " + 12342);
				genSocket = new Socket(generalIp, 12342);

				System.out.println("Just connected to " + genSocket.getRemoteSocketAddress());
				OutputStream outToServer = genSocket.getOutputStream();
				DataOutputStream out = new DataOutputStream(outToServer);

				out.writeUTF("You are general SDN, give me you mincut sets " + genSocket.getLocalSocketAddress());
				InputStream inFromServer = genSocket.getInputStream();
				DataInputStream in = new DataInputStream(inFromServer);
				msgFromGen = in.readUTF(in);
				System.out.println("general SDN MSG " + genMincutSets);

			}catch(IOException e) {
				e.printStackTrace();
			}


			//**************************************Connecting to dest SDN********************************************************			
			try {
				System.out.println("Connecting to " + generalIp + " on port " + 12343);
				destSocket = new Socket(destIp, 12343);

				System.out.println("Just connected to " + destSocket.getRemoteSocketAddress());
				OutputStream outToServer = destSocket.getOutputStream();
				DataOutputStream out = new DataOutputStream(outToServer);

				out.writeUTF("You are destination SDN, give me you mincut sets " + destSocket.getLocalSocketAddress());
				InputStream inFromServer = destSocket.getInputStream();
				DataInputStream in = new DataInputStream(inFromServer);
				msgFromdest = in.readUTF(in);
				System.out.println("dest SDN MSG " + destMincutSets);
				//server.close();
			}catch(IOException e) {
				e.printStackTrace();
			}


			//********************************Analyze mincuts sets msgs and calculate ****************************************************
			String[] srcmsg = msgFromSrc.split("@");
			int srcMincut = Integer.valueOf(srcmsg[1]);
			String[] msgfromSrcWithData = srcmsg[0].split(" ");
			String source = msgfromSrcWithData[0];
			String sink = msgfromSrcWithData[1];

			String[] genmsg = msgFromGen.split("@");
			int genMincut = Integer.valueOf(genmsg[1]);
			genMincutSets = genmsg[0];

			String[] destmsg = msgFromdest.split("@");
			int destMincut = Integer.valueOf(destmsg[1]);
			destMincutSets = destmsg[0];

			centralUnitSDN.addVertex(source);
			centralUnitSDN.addVertex(sink);

			String srcMincutSets = msgfromSrcWithData[2];				
			String[] srcMincutOp = srcMincutSets.split(" ");
			String[] genMincutOp = genMincutSets.split(" ");
			String[] destMincutOp = destMincutSets.split(" ");

			DirectGraph bestMincutGraph = buildCentralUnitCleanGraph();

			int[] mincutMaxOptions = {1,1,1};
			int[] mincutOptions = {1,1,1};
			int mincutMax = 0;

			for (String destOp : destMincutOp) 
			{
				String[] destLinks = splitFlow(destOp);
				for (String genOp : genMincutOp) 
				{
					String[] genLinks = splitFlow(genOp);
					for (String srcOp : srcMincutOp) 
					{
						String[] srcLinks = splitFlow(srcOp);
						DirectGraph temp = buildMincutGraph(destLinks,genLinks,srcLinks);
						int mincutTemp = temp.dinicsMaxFlow(temp.getVertex(source), temp.getVertex(sink));
						if (mincutTemp>mincutMax)
						{
							mincutMax = mincutTemp;
							for(int j = 0 ; j < mincutMaxOptions.length; j++)
								mincutMaxOptions[j]=mincutOptions[j];
							bestMincutGraph = temp;
						}

						mincutMaxOptions[2]++;
					}
					mincutMaxOptions[1]++;
				}
				mincutMaxOptions[0]++;
			}
			superNode(bestMincutGraph,srcMincut,genMincut,destMincut);
			String paths = mincutSets(bestMincutGraph);
			String[] path = paths.split(";");
			new Thread(new GraphVisual(bestMincutGraph,"Central Unit \\n The Graph with the best mincut","bestMincutGraph")).start();
			System.out.println( "Paths: \n" + paths);

			String sdnSrcflow = "";
			String sdnGenflow = "";
			String sdnDestflow = "";
			String sdnCUflow = "";

			for(int j = 0; j<path.length ; j++)
			{
				String[] temp = path[j].split("-");
				for (int k = 1; k < temp.length; k++) 
				{
					if(centralUnitSDN.getVertex(temp[k-1]).isMyVertex() || centralUnitSDN.getVertex(temp[k]).isMyVertex())
					{
						sdnCUflow += "flow" + j + "," + j + "," + temp[k-1] + "-" + temp[k] + ";";
					}

					if(centralUnitSDN.getVertex(temp[k-1]).getSdnVertex() == "src" || centralUnitSDN.getVertex(temp[k]).getSdnVertex() == "src")
					{
						sdnSrcflow += "flow" + j + "," + j + "," + temp[k-1] + "-" + temp[k] + ";";
					}
					else if(centralUnitSDN.getVertex(temp[k-1]).getSdnVertex() == "general" || centralUnitSDN.getVertex(temp[k]).getSdnVertex() == "general")
					{
						sdnGenflow += "flow" + j + "," + j + "," + temp[k-1] + "-" + temp[k] + ";";
					}
					else if(centralUnitSDN.getVertex(temp[k-1]).getSdnVertex() == "dest" || centralUnitSDN.getVertex(temp[k]).getSdnVertex() == "dest")
					{
						sdnDestflow += "flow" + j + "," + j + "," + temp[k-1] + "-" + temp[k] + ";";
					}
				}
			}

			System.out.println("sdnSrcflow " + sdnSrcflow + "\nsdnGenflow " + sdnGenflow + "\nsdnDestflow " + sdnDestflow );


			//**********************Send rules and close sockets******************************************
			sendRules(srcSocket,sdnSrcflow);
			sendRules(genSocket, sdnGenflow);
			sendRules(destSocket,sdnDestflow);

			//*************************Install CU rules*******************************************

			String[] flowsToinstall = sdnCUflow.split(";");
			installRules(flowsToinstall, bestMincutGraph);
			new Thread(new GraphVisual(centralUnitSDN,"Central Unit \\n The final graph including the rules","finalGraph")).start();
			for (Vertex v : centralUnitSDN.getVerticies().values()) {
				if(v.isMyVertex())
				{
					System.out.println("Vertex " + v.getName() + " Rules:");
					System.out.println(v.getRules());
				}
			}
		}


		private void superNode(DirectGraph dg, int srcMincut, int genMincut, int destMincut)
		{
			DirectGraph superNodegraph =  buildCentralUnitCleanGraph();

			//adding Super Nodes for each SDN
			superNodegraph.addVertex("SrcSuperNodeIn");
			superNodegraph.addVertex("SrcSuperNodeOut");
			superNodegraph.addVertex("GenaralSuperNodeIn");
			superNodegraph.addVertex("GeneralSuperNodeOut");
			superNodegraph.addVertex("DestinationSuperNodeIn");
			superNodegraph.addVertex("DestinationSuperNodeOut");
			superNodegraph.addVertex("S");
			superNodegraph.addVertex("t");
			//adding edges with capacity as their mincut
			superNodegraph.addEdge("SrcSuperNodeIn", "SrcSuperNodeOut", srcMincut, (short)2, (short)1);
			superNodegraph.addEdge("GenaralSuperNodeIn", "GeneralSuperNodeOut", genMincut, (short)2, (short)1);
			superNodegraph.addEdge("DestinationSuperNodeIn", "DestinationSuperNodeOut", destMincut, (short)2, (short)1);
			superNodegraph.addEdge("S", "SrcSuperNodeIn", Integer.MAX_VALUE, (short)2, (short)1);
			superNodegraph.addEdge("DestinationSuperNodeOut", "t", Integer.MAX_VALUE, (short)2, (short)1);
			List<Edge> sne = superNodegraph.getEdges();
			for (Edge edge : sne) {
				edge.setFlow(1);
			}
			List<Edge> e = dg.getEdges();

			for (Edge edge : e) {
				String from = edge.getFrom().getName();
				String to = edge.getTo().getName();

				if(vertexSDN.get(edge.getFrom().getName())=="src")
					from="SrcSuperNodeOut";
				else if(vertexSDN.get(edge.getFrom().getName())=="general")
					from = "GeneralSuperNodeOut";
				else if(vertexSDN.get(edge.getFrom().getName())=="dest")
					from = "DestinationSuperNodeOut";


				if(vertexSDN.get(edge.getTo().getName()) == "src")
					to = "SrcSuperNodeIn";
				else if(vertexSDN.get(edge.getTo().getName()) == "general")
					to = "GenaralSuperNodeIn";
				else if(vertexSDN.get(edge.getTo().getName()) == "dest")
					to = "DestinationSuperNodeIn";
				if(!from.startsWith(to.substring(0, 3)))
				{
					superNodegraph.addEdge(from, to, 1, (short)2, (short)1);
					superNodegraph.getEdge(superNodegraph.getVertex(from), superNodegraph.getVertex(to)).setFlow(edge.getFlow());			
				}
			}

			new Thread(new GraphVisual(superNodegraph, "Central Unit\\n Super Node Graph " , "superNodes")).start();
		}

		public ConcurrentHashMap<String, String> sdnBelong()
		{
			ConcurrentHashMap<String, String> vertexSDN = new ConcurrentHashMap<String, String>();
			vertexSDN.put("s1", "src");
			vertexSDN.put("s2", "src");
			vertexSDN.put("s3", "src");
			vertexSDN.put("s4", "src");
			vertexSDN.put("s5", "src");		
			vertexSDN.put("s6", "general");
			vertexSDN.put("s7", "general");
			vertexSDN.put("s8", "general");
			vertexSDN.put("s9", "general");
			vertexSDN.put("s10", "dest");
			vertexSDN.put("s11", "dest");
			vertexSDN.put("s12", "dest");
			vertexSDN.put("s13", "dest");
			return vertexSDN;
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
			String[] sdnSrcVertices = topo[1].split(",");
			String[] sdnGenVertices = topo[2].split(",");
			String[] sdnDestVertices = topo[3].split(",");
			String[] sdnSrcLinks = topo[4].split(",");
			String[] sdnGenLinks = topo[5].split(",");
			String[] sdnDestLinks = topo[6].split(",");

			for (String v : myVertices) 
			{
				dg.addVertex(v);
				dg.getVertex(v).setMyVertex(true);
			}

			for (String v : sdnSrcVertices) 
			{
				dg.addVertex(v);
				dg.getVertex(v).setMyVertex(false);
				dg.getVertex(v).setSdnVertex("src");
			}

			for (String v : sdnGenVertices) 
			{
				dg.addVertex(v);
				dg.getVertex(v).setMyVertex(false);
				dg.getVertex(v).setSdnVertex("general");
			}

			for (String v : sdnDestVertices) 
			{
				dg.addVertex(v);
				dg.getVertex(v).setMyVertex(false);
				dg.getVertex(v).setSdnVertex("dest");
			}


			for (String l : sdnSrcLinks) 
			{
				String[] temp = l.split("-");
				dg.addEdge(temp[0], temp[1], Integer.valueOf(temp[2]), (short)2, (short)1);
				dg.addEdge(temp[1], temp[0], Integer.valueOf(temp[2]), (short)2, (short)1);
			}
			for (String l : sdnGenLinks) 
			{
				String[] temp = l.split("-");
				dg.addEdge(temp[0], temp[1], Integer.valueOf(temp[2]), (short)2, (short)1);
				dg.addEdge(temp[1], temp[0], Integer.valueOf(temp[2]), (short)2, (short)1);
			}
			for (String l : sdnDestLinks) 
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

				if(centralUnitSDN.getVertex(m_switch).isMyVertex())
				{
					List<Edge> e = g.getVertex(m_switch).getIncomingEdges();
					for (Edge edge : e) 
					{
						if(edge.getFlow()>0 )
						{
							centralUnitSDN.getVertex(m_switch).addRule(edge.getFrom().getName(), to_switch, flowId);
						}
					}
				}
			}

		}


		/**Build a Graph only with edges between CU vertices
		 * 
		 * @return DirectGraph*/
		public DirectGraph buildCentralUnitCleanGraph()
		{
			ConcurrentHashMap<String, Vertex> v = centralUnitSDN.getVerticies();
			List<Edge> e = centralUnitSDN.getEdges();
			DirectGraph cleanGraph = new DirectGraph();
			for (Vertex ver : v.values())
			{
				cleanGraph.addVertex(ver.getName());
				cleanGraph.getVertex(ver.getName()).setMyVertex(ver.isMyVertex());			
			}

			for (Edge edge : e) 
			{
				if(edge.getFrom().isMyVertex() && edge.getTo().isMyVertex())
					cleanGraph.addEdge(edge.getFrom().getName(), edge.getTo().getName(), edge.getCapacity(), edge.getSourcePort(), edge.getDestPort());
			}

			return cleanGraph;
		}

		public String[] splitFlow(String flow)
		{
			String links = "";
			String[] path = flow.split(";");
			for (String p : path) 
			{
				String[] vertices = p.split("-");

				int size = 0;
				for (String v : vertices) //count vertices size without source and sink
					if(v!="Sink" && v!="Source")
						size++;

				String[] ver = new String[size];
				int index=0;
				for (String v : vertices) //create list without source and sink
				{
					if(v!="Sink" && v!="Source")
					{
						ver[index] = v;
						index++;
					}
				}
				for (int i = 1 ; i < ver.length ; i++) 
				{
					links += ver[i-1] + "-" + ver[i] + " ";

				}
			}

			String[] ret = links.split(" ");
			return ret;
		}

		public DirectGraph buildMincutGraph(String[] destLinks,String[] genLinks,String[] srcLinks)
		{
			DirectGraph dg = buildCentralUnitCleanGraph();

			for (String s : destLinks) {
				String[] vertices = s.split("-");
				dg.addEdge(vertices[0], vertices[1], 1, (short)0, (short)0);			
			}

			for (String s : genLinks) {
				String[] vertices = s.split("-");
				dg.addEdge(vertices[0], vertices[1], 1, (short)0, (short)0);			
			}

			for (String s : srcLinks) {
				String[] vertices = s.split("-");
				dg.addEdge(vertices[0], vertices[1], 1, (short)0, (short)0);			
			}

			return dg;
		}

		public void sendRules(Socket socket, String rules)
		{
			DataOutputStream out;
			try {
				out = new DataOutputStream(socket.getOutputStream());
				out.writeUTF(rules);
				socket.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
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
			this.dg = dg;
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
				if(ver.getName()== "Sink" || ver.getName() == "Source" || ver.getName() == "Sink" || ver.getName() == "Source")
				{
					newGraph.addVertex(ver.getName());
					newGraph.getVertex(ver.getName()).setMyVertex(ver.isMyVertex());
				}
			}

			for (Edge edge : e) 
			{
				if(edge.getFrom().getName() != "Sink" &&  edge.getFrom().getName() != "Source" && edge.getTo().getName() != "Sink" && edge.getTo().getName() != "Source")
					newGraph.addEdge(edge.getFrom().getName(), edge.getTo().getName(), edge.getCapacity(), edge.getSourcePort(), edge.getDestPort());
			}

			return newGraph;
		}

		public String createGraphvizGraph()
		{
			String graphToFile = "digraph G {\n";
			graphToFile += fileName + " [label = \"" + description +"\",shape = box,style=filled,color=purple]\n";
			ConcurrentHashMap<String, Vertex> vertices = dg.getVerticies();
			for (Vertex v : vertices.values()) {
				if(v.getName() == "Sink" || v.getName() == "Source")
				{
					List<Edge> l = v.getAllEdges();
					for (Edge edge : l) {
						dg.removeEdge(edge.getFrom(), edge.getTo());
					}
					dg.removeVertex(v);
				}
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

