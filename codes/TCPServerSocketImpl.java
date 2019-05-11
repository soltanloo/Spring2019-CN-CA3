import java.net.DatagramPacket;
import java.net.InetAddress;

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
        byte[] receiveData = new byte[20];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        socket.receive(receivePacket);
        dstPort = receivePacket.getPort();
        InetAddress dstAddr = receivePacket.getAddress();

        byte[] tcpPacket = new TCPPacket(new byte[0], srcPort, dstPort,
                1, 2, 1, 0, 0).pack();
        DatagramPacket packet = new DatagramPacket(tcpPacket, tcpPacket.length, dstAddr, dstPort);
        socket.send(packet);

        receiveData = new byte[20];
        receivePacket = new DatagramPacket(receiveData, receiveData.length);
        socket.receive(receivePacket);

        return new TCPSocketImpl(socket);
    }

    @Override
    public void close() {
        socket.close();
    }
}
