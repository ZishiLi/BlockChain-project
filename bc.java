/* 2020-10-04

bc.java for BlockChain

Dr. Clark Elliott for CSC435
Copyright (C) 2020 by Clark Elliott with all rights reserved

java 1.8.0_181

This is some quick sample code giving a simple example framework for coordinating multiple processes in a blockchain group.


INSTRUCTIONS:

Set the numProceses class variable (e.g., 1,2,3), and use a batch file to match it.

AllStart.bat:

REM for three procesess:
start java bc 0
start java bc 1
java bc 2

You might want to start with just one process to see how it works. I've run it with five processes.

Thanks: http://www.javacodex.com/Concurrency/PriorityBlockingQueue-Example

Sample output is at the bottom of this file.

All [three] processes run the same in this consortium, each in its own terminal window. Control-C to stop.

We start three servers listening for incoming connections:

Public Key server -- accept the public keys of all processes (including THIS process)
Unverified Block (UVB) server -- accept the sample simple unverified blocks from all processes (including THIS process)
Updated Blockchain server -- accept updated blockchains from all processes (including THIS process)

UVBs are placed in a priority queue (by timestamp of time created). They are removed by a consumer, which verifies
the blocks, adds them to the blockchain and multicasts the new blockchain.

WORK in this example is fake -- just sleeping for a random amount of time.

Included as a tool for your toolbox is the sending of BlockRecord objects through streaming data over sockets.

The UVB queue must be thread-safe for concurrent access because multiple UVB workers act as concurrent producers (into
the queue) and the UVB consumer is also operating concurrently. Each is in its own thread access the same queue.

In theory, this should work for any number of processes. But, make sure your priority queue is big enough for all
the data.

Sleep statements are used as a simple way to let servers settle before clients connect to them. Also random sleep statements
are used to introduce intresting interactions and simulate UVBs appearing from many sources at different times.

This example is to illustrate process coordination, and does not contain any actual chaining of the blocks.

I've left in many commented out print statements you can use to see further details if you like.

*/

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.*;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;


// basic java object for this assignment, representing a block
class BlockRecord implements Serializable {

    UUID uuid;
    String BlockID;
    String signature;
    String TimeStamp;
    String VerificationProcessID;
    //a winningHash from the last block
    String PreviousHash;
    String Maker;

    //basic information about a patient
    String Fname;
    String Lname;
    String SSNum;
    String DOB;
    String Diag;
    String Treat;
    String Rx;

    // valid seed to win the guess
    String RandomSeed;
    String WinningHash = "";
    String indexOfBc;


    //setup or get a field in a block
    public String getBlockID() {
        return BlockID;
    }

    public String getMaker() {
        return Maker;
    }

    public String getSignature() {
        return signature;
    }


    public void setBlockID(String BID) {
        this.BlockID = BID;
    }

    public void setMaker(String Maker) {
        this.Maker = Maker;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getVerificationProcessID() {
        return VerificationProcessID;
    }

    public void setVerificationProcessID(String VID) {
        this.VerificationProcessID = VID;
    }

    public String getPreviousHash() {
        return this.PreviousHash;
    }

    public void setPreviousHash(String PH) {
        this.PreviousHash = PH;
    }

    public UUID getUUID() {
        return uuid;
    }

    public void setUUID(UUID ud) {
        this.uuid = ud;
    }

    public String getLname() {
        return Lname;
    }

    public void setLname(String LN) {
        this.Lname = LN;
    }

    public String getFname() {
        return Fname;
    }

    public void setFname(String FN) {
        this.Fname = FN;
    }

    public String getSSNum() {
        return SSNum;
    }

    public void setSSNum(String SS) {
        this.SSNum = SS;
    }

    public String getDOB() {
        return DOB;
    }

    public void setDOB(String RS) {
        this.DOB = RS;
    }

    public String getDiag() {
        return Diag;
    }

    public void setDiag(String D) {
        this.Diag = D;
    }

    public String getTreat() {
        return Treat;
    }

    public void setTreat(String Tr) {
        this.Treat = Tr;
    }

    public String getRx() {
        return Rx;
    }

    public void setRx(String Rx) {
        this.Rx = Rx;
    }

    public String getRandomSeed() {
        return RandomSeed;
    }

    public void setRandomSeed(String RS) {
        this.RandomSeed = RS;
    }

    public String getWinningHash() {
        return WinningHash;
    }

    public void setWinningHash(String WH) {
        this.WinningHash = WH;
    }

    public String getTimeStamp() {
        return TimeStamp;
    }

    public void setTimeStamp(String TS) {
        this.TimeStamp = TS;
    }


}

class Ports {
    // port for four servers
    public static int KeyServerPortBase = 6050;
    public static int UnverifiedBlockServerPortBase = 6051;
    public static int BlockchainServerPortBase = 6052;
    public static int WaitingforRunningBase = 6053;


    public static int KeyServerPort;
    public static int UnverifiedBlockServerPort;
    public static int BlockchainServerPort;
    public static int WaitingforRunningPort;


    // specific ports depends on the specific process number
    public void setPorts() {
        KeyServerPort = KeyServerPortBase + (bc.PID * 1000);
        UnverifiedBlockServerPort = UnverifiedBlockServerPortBase + (bc.PID * 1000);
        BlockchainServerPort = BlockchainServerPortBase + (bc.PID * 1000);
        WaitingforRunningPort = WaitingforRunningBase + (bc.PID * 1000);
    }
}


//receive and process public keys from all procs
class PublicKeyWorker extends Thread {
    Socket keySock;

    PublicKeyWorker(Socket s) {
        keySock = s;
    }

    public void run() {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(keySock.getInputStream()));
            String data = in.readLine();
            //split the date, so get the PID and its public key
            String pid = data.substring(data.length() - 1);
            String pK = data.substring(0, data.length() - 1);

            //save its public key into a array, based on its PID
            bc.keys[Integer.parseInt(pid)] = pK;
            System.out.println("Got key: " + pK + "\n" + "from Process" + pid);
            keySock.close();
        } catch (IOException x) {
            x.printStackTrace();
        }
    }
}


class PublicKeyServer implements Runnable {
    public void run() {
        int q_len = 6;
        Socket keySock;
        System.out.println("Starting Key Server input thread using " + Ports.KeyServerPort);
        try {
            ServerSocket servsock = new ServerSocket(Ports.KeyServerPort, q_len);
            while (true) {
                keySock = servsock.accept();
                new PublicKeyWorker(keySock).start();
            }
        } catch (IOException ioe) {
            System.out.println(ioe);
        }
    }
}

class UnverifiedBlockServer implements Runnable {
    BlockingQueue<BlockRecord> queue;

    //initiate the local queue
    UnverifiedBlockServer(BlockingQueue<BlockRecord> queue) {
        this.queue = queue;
    }


    // Receive UBs, and put them into the priority queue based on those UBs'time.
    class UnverifiedBlockWorker extends Thread {
        Socket sock;

        UnverifiedBlockWorker(Socket s) {
            sock = s;
        }


        public void run() {

            try {
                BufferedReader unverifiedIn = new BufferedReader(new InputStreamReader(sock.getInputStream()));
                // Read a uvb in json format
                String in = unverifiedIn.readLine();
                String pid = in.substring(in.length() - 1);
                in = in.substring(0, in.length() - 1);

                //transform json String to a BR object
                BlockRecord BR = new Gson().fromJson(in, BlockRecord.class);
                System.out.println("Received UVB: " + BR.getTimeStamp() + " from P" + pid);

                //put the BR into the priority queue to sort them.
                queue.put(BR);
                sock.close();
            } catch (Exception x) {
                x.printStackTrace();
            }
        }
    }

    // get uvbs from uvbs sender
    public void run() {
        int q_len = 6;
        Socket sock;
        System.out.println("Starting the Unverified Block Server input thread using " +
                Ports.UnverifiedBlockServerPort);
        try {
            ServerSocket UVBServer = new ServerSocket(Ports.UnverifiedBlockServerPort, q_len);
            while (true) {
                sock = UVBServer.accept();
                //initiate a new work to process this uvb
                new UnverifiedBlockWorker(sock).start();
            }
        } catch (IOException ioe) {
            System.out.println(ioe);
        }
    }

    // customize a comparator so that the priority queue can sort blocks based on their timestamp.
    public static Comparator<BlockRecord> BlockTSComparator = new Comparator<BlockRecord>() {
        @Override
        public int compare(BlockRecord b1, BlockRecord b2) {
            String s1 = b1.getTimeStamp();
            String s2 = b2.getTimeStamp();
            if (s1 == null) {
                return -1;
            }
            if (s2 == null) {
                return 1;
            }
            if (s1.equals(s2)) {
                return 0;
            }
            return s1.compareTo(s2);
        }
    };
}


//very important class
//process all UVBs, do fake job while competing with other peers，and then send the newest bc to all processes
class UnverifiedBlockConsumer implements Runnable {
    PriorityBlockingQueue<BlockRecord> queue;

    UnverifiedBlockConsumer(PriorityBlockingQueue<BlockRecord> queue) {
        this.queue = queue;
    }

    //used to generate random seeds
    private static final String ALPHA_NUMERIC_STRING = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    static String randString;

    public static boolean verifySig(byte[] data, PublicKey key, byte[] sig) throws Exception {
        Signature signer = Signature.getInstance("SHA1withRSA");
        signer.initVerify(key);
        signer.update(data);

        return (signer.verify(sig));
    }


    public void run() {
        BlockRecord tempRec;
        PrintStream toBlockChainServer;
        Socket BlockChainSock;
        Random r = new Random();
        String lastBWinningHash;
        int workNumber;
        System.out.println("Starting the Unverified Block Priority Queue Consumer thread.\n");
        String concatString = "";
        String stringOut = "";

        try {

            while (true) {
                boolean solved = true;
                //get a UVB from PQ
                tempRec = queue.take();

//                //varify the signature
//                MessageDigest md = MessageDigest.getInstance("SHA-256");
//                md.update(tempRec.getBlockID().getBytes());
//                //get the hash
//                byte byteData[] = md.digest();
//
//                //get the sig
//                String sig = tempRec.getSignature();
//                byte[] testSignature = Base64.getDecoder().decode(sig);
//                //verify the sig by using hash, publicKey, and Signature.
//                String k = bc.keys[Integer.parseInt(tempRec.getMaker())];
//                byte[] bytePubkey2 = Base64.getDecoder().decode(k);
//
//                X509EncodedKeySpec pubSpec = new X509EncodedKeySpec(bytePubkey2);
//                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
//                PublicKey RestoredKey = keyFactory.generatePublic(pubSpec);
//                boolean verified = verifySig(byteData, RestoredKey, testSignature);
//                System.out.println("Signature verification results：" + verified);


                //when the block is the first block, just setup its previousHash 00000000
                if (bc.blockchain.length() == 0) {
                    lastBWinningHash = "00000000";
                } else {
                    //get the last block's winningHashcode
                    int ind = bc.blockchain.indexOf("\"WinningHash\":") + 15;
                    lastBWinningHash = bc.blockchain.substring(ind, ind + 64);
                }
                concatString = lastBWinningHash + tempRec.getBlockID();
                for (int i = 1; i < 20; i++) {
                    //fake work
                    Thread.sleep((r.nextInt(9) * 100));

                    //generate a random seed, used to guess
                    randString = randomAlphaNumeric(8);

                    //concatenate three "elements"
                    concatString = concatString + randString;

                    //trasnsform it to 256 Hash
                    MessageDigest MD = MessageDigest.getInstance("SHA-256");
                    byte[] bytesHash = MD.digest(concatString.getBytes("UTF-8"));

                    // transform 256Hash to Hex
                    stringOut = ByteArrayToString(bytesHash);

                    //get the number that is used to work
                    workNumber = Integer.parseInt(stringOut.substring(0, 4), 16); // Between 0000 (0) and FFFF (65535)

                    //if so, get the right answer!
                    if (workNumber < 20000) {
                        //update the varified block's information
                        tempRec.setRandomSeed(randString);
                        tempRec.setVerificationProcessID(bc.PID + "");
                        tempRec.setPreviousHash(lastBWinningHash);
                        tempRec.setWinningHash(stringOut);
                        break;
                    }

                    //periodically check if the current block has been verified by other procs
                    String temp;
                    if (bc.blockchain.length() == 0) {
                        temp = "00000000";
                    } else {
                        int ind2 = bc.blockchain.indexOf("\"WinningHash\":") + 15;
                        temp = bc.blockchain.substring(ind2, ind2 + 64);
                    }

                    //if the hashcode we get in the first is not equal to the last block's in blockchian,
                    //that means the blockchain has been updated, and somebody else has figured this puzzle out.
                    if (!temp.equals(lastBWinningHash)) {
                        solved = false;
                        System.out.println("The current Block this process is working on has been verified by other procs");
                        System.out.println("quited");
                        break;
                    }
                }


                //Check if this block has been added into BC already
                if (solved && !bc.blockchain.contains(tempRec.getSSNum())) {
                    System.out.println();

                    //update BC with a new varified block in json format
                    String tempRecJ = new Gson().toJson(tempRec);
                    String tempblockchain = tempRecJ + "\n" + bc.blockchain;

                    // multicast the new BC to all procs;
                    for (int i = 0; i < bc.numProcesses; i++) {
                        BlockChainSock = new Socket(bc.serverName, Ports.BlockchainServerPortBase + (i * 1000));
                        toBlockChainServer = new PrintStream(BlockChainSock.getOutputStream());
                        toBlockChainServer.println(tempblockchain);
                        toBlockChainServer.flush();
                        BlockChainSock.close();
                    }
                }
                Thread.sleep(1000); // For the example, wait for our blockchain to be updated before processing a new block
            }
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    //used to transform 256hash to hex
    public static String ByteArrayToString(byte[] ba) {
        StringBuilder hex = new StringBuilder(ba.length * 2);
        for (int i = 0; i < ba.length; i++) {
            hex.append(String.format("%02X", ba[i]));
        }
        return hex.toString();
    }

    //used to generate random seeds
    public static String randomAlphaNumeric(int count) {
        StringBuilder builder = new StringBuilder();
        while (count-- != 0) {
            int character = (int) (Math.random() * ALPHA_NUMERIC_STRING.length());
            builder.append(ALPHA_NUMERIC_STRING.charAt(character));
        }
        return builder.toString();
    }
}


//receive a new bc from procs
//display the new bc on console and update it with local bc.
//meanwhile process0 takes the resp to write the new bc into a json file
class BlockchainWorker extends Thread { // Class definition
    Socket sock;

    BlockchainWorker(Socket s) {
        sock = s;
    }

    public void run() {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
            //get new bc in json format
            String blockData = in.readLine();
            String blockDataIn;

            while ((blockDataIn = in.readLine()) != null) {
                blockData = blockData + "\n" + blockDataIn;
            }

            //display the current bc on console
            blockData = "[block " + bc.num + "]:" + blockData;
            bc.num++;

            //update blockchain with newest bc
            bc.blockchain = blockData;

            //only proc0 needs to write the new bc into json file
            if (bc.PID == 0) {
                LinkedList<BlockRecord> ll = new LinkedList<>();
                String[] bcs = bc.blockchain.split("\n");

                for (String b : bcs) {
                    String temp = b.substring(b.indexOf("{"));
                    //transform json to java object
                    ll.add(new Gson().fromJson(temp, BlockRecord.class));
                }

                //write all blocks in the file
                jason.WriteJSON(ll);
            }
            System.out.println("         --NEW BLOCKCHAIN--\n" + bc.blockchain + "\n");
            sock.close();
        } catch (IOException x) {
            x.printStackTrace();
        }
    }
}

//receive a new bc from other procs
class BlockchainServer implements Runnable {
    public void run() {
        int q_len = 6;
        Socket sock;
        // shows this server has been running
        System.out.println("Starting the Blockchain server input thread using " + Ports.BlockchainServerPort);
        try {
            ServerSocket servsock = new ServerSocket(Ports.BlockchainServerPort, q_len);
            while (true) {
                //receive a new bc
                sock = servsock.accept();
                new BlockchainWorker(sock).start();
            }
        } catch (IOException ioe) {
            System.out.println(ioe);
        }
    }
}


//write bc into a json file
class jason {
    public static void WriteJSON(LinkedList<BlockRecord> bc) {
        System.out.println("Writing the new BlockChain into a Json file\n");

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        // Convert the Java object to a JSON String:
        // Write the JSON object to a file:
        try (FileWriter writer = new FileWriter("BlockchainLedger.json")) {
            gson.toJson(bc, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}


public class bc {
    static String serverName = "localhost";
    static String blockchain = "";
    //count how many block has been added in BC
    static int num = 0;

    private static String FILENAME;

    // setup how many processes u want to run
    static int numProcesses = 3;
    //initiate the process id
    static int PID = 0;
    static KeyPair keyPair;

    LinkedList<BlockRecord> recordList = new LinkedList<>();
    //save public keys
    static String[] keys = new String[numProcesses];


    // save sorted UVBS
    final PriorityBlockingQueue<BlockRecord> ourPriorityQueue = new PriorityBlockingQueue<>(100, BlockTSComparator);

    // customize this comparator for priorityQ so that it can sort UVBs based on their timestamp
    public static Comparator<BlockRecord> BlockTSComparator = new Comparator<BlockRecord>() {
        @Override
        public int compare(BlockRecord b1, BlockRecord b2) {
            String s1 = b1.getTimeStamp();
            String s2 = b2.getTimeStamp();
            if (s1 == null) {
                return -1;
            }
            if (s2 == null) {
                return 1;
            }
            if (s1.equals(s2)) {
                return 0;
            }
            return s1.compareTo(s2);
        }
    };

    // sned public key to all processes
    public void KeySend() {
        Socket sock;
        PrintStream toServer;
        try {
            //use a randome Long to generate a pair of Keys for the current process
            keyPair = generateKeyPair(new Random().nextLong());

            //transform public key to byte[]
            byte[] bytePubkey = keyPair.getPublic().getEncoded();

            //transform public key in byte type to readable String type
            String stringKey = Base64.getEncoder().encodeToString(bytePubkey);

            //transform public key in String type to Json format
            String jK = new Gson().toJson(stringKey);

            //send public key in json format to all publicKeyServers
            for (int i = 0; i < numProcesses; i++) {

                sock = new Socket(serverName, Ports.KeyServerPortBase + (i * 1000));

                toServer = new PrintStream(sock.getOutputStream());

                //postfix public key with processID in order to distinguish those keys from different processes.
                toServer.println(jK + bc.PID);
                toServer.flush();
                sock.close();
            }
        } catch (Exception x) {
            x.printStackTrace();
        }
    }

    //generate a paird of keys
    public static KeyPair generateKeyPair(long seed) throws Exception {
        KeyPairGenerator keyGenerator = KeyPairGenerator.getInstance("RSA");
        SecureRandom rng = SecureRandom.getInstance("SHA1PRNG", "SUN");
        rng.setSeed(seed);
        keyGenerator.initialize(1024, rng);

        return (keyGenerator.generateKeyPair());
    }

    //Start the whole system
    private void runEngine() {
        Socket run;
        PrintStream toRunningSever;
        try {
            for (int i = 0; i < bc.numProcesses; i++) {// Send some sample Unverified Blocks (UVBs) to each process
                run = new Socket(serverName, Ports.WaitingforRunningBase + (i * 1000));
                toRunningSever = new PrintStream(run.getOutputStream());
                toRunningSever.println("go");
                toRunningSever.flush();
                run.close();

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //get the information from external files
    // and use them to generate UVBs.
    //Then, multicast UVBs to all process.
    public void UnverifiedSend() throws IOException { // Multicast some unverified blocks to the other processes
        // connect to the UnverifiedBloc Server for all process.
        Socket UVBsock;
        String T1;
        String TimeStampString;
        Date date;
        //convenient to generate a UVB
        int iFNAME = 0;
        int iLNAME = 1;
        int iDOB = 2;
        int iSSNUM = 3;
        int iDIAG = 4;
        int iTREAT = 5;
        int iRX = 6;

        BufferedReader br = new BufferedReader(new FileReader(bc.FILENAME));
        String[] tokens;
        String InputLineStr;
        String suuid;
        UUID BinaryUUID;
        BlockRecord tempRec;


        try {
            //read in  all lines from the file
            while ((InputLineStr = br.readLine()) != null) {
                if (InputLineStr.equals("")) {
                    continue;
                }
                //initiate a new BlockRecord object

                BlockRecord BR = new BlockRecord();

                //setup its timestamp
                date = new Date();
                T1 = String.format("%1$s %2$tF.%2$tT", "", date);
                TimeStampString = T1 + "." + PID; // No timestamp collisions!
                //make uvbs' timestamp different
                Thread.sleep(1000);
                BR.setTimeStamp(TimeStampString);

                //update the current uvb's information
                BinaryUUID = UUID.randomUUID();
                suuid = BinaryUUID.toString();
                BR.setMaker(PID + "");
                BR.setBlockID(suuid);
                BR.setUUID(BinaryUUID);
                tokens = InputLineStr.split(" +"); // Tokenize the input
                BR.setFname(tokens[iFNAME]);
                BR.setLname(tokens[iLNAME]);
                BR.setSSNum(tokens[iSSNUM]);
                BR.setDOB(tokens[iDOB]);
                BR.setDiag(tokens[iDIAG]);
                BR.setTreat(tokens[iTREAT]);
                BR.setRx(tokens[iRX]);

                //add the current ubv into a list, and multicast them to all process later
                recordList.add(BR);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        Iterator<BlockRecord> iterator;
        // Stream for sending blockRecord in json format
        PrintStream toUVBserver = null;
        try {
            for (int i = 0; i < numProcesses; i++) {// Send some sample Unverified Blocks (UVBs) to each process
                System.out.println("Sending UVBs to process " + i + "...");
                iterator = recordList.iterator();
                while (iterator.hasNext()) {
                    //sending all uvbs which have been generated by this process to all process
                    UVBsock = new Socket(serverName, Ports.UnverifiedBlockServerPortBase + (i * 1000));
                    tempRec = iterator.next();

                    //make digital signature for the current block.
                    MessageDigest md = MessageDigest.getInstance("SHA-256");
                    md.update(tempRec.getBlockID().getBytes());
                    byte byteData[] = md.digest();
                    byte[] digitalSignature = signData(byteData, keyPair.getPrivate());
                    String s = Base64.getEncoder().encodeToString(digitalSignature);
                    tempRec.setSignature(s);

                    String jT = new Gson().toJson(tempRec);
                    toUVBserver = new PrintStream(UVBsock.getOutputStream());
                    toUVBserver.println(jT + bc.PID);
                    toUVBserver.flush();
                    UVBsock.close();
                }
            }
        } catch (Exception x) {
            x.printStackTrace();
        }

    }

    public static byte[] signData(byte[] data, PrivateKey key) throws Exception {
        Signature signer = Signature.getInstance("SHA1withRSA");
        signer.initSign(key);
        signer.update(data);
        return (signer.sign());
    }

    public static void main(String[] args) throws IOException {
        bc s = new bc();
        s.run(args);
    }


    //receive the running signal from process2 to start the whole system
    class RunningWorker extends Thread {
        Socket sock;

        RunningWorker(Socket s) {
            sock = s;
        }

        public void run() {
            try {
                //send public keys to all process
                KeySend();
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {
                }
                //send uvbs to all process
                new bc().UnverifiedSend();
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {
                }
                //start to work
                new Thread(new UnverifiedBlockConsumer(ourPriorityQueue)).start(); // Start consuming the queued-up unverified blocks
                sock.close();
            } catch (IOException x) {
                x.printStackTrace();
            }
        }
    }

    //be prepared to receive the running signal from process2
    class RunningServer implements Runnable {
        public void run() {
            int q_len = 6; /* Number of requests for OpSys to queue */
            Socket sock;
            try {
                ServerSocket servsock = new ServerSocket(Ports.WaitingforRunningPort, q_len);
                sock = servsock.accept();
                new RunningWorker(sock).start();
            } catch (IOException ioe) {
                System.out.println(ioe);
            }
        }
    }

    public void run(String[] args) throws IOException {
        System.out.println("Running now\n");
        //setup the process id based on the command line
        PID = (args.length < 1) ? 0 : Integer.parseInt(args[0]); // Process ID is passed to the JVM
        System.out.println("Zishi's Block Coordination Framework. Use Control-C to stop the process.\n");
        System.out.println("ProcessID " + PID + " is running " + "\n");
        new Ports().setPorts(); // Establish OUR port number scheme, based on PID

        //setup which file this process is going to read in
        switch (PID) {
            case 1:
                FILENAME = "BlockInput1.txt";
                break;
            case 2:
                FILENAME = "BlockInput2.txt";
                break;
            default:
                FILENAME = "BlockInput0.txt";
                break;
        }
        System.out.println("Using input file: " + FILENAME);

        // New thread to process incoming public keys
        new Thread(new PublicKeyServer()).start();
        // New thread to process incoming unverified blocks
        new Thread(new UnverifiedBlockServer(ourPriorityQueue)).start();
        // New thread to process incomming new blockchains
        new Thread(new BlockchainServer()).start();
        // New thread to start the whole system
        new Thread(new RunningServer()).start();

        //wait for all servers to run
        try {
            Thread.sleep(1000);
        } catch (Exception e) {
        }

        // use proc2 as the trigger to start the events
        if (PID == 2) {
            runEngine();
        } else {
            System.out.println("Waiting for Process2");
        }
    }
}
