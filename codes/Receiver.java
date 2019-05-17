import java.io.IOException;
import java.net.DatagramPacket;

public class Receiver {
    public static void main(String[] args) throws Exception {
        TCPServerSocket tcpServerSocket = new TCPServerSocketImpl(12345);
        TCPSocket tcpSocket = tcpServerSocket.accept();
        tcpSocket.receive("1MB_rec.txt");
        tcpSocket.close();
        tcpServerSocket.close();
    }
}
