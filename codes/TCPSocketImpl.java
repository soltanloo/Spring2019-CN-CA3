import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TCPSocketImpl extends TCPSocket {

    protected EnhancedDatagramSocket socket;
    private int SSThreshold;
    private int windowSize = 5;
    private int seqNum;
    private InetAddress dstIP;
    private int dstPort;
    private int srcPort;

    public TCPSocketImpl(String ip, int port) throws Exception {
        //TODO: check seqNums and ackNums;
        super(ip, port);
        double randomDouble = Math.random();
        randomDouble = randomDouble * 64536 + 1000;
        int randomInt = (int) randomDouble;
        srcPort = randomInt;
        seqNum = new Random().nextInt();
        socket = new EnhancedDatagramSocket(randomInt);
        InetAddress IPAddress = InetAddress.getByName(ip);
        dstIP = IPAddress;
        dstPort = port;
        byte[] tcpPacket = new TCPPacket(new byte[0], randomInt, port, seqNum, -1, 1, 0, 0).pack();
        DatagramPacket packet = new DatagramPacket(tcpPacket, tcpPacket.length, IPAddress, port);
        socket.send(packet);
        seqNum++;

        byte[] receiveData = new byte[20];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        socket.receive(receivePacket);
        TCPPacket r = new TCPPacket(receiveData);


        tcpPacket = new TCPPacket(new byte[0], randomInt, port, seqNum, r.getSeqNum() + 1, 0, 0, 0).pack();
        packet = new DatagramPacket(tcpPacket, tcpPacket.length, IPAddress, port);
        socket.send(packet);
        seqNum++;
    }

    public TCPSocketImpl(EnhancedDatagramSocket socket, int seqNum) throws Exception {
        super(socket);
        this.socket = socket;
        this.seqNum = seqNum;
    }

    @Override
    public void send(String pathToFile) throws Exception {
        socket.setSoTimeout(1000);
        int base = 0;
        int nextToSend = 0;
        ArrayList<byte[]> chunks = new ArrayList<>();

        FileInputStream file = new FileInputStream(pathToFile);
        byte[] chunk = new byte[socket.getPayloadLimitInBytes() - 20];
        while((file.read(chunk))!=-1){
            chunks.add(chunk);
        }
        file.close();

        while (nextToSend < chunks.size()) {
            for (int i = 0; i < windowSize; i++) {
                byte[] chunkToSend = chunks.get(nextToSend);
                byte[] packet = new TCPPacket(chunkToSend, srcPort, dstPort, seqNum,
                        -1, 0, 0, chunkToSend.length).pack();
                DatagramPacket dp = new DatagramPacket(packet, packet.length, dstIP, dstPort);
                socket.send(dp);
                nextToSend += 1;
                seqNum += chunkToSend.length;
                System.out.println(base);
                System.out.println(nextToSend);
                System.out.println(seqNum);
                if (nextToSend >= chunks.size())
                    break;
            }

            try {
                byte[] receiveData = new byte[20];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                socket.receive(receivePacket);
                TCPPacket r = new TCPPacket(receiveData);
//                if (r.getACK() > base) {
//                    base = r.getACK() + 1;
//                }

            } catch (SocketTimeoutException ste) {
                //TODO: handle timeout;
//                nextToSend = base;
            }



        }


    }

    @Override
    public void receive(String pathToFile) throws Exception {
        byte[] receiveData = new byte[1500];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        while (true) {
            socket.receive(receivePacket);
            TCPPacket r = new TCPPacket(receiveData);
            System.out.println(r.toString());
        }
    }

    @Override
    public void close() throws Exception {
        socket.close();
    }

    @Override
    public long getSSThreshold() {
        return SSThreshold;
    }

    @Override
    public long getWindowSize() {
        return windowSize;
    }
}
