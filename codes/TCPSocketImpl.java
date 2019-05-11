import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.Random;

public class TCPSocketImpl extends TCPSocket {

    protected EnhancedDatagramSocket socket;

    public TCPSocketImpl(String ip, int port) throws Exception {
        super(ip, port);
        double randomDouble = Math.random();
        randomDouble = randomDouble * 64536 + 1000;
        int randomInt = (int) randomDouble;
        socket = new EnhancedDatagramSocket(randomInt);
        InetAddress IPAddress = InetAddress.getByName(ip);
        byte[] tcpPacket = new TCPPacket(new byte[0], randomInt, port, 1, -1, 1, 0, 0).pack();
        DatagramPacket packet = new DatagramPacket(tcpPacket, tcpPacket.length, IPAddress, port);
        socket.send(packet);

        byte[] receiveData = new byte[20];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        socket.receive(receivePacket);
        TCPPacket r = new TCPPacket(receiveData);
        System.out.println(r.getACK() + r.getSYN());

        tcpPacket = new TCPPacket(new byte[0], randomInt, port, 2, 2, 0, 0, 0).pack();
        packet = new DatagramPacket(tcpPacket, tcpPacket.length, IPAddress, port);
        socket.send(packet);
    }

    public TCPSocketImpl(EnhancedDatagramSocket socket) throws Exception {
        super(socket);
        this.socket = socket;
    }

    @Override
    public void send(String pathToFile) throws Exception {
        throw new RuntimeException("Not implemented!");
    }

    @Override
    public void receive(String pathToFile) throws Exception {
        throw new RuntimeException("Not implemented!");
    }

    @Override
    public void close() throws Exception {
        throw new RuntimeException("Not implemented!");
    }

    @Override
    public long getSSThreshold() {
        throw new RuntimeException("Not implemented!");
    }

    @Override
    public long getWindowSize() {
        throw new RuntimeException("Not implemented!");
    }
}
