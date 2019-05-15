import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.Random;

public class TCPServerSocketImpl extends TCPServerSocket {

    private EnhancedDatagramSocket socket;
    private int srcPort;
    private int dstPort;

    public TCPServerSocketImpl(int port) throws Exception {
        super(port);
        srcPort = port;
        socket = new EnhancedDatagramSocket(port);
    }

    @Override
    public TCPSocket accept() throws Exception {
        //TODO: check seqNums and ackNums;
        byte[] receiveData = new byte[20];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        socket.receive(receivePacket);
        dstPort = receivePacket.getPort();
        InetAddress dstAddr = receivePacket.getAddress();
        TCPPacket r = new TCPPacket(receiveData);
        int seqNum = Math.abs(new Random().nextInt());

        byte[] tcpPacket = new TCPPacket(new byte[0], srcPort, dstPort,
                seqNum, r.getSeqNum() + 1, 1, 0, 0).pack();
        DatagramPacket packet = new DatagramPacket(tcpPacket, tcpPacket.length, dstAddr, dstPort);
        socket.send(packet);

        receiveData = new byte[20];
        receivePacket = new DatagramPacket(receiveData, receiveData.length);
        socket.receive(receivePacket);
        r = new TCPPacket(receiveData);


        return new TCPSocketImpl(socket, seqNum, r.getSeqNum() + 1);
    }

    @Override
    public void close() {
        socket.close();
    }
}
