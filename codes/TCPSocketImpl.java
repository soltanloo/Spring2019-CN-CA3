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
    public enum State {
        SLOW_START, CONGESTION_AVOIDANCE, FAST_RECOVERY
    }
    private int SSThreshold = 64;
    private float windowSize = 1;
    private int seqNum;
    private int expectedSeqNum;
    private InetAddress dstIP;
    private int dstPort;
    private int srcPort;
    private State CCState = State.SLOW_START;

    public TCPSocketImpl(String ip, int port) throws Exception {
        //TODO: check seqNums and ackNums;
        super(ip, port);
        srcPort = Math.abs(new Random().nextInt(65536));
        System.out.println("PORT " + srcPort);
        seqNum = Math.abs(new Random().nextInt());
        socket = new EnhancedDatagramSocket(srcPort);
        InetAddress IPAddress = InetAddress.getByName(ip);
        dstIP = IPAddress;
        dstPort = (short) port;
        byte[] tcpPacket = new TCPPacket(new byte[0], srcPort, dstPort, seqNum, -1, 1, 0, 0).pack();
        DatagramPacket packet = new DatagramPacket(tcpPacket, tcpPacket.length, IPAddress, port);
        socket.send(packet);
        seqNum++;

        byte[] receiveData = new byte[20];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        socket.receive(receivePacket);
        TCPPacket r = new TCPPacket(receiveData);


        tcpPacket = new TCPPacket(new byte[0], srcPort, dstPort, seqNum, r.getSeqNum() + 1, 0, 0, 0).pack();
        packet = new DatagramPacket(tcpPacket, tcpPacket.length, IPAddress, port);
        socket.send(packet);
        seqNum++;

        onWindowChange();
    }

    public TCPSocketImpl(EnhancedDatagramSocket socket, int seqNum, int expectedSeqNum) throws Exception {
        super(socket);
        this.socket = socket;
        this.seqNum = seqNum;
        this.expectedSeqNum = expectedSeqNum;
    }

    @Override
    public void send(String pathToFile) throws Exception {
//        socket.setSoTimeout(1000);
        int base = 0;
        int nextToSend = 0;
        boolean dupAck = false;
        int lastAckNum = -1;
        int dupAckCount = 0;
        ArrayList<byte[]> chunks = new ArrayList<>();

        FileInputStream file = new FileInputStream(pathToFile);
        byte[] chunk = new byte[socket.getPayloadLimitInBytes() - 20];
        while((file.read(chunk))!=-1){
            chunks.add(chunk);
        }
        file.close();

        while (nextToSend < chunks.size()) {
            for (int i = nextToSend; i < base + windowSize; i++) {
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

            Boolean timedOut = false;
            try {
                byte[] receiveData = new byte[20];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                socket.receive(receivePacket);
                TCPPacket r = new TCPPacket(receiveData);
                dupAck = lastAckNum == r.getAckNum();
                lastAckNum = r.getAckNum();
                System.out.println(r.toString());
                //FIXME
                base++;

            } catch (SocketTimeoutException ste) {
                //TODO: handle timeout;
                nextToSend = base;
                timedOut = true;
            }

            switch (CCState) {
                case SLOW_START:
                    if (timedOut) {
                        SSThreshold = (int) windowSize / 2;
                        windowSize = 1;
                        dupAckCount = 0;
                    } else if (dupAckCount == 3) {
                        SSThreshold = (int) windowSize / 2;
                        windowSize = SSThreshold + 3;
                    } else if (dupAck) {
                        dupAckCount++;
                    } else if (!dupAck) {
                        windowSize++;
                        dupAckCount = 0;
                    } else if (windowSize >= SSThreshold) {
                        CCState = State.CONGESTION_AVOIDANCE;
                    }
                    break;
                case CONGESTION_AVOIDANCE:
                    if (timedOut) {
                        SSThreshold = (int) windowSize / 2;
                        windowSize = 1;
                        dupAckCount = 0;
                    } else if (dupAckCount == 3) {
                        SSThreshold = (int) windowSize / 2;
                        windowSize = SSThreshold + 3;
                    } else if (dupAck) {
                        dupAckCount++;
                    } else if (!dupAck) {
                        windowSize += 1 / windowSize;
                        dupAckCount = 0;
                    }
                    break;
                case FAST_RECOVERY:
                    if (timedOut) {
                        SSThreshold = (int) windowSize / 2;
                        windowSize = 1;
                        dupAckCount = 0;
                    } else if (dupAck) {
                        dupAckCount++;
                        windowSize++;
                    } else if (!dupAck) {
                        windowSize = SSThreshold;
                        dupAckCount = 0;
                    }
                    break;
            }
        }
    }

    @Override
    public void receive(String pathToFile) throws Exception {
        //FIXME: rwnd;
        byte[] receiveData = new byte[1500];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        TCPPacket lastReceived, lastSent;
        lastSent = new TCPPacket(new byte[0], socket.getLocalPort(), receivePacket.getPort(),
                seqNum, -1, 0, 10, 0);
        while (true) {
            socket.receive(receivePacket);
            lastReceived = new TCPPacket(receiveData);
            System.out.println(lastReceived.toString());
            System.out.println("Expected seq: " + expectedSeqNum);
            if (lastReceived.getSeqNum() == expectedSeqNum) {
                lastSent = new TCPPacket(new byte[0], socket.getLocalPort(), receivePacket.getPort(),
                        seqNum, lastReceived.getSeqNum() + 1, 0, 10, 0);
                byte[] tcpPacket = lastSent.pack();
                DatagramPacket packet = new DatagramPacket(tcpPacket, tcpPacket.length,
                        receivePacket.getAddress(), receivePacket.getPort());
                socket.send(packet);
                expectedSeqNum += lastReceived.getDataLen();
                seqNum++;
            } else {
                //TODO: send a packet with ack for last correctly received packet;
                byte[] tcpPacket = lastSent.pack();
                DatagramPacket packet = new DatagramPacket(tcpPacket, tcpPacket.length,
                        receivePacket.getAddress(), receivePacket.getPort());
                socket.send(packet);
                seqNum++;
            }
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
        return (long) windowSize;
    }
}
