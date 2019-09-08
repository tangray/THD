import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//
//
// 
// 新节点加入时需要知道一个已经在网络中的节点的IP和监听端口
//
public class NodeDHT implements Runnable 
{

    private int ID;
    private Socket connection;
    private static ServerSocket serverSocket = null; 
    private static Node me, pred;
    private static int m;
    private static int numDHT;
    private static int busy;
    private static Object object = new Object();
    private static FingerTable[] finger;    
    private static String knownhostIP;
    private static String knownhostport;
    private static String myIP;
    private static String myport;
    private static Vector<Node> nodeList = new Vector<Node>();
    private static List<Identification> IdentificationList = new ArrayList<Identification>();
    private static HashMap<Identification, String> IdentMap= new HashMap<Identification, String>();

    public NodeDHT(Socket s, int i) {
        this.connection = s;
        this.ID = i;
    }
    
    public static void main(String args[]) throws Exception
    {
        //第一个节点和其他节点的加入，通过参数的位数区分（修改）
        //args参数为两位:[当前节点监听端口] [numNodes]=>说明是第一个加入的节点 args[0] args[1]
        //args参数为三位:[已知节点IP] [已知节点监听端口] [当前节点监听端口] [numNodes]=>说明不是第一个加入的节点 args[0]  args[1]  args[2]  args[3]
        if (args.length==2){
        	System.out.println(" *********************************启动DHT网络*************************************");
        	myport=args[0];

            int maxNumNodes = Integer.parseInt(args[1]);
            m = (int) Math.ceil(Math.log(maxNumNodes) / Math.log(2));
            finger = new FingerTable[m+1];
            numDHT = (int)Math.pow(2,m);
         
            InetAddress mIP = InetAddress.getLocalHost();
            myIP=mIP.getHostAddress();
            System.out.println("本节点IP地址: " + myIP );

            int initInfo = getFisrtNodeInfo(myIP,myport);
            me = new Node(initInfo,myIP,myport);
            pred=me;
            System.out.println("节点ID:"+me.getID() + "   前继节点ID:" +pred.getID());

            Socket temp = null;
            Runnable runnable = new NodeDHT(temp,0);
            Thread thread = new Thread(runnable);
            thread.start();
            //节点退出线程
            Runnable exitRunnable = new NodeDHT(temp,-2);
            Thread exitThread = new Thread(exitRunnable);
            exitThread.start();
            
            int count = 1;
            int port = Integer.parseInt(args[0]);
            try {
                   serverSocket = new ServerSocket( port );
                } catch (IOException e) {
                   System.out.println("[系统提示]:"+"无法监听端口 - " + port);
                   System.exit(-1);
                }
            
            while (true) {
                   Socket newCon = serverSocket.accept();
                   Runnable runnable2 = new NodeDHT(newCon,count++);
                   Thread t = new Thread(runnable2);
                   t.start();
            }
        }     
        else if(args.length==4){
        	System.out.println(" *********************************启动DHT网络*************************************");
        	knownhostIP=args[0];
        	knownhostport=args[1];
        	myport=args[2];
        	int maxNumNodes = Integer.parseInt(args[3]);
            m = (int) Math.ceil(Math.log(maxNumNodes) / Math.log(2));
            finger = new FingerTable[m+1];
            numDHT = (int)Math.pow(2,m);
             
            InetAddress mIP = InetAddress.getLocalHost();
            myIP=mIP.getHostAddress(); 
            System.out.println("本节点IP地址: " + myIP);

            int initInfo = getNodeInfo(myIP,myport);
            me = new Node(initInfo,myIP,myport);

            String result=makeConnection(knownhostIP, knownhostport, "findPred/"+initInfo);
            String[] tokens = result.split("/");
            pred = new Node(Integer.parseInt(tokens[0]),tokens[1],tokens[2]);
            System.out.println("本节点 ID:"+me.getID() + "   前继节点 ID:" +pred.getID());

            Socket temp = null;
            Runnable runnable = new NodeDHT(temp,-1);
            Thread thread = new Thread(runnable);
            thread.start();
            //节点退出线程
            Runnable exitRunnable = new NodeDHT(temp,-2);
            Thread exitThread = new Thread(exitRunnable);
            exitThread.start();
            
            int count = 1;
            int port = Integer.parseInt(myport);
            try {
                   serverSocket = new ServerSocket( port );
                } catch (IOException e) {
                   System.out.println("无法监听端口 - " + port);
                   System.exit(-1);
                }
            
            while (true) {
                   Socket newCon = serverSocket.accept();
                   Runnable runnable2 = new NodeDHT(newCon,count++);
                   Thread t = new Thread(runnable2);
                   t.start();
            }
        }
        else
        {
            System.out.println("Syntax one - NodeDHT-First [LocalPortnumber] [numNodes]");
            System.out.println("Syntax two - NodeDHT-other [Known-HostIP]  [Known-HostPortnumber] [LocalPortnumber] [numNodes]");
            System.out.println("         *** [LocaPortNumber] = is the port number which the Node will be listening waiting for connections.");
            System.out.println("         *** [Known-HostName] = is the hostIP of one DHTNode already in the net.");
            System.out.println("         *** [Known-HostPortnumber] = is the port which the Known-Host listening waiting for connections.");
            System.exit(1);
        }	
      
    }
    //改动：通过路由表找后继获取随机节点
    public static String getRandomNode() throws Exception{
        Random rand = new Random();
        int randID = rand.nextInt(numDHT);
        Node randNode = find_successor(randID);
        String result = randNode.getIP() + ":" + randNode.getPort();
        return result;
    }

    public static void finishJoiningFirst(int id) throws Exception{
    	System.out.println();
        System.out.println("[系统提示]:"+"节点 " +id + "已经在DHT网络中！.");
        printNodeInfo();
        printNum();
        synchronized (object) {
            busy = 0;
        }
    }
    
    public static void finishJoining(int id) throws Exception{
    	System.out.println();
        System.out.println("[系统提示]:"+"节点 " +id + "已经在DHT网络中！.");
        printNodeInfo();
        printNum();
        synchronized (object) {
            busy = 0;
        }
    }
    //新增：获取第一个节点的信息
    public static int getFisrtNodeInfo(String nodeIP, String nodePort) throws Exception{
        if (busy == 0) {
              synchronized (object) {
                 busy = 1;
        }
        int nodeID = 0;
        int initInfo =0;
        try{ 
            MessageDigest md = MessageDigest.getInstance("SHA1");
            md.reset();
            String hashString = nodeIP+ nodePort;
            md.update(hashString.getBytes());
            byte[] hashBytes = md.digest();
            BigInteger hashNum = new BigInteger(1,hashBytes);
            
            nodeID = Math.abs(hashNum.intValue()) % numDHT;
            
            initInfo = nodeID;
        } catch (NoSuchAlgorithmException nsae){}


        return initInfo;

    } else {
        return -1;
    }
} 

    //改动：修改了确定冲突的方式
    public static int getNodeInfo(String nodeIP, String nodePort) throws Exception{
        if (busy == 0) {
              synchronized (object) {
                 busy = 1;
        }
        int nodeID = 0;
        int initInfo =0;

        try{ 
            MessageDigest md = MessageDigest.getInstance("SHA1");
            md.reset();
            String hashString = nodeIP+ nodePort;
            md.update(hashString.getBytes());
            byte[] hashBytes = md.digest();
            BigInteger hashNum = new BigInteger(1,hashBytes);

            nodeID = Math.abs(hashNum.intValue()) % numDHT;

            while(Integer.parseInt(makeConnection(knownhostIP, knownhostport, "findSucOfPred/"+nodeID))==nodeID) {
                md.reset();
                md.update(hashBytes);
                hashBytes = md.digest();
                hashNum = new BigInteger(1,hashBytes);
                nodeID = Math.abs(hashNum.intValue()) % numDHT;  
            }


            if (Integer.parseInt(makeConnection(knownhostIP, knownhostport, "findSucOfPred/"+nodeID))!=nodeID) {

                System.out.println("新节点加入... ");
            }

            initInfo = nodeID;

        } catch (NoSuchAlgorithmException nsae){}

        return initInfo;

    } else {
        return -1;
    }
} 
    
    public static int HashFunc(String url) {
    	int KID=-1;
    	try {
    		MessageDigest md = MessageDigest.getInstance("SHA1");
            md.reset();
            md.update(url.getBytes());
            byte[] hashBytes = md.digest();
            BigInteger hashNum = new BigInteger(1,hashBytes);
            
            KID = Math.abs(hashNum.intValue()) % numDHT;
    	}catch (Exception e) {}
           
        return KID;
    }
    
    public static String makeConnection(String ip, String port, String message) throws Exception {
            if(myIP.equals(ip) && myport.equals(port)) {
            	String response = considerInput(message);
                return response;
            }
            else {
            	 Socket sendingSocket = new Socket(ip,Integer.parseInt(port));
                 DataOutputStream out = new DataOutputStream(sendingSocket.getOutputStream());
                 BufferedReader inFromServer = new BufferedReader(new InputStreamReader(sendingSocket.getInputStream()));

                 out.writeBytes(message + "\n");

                 String result = inFromServer.readLine();
                 
                 out.close();
                 inFromServer.close();
                 sendingSocket.close(); 
                 return result;
            }   
    }


    public void run() {
        if (this.ID == 0) { //ID==0时，第一个加入的节点的入口        
            System.out.println("正在创建路由表 ... ");

            for (int i = 1; i <= m; i++) {
                finger[i] = new FingerTable();
                finger[i].setStart((me.getID() + (int)Math.pow(2,i-1)) % numDHT);
            }

            for (int i = 1; i < m; i++) {
                finger[i].setInterval(finger[i].getStart(),finger[i+1].getStart()); 
            }
            finger[m].setInterval(finger[m].getStart(),finger[1].getStart()-1); 

            for (int i = 1; i <= m; i++) {
                    finger[i].setSuccessor(me);
            }
            System.out.println("路由表创建完成，此节点是网络中唯一节点！");
            printFingerInfo();
            System.out.println();
           
            try {
            	System.out.println("开始创建节点列表...");
            	add(me);
				System.out.println("节点列表创建完成");
			} catch (Exception e1) {}
            
            try { 
                finishJoiningFirst(me.getID());//释放对象锁
            } catch (Exception e) {}
        }       
        else if (this.ID == -1) {//ID==1时，非第一个加入的节点的入口
            System.out.println("正在创建路由表 ...");
            for (int i = 1; i <= m; i++) {
                finger[i] = new FingerTable();
                finger[i].setStart((me.getID() + (int)Math.pow(2,i-1)) % numDHT);
            }
            for (int i = 1; i < m; i++) {
                finger[i].setInterval(finger[i].getStart(),finger[i+1].getStart()); 
            }
            finger[m].setInterval(finger[m].getStart(),finger[1].getStart()-1); 

            for (int i = 1; i <= m; i++) {
                    finger[i].setSuccessor(me);
            }
            System.out.println("空表创建完成....");
            System.out.println();
            try{    
            	    System.out.println("开始初始化路由表.....");
                    init_finger_table(pred);
                    System.out.println("路由表初始化完成.....");
                    printFingerInfo();
                    update_others();
                    System.out.println("其它节点路由表已更新");
                    System.out.println();
                    System.out.println("开始构建节点列表...");
                    buildNodeList();
                    System.out.println("节点列表创建完成");
                    updateOthersList();
                    System.out.println("其它节点的列表已更新");
            } catch (Exception e) {
            	e.printStackTrace();
            }
            
            try { 
                    finishJoining(me.getID());//
            } catch (Exception e) {}
        }
        else if (this.ID == -2) {//指令集
            Scanner scan = new Scanner(System.in);
            while (scan.hasNext()) {
                String str1 = scan.nextLine();
                if(str1.equals("exit")){
                    try {
						beforeExit();	   
					} catch (Exception e) {
						System.out.println("节点退出异常！");
					}
                    break;
                }
                else if(str1.equals("printnodelist")) {
                	try {
						printNodeInfo();
					} catch (Exception e) {
						e.printStackTrace();
					}
                }
                else if(str1.equals("printfinger")) {
                	printFingerInfo();
                }
                else if(str1.equals("printnum")) {
                	printNum();
                }
                else if(str1.equals("printpred")) {
                	printPred();
                }
                else if(str1.equals("printsuc")) {
                	printSuccessor();
                }
                else if(str1.startsWith("insert")) {
                	String fullIdent=null;
                	String url=null;
                	String[] tokens = str1.split("[\\s]+");
                	fullIdent=tokens[1];
                	url=tokens[2];
                
					try {
							InsertIdentification(fullIdent, url);							
							//System.out.println(IdentMap.entrySet());
						} catch (Exception e) {}

                }
                else if(str1.startsWith("geturl")) {
                	String fullIdent=null;
                	String[] tokens = str1.split("[\\s]+");
                	fullIdent=tokens[1];
                	
                	try {
                		Node locNode=find_successor(HashFunc(fullIdent));
                    	if(locNode.getID()==me.getID()) {
                    		System.out.println("解析结果："+getUrl(fullIdent));
                    	}
                    	else {
                    		System.out.println("解析结果："+makeConnection(locNode.getIP(), locNode.getPort(), "geturl/"+fullIdent));
                    	}      
                	}catch (Exception e) {}
                }
                else if(str1.startsWith("find")) {
                	String[] tokens = str1.split("\\s+");
                	try {
						System.out.println("[系统提示]: 此映射存储于节点"+find_successor((HashFunc(tokens[1]))).getID());
					} catch (Exception e) {}
                }
                else if(str1.startsWith("printident")){
                	printIdent();
                }
                else {
                	System.out.print("命令格式不正确！请重新输入");
                }
            }
            scan.close();
            System.exit(0);
        }
        else {//ID等于其他值时，进行的是信息交互的部分
            try {
                BufferedReader inFromClient =new BufferedReader(new InputStreamReader(connection.getInputStream()));
                DataOutputStream outToClient = new DataOutputStream(connection.getOutputStream());
                
                
                String received = inFromClient.readLine();
                String response = considerInput(received);

                outToClient.writeBytes(response + "\n");	
            } catch (Exception e) {
                System.out.println("[系统提示]:"+"线程无法服务连接");
            	//e.printStackTrace();
            }

        }
    }
    
    public void beforeExit() throws Exception{
    	if(nodeList.size()==1) {
    		System.out.println("\n"+"[系统提示]: 节点 "+me.getID()+"已经退出DHT网络");
			System.out.println("[系统提示]: 网络已关闭");
    	}
    	else if(nodeList.size()==2) {
    		makeConnection(finger[1].getSuccessor().getIP(), finger[1].getSuccessor().getPort(), "quitOfTwoNodes/");
    	}
    	else {
    		makeConnection(finger[1].getSuccessor().getIP(), finger[1].getSuccessor().getPort(), "quitOfManyNodes/"+pred.getID()+"/"+pred.getIP()+"/"+pred.getPort());
    	}
    }
    
    public static void quit_update_finger_table(Node s, int exitID){
        for(int i = 1; i <= m; i++){
            if (finger[i].getSuccessor().getID() == exitID){
                finger[i].setSuccessor(s);
            }
        }
    }
    
    public static String considerInput(String received) throws Exception {
        String[] tokens = received.split("/");
        String outResponse = "";

        if (tokens[0].equals("setPred")) {
            Node newNode = new Node(Integer.parseInt(tokens[1]),tokens[2],tokens[3]);
            setPredecessor(newNode);
            outResponse = "set it successfully";	
        }
        else if (tokens[0].equals("getPred")) {
            Node newNode = getPredecessor();
            outResponse = newNode.getID() + "/" + newNode.getIP() + "/" + newNode.getPort() ;
        }
        else if (tokens[0].equals("findSuc")) {
            Node newNode = find_successor(Integer.parseInt(tokens[1]));
            outResponse = newNode.getID() + "/" + newNode.getIP() + "/" + newNode.getPort() ;
        }
        else if (tokens[0].equals("getSuc")) {
            Node newNode = getSuccessor();
            outResponse = newNode.getID() + "/" + newNode.getIP() + "/" + newNode.getPort() ;
        }
        //新添加
        else if (tokens[0].equals("findPred")) {
            int id=Integer.parseInt(tokens[1]);
            Node newNode = find_predecessor(id);
            outResponse = newNode.getID() + "/" + newNode.getIP() + "/" + newNode.getPort() ;
        }
        //新添加
        else if (tokens[0].equals("quitOfTwoNodes")) {//只有两个节点的退出
        	delete(pred);//后继节点的列表中删除前继
        	System.out.println("\n"+"[系统提示]: 节点 "+pred.getID()+"已经退出DHT网络");
        	setPredecessor(me);
        	for(int i=1;i<=m;i++) {
        		finger[i].setSuccessor(me);
        	}       	
        	printNum();
        }
        //新添加
        else if (tokens[0].equals("quitOfManyNodes")) {//多于两个节点时的退出
        	delete(pred);//后继节点的列表中删除前继
        	noticeOthers("deleteNodeOfNodelist/"+pred.getID()+"/"+pred.getIP()+"/"+pred.getPort()+"/"+me.getID()+"/"+me.getIP()+"/"+me.getPort()+"/"+pred.getID());//通知剩余节点删除其前继
        	System.out.println("\n"+"[系统提示]: 节点 "+pred.getID()+"已经退出DHT网络");
        	setPredecessor(new Node(Integer.parseInt(tokens[1]),tokens[2],tokens[3]));//将前继设为删除节点的前继
        	printNum();
        }
        //新添加
        else if (tokens[0].equals("deleteNodeOfNodelist")) {
        	Node deletenode = new Node(Integer.parseInt(tokens[1]),tokens[2],tokens[3]);
        	Node updatenode = new Node(Integer.parseInt(tokens[4]),tokens[5],tokens[6]);
        	delete(deletenode);
        	quit_update_finger_table(updatenode,Integer.parseInt(tokens[7]));
        	System.out.println("\n"+"[系统提示]: 节点 "+tokens[7]+"已经退出DHT网络");
            printNum();
        }
        //新添加
        else if (tokens[0].equals("tryInsert")) {
            tryInsert(tokens[1], tokens[2], tokens[3]);
        }
        else if (tokens[0].equals("geturl")) {
            outResponse=getUrl(tokens[1]);
        }
        //新添加
        else if (tokens[0].equals("printNum")) {
        	printNum();
        }
        //新添加
        else if (tokens[0].equals("printNodeInfo")) {
        	printNodeInfo();
        }
        //新添加
        else if (tokens[0].equals("findSucOfPred")) {
        	outResponse=Integer.toString(find_successor(find_predecessor(Integer.parseInt(tokens[1])).getID()).getID());
        }
        //新添加
        else if (tokens[0].equals("load")) {
        	outResponse=loadNode();
        }
        //新添加
        else if (tokens[0].equals("updateList")) {
        	Node newnode = new Node(Integer.parseInt(tokens[1]),tokens[2],tokens[3]);
        	updateList(newnode);
        }
        else if (tokens[0].equals("closetPred")) {
            Node newNode = closet_preceding_finger(Integer.parseInt(tokens[1]));
            outResponse = newNode.getID() + "/" + newNode.getIP() + "/" + newNode.getPort() ;
        }
        else if (tokens[0].equals("updateFing")) {
            Node newNode = new Node(Integer.parseInt(tokens[1]),tokens[2],tokens[3]);
            update_finger_table(newNode,Integer.parseInt(tokens[4]));
            outResponse = "update finger " + Integer.parseInt(tokens[4]) + " successfully";	
        }
        return outResponse;
    }
    //查找url
    public static String getUrl(String fullIdent){	
    	String[] tokens = fullIdent.split("/");
    	String top=tokens[0];
    	String second=tokens[1];
    	Identification dest=new Identification(top, second);
    	if(IdentMap.containsKey(dest))
    	    return IdentMap.get(dest); 
    	else {
    		return "无法解析";
    	}
    }
    //向当前节点插入<Identification,url>键值对
    public static void InsertIdentification(String fullIdent, String url) throws Exception { 
    	String[] tokens = fullIdent.split("/");
    	String top=tokens[0];
    	String second=tokens[1];
    	//System.out.println(top);
    	//System.out.println(second);
    	int kid=HashFunc(fullIdent);//标识的哈希
    	Node temp=find_successor(kid);//应该存储的位置
    	if(temp.getID()==me.getID()) {
    		localInsert(top,second,url);
    	}
    	else {
    		makeConnection(temp.getIP(), temp.getPort(), "tryInsert/"+top+"/"+second+"/"+url);
    		System.out.println("[系统提示]: 标识映射已存入节点"+temp.getID());
    	}   	  
    }
    //新增：将标识映射添加到本地
    public static void localInsert(String top ,String second,String url) {
    	Identification newone= new Identification(top, second); 
    	//IdentificationList.add(newone);
    	//System.out.println(newone.getToplevelIdent()+"/"+newone.getSecondaryIdent());
    	IdentMap.put(newone, url);
		System.out.println("[系统提示]: 标识映射已存入节点"+me.getID());
    }
    //新增：将标识映射添加到其它节点
    public static void tryInsert(String top,String second ,String url) {
    	Identification newone= new Identification(top, second);
    	IdentMap.put(newone, url);
    	System.out.println("[系统提示]: 有新标识映射存入本节点");
    }
    //初始化路由表
    public static void init_finger_table(Node n) throws Exception {
        int myID, nextID;

        String request = "findSuc/" + finger[1].getStart();
        String result = makeConnection(n.getIP(),n.getPort(),request);

        String[] tokens = result.split("/");
        finger[1].setSuccessor(new Node(Integer.parseInt(tokens[0]),tokens[1],tokens[2]));
        
        String request2 = "getPred";
        String result2 = makeConnection(finger[1].getSuccessor().getIP(),finger[1].getSuccessor().getPort(),request2);
        String[] tokens2 = result2.split("/");
        pred = new Node(Integer.parseInt(tokens2[0]),tokens2[1],tokens2[2]);

        String request3 = "setPred/" + me.getID() + "/" + me.getIP() + "/" + me.getPort();
        makeConnection(finger[1].getSuccessor().getIP(),finger[1].getSuccessor().getPort(),request3);

        int normalInterval = 1;
        for (int i = 1; i <= m-1; i++) {

            myID = me.getID();
            nextID = finger[i].getSuccessor().getID(); 

            if (myID >= nextID)
                normalInterval = 0;
            else normalInterval = 1;

            if ( (normalInterval==1 && (finger[i+1].getStart() >= myID && finger[i+1].getStart() <= nextID))
                    || (normalInterval==0 && (finger[i+1].getStart() >= myID || finger[i+1].getStart() <= nextID))) {

                finger[i+1].setSuccessor(finger[i].getSuccessor());
            } else {

                String request4 = "findSuc/" + finger[i+1].getStart();
                String result4 = makeConnection(n.getIP(),n.getPort(),request4);
                String[] tokens4 = result4.split("/");

                int fiStart = finger[i+1].getStart();
                int succ = Integer.parseInt(tokens4[0]); 
                int fiSucc = finger[i+1].getSuccessor().getID();
                if (fiStart > succ) 
                    succ = succ + numDHT;
                if (fiStart > fiSucc)
                    fiSucc = fiSucc + numDHT;

                if ( fiStart <= succ && succ <= fiSucc ) {
                    finger[i+1].setSuccessor(new Node(Integer.parseInt(tokens4[0]),tokens4[1],tokens4[2]));
                }
            }
        }
    }
    //更新影响到的节点的路由表（波及到的节点范围：向前1～2^m-1）
    public static void update_others() throws Exception{
        Node p;
        for (int i = 1; i <= m; i++) {
            int id = me.getID() - (int)Math.pow(2,i-1) + 1;
            if (id < 0)
                id = id + numDHT; 

            p = find_predecessor(id);

            String request = "updateFing/" + me.getID() + "/" + me.getIP() + "/" + me.getPort() + "/" + i;  
            makeConnection(p.getIP(),p.getPort(),request);
        }
    }
    //更新路由表
    public static void update_finger_table(Node s, int i) throws Exception // RemoteException,
           {

               Node p;
               int normalInterval = 1;
               int myID = me.getID();
               int nextID = finger[i].getSuccessor().getID();
               if (myID >= nextID) 
                   normalInterval = 0;
               else normalInterval = 1;

               if ( ((normalInterval==1 && (s.getID() >= myID && s.getID() < nextID)) ||
                           (normalInterval==0 && (s.getID() >= myID || s.getID() < nextID)))
                       && (me.getID() != s.getID() ) ) {

                   finger[i].setSuccessor(s);
                   p = pred;

                   String request = "updateFing/" + s.getID() + "/" + s.getIP() + "/" + s.getPort() + "/" + i;  
                   makeConnection(p.getIP(),p.getPort(),request);
                   }
    }
    //设置当前节点的前继节点
    public static void setPredecessor(Node n) // throws RemoteException
    {
        pred = n;
    }
    //获取当前节点的前继节点
    public static Node getPredecessor() //throws RemoteException 
    {
        return pred;
    }
    //通过当前节点的路由表查询某个NID的后继节点
    public static Node find_successor(int id) throws Exception 
           { 
               Node n;
               n = find_predecessor(id);

               String request = "getSuc/" ;
               String result = makeConnection(n.getIP(),n.getPort(),request);
               String[] tokens = result.split("/");
               Node tempNode = new Node(Integer.parseInt(tokens[0]),tokens[1],tokens[2]);
               return tempNode;
    }
    //通过当前节点的路由表查询某个NID的前继节点
    public static Node find_predecessor(int id)  throws Exception
    {
        Node n = me;
        int myID = n.getID();
        int succID = finger[1].getSuccessor().getID();
        int normalInterval = 1;
        if (myID >= succID)
            normalInterval = 0;

        while ((normalInterval==1 && (id <= myID || id > succID)) ||
                (normalInterval==0 && (id <= myID && id > succID))) {

            String request = "closetPred/" + id ;
            String result = makeConnection(n.getIP(),n.getPort(),request);
            String[] tokens = result.split("/");

            n = new Node(Integer.parseInt(tokens[0]),tokens[1],tokens[2]);

            myID = n.getID();

            String request2 = "getSuc/" ;
            String result2 = makeConnection(n.getIP(),n.getPort(),request2);
            String[] tokens2 = result2.split("/");

            succID = Integer.parseInt(tokens2[0]);

            if (myID >= succID) 
                normalInterval = 0;
            else normalInterval = 1;
                }
        return n;
    }
    //获取当前节点的后继节点
    public static Node getSuccessor() 
    {
        return finger[1].getSuccessor();
    }
    //获取当前节点路由表中距离目标id最近的节点
    public static Node closet_preceding_finger(int id) 
    {
        int normalInterval = 1;
        int myID = me.getID();
        if (myID >= id) {
            normalInterval = 0;
        }

        for (int i = m; i >= 1; i--) {
            int nodeID = finger[i].getSuccessor().getID();
            if (normalInterval == 1) {
                if (nodeID > myID && nodeID < id) 
                    return finger[i].getSuccessor();
            } else {
                if (nodeID > myID || nodeID < id) 
                    return finger[i].getSuccessor();
            }
        }
        return me;
    }
    //新增：更新nodeList
    public synchronized static void updateList(Node node) throws Exception {
    	add(node);
    	System.out.println();
    	System.out.println("[系统提示]： "+"新节点 "+node.getID()+"加入DHT网络");
    	printNodeInfo();
    	printNum();
    }
    //新增：更新其它节点的nodeList
    public synchronized static void updateOthersList() throws Exception {
    	Iterator<Node> iterator = nodeList.iterator();
    	while(iterator.hasNext()) {
    		Node node =iterator.next();
    		if(node==me)
    			continue;
    	    makeConnection(node.getIP(),node.getPort(),"updateList/"+me.getID()+"/"+me.getIP()+"/"+me.getPort());
    	}
    }
    public synchronized static void buildNodeList() throws Exception{
    	add(me);
    	String str = makeConnection(knownhostIP, knownhostport, "load/");
    	getNode(str);
    }
    //新增：节点生成nodeList
    /*public static void buildNodeList() throws Exception{
    	addLocalNode();
    	Node current=new Node(finger[m].getSuccessor().getID(),finger[m].getSuccessor().getIP(), finger[m].getSuccessor().getPort());
    	while(!nodeList.contains(me)) {
    		getNode(makeConnection(current.getIP(),current.getPort(), "addLocalNode/"));
    		String str=makeConnection(current.getIP(), current.getPort(), "remoteNode/");
    		String[] tokens = str.split("/");
    		current=new Node(Integer.parseInt(tokens[0]),tokens[1],tokens[2]);
    	}
    	System.out.println("执行到此----");
    }*/
    //新增：处理返回的m个node信息并生成arraylist(路由表中最多只有m个node)
    public synchronized static void getNode(String str) {
    	 String[] tokens = str.split("/");
    	 Node newNode=null;
    	 for(int i=1;i<=(tokens.length/3);i++) {
    	      newNode=new Node(Integer.parseInt(tokens[0+3*(i-1)]),tokens[1+3*(i-1)],tokens[2+3*(i-1)]);
    	      add(newNode);
    	 }
    }
    //新增：将本节点的路由表信息中的节点信息加入nodeList    
    /*public static void addLocalNode(){
    	for(int i=1;i<=m;i++){
            nodeList.add(finger[i].getSuccessor());
         }
    }*/
    public synchronized static String loadNode(){
	     Node node =null;
	     String results="";
	     for(int i=0;i<nodeList.size()-1;i++) {
	    	  node = nodeList.get(i);
		      results=results+node.getID() + "/" + node.getIP() + "/" + node.getPort()+"/";
	     }
	     results=results+nodeList.get(nodeList.size()-1).getID() + "/" + nodeList.get(nodeList.size()-1).getIP() + "/" + nodeList.get(nodeList.size()-1).getPort()+"/";
	     return results;
    }
    //新增：提供本节点的路由表中的节点信息给其它节点
    /*public static String loadNode(){
    	Node newNode =null;
    	String results="";
    	for(int i=1;i<m;i++) {
    		newNode=finger[i].getSuccessor();
    		results=results+newNode.getID() + "/" + newNode.getIP() + "/" + newNode.getPort()+"/" ;
    	}
    	newNode=finger[m].getSuccessor();
    	results=results+newNode.getID() + "/" + newNode.getIP() + "/" + newNode.getPort();
    	return results;
    }*/
    //新增：返回路由表中最远的节点
   /* public static String remoteNode(){
    	String result = null;
    	result =finger[m].getSuccessor().getID()+"/"+finger[m].getSuccessor().getIP()+"/"+finger[m].getSuccessor().getPort();
    	return result;
    }*/
    //新增：广播消息
    public synchronized static void noticeOthers(String message) throws Exception{
    	Iterator<Node> iterator = nodeList.iterator();
    	while(iterator.hasNext()) {
    		Node node =iterator.next();
    		if(node==me)
    			continue;
    		makeConnection(node.getIP(),node.getPort(),message);
    	}
    	System.out.println("已通知所有节点");
    }
    //新增：打印节点个数
    public static void printNum(){
    	System.out.println("当前节点个数 ："+nodeList.size()+"个");
    }
    //新增：打印前继
    public static void printPred() {
    	System.out.println("本节点的前继节点是 ："+pred.getID());
    }
    //新增：打印后继
    public static void printSuccessor() {
    	System.out.println("本节点的后继节点是 ："+finger[1].getSuccessor().getID());
    }
    //新增：打印路由表信息
    public static void printFingerInfo(){
    	String results="";
    	System.out.println("*****路由表信息*****");
    	for(int i=1;i<=m;i++) {
		      System.out.println(results+"Index["+finger[i].getStart()+"]       "+"后继节点ID: "+finger[i].getSuccessor().getID());
	     }
    }
    //新增：打印节点信息
    public synchronized static void printNodeInfo() throws Exception{
    	Iterator<Node> iterator = nodeList.iterator();
    	String string="";
    	System.out.println("*****节点列表*****");
    	if(nodeList.size()==0)
    		System.out.println("列表为空！");
    	while(iterator.hasNext()) {
    		Node node = iterator.next();
    		string="节点ID:"+node.getID()+"  IP地址："+node.getIP()+"  端口号： "+node.getPort()+" ";
    		System.out.println(string);
    	}
    }
    //新增：打印映射
    public static void printIdent() {
    	Set<Entry<Identification,String>> ms =IdentMap.entrySet();
    	System.out.println("\n"+"******标识映射*****");
    	int count=1;
		for (Entry<Identification,String> entry : ms) {
			System.out.println((count++)+"."+entry.getKey().getToplevelIdent()+"/"+entry.getKey().getSecondaryIdent()+"="+entry.getValue());
		}
		System.out.println();
    } 
    //新增
    
    //新增：删除节点
    public synchronized static void delete(Node node){
    	nodeList.remove(node);
    }
    //新增：增加节点
    public synchronized static void add(Node node){
    	nodeList.add(node);
    }
}